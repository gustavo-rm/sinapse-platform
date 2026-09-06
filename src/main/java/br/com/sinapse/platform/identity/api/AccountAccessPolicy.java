package br.com.sinapse.platform.identity.api;

import java.util.UUID;

/**
 * The single decision point on what may be done with an account's data.
 *
 * <p>Section 5.5 of the architecture document: {@code planning} and {@code learningrecord}
 * consult this policy at the entry of their use cases, and no service anywhere checks a
 * status of its own. The reason is maintenance under legal change — when the rule moves,
 * the edit has to have one target rather than thirty.
 *
 * <p>Every answer is derived from state read at the moment of the call: the status of the
 * account and its consent records. Nothing is cached, because a revocation has to take
 * effect immediately; that is the same requirement that made sessions server-side rather
 * than stateless in ADR 0010.
 *
 * <p>An unknown account answers {@code false} everywhere. The caller learns that it may
 * not proceed and nothing about why, which is what {@code ApiErrorType.RESOURCE_NOT_FOUND}
 * exists for at the edge.
 */
public interface AccountAccessPolicy {

    /**
     * Whether the learning data of an account may be processed at all: reading study
     * history, generating a plan, recording a session.
     *
     * @param accountId account being acted upon
     * @return {@code true} only for an active account with a valid essential consent
     */
    boolean canProcessLearningData(UUID accountId);

    /**
     * Whether the account's data may be disclosed to a teacher of a classroom the holder
     * is enrolled in.
     *
     * @param accountId account being acted upon
     * @return {@code true} only for an active account with a valid
     *         {@link ConsentPurpose#INSTITUTION_SHARING} consent
     */
    boolean canShareWithInstitution(UUID accountId);

    /**
     * Whether the account's data may be used for research.
     *
     * @param accountId account being acted upon
     * @return {@code true} only for an active account with a valid
     *         {@link ConsentPurpose#ACADEMIC_RESEARCH} consent
     */
    boolean canUseForResearch(UUID accountId);
}
