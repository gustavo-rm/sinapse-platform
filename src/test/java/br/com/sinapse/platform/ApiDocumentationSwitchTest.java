package br.com.sinapse.platform;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.DefaultBootstrapContext;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;

/**
 * Which configurations switch the API documentation on (ADR 0018): the local and test profiles,
 * and nothing else.
 *
 * <p>The configuration files are read by the same config-data loader Spring Boot runs at start-up,
 * with the base file and the profile's file in the precedence a real start gives them, and the
 * two properties are read back. No context, no database, no web server and no logging system are
 * started: whether a profile switches the documentation on is a fact about its file, and a whole
 * application would add a PostgreSQL and a port to prove nothing more. What the switch does to a
 * running application without a profile is {@code ApiDocumentationClosedByDefaultIntegrationTest}.
 *
 * <p>The environment variables and system properties of the JVM are left out, so that the answer
 * is the files' and not whatever the machine running the build happens to export.
 */
class ApiDocumentationSwitchTest {

    private static final String API_DOCS = "springdoc.api-docs.enabled";

    private static final String SWAGGER_UI = "springdoc.swagger-ui.enabled";

    @Test
    void theBaseConfigurationLeavesTheDocumentationOff() {
        StandardEnvironment environment = configuration();

        assertThat(environment.getProperty(API_DOCS, Boolean.class)).as(API_DOCS).isFalse();
        assertThat(environment.getProperty(SWAGGER_UI, Boolean.class)).as(SWAGGER_UI).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "test"})
    void theProfileSwitchesTheDocumentationOn(String profile) {
        StandardEnvironment environment = configuration(profile);

        assertThat(environment.getProperty(API_DOCS, Boolean.class))
                .as("%s under the %s profile", API_DOCS, profile)
                .isTrue();
        assertThat(environment.getProperty(SWAGGER_UI, Boolean.class))
                .as("%s under the %s profile", SWAGGER_UI, profile)
                .isTrue();
    }

    /** The configuration files alone, as Spring Boot loads them for the given profiles. */
    private static StandardEnvironment configuration(String... profiles) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources()
                .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources()
                .remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        ConfigDataEnvironmentPostProcessor.applyTo(environment, new DefaultResourceLoader(),
                new DefaultBootstrapContext(), profiles);
        return environment;
    }
}
