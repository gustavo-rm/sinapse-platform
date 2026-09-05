-- Planning bounded context: availability, goals, generation job, study plan.
-- See docs/ARQUITETURA_BACKEND_SINAPSE.md section 9 and ADR 0007.
--
-- This is the output side of the interaction with the optimisation core.

-- ---------------------------------------------------------------------------
-- study_availability
-- Recurring weekly slots, with a validity window so that a change of routine
-- does not destroy the availability that a past plan was generated against.
-- ---------------------------------------------------------------------------

create table study_availability (
    id              uuid        primary key,
    account_id      uuid        not null references account (id),
    day_of_week     smallint    not null,
    start_time      time        not null,
    end_time        time        not null,
    effective_from  date        not null,
    effective_until date,
    created_at      timestamptz not null default now(),
    constraint ck_availability_day check (day_of_week between 0 and 6),
    constraint ck_availability_time check (end_time > start_time),
    constraint ck_availability_window check (
        effective_until is null or effective_until >= effective_from
    )
);

create index ix_availability_account
    on study_availability (account_id, day_of_week);

-- ---------------------------------------------------------------------------
-- study_goal
-- ---------------------------------------------------------------------------

create table study_goal (
    id          uuid        primary key,
    account_id  uuid        not null references account (id),
    subject_id  uuid        not null references subject (id),
    target_date date,
    priority    smallint    not null default 3,
    status      text        not null,
    created_at  timestamptz not null default now(),
    achieved_at timestamptz,
    constraint ck_goal_priority check (priority between 1 and 5),
    constraint ck_goal_status   check (status in ('ACTIVE', 'ACHIEVED', 'ABANDONED'))
);

create unique index ux_goal_active_subject
    on study_goal (account_id, subject_id)
    where status = 'ACTIVE';

-- ---------------------------------------------------------------------------
-- plan_generation_request
-- The asynchronous job. Doubles as the queue: workers claim rows with
-- SELECT ... FOR UPDATE SKIP LOCKED. A dedicated broker is not justified at the
-- pilot's scale.
--
-- snapshot, core_version, algorithm_params and random_seed together are what
-- make a plan reproducible. Without all four, a plan generated today cannot be
-- regenerated tomorrow, because availability and history will have changed.
-- This is a thesis requirement, not audit comfort.
--
-- snapshot stores the exact payload sent to the core. It is not normalised on
-- purpose: normalising it would create a second copy of the domain model that
-- would have to be kept in sync.
-- ---------------------------------------------------------------------------

create table plan_generation_request (
    id               uuid        primary key,
    account_id       uuid        not null references account (id),
    status           text        not null,
    horizon_start    date        not null,
    horizon_end      date        not null,
    snapshot         jsonb,
    core_version     text,
    algorithm_params jsonb,
    random_seed      bigint,

    -- Which curated catalogue state produced this plan. Needed to attribute an
    -- experimental result to a specific curation state; the snapshot alone
    -- carries the edges but not their revision.
    catalog_import_id uuid       references catalog_import (id),

    requested_at     timestamptz not null default now(),
    started_at       timestamptz,
    finished_at      timestamptz,
    attempt_count    integer     not null default 0,
    failure_reason   text,
    constraint ck_request_status check (
        status in ('PENDING', 'RUNNING', 'READY', 'FAILED', 'CANCELLED')
    ),
    constraint ck_request_horizon check (horizon_end > horizon_start),
    constraint ck_request_attempts check (attempt_count >= 0)
);

-- One non-terminal job per account. Prevents a student from queuing an unbounded
-- number of optimisation runs.
create unique index ux_request_active
    on plan_generation_request (account_id)
    where status in ('PENDING', 'RUNNING');

-- Queue scan.
create index ix_request_pending
    on plan_generation_request (requested_at)
    where status = 'PENDING';

-- ---------------------------------------------------------------------------
-- study_plan
-- Immutable output. Re-planning creates a new plan and supersedes the previous
-- one; a plan is never edited in place. The chain of superseded plans is
-- experimental data about how often and why re-planning happens.
-- ---------------------------------------------------------------------------

create table study_plan (
    id                    uuid        primary key,
    account_id            uuid        not null references account (id),
    generation_request_id uuid        not null unique
                                      references plan_generation_request (id),
    horizon_start         date        not null,
    horizon_end           date        not null,
    status                text        not null,
    fitness               jsonb,
    created_at            timestamptz not null default now(),
    superseded_at         timestamptz,
    superseded_by_plan_id uuid        references study_plan (id),
    constraint ck_plan_status check (status in ('ACTIVE', 'SUPERSEDED')),
    constraint ck_plan_supersession check (
        (status = 'SUPERSEDED') = (superseded_at is not null)
    )
);

create unique index ux_plan_active
    on study_plan (account_id)
    where status = 'ACTIVE';

-- ---------------------------------------------------------------------------
-- planned_session
-- ---------------------------------------------------------------------------

create table planned_session (
    id               uuid        primary key,
    plan_id          uuid        not null references study_plan (id),
    topic_id         uuid        not null references topic (id),
    kind             text        not null,
    scheduled_start  timestamptz not null,
    duration_minutes integer     not null,
    sequence_index   integer     not null,
    constraint ck_planned_kind check (kind in ('STUDY', 'REVISION')),
    constraint ck_planned_duration check (duration_minutes > 0),
    constraint uq_planned_sequence unique (plan_id, sequence_index)
);

create index ix_planned_session_plan
    on planned_session (plan_id, scheduled_start);

-- ---------------------------------------------------------------------------
-- Immutability of generated plans.
-- ---------------------------------------------------------------------------

create or replace function fn_planned_session_immutable()
returns trigger as $$
begin
    raise exception 'planned_session is immutable; generate a new plan instead';
end;
$$ language plpgsql;

create trigger tg_planned_session_immutable
    before update or delete on planned_session
    for each row execute function fn_planned_session_immutable();

create or replace function fn_study_plan_supersede_only()
returns trigger as $$
begin
    if new.horizon_start         is distinct from old.horizon_start
    or new.horizon_end           is distinct from old.horizon_end
    or new.account_id            is distinct from old.account_id
    or new.generation_request_id is distinct from old.generation_request_id
    or new.fitness               is distinct from old.fitness then
        raise exception 'study_plan is immutable except for supersession';
    end if;
    return new;
end;
$$ language plpgsql;

create trigger tg_study_plan_supersede_only
    before update on study_plan
    for each row execute function fn_study_plan_supersede_only();
