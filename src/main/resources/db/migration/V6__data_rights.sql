-- Data subject rights: erasure requests and the append-only exception.
-- See docs/ARQUITETURA_BACKEND_SINAPSE.md section 10 and ADR 0011.
--
-- Append-only remains the default. Erasure becomes an explicit, transaction-local
-- exception rather than a permanent hole: the triggers consult a setting that only
-- the erasure service sets, inside the erasure transaction.

-- ---------------------------------------------------------------------------
-- erasure_request
-- Deliberately holds no copy of what was erased and no e-mail: storing either
-- would reintroduce the data the request removes.
-- ---------------------------------------------------------------------------

create table erasure_request (
    id             uuid        primary key,
    account_id     uuid        not null references account (id),
    status         text        not null,
    requested_at   timestamptz not null default now(),
    effective_at   timestamptz not null,
    completed_at   timestamptz,
    cancelled_at   timestamptz,
    failure_reason text,
    constraint ck_erasure_status check (
        status in ('REQUESTED', 'COMPLETED', 'CANCELLED', 'FAILED')
    ),
    constraint ck_erasure_effective check (effective_at > requested_at)
);

-- One open request per account.
create unique index ux_erasure_open
    on erasure_request (account_id)
    where status = 'REQUESTED';

create index ix_erasure_due
    on erasure_request (effective_at)
    where status = 'REQUESTED';

-- ---------------------------------------------------------------------------
-- Transaction-local erasure flag.
-- ---------------------------------------------------------------------------

create or replace function fn_is_erasure()
returns boolean as $$
begin
    return coalesce(current_setting('sinapse.erasure', true), 'off') = 'on';
end;
$$ language plpgsql stable;

-- ---------------------------------------------------------------------------
-- study_session: deletable during erasure only.
-- The longitudinal sequence of timestamped topics is effectively a behavioural
-- fingerprint, so it cannot be anonymised in place. It is erased.
-- ---------------------------------------------------------------------------

create or replace function fn_study_session_no_delete()
returns trigger as $$
begin
    if fn_is_erasure() then
        return old;
    end if;
    raise exception 'study_session rows cannot be deleted';
end;
$$ language plpgsql;

-- ---------------------------------------------------------------------------
-- planned_session: deletable during erasure only, still never updatable.
-- ---------------------------------------------------------------------------

create or replace function fn_planned_session_immutable()
returns trigger as $$
begin
    if tg_op = 'DELETE' and fn_is_erasure() then
        return old;
    end if;
    raise exception 'planned_session is immutable; generate a new plan instead';
end;
$$ language plpgsql;

-- ---------------------------------------------------------------------------
-- consent_record: survives erasure. It is the proof of the legal basis for
-- treatment that already happened, and the burden of that proof is on the
-- controller. Deletion stays forbidden in every circumstance.
--
-- What does not survive is `evidence` (IP, user agent): it is personal data and
-- is not needed to prove that consent was given, for which purpose, under which
-- terms version, and when.
-- ---------------------------------------------------------------------------

create or replace function fn_consent_record_immutable()
returns trigger as $$
begin
    if fn_is_erasure() then
        if new.id               is distinct from old.id
        or new.account_id       is distinct from old.account_id
        or new.purpose          is distinct from old.purpose
        or new.terms_version_id is distinct from old.terms_version_id
        or new.granted_by       is distinct from old.granted_by
        or new.granted_at       is distinct from old.granted_at
        or new.revoked_at       is distinct from old.revoked_at then
            raise exception
                'during erasure only evidence and guardian_id may be cleared';
        end if;
        return new;
    end if;

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

-- The guardian_id foreign key must not block deletion of the guardian row during
-- erasure, since the consent record itself survives.
alter table consent_record
    drop constraint consent_record_guardian_id_fkey;

alter table consent_record
    add constraint consent_record_guardian_id_fkey
    foreign key (guardian_id) references guardian (id) on delete set null;

-- enrollment keeps its unconditional no-delete trigger: ended enrollments survive
-- erasure as the audit trail of past teacher access, pointing at the anonymised
-- account shell.
