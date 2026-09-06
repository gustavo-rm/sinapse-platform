package br.com.sinapse.platform.datarights.internal.security;

import br.com.sinapse.platform.datarights.internal.web.DataRightsRoutes;
import br.com.sinapse.platform.shared.security.ApiSecurityCustomizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.stereotype.Component;

/**
 * The routes of this coordinator, and what they require.
 *
 * <p>Signed in, and nothing more. There is no role to ask for and no account identifier in any
 * path: every route acts on the caller.
 *
 * <p>What is <em>not</em> asked for matters as much. These are the only routes in the platform
 * that do not consult {@code AccountAccessPolicy} at the entry of their use case, and that is
 * deliberate: a holder who has withdrawn their consent, or who is waiting out an erasure window
 * with their account suspended, still has the right to take their own data and to change their
 * mind. Gating these on whether the data may still be processed would make the seven-day window
 * a countdown the holder could only watch.
 */
@Component
public class DataRightsSecurityCustomizer implements ApiSecurityCustomizer {

    @Override
    public void customize(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(requests -> requests
                .requestMatchers(DataRightsRoutes.ERASURE_REQUESTS,
                        DataRightsRoutes.ERASURE_REQUESTS_ANY,
                        DataRightsRoutes.DATA_EXPORT,
                        DataRightsRoutes.ACCESS_DISCLOSURE)
                        .authenticated());
    }
}
