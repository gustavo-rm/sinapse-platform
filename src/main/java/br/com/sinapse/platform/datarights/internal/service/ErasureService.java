package br.com.sinapse.platform.datarights.internal.service;

import br.com.sinapse.platform.datarights.internal.domain.ErasureRequest;
import br.com.sinapse.platform.datarights.internal.error.UnknownErasureRequestException;
import br.com.sinapse.platform.datarights.internal.persistence.ErasureRequestRepository;
import br.com.sinapse.platform.shared.datarights.ModuleDataRights;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carries out an erasure.
 *
 * <p><strong>This is the only place in the platform that sets the erasure flag.</strong> The
 * append-only triggers on {@code study_session}, {@code planned_session} and
 * {@code consent_record} consult a transaction-local setting, and setting it anywhere else
 * would turn an explicit exception back into a permanent hole. {@code OnlyTheErasureServiceLiftsAppendOnlyTest}
 * fails if a second class ever mentions it.
 *
 * <p>{@code set_config(..., true)} makes the setting transaction-local. Session-local would
 * outlive the transaction on a pooled connection and hand the exception to whatever request
 * borrowed that connection next, which is the failure mode this whole mechanism exists to
 * avoid.
 *
 * <p><strong>One transaction, and it either happens or it does not.</strong> Partial erasure is
 * worse than none: an account with its plans gone and its study history intact is neither
 * erased nor usable, and nothing would say which half is missing. So every module runs inside
 * this method, and any failure takes all of them with it.
 *
 * <p>It never touches another module's tables. It asks Spring for every implementation of the
 * shared contract and runs them in the order they declare — a foreign key order, so that a row
 * is never removed before the rows referencing it. It does not know what any of them will do.
 */
@Service
public class ErasureService {

    /**
     * The transaction-local setting the append-only triggers consult.
     *
     * <p>Package-private so that the test which proves only this class sets it can name it
     * without a copy.
     */
    static final String ERASURE_FLAG = "sinapse.erasure";

    /** {@code true} is what makes it transaction-local rather than session-local. */
    private static final String LIFT_APPEND_ONLY = "select set_config(?, 'on', true)";

    private static final Logger LOG = LoggerFactory.getLogger(ErasureService.class);

    private final List<ModuleDataRights> modules;
    private final ErasureRequestRepository requests;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    /**
     * @param modules  every module that answers for its own data, in declared order
     * @param requests erasure requests
     * @param jdbc     template over the application datasource, used only to set the flag
     * @param clock    application clock
     */
    public ErasureService(List<ModuleDataRights> modules, ErasureRequestRepository requests,
            JdbcTemplate jdbc, Clock clock) {
        this.modules = List.copyOf(modules);
        this.requests = requests;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Erases the account of a request, or nothing.
     *
     * @param requestId request to carry out
     * @throws UnknownErasureRequestException if there is no such request
     */
    @Transactional
    public void erase(UUID requestId) {
        ErasureRequest request = requests.findById(requestId)
                .orElseThrow(UnknownErasureRequestException::new);

        liftAppendOnlyForThisTransaction();
        modules.forEach(module -> module.eraseFor(request.accountId()));
        request.complete(clock.instant());

        // The request, and never the account. The identifier of an account being erased is
        // about to point at a shell that carries nothing, but it is still the identifier of a
        // person's account and does not belong in a log line.
        LOG.info("Erasure request {} completed across {} modules", requestId, modules.size());
    }

    /**
     * The names of the modules that answer for their own data, in the order they run.
     *
     * @return the module names
     */
    public List<String> participatingModules() {
        return modules.stream().map(ModuleDataRights::moduleName).toList();
    }

    private void liftAppendOnlyForThisTransaction() {
        jdbc.query(LIFT_APPEND_ONLY, resultSet -> null, ERASURE_FLAG);
    }
}
