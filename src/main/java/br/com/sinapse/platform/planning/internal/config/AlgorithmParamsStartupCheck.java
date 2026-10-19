package br.com.sinapse.platform.planning.internal.config;

import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Component;

/**
 * Refuses to start when a parameter for the core was set to {@code null}.
 *
 * <p>{@link PlanningProperties.Generation} refuses a missing value it can see. This covers the
 * one it cannot: a {@code null} written in {@code SPRING_APPLICATION_JSON}
 * ({@code "engine": null}) stays in the environment as a name without a value, and the binder
 * skips it, so the key is simply absent from the map and the core runs its default engine. That
 * is the default used in silence, which the core itself refuses to do: it answers a
 * {@code null} engine with {@code 400}, not with its default. Whoever wrote the {@code null}
 * meant something, and the place to hear about it is startup, not the record of every run.
 */
@Component
public class AlgorithmParamsStartupCheck {

    private static final ConfigurationPropertyName ALGORITHM_PARAMS =
            ConfigurationPropertyName.of("sinapse.planning.generation.algorithm-params");

    /**
     * @param properties  the bound configuration, required so that binding has already run
     * @param environment every source the configuration was bound from
     */
    public AlgorithmParamsStartupCheck(PlanningProperties properties,
            ConfigurableEnvironment environment) {
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (source instanceof EnumerablePropertySource<?> enumerable) {
                for (String name : enumerable.getPropertyNames()) {
                    if (enumerable.getProperty(name) == null && isAlgorithmParam(name)) {
                        throw new IllegalArgumentException(name + " is set to null; remove the "
                                + "key to leave it to the core's default");
                    }
                }
            }
        }
    }

    private static boolean isAlgorithmParam(String name) {
        ConfigurationPropertyName adapted = ConfigurationPropertyName.adapt(name, '.');
        return ALGORITHM_PARAMS.isAncestorOf(adapted);
    }
}
