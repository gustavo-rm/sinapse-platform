-- Educational bounded context: teacher, classroom, invite, enrollment.
-- See docs/ARQUITETURA_BACKEND_SINAPSE.md section 7 and ADR 0005.
--
-- Foreign keys point only in the direction module dependencies are allowed to
-- point: educational -> identity and educational -> curriculum.

-- ---------------------------------------------------------------------------
-- teacher
-- The TEACHER role lives in identity and authorises the login.
-- This entity holds the professional link and owns classrooms.
-- ---------------------------------------------------------------------------

create table teacher (
    id               uuid        primary key,
    account_id       uuid        not null unique references account (id),
    display_name     text        not null,
    institution_name text,
    created_at       timestamptz not null default now()
);

-- ---------------------------------------------------------------------------
-- classroom
-- ---------------------------------------------------------------------------

create table classroom (
    id          uuid        primary key,
    teacher_id  uuid        not null references teacher (id),
    name        text        not null,
    status      text        not null,
    created_at  timestamptz not null default now(),
    archived_at timestamptz,
    constraint ck_classroom_status check (status in ('OPEN', 'ARCHIVED')),
    constraint ck_classroom_archived_consistency check (
        (status = 'ARCHIVED') = (archived_at is not null)
    )
);

create index ix_classroom_teacher on classroom (teacher_id);

-- Subjects the classroom covers. Does not define what the teacher can see;
-- it declares which subjects carry an institutional deadline, which is input
-- for the optimisation core.
create table classroom_subject (
    classroom_id uuid not null references classroom (id),
    subject_id   uuid not null references subject (id),
    primary key (classroom_id, subject_id)
);

-- ---------------------------------------------------------------------------
-- invite
-- Code is stored in clear text on purpose: the teacher must be able to display
-- it again after creation. Compensated by length, expiry, revocation and rate
-- limiting on redemption attempts (enforced in the application layer).
-- ---------------------------------------------------------------------------

create table invite (
    id           uuid        primary key,
    classroom_id uuid        not null references classroom (id),
    code         text        not null,
    created_by   uuid        not null references teacher (id),
    created_at   timestamptz not null default now(),
    expires_at   timestamptz not null,
    max_uses     integer,
    use_count    integer     not null default 0,
    revoked_at   timestamptz,
    constraint ck_invite_max_uses check (max_uses is null or max_uses > 0),
    constraint ck_invite_use_count check (
        use_count >= 0 and (max_uses is null or use_count <= max_uses)
    ),
    constraint ck_invite_expiry check (expires_at > created_at)
);

-- Global uniqueness, including expired and revoked codes: a code must never be
-- ambiguous, even historically.
create unique index ux_invite_code on invite (code);

create index ix_invite_classroom on invite (classroom_id);

-- ---------------------------------------------------------------------------
-- enrollment
-- Never deleted. Ending an enrollment is writing ended_at. This record is what
-- justifies past teacher access to a student's data.
-- ---------------------------------------------------------------------------

create table enrollment (
    id           uuid        primary key,
    classroom_id uuid        not null references classroom (id),
    account_id   uuid        not null references account (id),
    invite_id    uuid        references invite (id),
    enrolled_at  timestamptz not null default now(),
    ended_at     timestamptz,
    ended_reason text,
    constraint ck_enrollment_order check (
        ended_at is null or ended_at >= enrolled_at
    ),
    constraint ck_enrollment_reason check (
        (ended_at is null) = (ended_reason is null)
    ),
    constraint ck_enrollment_reason_value check (
        ended_reason is null or ended_reason in (
            'STUDENT_LEFT',
            'TEACHER_REMOVED',
            'CLASSROOM_ARCHIVED',
            'CONSENT_REVOKED'
        )
    )
);

-- At most one active enrollment per account and classroom.
create unique index ux_enrollment_active
    on enrollment (classroom_id, account_id)
    where ended_at is null;

-- Supports the authorisation check, which runs on every teacher read.
create index ix_enrollment_account_active
    on enrollment (account_id)
    where ended_at is null;

create or replace function fn_enrollment_no_delete()
returns trigger as $$
begin
    raise exception 'enrollment rows cannot be deleted; set ended_at instead';
end;
$$ language plpgsql;

create trigger tg_enrollment_no_delete
    before delete on enrollment
    for each row execute function fn_enrollment_no_delete();
