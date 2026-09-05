package br.com.sinapse.platform.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.sinapse.platform.identity.internal.service.SecureTokens;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The values that stand between an account and whoever guesses at it. */
class SecureTokensTest {

    private static final int EXPECTED_BYTES = 32;

    @Test
    void aTokenCarries256Bits() {
        byte[] decoded = Base64.getUrlDecoder().decode(SecureTokens.generate());

        assertThat(decoded).hasSize(EXPECTED_BYTES);
    }

    @Test
    void tokensDoNotRepeat() {
        Set<String> generated = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            generated.add(SecureTokens.generate());
        }

        assertThat(generated).hasSize(1000);
    }

    @Test
    void hashingIsStableAndHidesTheValue() {
        String token = SecureTokens.generate();

        assertThat(SecureTokens.hash(token)).isEqualTo(SecureTokens.hash(token));
        assertThat(SecureTokens.hash(token))
                .hasSize(64)
                .doesNotContain(token);
        assertThat(SecureTokens.hash(token)).isNotEqualTo(SecureTokens.hash(SecureTokens.generate()));
    }
}
