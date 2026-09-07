package br.com.sinapse.platform.readmodel.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/**
 * ADR 0013, third rule: the read models never write.
 *
 * <p>Stated as a rule because it is not enforceable by the module descriptor. Spring Modulith
 * checks which packages a module may reach, and every module's {@code api} is a legitimate
 * target — including, in principle, a method on it that changes something. What keeps this
 * component read-only is that it composes published reads and nothing else, and the thing most
 * likely to erode that is somebody adding "just one" write to a screen that already has all the
 * data in front of it.
 *
 * <p>Two checks. Nothing here may reach the infrastructure a write needs — a repository, an
 * entity manager, a JDBC template — and every transaction it opens must be read-only, so that
 * the database itself refuses a write that got past the first check. Between them, a write from
 * this package has to be added on purpose and against a failing build, rather than by accident.
 */
class ReadModelsNeverWriteTest {

    private static final String READ_MODEL = "br.com.sinapse.platform.readmodel";

    /** Ways of writing that a class here could otherwise reach for. */
    private static final List<String> WRITE_MACHINERY = List.of(
            "org.springframework.data.repository",
            "org.springframework.jdbc.core",
            "jakarta.persistence.EntityManager",
            "org.hibernate");

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(READ_MODEL);

    @Test
    void nothingHereCanReachTheMachineryAWriteWouldNeed() {
        List<String> offenders = PRODUCTION_CLASSES.stream()
                .filter(ReadModelsNeverWriteTest::touchesWriteMachinery)
                .map(JavaClass::getName)
                .toList();

        assertThat(offenders)
                .as("a read model composes each module's published reads. A repository or a "
                        + "JDBC template here is either a write, or this component reading a "
                        + "table that belongs to somebody else — and ADR 0013 forbids both")
                .isEmpty();
    }

    @Test
    void everyTransactionOpenedHereIsReadOnly() {
        List<String> offenders = PRODUCTION_CLASSES.stream()
                .flatMap(candidate -> candidate.getMethods().stream())
                .filter(method -> method.isAnnotatedWith(Transactional.class))
                .filter(method -> !method.getAnnotationOfType(Transactional.class).readOnly())
                .map(ReadModelsNeverWriteTest::describe)
                .toList();

        assertThat(offenders)
                .as("a writable transaction here is a write waiting to happen; read-only makes "
                        + "the database refuse one that got past the first check")
                .isEmpty();
    }

    private static boolean touchesWriteMachinery(JavaClass candidate) {
        return candidate.getDirectDependenciesFromSelf().stream()
                .map(dependency -> dependency.getTargetClass().getName())
                .anyMatch(target -> WRITE_MACHINERY.stream().anyMatch(target::startsWith));
    }

    private static String describe(JavaMethod method) {
        return method.getOwner().getName() + "#" + method.getName();
    }
}
