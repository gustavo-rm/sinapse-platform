package br.com.sinapse.platform;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Verifies the module boundaries described in section 3 of
 * {@code docs/ARQUITETURA_BACKEND_SINAPSE.md}.
 *
 * <p>Architecture rule R5: the boundaries are tested, not documented. This test fails
 * when a module reaches into another module's {@code internal} package, when the
 * dependency graph acquires a cycle, or when a class ends up outside the modules
 * altogether. When it fails, the design is what gets fixed — never the test.
 */
class ModularityTests {

    private static final ApplicationModules MODULES = ApplicationModules.of(PlatformApplication.class);

    @Test
    void modulesRespectTheirBoundaries() {
        MODULES.verify();
    }
}
