package br.com.sinapse.platform.coreclient.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration of the Sinapse Core adapter, bound from {@code sinapse.core}.
 *
 * @param baseUrl        where the core answers. No default: an address guessed in code is an
 *                       address nobody notices is wrong until a job fails
 * @param planPath       path of the generation endpoint on the core
 * @param connectTimeout how long to wait for the connection to be established
 * @param readTimeout    how long to wait for the answer. The optimisation is CPU-bound and
 *                       runs for seconds to minutes, so this is generous by nature; it is a
 *                       bound on a run that has hung, not on a run that is slow
 *
 * <p>The algorithm parameters are deliberately not here. What to ask the optimiser for is the
 * caller's decision and travels in the request; this adapter knows where the core is and how
 * long to wait for it, and nothing about what a good run looks like.
 */
@Validated
@ConfigurationProperties("sinapse.core")
public record CoreProperties(

        @NotBlank @DefaultValue("http://localhost:8090") String baseUrl,

        @NotBlank @DefaultValue("/plans") String planPath,

        @NotNull @DefaultValue("5s") Duration connectTimeout,

        @NotNull @DefaultValue("10m") Duration readTimeout) {
}
