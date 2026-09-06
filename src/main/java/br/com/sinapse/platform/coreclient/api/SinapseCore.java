package br.com.sinapse.platform.coreclient.api;

import br.com.sinapse.platform.coreclient.contract.PlanRequest;
import br.com.sinapse.platform.coreclient.contract.PlanResponse;

/**
 * The optimiser, as the rest of the platform sees it.
 *
 * <p>One method, because there is one thing the core does. Everything about it being a
 * separate process over HTTP — the address, the timeouts, the serialisation, the shapes a
 * failure can take — is behind this line, which is what ADR 0002 means by concentrating the
 * failure modes in the adapter.
 *
 * <p><strong>This call blocks and takes seconds to minutes.</strong> The optimisation is
 * CPU-bound, which is the whole reason generation is a job rather than a request. Nothing on
 * a request thread may call this.
 */
public interface SinapseCore {

    /**
     * Asks the core for a plan.
     *
     * @param request the snapshot, the parameters and the seed
     * @return the schedule it produced
     * @throws CoreUnavailableException if the core could not be reached, refused the call or
     *                                  did not answer in time
     * @throws CoreProtocolException    if it answered with something this contract cannot read
     */
    PlanResponse generate(PlanRequest request);
}
