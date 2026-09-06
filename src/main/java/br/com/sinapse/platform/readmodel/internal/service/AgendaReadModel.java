package br.com.sinapse.platform.readmodel.internal.service;

import br.com.sinapse.platform.identity.api.AccountDirectory;
import br.com.sinapse.platform.learningrecord.api.StudyHistory;
import br.com.sinapse.platform.learningrecord.api.StudySessionView;
import br.com.sinapse.platform.planning.api.PlannedSessionView;
import br.com.sinapse.platform.planning.api.PlanningDirectory;
import br.com.sinapse.platform.readmodel.api.DailyAgendaView;
import br.com.sinapse.platform.readmodel.internal.error.NotReadableException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code AgendaDoDia}: the plan for a window, day by day, with what was done about each slot.
 *
 * <p>Section 3.2 of the API contract, and the model that argument section rests on: one screen,
 * four modules. The matching between a planned session and the session that executed it is a
 * rule, and doing it here is what keeps it out of the browser.
 *
 * <p><strong>Five queries, whatever the size of the window.</strong> The planned sessions come
 * in one, their topics and subjects in two more, and every execution in a fifth — the batch
 * lookups rule R7 requires, used for the case they were written for. Resolving a topic per
 * entry instead would be the forty lookups the contract predicts, and it would pass every
 * functional test in this repository.
 */
@Service
public class AgendaReadModel {

    private final PlanningDirectory planning;
    private final StudyHistory history;
    private final AccountDirectory accounts;
    private final CatalogNames names;
    private final ReadModelAccess access;
    private final ReadWindow window;

    /**
     * @param planning the scheduled sessions
     * @param history  the sessions that executed them
     * @param accounts the holder's zone, which is what "a day" means here
     * @param names    topic and subject names, in two queries
     * @param access   the single gate these reads consult
     * @param window   the ceiling on what may be asked for
     */
    public AgendaReadModel(PlanningDirectory planning, StudyHistory history,
            AccountDirectory accounts, CatalogNames names, ReadModelAccess access,
            ReadWindow window) {
        this.planning = planning;
        this.history = history;
        this.accounts = accounts;
        this.names = names;
        this.access = access;
        this.window = window;
    }

    /**
     * The caller's agenda over a window.
     *
     * @param accountId the caller
     * @param from      start of the window, inclusive
     * @param to        end of the window, exclusive
     * @return the days that have something scheduled, earliest first
     */
    @Transactional(readOnly = true)
    public DailyAgendaView of(UUID accountId, Instant from, Instant to) {
        window.require(from, to);
        access.requireOwnLearningData(accountId);

        ZoneId zone = accounts.timeZoneOf(accountId).orElseThrow(NotReadableException::new);
        List<PlannedSessionView> planned = planning.plannedSessionsOf(accountId, from, to);
        if (planned.isEmpty()) {
            return new DailyAgendaView(List.of());
        }

        CatalogNames.Resolved resolved = names.of(planned.stream()
                .map(PlannedSessionView::topicId)
                .collect(Collectors.toCollection(LinkedHashSet::new)));

        Set<UUID> plannedIds = planned.stream()
                .map(PlannedSessionView::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, StudySessionView> executions = history.executionsOf(plannedIds);

        // Ordered by scheduled start already, so inserting in order produces days in order and
        // entries in order within each day, with no second sort.
        Map<LocalDate, List<DailyAgendaView.Entry>> byDay = new LinkedHashMap<>();
        for (PlannedSessionView session : planned) {
            LocalDate day = LocalDate.ofInstant(session.scheduledStart(), zone);
            byDay.computeIfAbsent(day, date -> new ArrayList<>())
                    .add(entry(session, resolved, executions.get(session.id())));
        }

        return new DailyAgendaView(byDay.entrySet().stream()
                .map(day -> new DailyAgendaView.Day(day.getKey(), day.getValue()))
                .toList());
    }

    private static DailyAgendaView.Entry entry(PlannedSessionView session,
            CatalogNames.Resolved resolved, StudySessionView execution) {

        return new DailyAgendaView.Entry(
                session.id(),
                session.topicId(),
                resolved.topicName(session.topicId()),
                resolved.subjectIdOf(session.topicId()),
                resolved.subjectNameOf(session.topicId()),
                session.kind(),
                session.scheduledStart(),
                session.durationMinutes(),
                execution == null ? null : execution(execution));
    }

    private static DailyAgendaView.Execution execution(StudySessionView session) {
        return new DailyAgendaView.Execution(session.id(), session.status(),
                session.actualDurationMinutes(), session.recallRating(), session.durationSource());
    }
}
