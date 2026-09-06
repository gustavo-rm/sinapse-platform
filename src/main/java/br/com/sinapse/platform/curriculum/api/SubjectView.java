package br.com.sinapse.platform.curriculum.api;

import java.util.UUID;

/**
 * A subject, as other modules see it.
 *
 * @param id   identifier other contexts reference
 * @param code stable natural key, unique across the catalogue
 * @param name display name
 */
public record SubjectView(UUID id, String code, String name) {
}
