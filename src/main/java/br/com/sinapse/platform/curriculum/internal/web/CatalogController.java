package br.com.sinapse.platform.curriculum.internal.web;

import br.com.sinapse.platform.curriculum.api.CurriculumCatalog;
import br.com.sinapse.platform.curriculum.api.SubjectView;
import br.com.sinapse.platform.curriculum.internal.error.UnknownTopicException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * The catalogue, read-only.
 *
 * <p>This is where a client gets the identifiers it needs to set a goal, and where it resolves
 * the name of a topic it only holds the identifier of — a study session, for instance, carries
 * the topic's identifier and not its name.
 *
 * <p>Returned whole, without paging or filtering: the catalogue is a curated list of dozens of
 * items, not something that grows with use.
 */
@RestController
@Tag(name = "Catalogue", description = "Subjects and topics of the curated catalogue")
public class CatalogController {

    /** Curricular order, with the identifier breaking ties so the database never decides. */
    private static final Comparator<TopicResponse> CURRICULAR =
            Comparator.comparingInt(TopicResponse::position).thenComparing(TopicResponse::id);

    private final CurriculumCatalog catalog;

    /**
     * @param catalog reads of the catalogue
     */
    public CatalogController(CurriculumCatalog catalog) {
        this.catalog = catalog;
    }

    /**
     * Every subject of the catalogue.
     *
     * @return the subjects, by position
     */
    @GetMapping(value = CurriculumRoutes.SUBJECTS, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Lists the subjects of the catalogue",
            description = "The identifiers a goal is set against. Subjects have no curated order, "
                    + "so position is their rank in catalogue-code order, starting at 1.")
    @ApiResponse(responseCode = "200", description = "The subjects")
    public List<SubjectResponse> subjects() {
        List<SubjectView> subjects = catalog.subjects();
        return IntStream.range(0, subjects.size())
                .mapToObj(index -> new SubjectResponse(subjects.get(index).id(),
                        subjects.get(index).name(), index + 1))
                .toList();
    }

    /**
     * The topics of one subject.
     *
     * @param subjectId subject to read
     * @return its topics, in curricular order
     */
    @GetMapping(value = CurriculumRoutes.SUBJECTS + "/{subjectId}/topics",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Lists the topics of a subject",
            description = "In curricular order. Also where a topic name is resolved for a client "
                    + "that only holds the topic's identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The topics"),
            @ApiResponse(responseCode = "404", description = "No such subject",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public List<TopicResponse> topics(@PathVariable UUID subjectId) {
        if (catalog.subjectsByIds(Set.of(subjectId)).isEmpty()) {
            throw new UnknownTopicException();
        }
        return catalog.topicsOfSubjects(Set.of(subjectId)).stream()
                .map(TopicResponse::of)
                .sorted(CURRICULAR)
                .toList();
    }
}
