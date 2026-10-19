package br.com.sinapse.platform.planning.orchestration.endtoend;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** {@code greedy-baseline}, the deterministic heuristic, the core's default, named by configuration against the real core. */
class GreedyBaselineEngineEndToEndIntegrationTest extends EngineMatrixEndToEnd {

    private static final String ENGINE = "greedy-baseline";

    @DynamicPropertySource
    static void namesTheEngine(DynamicPropertyRegistry registry) {
        deployment(registry, ENGINE);
    }

    @Override
    String engine() {
        return ENGINE;
    }
}
