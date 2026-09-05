-- Identity bounded context.
-- See docs/ARQUITETURA_BACKEND_SINAPSE.md section 5 and ADR 0004.

create extension if not exists citext;

-- ---------------------------------------------------------------------------
-- account
-- ---------------------------------------------------------------------------

create table account (
    id            uuid        primary key,
    email         citext      not null,
    password_hash text        not null,
    date_of_birth date        not null,
    -- IANA zone id, e.g. America/Sao_Paulo. Required: a local time such as
    -- "19:00 to 21:00" has no determined meaning without it, and the plan is
    -- generated in absolute instants.
    time_zone     text        not null,
    status        text        not null,
    created_at    timestamptz not null default now(),
    activated_at  timestamptz,
    suspended_at  timestamptz,
    anonymized_at timestamptz,
    constraint ck_account_status check (status in (
        'PENDING_VERIFICATION',
        'PENDING_GUARDIAN_CONSENT',
        'ACTIVE',
        'SUSPENDED',
        'ANONYMIZED'
    ))
);

-- Email uniqueness must not block re-registration after an Art. 18 erasure.
create unique index ux_account_email
    on account (email)
    where anonymized_at is null;

create table account_role (
    account_id uuid not null references account (id),
    role       text not null,
    primary key (account_id, role),
    constraint ck_account_role check (role in ('STUDENT', 'TEACHER', 'ADMIN'))
);

-- ---------------------------------------------------------------------------
-- guardian
-- Internal entity of the Account aggregate. One guardian per account.
-- ---------------------------------------------------------------------------

create table guardian (
    id                      uuid        primary key,
    account_id              uuid        not null unique references account (id),
    full_name               text        not null,
    email                   citext      not null,
    relationship            text        not null,
    verification_token_hash text,
    verification_expires_at timestamptz,
    verified_at             timestamptz,
    created_at              timestamptz not null default now()
);

-- ---------------------------------------------------------------------------
-- terms_version
-- Without the stored text we can prove that consent happened, but not to what.
-- ---------------------------------------------------------------------------

create table terms_version (
    id           uuid        primary key,
    purpose      text        not null,
    version      text        not null,
    body         text        not null,
    published_at timestamptz not null,
    constraint ck_terms_purpose check (purpose in (
        'LEARNING_DATA_PROCESSING',
        'INSTITUTION_SHARING',
        'ACADEMIC_RESEARCH'
    )),
    constraint uq_terms_purpose_version unique (purpose, version),
    -- Enables the composite foreign key from consent_record below.
    constraint uq_terms_id_purpose unique (id, purpose)
);

-- ---------------------------------------------------------------------------
-- consent_record
-- Append-only legal record. Only revoked_at may ever change.
-- ---------------------------------------------------------------------------

create table consent_record (
    id               uuid        primary key,
    account_id       uuid        not null references account (id),
    purpose          text        not null,
    terms_version_id uuid        not null,
    granted_by       text        not null,
    guardian_id      uuid        references guardian (id),
    granted_at       timestamptz not null,
    revoked_at       timestamptz,
    evidence         jsonb       not null,
    constraint ck_consent_granted_by check (granted_by in ('SELF', 'GUARDIAN')),
    constraint ck_consent_guardian_presence check (
        (granted_by = 'GUARDIAN' and guardian_id is not null) or
        (granted_by = 'SELF'     and guardian_id is null)
    ),
    constraint ck_consent_revocation_order check (
        revoked_at is null or revoked_at >= granted_at
    ),
    -- Guarantees consent_record.purpose matches the purpose of the accepted terms.
    constraint fk_consent_terms
        foreign key (terms_version_id, purpose)
        references terms_version (id, purpose)
);

-- At most one active consent per account and purpose, enforced by the database.
create unique index ux_active_consent
    on consent_record (account_id, purpose)
    where revoked_at is null;

create index ix_consent_account
    on consent_record (account_id);

-- Supports the daily majority-transition job: finds accounts whose essential
-- consent was granted by a guardian.
create index ix_consent_granted_by_guardian
    on consent_record (account_id, granted_at)
    where granted_by = 'GUARDIAN' and revoked_at is null;

-- ---------------------------------------------------------------------------
-- Append-only enforcement.
-- This guards against our own ORM mapping mistakes, not against an attacker.
-- ---------------------------------------------------------------------------

create or replace function fn_consent_record_immutable()
returns trigger as $$
begin
    if new.id               is distinct from old.id
    or new.account_id       is distinct from old.account_id
    or new.purpose          is distinct from old.purpose
    or new.terms_version_id is distinct from old.terms_version_id
    or new.granted_by       is distinct from old.granted_by
    or new.guardian_id      is distinct from old.guardian_id
    or new.granted_at       is distinct from old.granted_at
    or new.evidence         is distinct from old.evidence then
        raise exception 'consent_record is append-only: only revoked_at may change';
    end if;

    if old.revoked_at is not null
       and new.revoked_at is distinct from old.revoked_at then
        raise exception 'consent_record.revoked_at cannot be changed once set';
    end if;

    return new;
end;
$$ language plpgsql;

create trigger tg_consent_record_immutable
    before update on consent_record
    for each row execute function fn_consent_record_immutable();

create or replace function fn_consent_record_no_delete()
returns trigger as $$
begin
    raise exception 'consent_record rows cannot be deleted; revoke instead';
end;
$$ language plpgsql;

create trigger tg_consent_record_no_delete
    before delete on consent_record
    for each row execute function fn_consent_record_no_delete();

-- ---------------------------------------------------------------------------
-- account_token
-- Single-use tokens delivered to the account holder: e-mail verification,
-- password reset, majority reaffirmation.
--
-- Guardian verification keeps its own columns on the guardian table because
-- that token is delivered to a third party, not to the account holder.
-- ---------------------------------------------------------------------------

create table account_token (
    id          uuid        primary key,
    account_id  uuid        not null references account (id),
    purpose     text        not null,
    token_hash  text        not null unique,
    expires_at  timestamptz not null,
    consumed_at timestamptz,
    created_at  timestamptz not null default now(),
    constraint ck_account_token_purpose check (purpose in (
        'EMAIL_VERIFICATION',
        'PASSWORD_RESET',
        'MAJORITY_REAFFIRMATION'
    ))
);

create index ix_account_token_active
    on account_token (account_id, purpose)
    where consumed_at is null;

-- ---------------------------------------------------------------------------
-- user_session
-- Server-side opaque sessions. Stateless tokens were rejected: suspending an
-- account must terminate its active sessions immediately, because the access
-- gate is the compliance mechanism, and a stateless token would keep working
-- until it expired. See ADR 0010.
-- ---------------------------------------------------------------------------

create table user_session (
    id                  uuid        primary key,
    account_id          uuid        not null references account (id),
    token_hash          text        not null unique,
    created_at          timestamptz not null default now(),
    last_seen_at        timestamptz not null default now(),
    absolute_expires_at timestamptz not null,
    revoked_at          timestamptz,
    user_agent          text,
    ip_hash             text
);

create index ix_user_session_account_active
    on user_session (account_id)
    where revoked_at is null;
