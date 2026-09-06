package br.com.sinapse.platform.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.sinapse.platform.identity.api.AccountAccessPolicy;
import br.com.sinapse.platform.identity.api.AccountStatus;
import br.com.sinapse.platform.identity.api.ConsentGrantedBy;
import br.com.sinapse.platform.identity.api.ConsentPurpose;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.identity.internal.service.ConsentService;
import br.com.sinapse.platform.identity.support.IdentityIntegrationTest;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The gate of section 5.5.
 *
 * <p>Everything downstream — planning, the learning record, a teacher's view — asks this one
 * question, so the answers it gives are the whole compliance surface of the product.
 */
class AccountAccessPolicyIntegrationTest extends IdentityIntegrationTest {

    @Autowired
    private AccountAccessPolicy policy;

    @Autowired
    private ConsentService consents;

    @Autowired
    private Clock clock;

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
    void learningDataIsClosedInEveryStateButActive(AccountStatus status) {
        UUID accountId = fixtures.insertAccount(fixtures.uniqueEmail(), fixtures.adultDateOfBirth(),
                status);
        fixtures.insertConsent(accountId, ConsentPurpose.LEARNING_DATA_PROCESSING,
                ConsentGrantedBy.SELF, null, clock.instant());

        assertThat(policy.canProcessLearningData(accountId))
                .as("a consent in force is not enough: the status is the other half, and it is "
                        + "the half a suspension acts on")
                .isFalse();
    }

    @Test
    void anUnknownAccountIsClosedEverywhere() {
        UUID unknown = UUID.randomUUID();

        assertThat(policy.canProcessLearningData(unknown)).isFalse();
        assertThat(policy.canShareWithInstitution(unknown)).isFalse();
        assertThat(policy.canUseForResearch(unknown)).isFalse();
    }

    @Test
    void anActiveHolderOpensExactlyThePurposesTheyConsentedTo() {
        Account account = fixtures.activeAdult(fixtures.uniqueEmail());

        assertThat(policy.canProcessLearningData(account.id())).isTrue();
        assertThat(policy.canShareWithInstitution(account.id()))
                .as("sharing is consented to when an invite is redeemed, not at registration")
                .isFalse();
        assertThat(policy.canUseForResearch(account.id())).isFalse();

        consents.grant(account.id(), ConsentPurpose.INSTITUTION_SHARING,
                fixtures.currentTermsId(ConsentPurpose.INSTITUTION_SHARING), fixtures.evidence());

        assertThat(policy.canShareWithInstitution(account.id())).isTrue();
    }

    @Test
    void aWithdrawalIsVisibleToTheGateImmediately() {
        Account account = fixtures.activeAdult(fixtures.uniqueEmail(), true);
        assertThat(policy.canUseForResearch(account.id())).isTrue();

        consents.revoke(account.id(), ConsentPurpose.ACADEMIC_RESEARCH);

        assertThat(policy.canUseForResearch(account.id()))
                .as("nothing is cached: the next request has to see the withdrawal, which is the "
                        + "same requirement that ruled out a stateless session")
                .isFalse();
    }
}
