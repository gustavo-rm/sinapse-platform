package br.com.sinapse.platform.planning.support;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.sinapse.platform.planning.internal.config.AlgorithmParamsStartupCheck;
import br.com.sinapse.platform.planning.internal.config.PlanningProperties;
import java.util.Map;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * What the default configuration — the base {@code application.yml}, no profile — makes of the
 * planning settings, as production binds it.
 *
 * <p>The test profile declares algorithm parameters of its own to keep the suite fast, and a map
 * cannot lose a key to a property source laid over it: whatever is registered on top, the test
 * profile's keys stay. A test that needs the map a deployment really sends therefore asks for it
 * here and puts it in place of the bound one, instead of adding to it.
 *
 * <p>A real Spring application is started around {@link PlanningProperties}: the binder, the
 * config-data loader, the environment post-processors and {@link AlgorithmParamsStartupCheck} are
 * the ones production runs, so a map this returns is one production would have started with.
 * Only the environment variables are supplied, under the name Spring gives the real ones (see
 * {@code AlgorithmParamsConfigurationTest}).
 */
public final class DefaultConfiguration {

    private DefaultConfiguration() {
    }

    /**
     * @return the algorithm parameters of the default configuration with no environment at all
     */
    public static Map<String, Object> algorithmParams() {
        return algorithmParams(Map.of());
    }

    /**
     * @param environment environment variables of the deployment, by name
     * @return the algorithm parameters of the default configuration under that environment
     */
    public static Map<String, Object> algorithmParams(Map<String, ?> environment) {
        try (ConfigurableApplicationContext context = start(environment)) {
            assertThat(context.getEnvironment().getActiveProfiles())
                    .as("the default configuration is the one with no profile")
                    .isEmpty();
            return context.getBean(PlanningProperties.class).generation().algorithmParams();
        }
    }

    /**
     * Replaces the algorithm parameters of bound planning settings, as a deployment with that
     * map would have them.
     *
     * @param bound  the settings as bound
     * @param params the map in their place
     * @return the same settings with only the map changed
     */
    public static PlanningProperties withAlgorithmParams(PlanningProperties bound,
            Map<String, Object> params) {
        PlanningProperties.Generation configured = bound.generation();
        return new PlanningProperties(bound.maxScheduleWindow(), bound.maxPlanHistory(),
                new PlanningProperties.Generation(configured.horizon(), configured.maxAttempts(),
                        configured.retryBackoff(), configured.orphanMargin(),
                        configured.batchSize(), configured.pollCron(),
                        configured.historyWindow(), params,
                        configured.minCalibrationSessions(), configured.minEffortFactor(),
                        configured.maxEffortFactor()));
    }

    /**
     * Starts the application on the base file alone. Logging keeps the test profile's console
     * format: the application.yml one would switch the console of the whole test JVM to JSON,
     * and the map does not depend on it.
     */
    public static ConfigurableApplicationContext start(Map<String, ?> environment) {
        Map<String, Object> variables = Map.copyOf(environment);
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
                        sources.addFirst(new MapPropertySource("testConsole",
                                Map.of("logging.structured.format.console", "")));
                    }
                })
                .properties("spring.config.location=classpath:/application.yml",
                        "spring.main.banner-mode=off")
                .run();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PlanningProperties.class)
    @Import(AlgorithmParamsStartupCheck.class)
    static class BindsPlanningProperties {
    }
}
