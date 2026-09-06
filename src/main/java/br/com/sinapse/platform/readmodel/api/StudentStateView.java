package br.com.sinapse.platform.readmodel.api;

import br.com.sinapse.platform.identity.api.AccountStatus;
import br.com.sinapse.platform.identity.api.ConsentPurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

/**
 * What the initial screen asks for, in one call.
 *
 * <p>Section 3.1 of the API contract. Every field here answers "what should this screen show
 * first", and nothing here is study content: four identifiers, a status, a zone and two
 * flags.
 *
 * <p><strong>The last four fields are empty when the account's learning data may not be
 * processed.</strong> A suspended holder, or one who has withdrawn the essential consent,
 * still gets an answer — being told the account is suspended is the whole reason this screen
 * exists for them — but the platform does not report a plan or an open session it is not
 * entitled to act on. That is {@code AccountAccessPolicy} applied to the part of the read it
 * governs, rather than to the whole call, which would leave the holder with a 403 and no way
 * to learn why.
 *
 * @param accountStatus                 where the account stands
 * @param timeZone                      IANA zone the holder's local times are read in
 * @param pendingConsents               purposes the holder has never decided and that are
 *                                      raised unprompted. A purpose granted in the course of
 *                                      another act, such as joining a classroom, never
 *                                      appears here
 * @param requiresMajorityReaffirmation whether the holder has reached the consent age and owes
 *                                      a reaffirmation in their own name
 * @param setupComplete                 whether availability and at least one goal exist over
 *                                      the horizon a plan would be generated for. The same
 *                                      condition the generation job checks, so a client that
 *                                      shows the button on this flag will not be refused
 * @param activePlanId                  the plan in force, or {@code null}
 * @param activeGenerationJobId         a generation job that has not finished, or {@code null}.
 *                                      Its progress is read by polling the job itself
 * @param openSessionId                 the session left running, or {@code null}. Present
 *                                      because there is at most one in progress per account:
 *                                      the screen has to offer to resume it rather than start
 *                                      another and be refused
 */
@Schema(description = "Everything the initial screen needs to decide what to show")
public record StudentStateView(
        AccountStatus accountStatus,
        ZoneId timeZone,
        Set<ConsentPurpose> pendingConsents,
        boolean requiresMajorityReaffirmation,
        boolean setupComplete,
        UUID activePlanId,
        UUID activeGenerationJobId,
        UUID openSessionId) {

    /** Copies the set, so the state cannot change under whoever is rendering it. */
    public StudentStateView {
        pendingConsents = Set.copyOf(pendingConsents);
    }
}
