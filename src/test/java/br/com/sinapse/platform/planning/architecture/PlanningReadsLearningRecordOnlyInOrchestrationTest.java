package br.com.sinapse.platform.planning.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Rule R2, stated where it can actually be checked.
 *
 * <p>"Planning does not read the learning record. Composition happens in
 * {@code planning.orchestration}." The module descriptor cannot express that: Spring Modulith
 * declares allowed targets per module, and {@code orchestration} is a sub-package of this
 * module rather than a module of its own. So the descriptor has to permit
 * {@code learningrecord :: api} for the whole of planning, and the narrower rule — the one the
 * architecture actually states — is enforced here.
 *
 * <p>What the rule buys is an acyclic dependency graph. The learning record is forbidden from
 * depending on planning by its own descriptor, and planning is forbidden from depending on the
 * learning record anywhere its aggregates live. One package composes the two, and it is the
 * one section 9.5 says exists for that purpose.
 */
class PlanningReadsLearningRecordOnlyInOrchestrationTest {

    private static final String APPLICATION_PACKAGE = "br.com.sinapse.platform";

    private static final String LEARNING_RECORD = "br.com.sinapse.platform.learningrecord";

    /** The one package allowed to know both contexts. */
    private static final String ORCHESTRATION = "br.com.sinapse.platform.planning.orchestration";

    private static final List<String> PLANNING_PROPER = List.of(
            "br.com.sinapse.platform.planning.api",
            "br.com.sinapse.platform.planning.internal");

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(APPLICATION_PACKAGE);

    @Test
    void nothingOutsideOrchestrationTouchesTheLearningRecord() {
        List<String> offenders = PRODUCTION_CLASSES.stream()
                .filter(PlanningReadsLearningRecordOnlyInOrchestrationTest::isPlanningProper)
                .filter(PlanningReadsLearningRecordOnlyInOrchestrationTest::readsLearningRecord)
                .map(JavaClass::getName)
                .toList();

        assertThat(offenders)
                .as("rule R2. A dependency from an aggregate of this module onto the learning "
                        + "record is the cycle the architecture is built to avoid, and it would "
                        + "also make a self-directed study session structurally different from "
                        + "a planned one")
                .isEmpty();
    }

    @Test
    void orchestrationIsWhereTheCompositionActuallyHappens() {
        List<String> composers = PRODUCTION_CLASSES.stream()
                .filter(candidate -> candidate.getPackageName().startsWith(ORCHESTRATION))
                .filter(PlanningReadsLearningRecordOnlyInOrchestrationTest::readsLearningRecord)
                .map(JavaClass::getName)
                .toList();

        assertThat(composers)
                .as("the exemption is not theoretical: if nothing composes the two contexts, "
                        + "this test is asserting a rule about a package that does nothing, and "
                        + "the first test above would pass for the wrong reason")
                .isNotEmpty();
    }

    private static boolean isPlanningProper(JavaClass candidate) {
        return PLANNING_PROPER.stream().anyMatch(candidate.getPackageName()::startsWith);
    }

    private static boolean readsLearningRecord(JavaClass candidate) {
        return candidate.getDirectDependenciesFromSelf().stream()
                .map(dependency -> dependency.getTargetClass().getName())
                .anyMatch(target -> target.startsWith(LEARNING_RECORD));
    }
}
