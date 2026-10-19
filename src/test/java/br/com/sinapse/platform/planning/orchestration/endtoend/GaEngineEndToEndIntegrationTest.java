package br.com.sinapse.platform.planning.orchestration.endtoend;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** {@code ga}, the genetic algorithm on the SINAPSE contract, named by configuration against the real core. */
class GaEngineEndToEndIntegrationTest extends EngineMatrixEndToEnd {

    private static final String ENGINE = "ga";

    @DynamicPropertySource
    static void namesTheEngine(DynamicPropertyRegistry registry) {
        deployment(registry, ENGINE);
    }

    @Override
    String engine() {
        return ENGINE;
    }
}
