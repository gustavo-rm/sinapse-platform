package br.com.sinapse.platform.identity.internal.config;

import br.com.sinapse.platform.identity.api.ConsentPurpose;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration of the identity module, bound from {@code sinapse.identity}.
 *
 * @param consentAgeThreshold    age from which the holder consents in their own name. It
 *                               is a parameter and not a constant because the legal
 *                               question behind it is open (J1): Article 14 §1 requires a
 *                               guardian for <em>children</em>, defined in law as under
 *                               12, and the treatment of adolescents is contested. The
 *                               default of 18 is the conservative reading, and whatever
 *                               guidance arrives changes this line and nothing else.
 * @param majorityGracePeriodDays days the holder has to reaffirm, in their own name, a
 *                               consent a guardian had granted, counted from the day the
 *                               threshold is reached. The account stays usable throughout
 *                               (ADR 0004).
 * @param tokens                 lifetimes of the single-use tokens
 * @param session                lifetimes and cookie attributes of a session
 * @param terms                  wordings to publish at startup, if they are not published
 *                               yet
 */
@Validated
@ConfigurationProperties("sinapse.identity")
public record IdentityProperties(

        @Positive @DefaultValue("18") int consentAgeThreshold,

        @Positive @DefaultValue("30") int majorityGracePeriodDays,

        @Valid @DefaultValue Tokens tokens,

        @Valid @DefaultValue Session session,

        @Valid @DefaultValue List<Terms> terms) {

    /**
     * Lifetimes of the tokens delivered to the account holder.
     *
     * @param emailVerification     how long a verification link stays usable
     * @param passwordReset         how long a reset link stays usable. Short on purpose: it
     *                              is the one token that replaces knowing the password
     * @param majorityReaffirmation how long a reaffirmation link stays usable. It matches
     *                              the grace period, because the link is worthless once the
     *                              account is suspended for not having used it
     */
    public record Tokens(
            @NotNull @DefaultValue("24h") Duration emailVerification,
            @NotNull @DefaultValue("1h") Duration passwordReset,
            @NotNull @DefaultValue("30d") Duration majorityReaffirmation) {
    }

    /**
     * Session lifetimes and the attributes of the cookie that carries one.
     *
     * @param idleTimeout     how long a session survives without a request (ADR 0010)
     * @param absoluteTimeout how long a session survives at all, never extended
     * @param cookieName      name of the cookie
     * @param cookiePath      path the cookie is sent for. Scoped to the API, because
     *                        nothing else on the origin needs it
     * @param cookieSecure    whether the cookie is marked {@code Secure}. True everywhere
     *                        it matters; a local profile that serves plain HTTP is the
     *                        only reason this is configurable at all
     */
    public record Session(
            @NotNull @DefaultValue("7d") Duration idleTimeout,
            @NotNull @DefaultValue("30d") Duration absoluteTimeout,
            @NotBlank @DefaultValue("sinapse_session") String cookieName,
            @NotBlank @DefaultValue("/api/v1") String cookiePath,
            @DefaultValue("true") boolean cookieSecure) {
    }

    /**
     * A wording of the consent terms, as supplied by the operator.
     *
     * <p>The text is configuration and not code: it is a legal document, it is reviewed by
     * people who do not open this repository, and it is republished on its own schedule.
     * Publication is idempotent by {@code (purpose, version)} and never rewrites a wording
     * that is already published, because consent records point at it.
     *
     * @param purpose purpose the wording covers
     * @param version label of this wording, unique per purpose
     * @param body    full text presented to the holder before the acceptance
     */
    public record Terms(
            @NotNull ConsentPurpose purpose,
            @NotBlank String version,
            @NotBlank String body) {
    }
}
