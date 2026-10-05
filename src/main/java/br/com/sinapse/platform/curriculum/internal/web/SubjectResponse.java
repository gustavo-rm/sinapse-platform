package br.com.sinapse.platform.curriculum.internal.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * A subject, as the catalogue route returns it.
 *
 * <p>Exactly these three fields. The code is a curator's key and not text for a student, and
 * nothing else about a subject is meant for the screen.
 *
 * @param id       identifier, the one a goal is set against
 * @param name     display name, as curated
 * @param position place of the subject in the listing, from 1. Subjects carry no curated
 *                 order of their own, so this is their rank in catalogue-code order: stable
 *                 across calls while the catalogue does not change, and nothing more
 */
@Schema(description = "A subject of the catalogue")
public record SubjectResponse(UUID id, String name, int position) {
}
