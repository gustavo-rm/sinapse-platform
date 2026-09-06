package br.com.sinapse.platform.learningrecord.internal.security;

import br.com.sinapse.platform.learningrecord.internal.web.LearningRecordRoutes;
import br.com.sinapse.platform.shared.security.ApiSecurityCustomizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.stereotype.Component;

/**
 * The routes of this module, and what they require.
 *
 * <p>All of them, and nothing but being signed in. There is no role to ask for: every route
 * here acts on the caller's own sessions, and no route takes an account identifier — a
 * teacher reads a student through the read models, where {@code TeacherAccessPolicy} is
 * consulted.
 *
 * <p>What the chain cannot decide is whether this account's learning data may be processed at
 * all. A suspended account or a withdrawn essential consent still presents a valid session
 * token; the answer comes from {@code AccountAccessPolicy}, at the entry of each use case.
 *
 * <p>No filter is added. Authentication is identity's, and this module only declares what it
 * needs from the result.
 */
@Component
public class LearningRecordSecurityCustomizer implements ApiSecurityCustomizer {

    @Override
    public void customize(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(requests -> requests
                .requestMatchers(LearningRecordRoutes.SESSIONS, LearningRecordRoutes.SESSIONS_ANY)
                        .authenticated());
    }
}
