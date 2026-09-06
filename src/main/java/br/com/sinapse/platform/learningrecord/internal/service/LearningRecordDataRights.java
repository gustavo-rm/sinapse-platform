package br.com.sinapse.platform.learningrecord.internal.service;

import br.com.sinapse.platform.learningrecord.internal.persistence.StudySessionRepository;
import br.com.sinapse.platform.shared.datarights.ModuleDataRights;
import br.com.sinapse.platform.shared.datarights.ModuleExport;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * What this module owes the holder of an account.
 *
 * <p><strong>Study sessions are erased, not anonymised.</strong> ADR 0011 settles it and the
 * reason is worth repeating where the code is: a longitudinal sequence of topics with times is
 * a behavioural fingerprint. Crossed with a classroom roster and a timetable it re-identifies,
 * and anonymisation that does not survive cross-referencing is pseudonymisation, which is
 * still personal data. There is no scrub-and-keep path here, however tempting one looks.
 *
 * <p>It runs first among the modules, before planning: nothing here references a plan, and
 * going first means the heaviest table is gone before anything else is touched.
 */
@Component
@Order(LearningRecordDataRights.ORDER)
public class LearningRecordDataRights implements ModuleDataRights {

    /** First. Nothing this module holds is referenced by another module's rows. */
    public static final int ORDER = 10;

    private static final Logger LOG = LoggerFactory.getLogger(LearningRecordDataRights.class);

    private final StudySessionRepository sessions;

    /**
     * @param sessions study sessions
     */
    public LearningRecordDataRights(StudySessionRepository sessions) {
        this.sessions = sessions;
    }

    @Override
    public String moduleName() {
        return "learningrecord";
    }

    @Override
    @Transactional
    public void eraseFor(UUID accountId) {
        int erased = sessions.eraseFor(accountId);
        // The count and nothing else. What was studied, and when, is the data being removed.
        LOG.info("Erasure removed {} study sessions", erased);
    }

    @Override
    @Transactional(readOnly = true)
    public ModuleExport exportFor(UUID accountId) {
        List<Object> rows = sessions.findByAccountIdOrderByStartedAtDesc(accountId).stream()
                .map(session -> (Object) LearningRecordViews.of(session))
                .toList();
        return new ModuleExport(moduleName(), Map.of("studySessions", rows));
    }
}
