package br.com.sinapse.platform.identity.internal.service;

import br.com.sinapse.platform.identity.api.ConsentPurpose;
import br.com.sinapse.platform.identity.internal.config.IdentityProperties;
import br.com.sinapse.platform.identity.internal.domain.TermsVersion;
import br.com.sinapse.platform.identity.internal.error.UnknownTermsVersionException;
import br.com.sinapse.platform.identity.internal.persistence.TermsVersionRepository;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The wordings a holder may consent to.
 *
 * <p>Where the text comes from is a decision worth stating. It is not code: it is a legal
 * document, reviewed by people who never open this repository, and replaced on a schedule
 * of its own. It is not a migration either: a migration is a change of schema, and this is
 * content. So the operator supplies it as configuration, and startup publishes whatever is
 * not published yet.
 *
 * <p>Publication only ever inserts. A wording already published is never rewritten, because
 * consent records point at it and rewriting it would silently change what a holder is
 * recorded as having accepted. Correcting a text means publishing a new version.
 *
 * <p>With no wording configured, registration fails with a clear answer rather than
 * recording consent to an empty document. That is deliberate: inventing placeholder legal
 * text would be worse than refusing.
 */
@Service
public class TermsService {

    private static final Logger LOG = LoggerFactory.getLogger(TermsService.class);

    private final TermsVersionRepository terms;
    private final IdentityProperties properties;
    private final Clock clock;

    /**
     * @param terms      published wordings
     * @param properties configuration, source of the wordings to publish
     * @param clock      application clock
     */
    public TermsService(TermsVersionRepository terms, IdentityProperties properties, Clock clock) {
        this.terms = terms;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Publishes the configured wordings that are not published yet.
     *
     * @return how many were inserted
     */
    @Transactional
    public int publishConfigured() {
        int published = 0;
        for (IdentityProperties.Terms configured : properties.terms()) {
            if (terms.existsByPurposeAndVersion(configured.purpose(), configured.version())) {
                continue;
            }
            terms.save(new TermsVersion(UUID.randomUUID(), configured.purpose(), configured.version(),
                    configured.body(), clock.instant()));
            published++;
        }
        if (published > 0) {
            LOG.info("Published {} consent terms version(s) from configuration", published);
        }
        return published;
    }

    /**
     * Every published wording, newest first.
     *
     * @return the catalogue a client shows before an acceptance
     */
    @Transactional(readOnly = true)
    public List<TermsVersion> published() {
        return terms.findAllByOrderByPublishedAtDesc();
    }

    /**
     * The wording currently in force for a purpose.
     *
     * @param purpose purpose
     * @return the wording, if one was ever published for it
     */
    @Transactional(readOnly = true)
    public Optional<TermsVersion> currentFor(ConsentPurpose purpose) {
        return terms.findFirstByPurposeOrderByPublishedAtDesc(purpose);
    }

    /**
     * Resolves the wording a client says it displayed, and checks that it is still the one
     * in force.
     *
     * <p>The client submits an identifier rather than the server picking the current one on
     * its own. That is what makes the record probative: it ties the consent to the text
     * that was actually on the screen. If a new version was published between the display
     * and the acceptance, the holder consented to something that is no longer the terms,
     * and the act has to be repeated.
     *
     * @param purpose        purpose being consented to
     * @param termsVersionId wording the client displayed
     * @return the wording
     * @throws UnknownTermsVersionException if it does not exist, covers another purpose, or
     *                                      has been superseded
     */
    @Transactional(readOnly = true)
    public TermsVersion requireCurrent(ConsentPurpose purpose, UUID termsVersionId) {
        TermsVersion current = currentFor(purpose).orElseThrow(UnknownTermsVersionException::new);
        if (!current.id().equals(termsVersionId)) {
            throw new UnknownTermsVersionException();
        }
        return current;
    }
}
