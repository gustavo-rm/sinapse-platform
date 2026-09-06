package br.com.sinapse.platform.identity.internal.web;

import br.com.sinapse.platform.identity.api.AccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * What a successful registration returns.
 *
 * <p>The identifier and the status, and nothing else. In particular not the verification
 * token: it goes to the address that was registered, which is the whole point of verifying
 * it. Returning it in the response would verify that someone can call an API.
 *
 * @param accountId identifier of the new account
 * @param status    where the account is, which is always awaiting verification
 */
@Schema(description = "Identifier and state of a newly registered account")
public record RegistrationResponse(UUID accountId, AccountStatus status) {
}
