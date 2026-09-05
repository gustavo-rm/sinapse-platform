package br.com.sinapse.platform.shared;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

/**
 * ADR 0009: no code depends on the default zone of the JVM or of the server.
 *
 * <p>The rule is only meaningful if it is enforced. Every method forbidden here reads the
 * ambient zone or the ambient clock; the application asks the injected {@link Clock}
 * instead, which is fixed to the configured zone and can be driven by a test.
 *
 * <p>The overloads that take a {@code Clock} or an explicit {@code ZoneId} are untouched:
 * it is the implicit ones that are the defect.
 */
class DefaultTimeZoneIndependenceTest {

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("br.com.sinapse.platform");

    @Test
    void noCodeReadsTheAmbientClockOrZone() {
        noClasses()
                .should().callMethod(ZoneId.class, "systemDefault")
                .orShould().callMethod(TimeZone.class, "getDefault")
                .orShould().callMethod(Clock.class, "systemDefaultZone")
                .orShould().callMethod(Clock.class, "systemUTC")
                .orShould().callMethod(Instant.class, "now")
                .orShould().callMethod(LocalDate.class, "now")
                .orShould().callMethod(LocalDateTime.class, "now")
                .orShould().callMethod(LocalTime.class, "now")
                .orShould().callMethod(OffsetDateTime.class, "now")
                .orShould().callMethod(ZonedDateTime.class, "now")
                .orShould().callConstructor(Date.class)
                .because("""
                        instants must come from the injected Clock, which is fixed to the \
                        configured application zone; local times belonging to an account are \
                        interpreted in Account.timeZone (ADR 0009)""")
                .check(PRODUCTION_CLASSES);
    }
}
