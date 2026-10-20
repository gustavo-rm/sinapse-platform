package br.com.sinapse.platform.planning.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.sinapse.platform.planning.support.DefaultConfiguration;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * The algorithm parameters of the default configuration: the base {@code application.yml}, no
 * profile.
 *
 * <p>The core owns its search parameters (its decision D4) and ignores {@code generations},
 * {@code population-size} and {@code mutation-rate} when they are sent, so the default
 * configuration sends none of them: its map has no entry at all. What a deployment adds to it,
 * and how it is refused at startup, is {@code AlgorithmParamsConfigurationTest}'s; here the same
 * mechanisms are checked against the real base file, starting from the empty map.
 */
class DefaultAlgorithmParamsConfigurationTest {

    private static final String ENGINE_VARIABLE = "SINAPSE_PLANNING_GENERATION_ALGORITHMPARAMS_ENGINE";

    private static final String ALGORITHM_PARAMS = "sinapse.planning.generation.algorithm-params";

    @Test
    void theBaseFileDeclaresNoAlgorithmParameterNotEvenAnEmptyKey() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));

        assertThat(sources).allSatisfy(source -> assertThat(
                ((EnumerablePropertySource<?>) source).getPropertyNames())
                .as("an \"algorithm-params:\" with nothing under it would be a value, not an "
                        + "empty map")
                .noneMatch(name -> name.startsWith(ALGORITHM_PARAMS)));
    }

    @Test
    void theDefaultConfigurationStartsWithAnEmptyMap() {
        assertThat(DefaultConfiguration.algorithmParams())
                .as("empty, not null and not missing: the core gets {} and runs its defaults")
                .isNotNull()
                .isEmpty();
    }

    @Test
    void anEngineFromAnEnvironmentVariableCreatesTheOnlyEntryOfTheEmptyMap() {
        assertThat(DefaultConfiguration.algorithmParams(Map.of(ENGINE_VARIABLE, "ga-timeline")))
                .as("relaxed binding creates the entry, there being none to land on")
                .isEqualTo(Map.of("engine", "ga-timeline"));
    }

    @Test
    void anEngineFromSpringApplicationJsonCreatesTheOnlyEntryOfTheEmptyMap() {
        assertThat(DefaultConfiguration.algorithmParams(Map.of("SPRING_APPLICATION_JSON",
                "{\"sinapse\":{\"planning\":{\"generation\":{\"algorithm-params\":"
                        + "{\"engine\":\"ga\"}}}}}")))
                .isEqualTo(Map.of("engine", "ga"));
    }

    @Test
    void anEmptyEngineIsStillRefusedAtStartupOverTheEmptyMap() {
        assertThatThrownBy(() -> DefaultConfiguration.start(Map.of(ENGINE_VARIABLE, "")).close())
                .as("the startup validation of SP-8 is unchanged")
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("algorithm-params.engine");
    }
}
