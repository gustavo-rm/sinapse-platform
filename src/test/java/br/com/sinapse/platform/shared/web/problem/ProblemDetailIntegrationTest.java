package br.com.sinapse.platform.shared.web.problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.sinapse.platform.IntegrationTest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Asserts the error contract of ADR 0009 against the running chain: media type, members
 * of the body, and — the part that actually matters — the absence of anything the client
 * must never see.
 */
@ExtendWith(OutputCaptureExtension.class)
class ProblemDetailIntegrationTest extends IntegrationTest {

    /**
     * Strings the probe's failure deliberately carries in its exception message, plus the
     * markers of a leaked stack trace. None of them may appear in a response.
     */
    private static final String[] FORBIDDEN_IN_RESPONSE = {
            "aluno@example.com",
            "identity_account",
            "email_hash",
            "IllegalStateException",
            "java.lang",
            "br.com.sinapse",
            "\tat ",
            "trace"
    };

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unexpectedFailureIsLoggedWithoutTheExceptionMessage(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/api/v1/probe/failure"))
                .andExpect(status().isInternalServerError());

        assertThat(output.getAll())
                .as("the message of an exception routinely quotes the value that caused it")
                .doesNotContain("aluno@example.com")
                .doesNotContain("identity_account")
                .doesNotContain("email_hash");
        assertThat(output.getAll())
                .as("a diagnosis still needs the exception type and where it came from")
                .contains("java.lang.IllegalStateException")
                .contains("br.com.sinapse.platform.shared.web.ProbeController.failure");
    }

    @Test
    void unexpectedFailureAnswersProblemJsonWithoutLeaking() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/probe/failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:sinapse:problem:internal-error"))
                .andExpect(jsonPath("$.title").value("Internal error"))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail").value("The request could not be completed."))
                .andExpect(jsonPath("$.instance").value("/api/v1/probe/failure"))
                .andReturn();

        assertBodyLeaksNothing(result);
    }

    @Test
    void validationFailureNamesTheFieldWithoutQuotingItsValue() throws Exception {
        String submitted = "aluno@example.com aluno@example.com aluno@example.com";

        MvcResult result = mockMvc.perform(post("/api/v1/probe/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"" + submitted + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:sinapse:problem:validation-failed"))
                .andExpect(jsonPath("$.errors[0].field").value("label"))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andReturn();

        assertThat(bodyOf(result))
                .as("the rejected value is the part of the request most likely to be personal data")
                .doesNotContain(submitted)
                .doesNotContain("aluno@example.com");
    }

    @Test
    void unreadableBodyIsRejectedWithoutQuotingIt() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/probe/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\": \"aluno@example.com\" "))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:sinapse:problem:malformed-request"))
                .andReturn();

        assertBodyLeaksNothing(result);
    }

    @Test
    void unknownRouteAnswersProblemJson() throws Exception {
        mockMvc.perform(get("/api/v1/probe/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:sinapse:problem:resource-not-found"));
    }

    @Test
    void unsupportedMethodAnswersProblemJson() throws Exception {
        mockMvc.perform(delete("/api/v1/probe/ping"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:sinapse:problem:method-not-allowed"));
    }

    private static void assertBodyLeaksNothing(MvcResult result) throws Exception {
        assertThat(bodyOf(result)).doesNotContain(FORBIDDEN_IN_RESPONSE);
    }

    private static String bodyOf(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
