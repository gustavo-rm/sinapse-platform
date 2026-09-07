package br.com.sinapse.platform.readmodel.internal.security;

import br.com.sinapse.platform.readmodel.internal.web.ReadModelRoutes;
import br.com.sinapse.platform.shared.security.ApiSecurityCustomizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.stereotype.Component;

/**
 * The two routes this component owns outright, and what they require.
 *
 * <p>Signed in, and nothing more. Neither takes an account identifier: both act on the caller,
 * and whether that caller's learning data may be read at all is
 * {@code AccountAccessPolicy}, asked at the entry of the composition rather than by the chain.
 *
 * <p><strong>The other three routes are deliberately absent.</strong> The plan summary lives
 * under {@code /study-plans/**} and the two classroom reads under {@code /classrooms/**}, and
 * both patterns are already claimed — by planning, which requires a session, and by
 * educational, which requires the teacher role. Declaring them again here would not add a
 * second check: rules are matched in the order the customizers happen to run, so a broader
 * pattern registered first silently replaces the narrower one, and the plausible mistake is
 * this component quietly downgrading {@code /classrooms/**} from "teacher" to "signed in".
 * {@code ReadModelEndpointIntegrationTest} asserts what each of the five actually requires,
 * so the arrangement is verified rather than trusted.
 */
@Component
public class ReadModelSecurityCustomizer implements ApiSecurityCustomizer {

    @Override
    public void customize(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(requests -> requests
                .requestMatchers(ReadModelRoutes.MY_STATE, ReadModelRoutes.MY_AGENDA)
                        .authenticated());
    }
}
