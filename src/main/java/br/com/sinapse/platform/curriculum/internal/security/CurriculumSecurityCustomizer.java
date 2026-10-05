package br.com.sinapse.platform.curriculum.internal.security;

import br.com.sinapse.platform.curriculum.internal.web.CurriculumRoutes;
import br.com.sinapse.platform.shared.security.ApiSecurityCustomizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.stereotype.Component;

/**
 * The routes of this module, and what they require.
 *
 * <p>Being signed in, and nothing else. The catalogue is curriculum, not anybody's data, so
 * there is no access policy to consult and no role to ask for; it is closed to anonymous
 * callers only because every other route of the product is.
 *
 * <p>No filter is added. Authentication is identity's, and this module only declares what it
 * needs from the result — which is also what keeps it from depending on identity (rule R3).
 */
@Component
public class CurriculumSecurityCustomizer implements ApiSecurityCustomizer {

    @Override
    public void customize(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(requests -> requests
                .requestMatchers(CurriculumRoutes.SUBJECTS, CurriculumRoutes.SUBJECTS_ANY)
                        .authenticated());
    }
}
