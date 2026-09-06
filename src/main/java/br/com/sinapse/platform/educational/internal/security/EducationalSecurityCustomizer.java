package br.com.sinapse.platform.educational.internal.security;

import br.com.sinapse.platform.educational.internal.web.EducationalRoutes;
import br.com.sinapse.platform.identity.api.AccountRole;
import br.com.sinapse.platform.shared.security.ApiSecurityCustomizer;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.stereotype.Component;

/**
 * The routes of this module that are not open, and what each of them requires.
 *
 * <p>No route here is anonymous. A student previewing an invite is already signed in — the
 * preview says what <em>their</em> data would be disclosed to, and answering that to whoever
 * holds a code would turn the route into a directory of classrooms and teacher names for
 * anyone willing to guess.
 *
 * <p>The teacher routes ask for the role and nothing more. Ownership of the particular
 * classroom is checked in the service, because the chain can see a path and a role and cannot
 * see who owns what. Both halves are needed: the role keeps a student out of classroom
 * management, and the ownership check keeps one teacher out of another's classroom.
 *
 * <p>No filter is added. Authentication is identity's, and this module only declares what it
 * needs from the result.
 */
@Component
public class EducationalSecurityCustomizer implements ApiSecurityCustomizer {

    @Override
    public void customize(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(requests -> requests
                // Teacher-side. The role comes from identity; how an account gets it is P2 and
                // no code path in this platform grants it yet.
                .requestMatchers(EducationalRoutes.CLASSROOMS, EducationalRoutes.CLASSROOMS_ANY)
                        .hasAuthority(AccountRole.TEACHER.authority())
                .requestMatchers(HttpMethod.DELETE, EducationalRoutes.INVITES + "/*")
                        .hasAuthority(AccountRole.TEACHER.authority())

                // Student-side. Signed in, and that is all the chain can tell: whether the
                // account may share its data is a consent question, answered in the service.
                .requestMatchers(EducationalRoutes.INVITES, EducationalRoutes.INVITES_ANY)
                        .authenticated()
                .requestMatchers(EducationalRoutes.ENROLLMENTS, EducationalRoutes.ENROLLMENTS_ANY)
                        .authenticated());
    }
}
