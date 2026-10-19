package br.com.sinapse.platform.planning.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.sinapse.platform.planning.internal.config.AlgorithmParamsStartupCheck;
import br.com.sinapse.platform.planning.internal.config.PlanningProperties;
import java.io.IOException;
import java.time.Duration;
import java.time.Period;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * How a deployment chooses the core's engine, and what it cannot get past startup.
 *
 * <p>The engine travels as {@code algorithmParams.engine}, a key of the open parameter map, and
 * is chosen per deployment, never per request or per student. These tests start a real Spring
 * application around {@link PlanningProperties} — the binder, the config-data loader and the
 * environment post-processors are the ones production runs — and feed it the three ways an
 * operator has of setting the key: a configuration file, {@code SPRING_APPLICATION_JSON} and a
 * plain environment variable.
 *
 * <p>The environment variables are not the JVM's own, which a test cannot set. They are put in
 * a {@link SystemEnvironmentPropertySource} under the name Spring gives the real one, which is
 * the same class, the same name and therefore the same relaxed-name mapping that
 * {@code System.getenv()} goes through in production. Only where the map comes from differs.
 */
class AlgorithmParamsConfigurationTest {

    private static final String ENGINE_VARIABLE = "SINAPSE_PLANNING_GENERATION_ALGORITHMPARAMS_ENGINE";

    @Test
    void anEngineNamedInAConfigurationFileReachesTheMap() {
        assertThat(paramsFrom("engine-named.yml", Map.of()))
                .containsEntry("engine", "ga-timeline");
    }

    @Test
    void anEngineNamedInSpringApplicationJsonReachesTheMap() {
        assertThat(paramsFrom("nothing.yml", Map.of("SPRING_APPLICATION_JSON",
                "{\"sinapse\":{\"planning\":{\"generation\":{\"algorithm-params\":"
                        + "{\"engine\":\"ga\"}}}}}")))
                .containsEntry("engine", "ga");
    }

    @Test
    void anEngineNamedInAnEnvironmentVariableReachesTheMap() {
        assertThat(paramsFrom("nothing.yml", Map.of(ENGINE_VARIABLE, "ga-timeline")))
                .as("relaxed binding maps the variable onto the key of the map: \"engine\" has "
                        + "no hyphen, so nothing is lost on the way")
                .containsEntry("engine", "ga-timeline");
    }

    @Test
    void aHyphenatedKeyCannotBeWrittenAsAnEnvironmentVariable() {
        assertThat(paramsFrom("nothing.yml",
                Map.of("SINAPSE_PLANNING_GENERATION_ALGORITHMPARAMS_POPULATIONSIZE", "40")))
                .as("a variable has no way to say where the hyphen was, so the key arrives as "
                        + "another key: what the documentation has to warn about")
                .doesNotContainKey("population-size")
                .containsKey("populationsize");
    }

    @Test
    void withNoEngineConfiguredTheMapHasNoEngineKey() {
        assertThat(paramsFrom("nothing.yml", Map.of()))
                .as("absent, so the core runs its default; never present and empty")
                .doesNotContainKey("engine");
    }

    @Test
    void yamlWithTheKeyAndNoValueIsReadAsAnEmptyString() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load("engine-empty",
                new ClassPathResource("algorithm-params/engine-empty.yml"));

        assertThat(sources.get(0).getProperty("sinapse.planning.generation.algorithm-params.engine"))
                .as("what the configuration file hands the binder for \"engine:\"")
                .hasToString("");
    }

    @Test
    void anEmptyEngineInTheConfigurationFileStopsStartup() {
        assertRefusedAtStartup("engine-empty.yml", Map.of(), "algorithm-params.engine");
    }

    @Test
    void yamlsExplicitNullStopsStartup() {
        assertRefusedAtStartup("engine-tilde.yml", Map.of(), "algorithm-params.engine");
    }

    @Test
    void anEmptyEngineInAnEnvironmentVariableStopsStartup() {
        assertRefusedAtStartup("nothing.yml", Map.of(ENGINE_VARIABLE, ""),
                "algorithm-params.engine");
    }

    @Test
    void aNullEngineInSpringApplicationJsonStopsStartup() {
        assertRefusedAtStartup("nothing.yml", Map.of("SPRING_APPLICATION_JSON",
                "{\"sinapse\":{\"planning\":{\"generation\":{\"algorithm-params\":"
                        + "{\"engine\":null}}}}}"), "algorithm-params.engine");
    }

    @Test
    void anEngineThatIsNotTextStopsStartup() {
        assertRefusedAtStartup("engine-number.yml", Map.of(), "algorithm-params.engine");
    }

    @Test
    void anEngineOutsideLowercaseDigitsAndHyphensStopsStartup() {
        assertRefusedAtStartup("engine-uppercase.yml", Map.of(), "algorithm-params.engine");
    }

    @Test
    void anyOtherKeyWithoutAValueStopsStartupToo() {
        assertRefusedAtStartup("other-key-empty.yml", Map.of(), "algorithm-params.importance");
    }

    @Test
    void theRecordRefusesANullValueWhereverItIsBuilt() {
        HashMap<String, Object> params = new HashMap<>();
        params.put("engine", null);

        assertThatThrownBy(() -> generationWith(params))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("algorithm-params.engine");
    }

    @Test
    void theRecordRefusesAnEmptyTextValue() {
        assertThatThrownBy(() -> generationWith(Map.of("importance", "")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("algorithm-params.importance");
    }

    @Test
    void theRecordRefusesAnEmptyKey() {
        assertThatThrownBy(() -> generationWith(Map.of(" ", "x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty key");
    }

    @Test
    void theRecordAcceptsAnEmptyMapAndAnyEngineNameOfTheRightShape() {
        assertThat(generationWith(Map.of()).algorithmParams()).isEmpty();
        assertThat(generationWith(Map.of("engine", "an-engine-nobody-registered-2"))
                .algorithmParams())
                .as("which names exist is the core's to say, not this side's")
                .containsEntry("engine", "an-engine-nobody-registered-2");
    }

    private static PlanningProperties.Generation generationWith(Map<String, Object> params) {
        return new PlanningProperties.Generation(Period.ofWeeks(4), 3,
                Duration.ofSeconds(30), Duration.ofMinutes(2), 1, "-",
                Duration.ofDays(90), params, 5, 0.5, 2.0);
    }

    private static Map<String, Object> paramsFrom(String file, Map<String, Object> variables) {
        try (ConfigurableApplicationContext context = start(file, variables)) {
            return context.getBean(PlanningProperties.class).generation().algorithmParams();
        }
    }

    private static void assertRefusedAtStartup(String file, Map<String, Object> variables,
            String named) {
        assertThatThrownBy(() -> start(file, variables).close())
                .as("the application does not start")
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(named);
    }

    private static ConfigurableApplicationContext start(String file,
            Map<String, Object> variables) {
        return new SpringApplicationBuilder(BindsPlanningProperties.class)
                .web(WebApplicationType.NONE)
                .logStartupInfo(false)
                .environment(new StandardEnvironment() {
                    @Override
                    protected void customizePropertySources(MutablePropertySources sources) {
                        super.customizePropertySources(sources);
                        sources.replace(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                                new SystemEnvironmentPropertySource(
                                        SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variables));
                    }
                })
                .properties("spring.config.location=classpath:/algorithm-params/" + file,
                        "spring.main.banner-mode=off")
                .run();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PlanningProperties.class)
    @Import(AlgorithmParamsStartupCheck.class)
    static class BindsPlanningProperties {
    }
}
