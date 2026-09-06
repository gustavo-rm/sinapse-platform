package br.com.sinapse.platform.identity.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.identity.internal.domain.ConsentRecord;
import br.com.sinapse.platform.identity.internal.service.ConsentService;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Invariant 1 has exactly one keeper.
 *
 * <p>"An active account has a valid consent for every essential purpose, granted by whoever
 * the holder's age at the time made appropriate" crosses two aggregates, and section 5.3
 * settles how a monolith over one database holds such a thing: one service writes both,
 * inside one transaction, and the logic is not distributed. That is a claim about the whole
 * module, not about one class, so it cannot be checked by reading one class.
 *
 * <p>What is enforced here is the shape that makes the claim true. Only
 * {@link ConsentService} writes a consent record, and only it moves an account between
 * states. Everything else — registration, verification, the daily sweep, reaffirmation —
 * asks it to.
 *
 * <p>Writing the password hash is deliberately not on the list. It is a write to the
 * account, but it is not a transition, and it cannot put the invariant in question.
 */
class ConsentServiceIsTheOnlyWriterTest {

    private static final String APPLICATION_PACKAGE = "br.com.sinapse.platform";

    /** The methods that move an account between states. */
    private static final Set<String> TRANSITIONS =
            Set.of("activate", "awaitGuardianConsent", "suspend", "anonymize");

    private static final Set<String> PERSISTING = Set.of("save", "saveAll", "saveAndFlush");

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(APPLICATION_PACKAGE);

    @Test
    void onlyTheConsentServiceMovesAnAccountBetweenStates() {
        List<String> callers = callersOf(Account.class.getName(), TRANSITIONS);

        assertThat(callers)
                .as("a second place that suspends or activates an account is a second place where "
                        + "invariant 1 can be broken, and the whole design of section 5.3 is that "
                        + "there is only one")
                .isNotEmpty()
                .allSatisfy(caller -> assertThat(caller).startsWith(ConsentService.class.getName()));
    }

    @Test
    void onlyTheConsentServiceWritesAConsentRecord() {
        List<String> callers = new ArrayList<>();

        for (JavaClass type : PRODUCTION_CLASSES) {
            for (JavaMethod method : type.getMethods()) {
                for (JavaMethodCall call : method.getMethodCallsFromSelf()) {
                    if (writesAConsentRecord(call)) {
                        callers.add(method.getFullName());
                    }
                }
            }
        }

        assertThat(callers)
                .as("the grantor of a consent is derived from the holder's age; a second writer "
                        + "would be a second chance to derive it differently")
                .isNotEmpty()
                .allSatisfy(caller -> assertThat(caller).startsWith(ConsentService.class.getName()));
    }

    @Test
    void onlyTheConsentServiceRevokesAConsentRecord() {
        List<String> callers = callersOf(ConsentRecord.class.getName(), Set.of("revoke"));

        assertThat(callers)
                .as("revoking an essential purpose has to suspend the account in the same "
                        + "transaction, which only the class that does both can guarantee")
                .isNotEmpty()
                .allSatisfy(caller -> assertThat(caller).startsWith(ConsentService.class.getName()));
    }

    private static List<String> callersOf(String ownerName, Set<String> methodNames) {
        List<String> callers = new ArrayList<>();
        for (JavaClass type : PRODUCTION_CLASSES) {
            for (JavaMethod method : type.getMethods()) {
                for (JavaMethodCall call : method.getMethodCallsFromSelf()) {
                    if (call.getTargetOwner().getName().equals(ownerName)
                            && methodNames.contains(call.getTarget().getName())) {
                        callers.add(method.getFullName());
                    }
                }
            }
        }
        return callers;
    }

    /**
     * A call that persists a consent record.
     *
     * <p>Recognised by the repository it goes through rather than by the argument, because the
     * argument's type is erased at the call site of a generic method.
     */
    private static boolean writesAConsentRecord(JavaMethodCall call) {
        return call.getTargetOwner().getName().endsWith("ConsentRecordRepository")
                && PERSISTING.contains(call.getTarget().getName());
    }
}
