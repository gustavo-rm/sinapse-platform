package br.com.sinapse.platform.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.sinapse.platform.identity.api.AccountStatus;
import br.com.sinapse.platform.identity.api.ConsentPurpose;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.identity.internal.domain.AccountTokenPurpose;
import br.com.sinapse.platform.identity.support.IdentityFixtures;
import br.com.sinapse.platform.identity.support.IdentityIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Registration and verification, over HTTP. */
class RegistrationEndpointIntegrationTest extends IdentityIntegrationTest {

    private static final String ACCOUNTS = "/api/v1/accounts";
    private static final String EMAIL_VERIFICATIONS = "/api/v1/email-verifications";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Clock clock;

    @Test
    void anAdultRegistersAndThenActivatesByVerifyingTheAddress() throws Exception {
        String email = fixtures.uniqueEmail();

        MvcResult result = mockMvc.perform(post(ACCOUNTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody(email, fixtures.adultDateOfBirth(), true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(AccountStatus.PENDING_VERIFICATION.name()))
                .andReturn();

        UUID accountId = UUID.fromString(
                com.jayway.jsonpath.JsonPath.read(bodyOf(result), "$.accountId"));
        assertThat(bodyOf(result))
                .as("the verification token goes to the address, which is the entire point of "
                        + "verifying it")
                .doesNotContain(notifications.requireToken(accountId,
                        AccountTokenPurpose.EMAIL_VERIFICATION));

        mockMvc.perform(post(EMAIL_VERIFICATIONS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + notifications.requireToken(accountId,
                                AccountTokenPurpose.EMAIL_VERIFICATION) + "\"}"))
                .andExpect(status().isNoContent());

        Account account = fixtures.reload(accountId);
        assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(jdbc.queryForObject(
                "select count(*) from consent_record where account_id = ? and revoked_at is null",
                Integer.class, accountId))
                .as("the essential purpose and the research one it accepted")
                .isEqualTo(2);
    }

    @Test
    void aHolderBelowTheConsentAgeIsRefusedAndNothingIsWritten() throws Exception {
        String email = fixtures.uniqueEmail();
        LocalDate seventeen = LocalDate.ofInstant(clock.instant(), IdentityFixtures.DEFAULT_ZONE)
                .minusYears(17);

        MvcResult result = mockMvc.perform(post(ACCOUNTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody(email, seventeen, false)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:sinapse:problem:minor-registration-not-supported"))
                .andReturn();

        assertThat(bodyOf(result))
                .as("the refusal explains the rule and never quotes the date of birth of the "
                        + "child it was just told about")
                .doesNotContain(seventeen.toString())
                .doesNotContain(email);
        assertThat(jdbc.queryForObject("select count(*) from account", Integer.class))
                .as("a refused registration writes nothing at all")
                .isZero();
    }

    @Test
    void anAddressAlreadyInUseIsRefused() throws Exception {
        String email = fixtures.uniqueEmail();
        fixtures.registerAdult(email);

        mockMvc.perform(post(ACCOUNTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody(email, fixtures.adultDateOfBirth(), false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:sinapse:problem:conflict"));
    }

    @Test
    void anUnknownTimeZoneIsRejectedByName() throws Exception {
        String body = """
                {"email":"%s","password":"%s","dateOfBirth":"%s","timeZone":"Mars/Olympus_Mons",
                 "acceptedTerms":[{"purpose":"LEARNING_DATA_PROCESSING","termsVersionId":"%s"}]}
                """.formatted(fixtures.uniqueEmail(), IdentityFixtures.DEFAULT_PASSWORD,
                fixtures.adultDateOfBirth(),
                fixtures.currentTermsId(ConsentPurpose.LEARNING_DATA_PROCESSING));

        mockMvc.perform(post(ACCOUNTS).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:sinapse:problem:validation-failed"))
                .andExpect(jsonPath("$.errors[0].field").value("timeZone"));
    }

    @Test
    void aRegistrationWithoutTheEssentialPurposeIsRejected() throws Exception {
        String body = """
                {"email":"%s","password":"%s","dateOfBirth":"%s","timeZone":"%s",
                 "acceptedTerms":[{"purpose":"ACADEMIC_RESEARCH","termsVersionId":"%s"}]}
                """.formatted(fixtures.uniqueEmail(), IdentityFixtures.DEFAULT_PASSWORD,
                fixtures.adultDateOfBirth(), IdentityFixtures.DEFAULT_ZONE,
                fixtures.currentTermsId(ConsentPurpose.ACADEMIC_RESEARCH));

        mockMvc.perform(post(ACCOUNTS).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:sinapse:problem:validation-failed"))
                .andExpect(jsonPath("$.errors[?(@.field == 'essentialPurposesAccepted')]").exists());
    }

    @Test
    void sharingWithAnInstitutionCannotBeConsentedToAtRegistration() throws Exception {
        String body = """
                {"email":"%s","password":"%s","dateOfBirth":"%s","timeZone":"%s",
                 "acceptedTerms":[{"purpose":"LEARNING_DATA_PROCESSING","termsVersionId":"%s"},
                                  {"purpose":"INSTITUTION_SHARING","termsVersionId":"%s"}]}
                """.formatted(fixtures.uniqueEmail(), IdentityFixtures.DEFAULT_PASSWORD,
                fixtures.adultDateOfBirth(), IdentityFixtures.DEFAULT_ZONE,
                fixtures.currentTermsId(ConsentPurpose.LEARNING_DATA_PROCESSING),
                fixtures.currentTermsId(ConsentPurpose.INSTITUTION_SHARING));

        mockMvc.perform(post(ACCOUNTS).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'institutionSharingAbsent')]").exists());
    }

    @Test
    void anOutdatedWordingIsRefused() throws Exception {
        String body = """
                {"email":"%s","password":"%s","dateOfBirth":"%s","timeZone":"%s",
                 "acceptedTerms":[{"purpose":"LEARNING_DATA_PROCESSING","termsVersionId":"%s"}]}
                """.formatted(fixtures.uniqueEmail(), IdentityFixtures.DEFAULT_PASSWORD,
                fixtures.adultDateOfBirth(), IdentityFixtures.DEFAULT_ZONE, UUID.randomUUID());

        mockMvc.perform(post(ACCOUNTS).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:sinapse:problem:conflict"));
    }

    @Test
    void aShortPasswordIsRejectedWithoutBeingQuotedBack() throws Exception {
        String submitted = "short";
        String body = """
                {"email":"%s","password":"%s","dateOfBirth":"%s","timeZone":"%s",
                 "acceptedTerms":[{"purpose":"LEARNING_DATA_PROCESSING","termsVersionId":"%s"}]}
                """.formatted(fixtures.uniqueEmail(), submitted, fixtures.adultDateOfBirth(),
                IdentityFixtures.DEFAULT_ZONE,
                fixtures.currentTermsId(ConsentPurpose.LEARNING_DATA_PROCESSING));

        MvcResult result = mockMvc.perform(post(ACCOUNTS)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'password')]").exists())
                .andReturn();

        assertThat(bodyOf(result))
                .as("a rejected password is the last value that may appear in a response")
                .doesNotContain(submitted);
    }

    @Test
    void anUnknownVerificationTokenIsRefused() throws Exception {
        mockMvc.perform(post(EMAIL_VERIFICATIONS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"not-a-token\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:sinapse:problem:resource-not-found"));
    }

    @Test
    void aVerificationTokenIsSingleUse() throws Exception {
        Account account = fixtures.registerAdult(fixtures.uniqueEmail());
        String token = notifications.requireToken(account.id(), AccountTokenPurpose.EMAIL_VERIFICATION);
        String body = "{\"token\":\"" + token + "\"}";

        mockMvc.perform(post(EMAIL_VERIFICATIONS).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());
        mockMvc.perform(post(EMAIL_VERIFICATIONS).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    private String registrationBody(String email, LocalDate dateOfBirth, boolean acceptsResearch) {
        String essential = """
                {"purpose":"LEARNING_DATA_PROCESSING","termsVersionId":"%s"}"""
                .formatted(fixtures.currentTermsId(ConsentPurpose.LEARNING_DATA_PROCESSING));
        String research = """
                ,{"purpose":"ACADEMIC_RESEARCH","termsVersionId":"%s"}"""
                .formatted(fixtures.currentTermsId(ConsentPurpose.ACADEMIC_RESEARCH));

        return """
                {"email":"%s","password":"%s","dateOfBirth":"%s","timeZone":"%s",
                 "acceptedTerms":[%s%s]}
                """.formatted(email, IdentityFixtures.DEFAULT_PASSWORD, dateOfBirth,
                IdentityFixtures.DEFAULT_ZONE, essential, acceptsResearch ? research : "");
    }

    private static String bodyOf(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
