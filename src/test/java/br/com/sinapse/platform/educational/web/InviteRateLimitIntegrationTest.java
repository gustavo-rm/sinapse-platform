package br.com.sinapse.platform.educational.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.sinapse.platform.educational.internal.domain.Invite;
import br.com.sinapse.platform.educational.support.EducationalIntegrationTest;
import br.com.sinapse.platform.identity.internal.domain.Account;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The rate limit on redemption attempts.
 *
 * <p>This is not a hardening step that could have waited. Invite codes are stored in clear
 * text so that a teacher can read one out again after creating it, and section 7.2 of the
 * architecture document names the limit as the compensation for that: fifty bits of entropy
 * protect nothing against a caller who may guess without limit.
 *
 * <p>Two policies cover the route, one per phase of the filter chain, because the two keys
 * answer different attacks — a script working through the code space from one address, and a
 * signed-in account doing the same from a fresh address each time. The test profile sets them
 * to three and five so that each can be reached on its own.
 */
class InviteRateLimitIntegrationTest extends EducationalIntegrationTest {

    private static final String REDEMPTIONS = "/api/v1/invites/%s/redemptions";

    /** The origin-keyed policy in the test profile. Lower than the account one, so it bites first. */
    private static final int ORIGIN_LIMIT = 3;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void guessingCodesIsCutOffAfterTheConfiguredNumberOfAttempts() throws Exception {
        String token = tokenFor(student());

        for (int attempt = 0; attempt < ORIGIN_LIMIT; attempt++) {
            mockMvc.perform(post(REDEMPTIONS.formatted("ZZZZZZZZZ" + attempt))
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isNotFound());
        }

        mockMvc.perform(post(REDEMPTIONS.formatted("ZZZZZZZZZX"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.type").value("urn:sinapse:problem:rate-limit-exceeded"));
    }

    /**
     * The refusal happens before the invite is looked up, and this is how that is shown.
     *
     * <p>Once the window is spent, a code that is <em>valid</em> is refused too. The only way
     * that can happen is if nothing consulted the invite table, which is the requirement: a
     * limit applied after the lookup would still let a guesser learn which codes are real by
     * timing or by the shape of the answer.
     */
    @Test
    void aValidCodeIsRefusedOnceTheWindowIsSpentBecauseNothingLooksItUp() throws Exception {
        Account teacher = teacher("Prof. Exemplo");
        Invite invite = invite(teacher, classroom(teacher));
        Account student = student();
        String token = tokenFor(student);

        for (int attempt = 0; attempt < ORIGIN_LIMIT; attempt++) {
            mockMvc.perform(post(REDEMPTIONS.formatted("ZZZZZZZZZ" + attempt))
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isNotFound());
        }

        mockMvc.perform(post(REDEMPTIONS.formatted(invite.code()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isTooManyRequests());

        org.assertj.core.api.Assertions.assertThat(activeEnrollmentCount(invite.classroomId()))
                .as("nothing was redeemed, because nothing reached the service")
                .isZero();
    }

    @Test
    void aDifferentCodeInThePathIsTheSameCounter() throws Exception {
        String token = tokenFor(student());

        for (int attempt = 0; attempt < ORIGIN_LIMIT; attempt++) {
            mockMvc.perform(post(REDEMPTIONS.formatted("AAAAAAAAA" + attempt))
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isNotFound());
        }

        // A counter keyed by the code in the path would reset on every guess, which is the one
        // shape of rate limiting that a guesser does not even have to work around.
        mockMvc.perform(post(REDEMPTIONS.formatted("BBBBBBBBBB"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void aForgedForwardedHeaderDoesNotResetTheCounter() throws Exception {
        String token = tokenFor(student());

        for (int attempt = 0; attempt < ORIGIN_LIMIT; attempt++) {
            mockMvc.perform(post(REDEMPTIONS.formatted("CCCCCCCCC" + attempt))
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isNotFound());
        }

        // No trusted proxy is configured, so the header is not believed. A bypass of exactly
        // this kind was already found in the Core repository.
        mockMvc.perform(post(REDEMPTIONS.formatted("DDDDDDDDDD"))
                        .header("X-Forwarded-For", "198.51.100.1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isTooManyRequests());
    }
}
