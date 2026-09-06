package br.com.sinapse.platform.learningrecord.internal.service;

import br.com.sinapse.platform.learningrecord.api.StudySessionView;
import br.com.sinapse.platform.learningrecord.internal.domain.StudySession;

/**
 * Turns the aggregate into the view other modules and clients receive.
 *
 * <p>One place, so that an entity cannot reach a caller by being returned from a method
 * somebody wrote in a hurry. A {@code StudySession} handed across the module boundary is a
 * closed session waiting to be edited.
 */
final class LearningRecordViews {

    private LearningRecordViews() {
    }

    /**
     * @param session session to publish
     * @return its view
     */
    static StudySessionView of(StudySession session) {
        return new StudySessionView(
                session.id(),
                session.accountId(),
                session.topicId(),
                session.plannedSessionId(),
                session.kind(),
                session.source(),
                session.status(),
                session.startedAt(),
                session.endedAt(),
                session.plannedDurationMinutes(),
                session.actualDurationMinutes(),
                session.durationSource(),
                session.recallRating());
    }
}
