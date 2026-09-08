package br.com.sinapse.platform.curation;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.sinapse.platform.curation.internal.CatalogCommand;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Reading the four verbs and two flags of ADR 0014. */
class CatalogCommandTest {

    @Test
    void eachVerbIsRecognisedWithOrWithoutTheLeadingWord() {
        assertThat(CatalogCommand.parse(List.of("validate"), List.of()).verb())
                .isEqualTo(CatalogCommand.Verb.VALIDATE);
        assertThat(CatalogCommand.parse(List.of("catalog", "diff"), List.of()).verb())
                .isEqualTo(CatalogCommand.Verb.DIFF);
        assertThat(CatalogCommand.parse(List.of("catalog", "apply"), List.of()).verb())
                .isEqualTo(CatalogCommand.Verb.APPLY);
    }

    @Test
    void removalIsOffUnlessItIsAskedForByName() {
        assertThat(CatalogCommand.parse(List.of("apply"), List.of()).allowTopicRemoval()).isFalse();
        assertThat(CatalogCommand.parse(List.of("apply"), List.of("--allow-topic-removal"))
                .allowTopicRemoval())
                .isTrue();
    }

    @Test
    void aSubjectNarrowsARunAndIsOptionalExceptForSeeding() {
        assertThat(CatalogCommand.parse(List.of("validate"), List.of("--subject=MED-ANAT")).subject())
                .isEqualTo("MED-ANAT");
        assertThat(CatalogCommand.parse(List.of("validate"), List.of()).subject()).isNull();
        assertThat(CatalogCommand.parse(List.of("seed-order"), List.of()))
                .as("seeding one subject at a time is the whole shape of the command")
                .isNull();
        assertThat(CatalogCommand.parse(List.of("seed-order"), List.of("--subject=SUB")))
                .isNotNull();
    }

    /** The natural key is two parts and one colon, and anything else is not one. */
    @Test
    void aTopicReferenceIsSubjectThenTopicAndNothingElse() {
        assertThat(br.com.sinapse.platform.curation.internal.model.TopicKey.parse("SUB:a"))
                .isEqualTo(new br.com.sinapse.platform.curation.internal.model.TopicKey("SUB", "a"));
        assertThat(br.com.sinapse.platform.curation.internal.model.TopicKey.parse("SUB:a").toString())
                .isEqualTo("SUB:a");
        assertThat(br.com.sinapse.platform.curation.internal.model.TopicKey.parse("no-colon")).isNull();
        assertThat(br.com.sinapse.platform.curation.internal.model.TopicKey.parse(":a")).isNull();
        assertThat(br.com.sinapse.platform.curation.internal.model.TopicKey.parse("SUB:")).isNull();
        assertThat(br.com.sinapse.platform.curation.internal.model.TopicKey.parse("A:B:C")).isNull();
        assertThat(br.com.sinapse.platform.curation.internal.model.TopicKey.parse(null)).isNull();
    }

    @Test
    void anUnknownVerbIsRefusedRatherThanGuessedAt() {
        assertThat(CatalogCommand.parse(List.of("aply"), List.of())).isNull();
        assertThat(CatalogCommand.parse(List.of(), List.of())).isNull();
        assertThat(CatalogCommand.usage()).contains("validate", "diff", "apply", "seed-order");
    }
}
