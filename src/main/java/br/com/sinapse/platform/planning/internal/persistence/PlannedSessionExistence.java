package br.com.sinapse.platform.planning.internal.persistence;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Which of a set of planned sessions still exist.
 *
 * <p>The whole of planning's persistence for now. The aggregates, their repositories and the
 * generation job belong to the next step of the build; what exists here is the one read the
 * consistency check needs, and it reads {@code planned_session}, which is planning's own
 * table — the check crosses modules, this does not.
 *
 * <p>Plain SQL rather than a JPA entity on purpose. Mapping the table now would fix a shape
 * before the module that owns it is written, and the only question being asked is which
 * identifiers are present.
 */
@Repository
public class PlannedSessionExistence {

    /** Chunk size for the lookup, so that one huge statement is never assembled. */
    private static final int BATCH = 1000;

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * @param jdbc template over the application datasource
     */
    public PlannedSessionExistence(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * The subset of the given identifiers that name a planned session.
     *
     * @param plannedSessionIds identifiers to look for
     * @return the ones that exist
     */
    public Set<UUID> existing(Collection<UUID> plannedSessionIds) {
        List<UUID> ids = List.copyOf(plannedSessionIds);
        if (ids.isEmpty()) {
            return Set.of();
        }
        Set<UUID> found = new HashSet<>();
        for (int start = 0; start < ids.size(); start += BATCH) {
            List<UUID> chunk = ids.subList(start, Math.min(start + BATCH, ids.size()));
            found.addAll(jdbc.queryForList(
                    "select id from planned_session where id in (:ids)",
                    Map.of("ids", chunk), UUID.class));
        }
        return Set.copyOf(found);
    }
}
