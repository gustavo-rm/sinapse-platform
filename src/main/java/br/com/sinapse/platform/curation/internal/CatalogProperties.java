package br.com.sinapse.platform.curation.internal;

import jakarta.validation.constraints.NotBlank;
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
 */
@Validated
@ConfigurationProperties("sinapse.catalog")
public record CatalogProperties(@NotBlank @DefaultValue("catalog") String directory) {
}
