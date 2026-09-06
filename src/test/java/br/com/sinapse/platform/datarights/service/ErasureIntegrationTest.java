package br.com.sinapse.platform.datarights.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.sinapse.platform.datarights.support.DataRightsIntegrationTest;
import br.com.sinapse.platform.identity.api.AccountStatus;
import br.com.sinapse.platform.identity.internal.domain.Account;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

/**
 * The erasure itself: what goes, what stays, and what happens when it cannot finish.
 *
 * <p>Everything is verified by reading the tables directly after the transaction has committed.
 * Asserting through the services would only prove that the services no longer return the rows,
 * which is exactly what a scrub-and-keep implementation would also do.
 */
class ErasureIntegrationTest extends DataRightsIntegrationTest {

    @Test
    void everythingAdr0011ListsAsErasedIsGone() {
        Account student = fullyPopulatedStudent();
        UUID accountId = student.id();

        assertThat(erasedTables())
                .as("the fixture has to put something in every one of them, or the test proves "
                        + "nothing about the ones it left empty")
                .allSatisfy(table -> assertThat(countFor(table, accountId))
                        .withFailMessage("fixture left %s empty", table)
                        .isPositive());
        assertThat(plannedSessionCountFor(accountId)).isPositive();

        UUID requestId = eraseNow(student);

        assertThat(erasureStatusOf(requestId)).isEqualTo("COMPLETED");
        assertThat(erasedTables())
                .allSatisfy(table -> assertThat(countFor(table, accountId))
                        .withFailMessage("%s survived the erasure", table)
                        .isZero());
        assertThat(plannedSessionCountFor(accountId)).isZero();
    }

    /**
     * The snapshot, singled out because ADR 0011 singles it out.
     *
     * <p>It holds the student's whole state in one document — availability, goals, topics and
     * the history the plan was built from — which makes it the most sensitive artifact in the
     * system. The row it lives on is checked by the test above; this one checks the column,
     * because a future change that kept the job row "for the audit trail" would pass that test
     * and leave this document behind.
     */
    @Test
    void theSnapshotIsGone() {
        Account student = fullyPopulatedStudent();

        assertThat(jdbc.queryForObject("""
                select count(*) from plan_generation_request
                 where account_id = ? and snapshot is not null
                """, Integer.class, student.id()))
                .as("the fixture generated a plan, so a snapshot was stored")
                .isPositive();

        eraseNow(student);

        assertThat(jdbc.queryForObject(
                "select count(*) from plan_generation_request where snapshot is not null",
                Integer.class))
                .as("not merely none for this account: nothing in the fixture belongs to anyone "
                        + "else, so a surviving snapshot anywhere is this one")
                .isZero();
    }

    @Test
    void theConsentRecordSurvivesWithItsEvidenceCleared() {
        Account student = fullyPopulatedStudent();
        List<Map<String, Object>> before = consentRowsOf(student.id());
        assertThat(before).isNotEmpty();

        eraseNow(student);

        List<Map<String, Object>> after = consentRowsOf(student.id());
        assertThat(after)
                .as("the record is the proof of the legal basis for treatment that already "
                        + "happened, and the burden of that proof is the controller's")
                .hasSameSizeAs(before);
        assertThat(after).allSatisfy(row -> {
            assertThat(row.get("evidence"))
                    .as("the address and the agent string prove nothing about the fact and are "
                            + "the holder's personal data")
                    .isNull();
            assertThat(row.get("guardian_id"))
                    .as("a third party with no basis for retention once the holder's data is gone")
                    .isNull();
        });

        for (int index = 0; index < before.size(); index++) {
            Map<String, Object> was = before.get(index);
            Map<String, Object> is = after.get(index);
            assertThat(is.get("purpose")).isEqualTo(was.get("purpose"));
            assertThat(is.get("terms_version_id")).isEqualTo(was.get("terms_version_id"));
            assertThat(is.get("granted_by")).isEqualTo(was.get("granted_by"));
            assertThat(is.get("granted_at")).isEqualTo(was.get("granted_at"));
            assertThat(is.get("revoked_at")).isEqualTo(was.get("revoked_at"));
        }
    }

    @Test
    void endedEnrollmentsSurvivePointingAtTheShell() {
        Account student = fullyPopulatedStudent();
        int memberships = countFor("enrollment", student.id());
        assertThat(memberships).isPositive();

        eraseNow(student);

        assertThat(countFor("enrollment", student.id()))
                .as("an enrollment accounts for the access a teacher had to this student's "
                        + "data; erasing it would remove somebody else's record of who could "
                        + "see them")
                .isEqualTo(memberships);
        assertThat(jdbc.queryForObject(
                "select count(*) from enrollment where account_id = ? and ended_at is null",
                Integer.class, student.id()))
                .as("an anonymised shell cannot be an active member of anything, and a "
                        + "teacher's roster showing somebody who is no longer there would be "
                        + "wrong in the one place this module exists to be right about")
                .isZero();
    }

    @Test
    void theAccountSurvivesAsAnEmptiedShell() {
        Account student = fullyPopulatedStudent();
        String originalEmail = accountColumn(student.id(), "email", String.class);

        eraseNow(student);

        assertThat(accountColumn(student.id(), "status", String.class))
                .isEqualTo(AccountStatus.ANONYMIZED.name());
        assertThat(accountColumn(student.id(), "anonymized_at", java.sql.Timestamp.class))
                .isNotNull();
        assertThat(accountColumn(student.id(), "email", String.class))
                .as("the columns are not null in the schema, so they are overwritten with "
                        + "values that identify nobody rather than nulled")
                .isNotEqualTo(originalEmail)
                .doesNotContain("@");
        assertThat(accountColumn(student.id(), "date_of_birth", java.sql.Date.class).toString())
                .isEqualTo("1970-01-01");
        assertThat(accountColumn(student.id(), "time_zone", String.class)).isEqualTo("UTC");
        assertThat(accountColumn(student.id(), "password_hash", String.class))
                .as("not a hash of anything, so nothing can ever verify against it")
                .doesNotStartWith("$argon2");
    }

    @Test
    void theEmailOfAnErasedAccountIsFreeForANewRegistration() {
        Account student = fullyPopulatedStudent();
        String email = accountColumn(student.id(), "email", String.class);

        eraseNow(student);

        assertThatCode(() -> identity.registerAdult(email))
                .as("the unique index on the address is partial over accounts that have not "
                        + "been anonymised, precisely so that erasure does not take the address "
                        + "with it")
                .doesNotThrowAnyException();
    }

    /**
     * The exception to append-only stays an exception.
     *
     * <p>The trigger consults a transaction-local setting that only the erasure service sets. If
     * it were session-local it would outlive the transaction on a pooled connection and hand the
     * exception to whatever borrowed that connection next.
     */
    @Test
    void aDeleteOnStudySessionOutsideTheErasureStillFails() {
        Account student = fullyPopulatedStudent();

        assertThatThrownBy(() -> jdbc.update("delete from study_session where account_id = ?",
                student.id()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("study_session rows cannot be deleted");
        assertThatThrownBy(() -> jdbc.update("delete from planned_session"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("planned_session is immutable");
        assertThatThrownBy(() -> jdbc.update("delete from consent_record where account_id = ?",
                student.id()))
                .as("the consent record is undeletable in every circumstance, erasure included")
                .isInstanceOf(DataAccessException.class);

        assertThat(countFor("study_session", student.id())).isPositive();
    }

    @Test
    void theFlagDoesNotSurviveTheTransactionThatSetIt() {
        Account student = fullyPopulatedStudent();
        eraseNow(student);

        // PostgreSQL restores the value the setting had before the transaction, which for one
        // that was never set in this session is the empty string rather than unset. What
        // matters is that it is not "on", which is the only value the trigger acts on.
        assertThat(jdbc.queryForObject("select current_setting('sinapse.erasure', true)",
                String.class))
                .as("set_config with the local flag ties it to the transaction; a session-local "
                        + "one would ride a pooled connection into the next request")
                .isNotEqualTo("on");

        Account other = fullyPopulatedStudent();
        assertThatThrownBy(() -> jdbc.update("delete from study_session where account_id = ?",
                other.id()))
                .isInstanceOf(DataAccessException.class);
    }

    /**
     * The property that matters most, and the only way to check it.
     *
     * <p>A module ordered between the deletions and the emptying of the account is told to throw.
     * Everything before it has already deleted, so if the transaction did not roll back whole the
     * account would be left with its plans gone and its history intact — neither erased nor
     * usable, and nothing to say which half was missing.
     */
    @Test
    void aFailureMidErasureRollsBackEverything() {
        Account student = fullyPopulatedStudent();
        Map<String, Integer> before = countsOf(student.id());
        probe.setFailing(true);

        UUID requestId = eraseNow(student);

        assertThat(erasureStatusOf(requestId))
                .as("recorded in a transaction of its own, because the attempt's transaction "
                        + "rolled back and took every write with it")
                .isEqualTo("FAILED");
        assertThat(countsOf(student.id()))
                .as("partial erasure is worse than none")
                .isEqualTo(before);
        assertThat(accountColumn(student.id(), "status", String.class))
                .as("the account is where the request left it, suspended and not anonymised")
                .isEqualTo(AccountStatus.SUSPENDED.name());
    }

    @Test
    void aFailedRequestIsNotRetriedByTheNextPass() {
        Account student = fullyPopulatedStudent();
        probe.setFailing(true);
        UUID requestId = eraseNow(student);
        probe.setFailing(false);

        assertThat(erasureJob.runOnce())
                .as("whatever broke will break again, and an erasure looping against a broken "
                        + "module is worse than one that stopped and said so")
                .isZero();
        assertThat(erasureStatusOf(requestId)).isEqualTo("FAILED");
        assertThat(countFor("study_session", student.id())).isPositive();
    }

    @Test
    void everyModuleThatHoldsPersonalDataRunsInTheErasure() {
        assertThat(erasureService.participatingModules())
                .as("the order is a foreign key order: a row is never removed before the rows "
                        + "referencing it, and the account shell is emptied last")
                .startsWith("learningrecord", "planning", "educational")
                .endsWith("identity");
    }

    private Map<String, Integer> countsOf(UUID accountId) {
        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        erasedTables().forEach(table -> counts.put(table, countFor(table, accountId)));
        counts.put("planned_session", plannedSessionCountFor(accountId));
        counts.put("consent_record", countFor("consent_record", accountId));
        counts.put("enrollment", countFor("enrollment", accountId));
        return counts;
    }

    private List<Map<String, Object>> consentRowsOf(UUID accountId) {
        return jdbc.queryForList("""
                select id, purpose, terms_version_id, granted_by, guardian_id, granted_at,
                       revoked_at, evidence
                  from consent_record
                 where account_id = ?
                 order by granted_at, id
                """, accountId);
    }
}
