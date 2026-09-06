package br.com.sinapse.platform.datarights.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.sinapse.platform.IntegrationTest;
import br.com.sinapse.platform.shared.datarights.ModuleDataRights;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A module that holds personal data and does not participate in erasure is a silent leak.
 *
 * <p>ADR 0011 recommends a test for it and the reason is not hypothetical: it will happen the
 * first time somebody adds a module in a hurry, and nothing else in the build would notice. An
 * account would be erased, the new module would keep its rows, and the only symptom would be
 * data that should not exist.
 *
 * <p><strong>The list of modules that owe an implementation is derived, not written down.</strong>
 * It comes from the foreign keys in the schema: any table that references {@code account} holds
 * something about a person, whatever it is called and whoever added it. A hand-maintained list
 * would be one more thing to forget, and forgetting it is the failure this test exists to catch.
 */
class EveryModuleWithPersonalDataParticipatesTest extends IntegrationTest {

    private static final String APPLICATION_PACKAGE = "br.com.sinapse.platform";

    /**
     * The coordinator's own table.
     *
     * <p>{@code erasure_request} references an account and holds nothing about the person: no
     * copy of what was erased and no e-mail, by design, because it is the one row built to
     * outlive the erasure. The coordinator sequences the modules and is not one of them.
     */
    private static final String COORDINATOR_MODULE = "datarights";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private List<ModuleDataRights> registered;

    @Test
    void everyModuleWhoseTablesReferenceAnAccountAnswersForItsOwnData() {
        Map<String, String> owners = tableOwners();
        Set<String> participating = registered.stream()
                .map(ModuleDataRights::moduleName)
                .collect(Collectors.toSet());

        List<String> unmapped = tablesReferencingAccount().stream()
                .filter(table -> !owners.containsKey(table))
                .toList();
        assertThat(unmapped)
                .as("every table in this schema is mapped by an entity of the module that owns "
                        + "it; one that is not cannot be attributed to anybody, and its erasure "
                        + "cannot be anybody's job")
                .isEmpty();

        List<String> silentLeaks = tablesReferencingAccount().stream()
                .map(owners::get)
                .filter(module -> !COORDINATOR_MODULE.equals(module))
                .distinct()
                .filter(module -> !participating.contains(module))
                .toList();

        assertThat(silentLeaks)
                .as("these modules hold rows that reference an account and have no registered "
                        + "ModuleDataRights, so an Article 18 erasure would leave their data "
                        + "behind and nothing would say so")
                .isEmpty();
    }

    @Test
    void theModulesThatDoAnswerAreTheOnesWithSomethingToAnswerFor() {
        Map<String, String> owners = tableOwners();
        Set<String> holdPersonalData = tablesReferencingAccount().stream()
                .map(owners::get)
                .collect(Collectors.toSet());

        assertThat(registered).extracting(ModuleDataRights::moduleName)
                .as("an implementation in a module that holds nothing about anybody would be a "
                        + "claim nobody checks; if one appears, either the module gained a table "
                        + "or the implementation is in the wrong place")
                .allSatisfy(module -> assertThat(holdPersonalData).contains(module));
    }

    /** The tables with a foreign key to {@code account}, asked of the database itself. */
    private List<String> tablesReferencingAccount() {
        return jdbc.queryForList("""
                select distinct child.relname as table_name
                  from pg_constraint constraint_
                  join pg_class child on child.oid = constraint_.conrelid
                  join pg_class parent on parent.oid = constraint_.confrelid
                 where constraint_.contype = 'f'
                   and parent.relname = 'account'
                 order by table_name
                """, String.class);
    }

    /**
     * Which module maps which table, read from the entities rather than from a list.
     *
     * <p>Element collections count: {@code account_role} is a table with a foreign key to an
     * account and is mapped by a field rather than by a class of its own.
     */
    private static Map<String, String> tableOwners() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));

        Map<String, String> owners = new LinkedHashMap<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(APPLICATION_PACKAGE)) {
            Class<?> entity = entityClass(definition.getBeanClassName());
            String module = moduleOf(entity);
            Table table = entity.getAnnotation(Table.class);
            if (table != null && !table.name().isBlank()) {
                owners.put(table.name().toLowerCase(Locale.ROOT), module);
            }
            for (Field field : entity.getDeclaredFields()) {
                CollectionTable collectionTable = field.getAnnotation(CollectionTable.class);
                if (collectionTable != null && !collectionTable.name().isBlank()) {
                    owners.put(collectionTable.name().toLowerCase(Locale.ROOT), module);
                }
            }
        }
        return owners;
    }

    private static Class<?> entityClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException missing) {
            throw new IllegalStateException("scanned an entity that cannot be loaded", missing);
        }
    }

    /** The module is the package segment after the application root. */
    private static String moduleOf(Class<?> entity) {
        String name = entity.getPackageName().substring(APPLICATION_PACKAGE.length() + 1);
        int end = name.indexOf('.');
        return end < 0 ? name : name.substring(0, end);
    }
}
