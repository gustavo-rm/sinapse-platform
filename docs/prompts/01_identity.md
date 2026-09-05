# Prompt 01 — Identity bounded context

**Prerequisite:** prompt 00 complete and verified.
**Scope:** identity module only — account, guardian, terms, consent, access gate.
**Out of scope:** teacher, classroom, invite, enrollment, curriculum, planning,
authentication endpoints for teachers, the `GUARDIAN` consent branch.

Read `CLAUDE.md` and section 5 of `docs/ARQUITETURA_BACKEND_SINAPSE.md` before starting.
The migration `V1__identity.sql` already exists and is authoritative — do not modify it;
map the code to it.

---

## Task

### 1. Aggregates

| Aggregate | Notes |
|---|---|
| `Account` (root) | holds `Guardian` as an internal entity |
| `ConsentRecord` (root) | append-only |
| `TermsVersion` (root) | versioned consent text |

`Account` status: `PENDING_VERIFICATION`, `PENDING_GUARDIAN_CONSENT`, `ACTIVE`,
`SUSPENDED`, `ANONYMIZED`.

Consent purposes: `LEARNING_DATA_PROCESSING` (essential), `INSTITUTION_SHARING`
(conditional), `ACADEMIC_RESEARCH` (optional, refusable with no service impact).

### 2. Invariants

Each of these needs a test that fails when the invariant is violated:

1. `ACTIVE` requires a valid `ConsentRecord` for every essential purpose, with
   `grantedBy` consistent with the holder's age at the time of granting.
2. `dateOfBirth` is immutable after activation, mutable before.
3. `Guardian` is required if and only if age at consent is below the configured threshold.
4. `ConsentRecord` is never updated except for a single write of `revokedAt`.
5. Revoking an essential purpose moves the account to `SUSPENDED` in the same transaction.
6. E-mail is unique among non-anonymised accounts.

Invariant 1 spans two aggregates. Enforce it transactionally in a `ConsentService` that is
the **only** writer of both. Do not distribute this logic.

### 3. Access gate

```java
public interface AccountAccessPolicy {
    boolean canProcessLearningData(UUID accountId);
    boolean canShareWithInstitution(UUID accountId);
    boolean canUseForResearch(UUID accountId);
}
```

Single decision point, exposed from `identity.api`. Other modules consult it. Do not
scatter status checks.

### 4. Majority transition

Not a schema change. The condition is derivable:

> An account needs reaffirmation if `dateOfBirth + 18 years <= today` and there is no
> `ConsentRecord` for an essential purpose with `grantedBy = SELF` granted on or after
> the 18th birthday.

Accounts created in adulthood satisfy this trivially. Implement a scheduled daily job
that flags accounts in the 30-day grace period and suspends those past it. Reaffirmation
creates a **new** `ConsentRecord`; the previous one is preserved.

### 5. v1 restriction

Only the `SELF` branch is implemented. Registration by anyone under 18 is rejected with a
clear message. Build the full structure — `ConsentRecord`, `dateOfBirth`, the gate, the
`GUARDIAN` value in the enum — but do not implement the guardian verification flow.

Do not hardcode 18 as the threshold. It is a configuration parameter (`J1` is unresolved);
default it to 18 and read it from configuration.

### 6. Authentication and sessions

See `docs/adr/0010-autenticacao-e-sessao.md`.

**Server-side opaque sessions. Do not use JWT.** The reason is specific: revoking an
essential consent suspends the account in the same transaction, and `AccountAccessPolicy`
is what makes that suspension effective. A stateless token would keep working until expiry,
leaving the gate inoperative in the meantime. The gate is the compliance mechanism, so this
is not acceptable.

- 256-bit token from a cryptographically secure source
- Stored only as a SHA-256 hash in `user_session`; the clear value exists only on the client
- Idle expiry 7 days, absolute expiry 30 days, both configurable
- Delivered as an `HttpOnly`, `Secure`, `SameSite=Lax` cookie for browsers, and as
  `Authorization: Bearer` with the same opaque token for non-browser clients

Required behaviour, all needing tests:

- Suspending or anonymising an account revokes its sessions in the same transaction
- Changing the password revokes all sessions of the account
- An expired or revoked token is rejected
- The account holder can list and terminate their own active sessions

Out of scope: federated login, second factor, social login.

### 7. Time zone

`Account.timeZone` is required and holds an IANA zone id. Validate it with `ZoneId.of`.

No code in this module may depend on the JVM or server default zone.

### 8. Account tokens

`account_token` holds single-use, expiring, hashed tokens for `EMAIL_VERIFICATION`,
`PASSWORD_RESET` and `MAJORITY_REAFFIRMATION`. Consuming a token is a single write of
`consumedAt`; a consumed or expired token is rejected.

Guardian verification keeps its own columns on `guardian` — that token goes to a third
party, not the account holder. Do not merge the two.

### 9. Endpoints

Registration, e-mail verification, login, consent granting, consent revocation, and
reading one's own consent history. Bean Validation on all inputs. Argon2id for passwords.

All routes under `/api/v1`. Error responses follow the RFC 7807 contract established in
prompt 00; do not introduce a second error format here.

Authentication, e-mail verification and password reset are rate-limited routes.

---

## Definition of done

All items in `CLAUDE.md` §8, plus the specific tests listed in section 8 of
`docs/ARQUITETURA_BACKEND_SINAPSE.md` under "Contexto de Identidade".

Additionally, verify at the database level, not only through the ORM:

- The partial index rejects a second active consent for the same account and purpose
- The trigger rejects an `UPDATE` to any column other than `revoked_at`
- The trigger rejects a `DELETE`

## Report

In Portuguese: what was implemented, which invariants have tests and which do not,
anything in the migration that did not map cleanly to the code, and any decision you had
to make that was not specified here.

Work on `feature/1.0/identity-context`. Commit in Conventional Commits format, in
English, scoped to `identity`, following `CLAUDE.md` §4.
