package br.com.sinapse.platform.curation.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Period;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Where the curated catalogue lives, bound from {@code sinapse.catalog}.
 *
 * @param directory path of the catalogue root, relative to where the tool is run. It defaults to
 *                  {@code catalog} because that is the directory ADR 0014 puts in the
 *                  repository, and configuration exists so that a copy checked out somewhere
 *                  else can be applied without editing anything
 * @param horizon   the planning horizon {@code catalog shape} lays the availability over. The
 *                  {@code catalog} profile binds it to {@code sinapse.planning.generation.horizon},
 *                  so the shape is computed over the horizon the platform actually plans and the
 *                  two cannot drift apart
 */
@Validated
@ConfigurationProperties("sinapse.catalog")
public record CatalogProperties(@NotBlank @DefaultValue("catalog") String directory,
        @NotNull @DefaultValue("4w") Period horizon) {
}
