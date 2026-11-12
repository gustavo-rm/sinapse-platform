package br.com.sinapse.platform.planning.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.sinapse.platform.coreclient.contract.EdgeProvenance;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * What an edge's provenance becomes on the wire.
 *
 * <p>Pins the one lossy step deliberately: contract 1.0 has no value for an unreviewed edge, so a
 * {@code PROPOSED} edge is sent as {@code CURATED}, as it was before the catalogue could tell the
 * two apart. If the owner and the core decide to carry the distinction (decision SP-17-W), this
 * test is the one that has to change, and the contract version with it.
 */
class WireProvenanceTest {

    @Test
    void everyValueTheCoreAlreadyKnowsTravelsUnchanged() {
        assertThat(SnapshotAssembler.wireProvenanceOf(
                br.com.sinapse.platform.curriculum.api.EdgeProvenance.CURATED))
                .isEqualTo(EdgeProvenance.CURATED);
        assertThat(SnapshotAssembler.wireProvenanceOf(
                br.com.sinapse.platform.curriculum.api.EdgeProvenance.TEXTBOOK_ORDER))
                .isEqualTo(EdgeProvenance.TEXTBOOK_ORDER);
        assertThat(SnapshotAssembler.wireProvenanceOf(
                br.com.sinapse.platform.curriculum.api.EdgeProvenance.DERIVED))
                .isEqualTo(EdgeProvenance.DERIVED);
    }

    @Test
    void aProposedEdgeTravelsAsTheContractsCuratedWhichMeansAssertedByAHuman() {
        assertThat(SnapshotAssembler.wireProvenanceOf(
                br.com.sinapse.platform.curriculum.api.EdgeProvenance.PROPOSED))
                .isEqualTo(EdgeProvenance.CURATED);
    }

    @Test
    void theContractStillHasExactlyItsThreeValues() {
        assertThat(Arrays.stream(EdgeProvenance.values()).map(Enum::name))
                .as("adding a value here is a contract change, agreed with the core first")
                .containsExactly("CURATED", "TEXTBOOK_ORDER", "DERIVED");
    }
}
