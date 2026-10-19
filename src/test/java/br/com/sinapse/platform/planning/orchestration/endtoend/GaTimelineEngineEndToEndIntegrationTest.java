package br.com.sinapse.platform.planning.orchestration.endtoend;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** {@code ga-timeline}, the genetic algorithm on the timeline chromosome, named by configuration against the real core. */
class GaTimelineEngineEndToEndIntegrationTest extends EngineMatrixEndToEnd {

    private static final String ENGINE = "ga-timeline";

    @DynamicPropertySource
    static void namesTheEngine(DynamicPropertyRegistry registry) {
        deployment(registry, ENGINE);
    }

    @Override
    String engine() {
        return ENGINE;
    }
}
