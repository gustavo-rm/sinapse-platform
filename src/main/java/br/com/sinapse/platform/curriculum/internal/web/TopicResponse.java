package br.com.sinapse.platform.curriculum.internal.web;

import br.com.sinapse.platform.curriculum.api.TopicView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * A topic, as the catalogue route returns it.
 *
 * <p>Exactly these three fields. The effort band and the minutes derived from it are left out
 * on purpose: the mapping is declared uncalibrated, and a number on a student's or a teacher's
 * screen gets read as a measurement. Prerequisite edges are left out for the same kind of
 * reason — they are an input to the optimiser, not a statement made to the student.
 *
 * @param id       identifier, the one plans and study sessions refer to
 * @param name     display name, as curated
 * @param position curricular position within the subject
 */
@Schema(description = "A topic of a subject")
public record TopicResponse(UUID id, String name, int position) {

    /**
     * @param view topic to render
     * @return the response body
     */
    static TopicResponse of(TopicView view) {
        return new TopicResponse(view.id(), view.name(), view.position());
    }
}
