package br.com.sinapse.platform.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.sinapse.platform.identity.api.ConsentPurpose;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.identity.internal.domain.AccountTokenPurpose;
import br.com.sinapse.platform.identity.internal.service.ConsentService;
import br.com.sinapse.platform.identity.support.IdentityFixtures;
import br.com.sinapse.platform.identity.support.IdentityIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Sessions over HTTP: the cookie ADR 0010 describes, the header alternative, and every way
 * a session has to stop working.
 */
class SessionEndpointIntegrationTest extends IdentityIntegrationTest {

    private static final String SESSIONS = "/api/v1/sessions";
    private static final String PASSWORD_CHANGES = "/api/v1/password-changes";
    private static final String PASSWORD_RESETS = "/api/v1/password-resets";
    private static final String CONSENTS = "/api/v1/consents";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ConsentService consents;

    @Test
    void theSessionCookieCarriesTheAttributesTheCsrfDecisionRestsOn() throws Exception {
        Account account = fixtures.activeAdult(fixtures.uniqueEmail());

        MvcResult result = login(account.email());
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);

        assertThat(setCookie)
                .as("CSRF protection is off, and what stands in for it is this cookie never "
                        + "leaving the site. Every attribute here is load-bearing.")
                .isNotNull()
                .contains("sinapse_session=")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Strict")
                .contains("Path=/api/v1");
    }

    @Test
    void theSameTokenWorksAsACookieAndAsABearerHeader() throws Exception {
        Account account = fixtures.activeAdult(fixtures.uniqueEmail());
        String token = tokenOf(login(account.email()));

        mockMvc.perform(get(SESSIONS).cookie(new jakarta.servlet.http.Cookie("sinapse_session", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].current").value(true));

        mockMvc.perform(get(SESSIONS).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void anAnonymousRequestToAProtectedRouteIsRefusedWithTheSharedContract() throws Exception {
        mockMvc.perform(get(SESSIONS))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:sinapse:problem:unauthenticated"));
    }

    @Test
    void anUnknownTokenAuthenticatesNothing() throws Exception {
        mockMvc.perform(get(SESSIONS).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aRevokedTokenIsRefusedOnTheNextRequest() throws Exception {
        Account account = fixtures.activeAdult(fixtures.uniqueEmail());
        String token = tokenOf(login(account.email()));

        mockMvc.perform(delete(SESSIONS + "/current").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(SESSIONS).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anExpiredTokenIsRefused() throws Exception {
        Account account = fixtures.activeAdult(fixtures.uniqueEmail());
        String token = tokenOf(login(account.email()));

        // The absolute expiry is a stored instant, so moving it into the past is how a test
        // reaches a state that would otherwise take thirty days.
        jdbc.update("update user_session set absolute_expires_at = now() - interval '1 minute' "
                + "where account_id = ?", account.id());

        mockMvc.perform(get(SESSIONS).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anIdleSessionIsRefused() throws Exception {
        Account account = fixtures.activeAdult(fixtures.uniqueEmail());
        String token = tokenOf(login(account.email()));

        jdbc.update("update user_session set last_seen_at = now() - interval '8 days' "
                + "where account_id = ?", account.id());

        mockMvc.perform(get(SESSIONS).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theHolderSeesAndEndsTheirOwnSessions() throws Exception {
        Account account = fixtures.activeAdult(fixtures.uniqueEmail());
        String first = tokenOf(login(account.email()));
        String second = tokenOf(login(account.email()));

        MvcResult listed = mockMvc.perform(get(SESSIONS)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + second))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(JsonPath.<java.util.List<Object>>read(bodyOf(listed), "$")).hasSize(2);
        assertThat(bodyOf(listed))
                .as("a session list must not become a way of reading back tokens")
                .doesNotContain(first)
                .doesNotContain(second);

        java.util.List<String> notCurrent = JsonPath.read(bodyOf(listed), "$[?(@.current == false)].id");
        assertThat(notCurrent).hasSize(1);
        UUID otherSession = UUID.fromString(notCurrent.get(0));

        mockMvc.perform(delete(SESSIONS + "/" + otherSession)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + second))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(SESSIONS).header(HttpHeaders.AUTHORIZATION, "Bearer " + first))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aSessionOfAnotherHolderCannotBeEnded() throws Exception {
        Account mine = fixtures.activeAdult(fixtures.uniqueEmail());
        Account theirs = fixtures.activeAdult(fixtures.uniqueEmail());
        String myToken = tokenOf(login(mine.email()));
        UUID theirSession = UUID.fromString(JsonPath.read(bodyOf(login(theirs.email())), "$.sessionId"));

        mockMvc.perform(delete(SESSIONS + "/" + theirSession)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + myToken))
                .andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject(
                "select count(*) from user_session where id = ? and revoked_at is null",
                Integer.class, theirSession)).isEqualTo(1);
    }

    @Test
    void suspendingAnAccountEndsItsSessionsInTheSameTransaction() throws Exception {
        Account account = fixtures.activeAdult(fixtures.uniqueEmail());
        String token = tokenOf(login(account.email()));

        consents.revoke(account.id(), ConsentPurpose.LEARNING_DATA_PROCESSING);

        assertThat(jdbc.queryForObject(
                "select count(*) from user_session where account_id = ? and revoked_at is null",
                Integer.class, account.id())).isZero();
        mockMvc.perform(get(SESSIONS).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymisingAnAccountEndsItsSessions() throws Exception {
        Account account = fixtures.activeAdult(fixtures.uniqueEmail());
        String token = tokenOf(login(account.email()));

        consents.anonymize(account.id());

        mockMvc.perform(get(SESSIONS).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changingThePasswordEndsEverySession() throws Exception {
        Account account = fixtures.activeAdult(fixtures.uniqueEmail());
        String other = tokenOf(login(account.email()));
        String mine = tokenOf(login(account.email()));

        mockMvc.perform(post(PASSWORD_CHANGES)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mine)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"%s","newPassword":"a-different-long-password"}"""
                                .formatted(IdentityFixtures.DEFAULT_PASSWORD)))
                .andExpect(status().isNoContent());

        // The case this rule exists for is the one where somebody else knows the password
        // being changed: leaving their session open would defeat the change.
        mockMvc.perform(get(SESSIONS).header(HttpHeaders.AUTHORIZATION, "Bearer " + other))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(SESSIONS).header(HttpHeaders.AUTHORIZATION, "Bearer " + mine))
                .andExpect(status().isUnauthorized());

        login(account.email(), "a-different-long-password");
    }

    @Test
    void aWrongCurrentPasswordChangesNothing() throws Exception {
        Account account = fixtures.activeAdult(fixtures.uniqueEmail());
        String token = tokenOf(login(account.email()));

        mockMvc.perform(post(PASSWORD_CHANGES)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"not-the-password","newPassword":"a-long-enough-password"}"""))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get(SESSIONS).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void resettingThePasswordEndsEverySession() throws Exception {
        Account account = fixtures.activeAdult(fixtures.uniqueEmail());
        String token = tokenOf(login(account.email()));

        mockMvc.perform(post(PASSWORD_RESETS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + account.email() + "\"}"))
                .andExpect(status().isNoContent());

        String resetToken = notifications.requireToken(account.id(), AccountTokenPurpose.PASSWORD_RESET);
        mockMvc.perform(post(PASSWORD_RESETS + "/confirmation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","newPassword":"another-long-enough-password"}"""
                                .formatted(resetToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(SESSIONS).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
        login(account.email(), "another-long-enough-password");
    }

    @Test
    void askingToResetAnUnknownAddressAnswersLikeAKnownOne() throws Exception {
        mockMvc.perform(post(PASSWORD_RESETS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody-here@example.com\"}"))
                .andExpect(status().isNoContent());

        assertThat(notifications.delivered())
                .as("answering identically is only half of it: nothing may be sent either")
                .isEmpty();
    }

    @Test
    void aWrongPasswordAndAnUnknownAddressAnswerIdentically() throws Exception {
        Account account = fixtures.activeAdult(fixtures.uniqueEmail());

        MvcResult wrongPassword = mockMvc.perform(post(SESSIONS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(account.email(), "not-the-password")))
                .andExpect(status().isUnauthorized())
                .andReturn();
        MvcResult unknownAddress = mockMvc.perform(post(SESSIONS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("nobody-here@example.com", "not-the-password")))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(bodyOf(wrongPassword))
                .as("otherwise the login route becomes a way of finding out who has an account here")
                .isEqualTo(bodyOf(unknownAddress));
    }

    /**
     * A suspended holder signs in, and can do nothing but exercise their own rights.
     *
     * <p>This used to be a refusal, and ADR 0011 is what changed it. Asking to be erased
     * suspends the account and revokes its sessions at once, and the holder then has seven days
     * to withdraw that request — which they cannot do if suspension locks them out. The same
     * applies to asking for their own data: Article 18 is about data the platform holds, not
     * about whether it may still process it for anything else.
     *
     * <p>What keeps that from being a hole is that authentication only says who is calling.
     * Every use case in every module asks {@code AccountAccessPolicy} at its entry, and it
     * answers false for anything but an active account with a valid essential consent.
     */
    @Test
    void aSuspendedHolderSignsInAndCanOnlyExerciseTheirOwnRights() throws Exception {
        Account account = fixtures.activeAdult(fixtures.uniqueEmail());
        consents.revoke(account.id(), ConsentPurpose.LEARNING_DATA_PROCESSING);

        MvcResult signedIn = mockMvc.perform(post(SESSIONS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(account.email(), IdentityFixtures.DEFAULT_PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();

        String token = JsonPath.read(bodyOf(signedIn), "$.token");
        mockMvc.perform(get("/api/v1/me/data-export")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/goals")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:sinapse:problem:access-denied"));
    }

    @Test
    void anAnonymisedHolderCannotSignInAtAll() throws Exception {
        Account account = fixtures.activeAdult(fixtures.uniqueEmail());
        jdbc.update("update account set status = 'ANONYMIZED', anonymized_at = now() where id = ?",
                account.id());

        mockMvc.perform(post(SESSIONS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(account.email(), IdentityFixtures.DEFAULT_PASSWORD)))
                // An anonymised account no longer has a holder.
                .andExpect(status().isUnauthorized());
    }

    @Test
    void repeatedGuessingIsRateLimited() throws Exception {
        Account account = fixtures.activeAdult(fixtures.uniqueEmail());
        String body = credentials(account.email(), "not-the-password");

        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post(SESSIONS).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post(SESSIONS).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.type").value("urn:sinapse:problem:rate-limit-exceeded"));
    }

    @Test
    void aForgedForwardedHeaderDoesNotResetTheCounter() throws Exception {
        Account account = fixtures.activeAdult(fixtures.uniqueEmail());
        String body = credentials(account.email(), "not-the-password");

        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post(SESSIONS).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());
        }

        // No trusted proxy is configured, so the header is not believed and the counter is
        // still the one for the address the container reported.
        mockMvc.perform(post(SESSIONS)
                        .header("X-Forwarded-For", "198.51.100.1")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void consentHistoryIsReadableByItsHolderAndNobodyElse() throws Exception {
        Account account = fixtures.activeAdult(fixtures.uniqueEmail(), true);
        String token = tokenOf(login(account.email()));
        consents.revoke(account.id(), ConsentPurpose.ACADEMIC_RESEARCH);

        mockMvc.perform(get(CONSENTS).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.purpose == 'ACADEMIC_RESEARCH')].revokedAt").isNotEmpty());

        mockMvc.perform(get(CONSENTS)).andExpect(status().isUnauthorized());
    }

    private MvcResult login(String email) throws Exception {
        return login(email, IdentityFixtures.DEFAULT_PASSWORD);
    }

    private MvcResult login(String email, String password) throws Exception {
        return mockMvc.perform(post(SESSIONS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, password)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private static String credentials(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private static String tokenOf(MvcResult result) throws Exception {
        return JsonPath.read(bodyOf(result), "$.token");
    }

    private static String bodyOf(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
