package br.com.sinapse.platform.shared.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body accepted by the validation probe.
 *
 * @param label arbitrary text, constrained so that a violation can be observed
 */
public record ProbeRequest(

        @NotBlank
        @Size(max = 40)
        String label) {
}
