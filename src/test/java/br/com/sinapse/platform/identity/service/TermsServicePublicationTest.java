package br.com.sinapse.platform.identity.service;

import static br.com.sinapse.platform.identity.api.ConsentPurpose.ACADEMIC_RESEARCH;
import static br.com.sinapse.platform.identity.api.ConsentPurpose.INSTITUTION_SHARING;
import static br.com.sinapse.platform.identity.api.ConsentPurpose.LEARNING_DATA_PROCESSING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.sinapse.platform.identity.api.ConsentPurpose;
import br.com.sinapse.platform.identity.internal.config.IdentityProperties;
import br.com.sinapse.platform.identity.internal.domain.TermsVersion;
import br.com.sinapse.platform.identity.internal.persistence.TermsVersionRepository;
import br.com.sinapse.platform.identity.internal.service.TermsService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * What startup publishes from the configured wordings, against a repository held in memory.
 *
 * <p>The integration suite starts the runner too, but against a shared database: on an empty one
 * the first context inserts the wordings, on a reused one they are already there and the insert
 * never runs, so whether the insert is exercised at all depended on what earlier runs left (K28).
 * Here the repository is a double that keeps what is saved, so each case sets up exactly the
 * wordings already published and nothing outside the test decides which path runs.
 */
class TermsServicePublicationTest {

    private static final Instant NOW = Instant.parse("2026-10-25T12:00:00Z");

    private final TermsVersionRepository repository = mock(TermsVersionRepository.class);

    private final List<TermsVersion> stored = new ArrayList<>();

    @BeforeEach
    void keepWhatIsSaved() {
        when(repository.existsByPurposeAndVersion(any(ConsentPurpose.class), anyString()))
                .thenAnswer(call -> stored.stream().anyMatch(terms ->
                        terms.purpose() == call.getArgument(0)
                                && terms.version().equals(call.getArgument(1))));
        when(repository.save(any(TermsVersion.class))).thenAnswer(call -> {
            TermsVersion terms = call.getArgument(0);
            stored.add(terms);
            return terms;
        });
    }

    @Test
    void everyConfiguredWordingNotYetPublishedIsInsertedAsConfigured() {
        TermsService service = serviceWith(
                configured(LEARNING_DATA_PROCESSING, "v1", "Learning data, v1."),
                configured(INSTITUTION_SHARING, "v1", "Institution sharing, v1."),
                configured(ACADEMIC_RESEARCH, "v1", "Academic research, v1."));

        assertThat(service.publishConfigured()).isEqualTo(3);

        assertThat(stored)
                .extracting(TermsVersion::purpose, TermsVersion::version, TermsVersion::body,
                        TermsVersion::publishedAt)
                .containsExactly(
                        tuple(LEARNING_DATA_PROCESSING, "v1", "Learning data, v1.", NOW),
                        tuple(INSTITUTION_SHARING, "v1", "Institution sharing, v1.", NOW),
                        tuple(ACADEMIC_RESEARCH, "v1", "Academic research, v1.", NOW));
        assertThat(stored).extracting(TermsVersion::id)
                .doesNotContainNull()
                .doesNotHaveDuplicates();
    }

    @Test
    void publishingAgainInsertsNothing() {
        TermsService service = serviceWith(
                configured(LEARNING_DATA_PROCESSING, "v1", "Learning data, v1."),
                configured(INSTITUTION_SHARING, "v1", "Institution sharing, v1."));
        assertThat(service.publishConfigured()).isEqualTo(2);
        List<TermsVersion> afterFirst = List.copyOf(stored);

        assertThat(service.publishConfigured())
                .as("a second startup with the same configuration")
                .isZero();
        assertThat(stored).as("nothing inserted again, nothing replaced").isEqualTo(afterFirst);
    }

    @Test
    void onlyTheMissingVersionsAreInsertedAndAPublishedWordingIsNeverRewritten() {
        TermsVersion published = new TermsVersion(UUID.randomUUID(),
                LEARNING_DATA_PROCESSING, "v1", "Learning data, v1.",
                NOW.minus(Duration.ofDays(30)));
        stored.add(published);
        TermsService service = serviceWith(
                configured(LEARNING_DATA_PROCESSING, "v1", "Edited after the fact."),
                configured(LEARNING_DATA_PROCESSING, "v2", "Learning data, v2."),
                configured(INSTITUTION_SHARING, "v1", "Institution sharing, v1."));

        assertThat(service.publishConfigured()).isEqualTo(2);

        assertThat(stored).first().as("the wording a consent record may point at")
                .isSameAs(published)
                .extracting(TermsVersion::body).isEqualTo("Learning data, v1.");
        assertThat(stored.subList(1, stored.size()))
                .extracting(TermsVersion::purpose, TermsVersion::version)
                .containsExactly(
                        tuple(LEARNING_DATA_PROCESSING, "v2"),
                        tuple(INSTITUTION_SHARING, "v1"));
    }

    @Test
    void withNothingConfiguredNothingIsInserted() {
        assertThat(serviceWith().publishConfigured()).isZero();

        verify(repository, never()).save(any(TermsVersion.class));
    }

    private TermsService serviceWith(IdentityProperties.Terms... terms) {
        IdentityProperties properties = new IdentityProperties(18, 30,
                new IdentityProperties.Tokens(Duration.ofHours(24), Duration.ofHours(1),
                        Duration.ofDays(30)),
                new IdentityProperties.Session(Duration.ofDays(7), Duration.ofDays(30),
                        "sinapse_session", "/api/v1", true),
                List.of(terms));
        return new TermsService(repository, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static IdentityProperties.Terms configured(ConsentPurpose purpose, String version,
            String body) {
        return new IdentityProperties.Terms(purpose, version, body);
    }
}
