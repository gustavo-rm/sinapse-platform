package br.com.sinapse.platform.educational.domain;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.sinapse.platform.educational.internal.service.InviteCodes;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The alphabet, the length and the reading of an invite code. */
class InviteCodesTest {

    @Test
    void aCodeIsTenCharactersOfTheCrockfordAlphabet() {
        for (int i = 0; i < 200; i++) {
            assertThat(InviteCodes.generate())
                    .hasSize(10)
                    .matches("[0-9A-HJKMNP-TV-Z]{10}");
        }
    }

    @Test
    void theExcludedLettersNeverAppear() {
        StringBuilder drawn = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            drawn.append(InviteCodes.generate());
        }

        assertThat(drawn.toString())
                .as("I, L and O are what a reader types as 1, 1 and 0; U is left out so a draw "
                        + "cannot spell something the teacher has to apologise for")
                .doesNotContain("I")
                .doesNotContain("L")
                .doesNotContain("O")
                .doesNotContain("U");
    }

    @Test
    void codesDoNotRepeat() {
        Set<String> drawn = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            drawn.add(InviteCodes.generate());
        }

        assertThat(drawn).hasSize(2000);
    }

    @Test
    void readingFoldsCaseAndDropsSeparators() {
        assertThat(InviteCodes.normalise("abcd efghjk")).isEqualTo("ABCDEFGHJK");
        assertThat(InviteCodes.normalise("ABCDE-FGHJK")).isEqualTo("ABCDEFGHJK");
        assertThat(InviteCodes.normalise("  ABCDEFGHJK  ")).isEqualTo("ABCDEFGHJK");
    }

    @Test
    void readingMapsTheConfusableLettersOntoTheDigitsTheyLookLike() {
        assertThat(InviteCodes.normalise("I")).isEqualTo("1");
        assertThat(InviteCodes.normalise("l")).isEqualTo("1");
        assertThat(InviteCodes.normalise("O")).isEqualTo("0");
        assertThat(InviteCodes.normalise("oIl"))
                .as("unambiguous precisely because those letters were excluded from the "
                        + "alphabet: a student who typed one can only have meant the digit")
                .isEqualTo("011");
    }

    @Test
    void readingDoesNotMapU() {
        assertThat(InviteCodes.normalise("U"))
                .as("U is not a confusable, so a code containing one was simply mistyped and "
                        + "will not be found")
                .isEqualTo("U");
    }

    @Test
    void readingNeverRejects() {
        assertThat(InviteCodes.normalise(null)).isEmpty();
        assertThat(InviteCodes.normalise("")).isEmpty();
        assertThat(InviteCodes.normalise("' or 1=1 --"))
                .as("a value that is not a code normalises to something the table does not "
                        + "have, and the lookup answers exactly as it does for a code nobody "
                        + "ever issued")
                .isEqualTo("'0R1=1");
    }
}
