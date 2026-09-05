package br.com.sinapse.platform.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Description of the API published by springdoc.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    /**
     * @param version version of the running artifact
     * @return the document metadata
     */
    @Bean
    public OpenAPI sinapseOpenApi(@Value("${sinapse.api.version:v1}") String version) {
        return new OpenAPI().info(new Info()
                .title("Sinapse Platform API")
                .version(version)
                .description("""
                        Backend of the Sinapse platform: identity, curriculum, educational \
                        relationships, learning record and study planning.

                        Every route lives under /api/v1. Errors follow RFC 7807 and are \
                        served as application/problem+json. Instants are ISO-8601 with \
                        offset; local times are interpreted in the account's time zone.""")
                .license(new License().name("MIT")));
    }
}
