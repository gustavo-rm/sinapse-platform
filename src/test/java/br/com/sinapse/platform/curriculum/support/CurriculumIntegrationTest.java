package br.com.sinapse.platform.curriculum.support;

import br.com.sinapse.platform.IntegrationTest;
import br.com.sinapse.platform.curriculum.api.CatalogCuration;
import br.com.sinapse.platform.curriculum.api.EdgeProvenance;
import br.com.sinapse.platform.curriculum.api.EdgeStrength;
import br.com.sinapse.platform.curriculum.api.EffortTier;
import br.com.sinapse.platform.curriculum.api.SubjectView;
import br.com.sinapse.platform.curriculum.api.TopicView;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Base of the curriculum integration tests.
 *
 * <p>Each test starts from an empty catalogue. The truncation cascades from {@code subject},
 * which reaches topics and every edge between them.
 *
 * <p>The fixtures build through the real curation service wherever they can, so that a test
 * never asserts against a catalogue the application could not have produced. Edges written
 * straight through SQL appear only where the database itself is what is under test.
 */
public abstract class CurriculumIntegrationTest extends IntegrationTest {

    /** Insert that bypasses the service, for the tests whose subject is the database. */
    protected static final String INSERT_EDGE = """
            insert into topic_prerequisite (id, prerequisite_topic_id, dependent_topic_id,
                                            strength, provenance)
            values (?, ?, ?, ?, ?)
            """;

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected CatalogCuration curation;

    @BeforeEach
    void resetCatalogue() {
        jdbc.execute("truncate table subject cascade");
    }

    /** A subject code no other test is using. */
    protected String uniqueCode(String prefix) {
        return prefix + "-" + SEQUENCE.incrementAndGet();
    }

    /**
     * Registers a subject.
     *
     * @param name display name
     * @return the subject
     */
    protected SubjectView subject(String name) {
        return curation.defineSubject(uniqueCode("SUB"), name);
    }

    /**
     * Registers a topic at the next free position of a subject.
     *
     * @param subject  subject
     * @param code     natural key within the subject
     * @param position curricular ordering
     * @return the topic
     */
    protected TopicView topic(SubjectView subject, String code, int position) {
        return curation.defineTopic(new CatalogCuration.TopicDefinition(
                subject.id(), code, "Topic " + code, position, EffortTier.STANDARD));
    }

    /**
     * Writes an edge straight to the database, bypassing every rule this module holds.
     *
     * <p>Used where the point of the test is what the database does on its own.
     *
     * @param prerequisite topic that comes first
     * @param dependent    topic that depends on it
     */
    protected void insertEdgeDirectly(UUID prerequisite, UUID dependent) {
        jdbc.update(INSERT_EDGE, UUID.randomUUID(), prerequisite, dependent,
                EdgeStrength.HARD.name(), EdgeProvenance.CURATED.name());
    }

    /** How many edges the catalogue currently holds. */
    protected int edgeCount() {
        return jdbc.queryForObject("select count(*) from topic_prerequisite", Integer.class);
    }
}
