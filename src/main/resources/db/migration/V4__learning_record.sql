-- Learning record bounded context: executed study sessions.
-- See docs/ARQUITETURA_BACKEND_SINAPSE.md section 8 and ADR 0008.
--
-- This is the evidence side of the interaction with the optimisation core.
-- Append-only once a session is closed.

create table study_session (
    id                       uuid        primary key,
    account_id               uuid        not null references account (id),
    topic_id                 uuid        not null references topic (id),

    -- Deliberately without a foreign key. Neither planning nor learningrecord may
    -- depend on the other (rule R2), and a session recorded off-plan must be
    -- structurally identical to one recorded from a plan. Orphan references are
    -- possible and are detected by a consistency job, not prevented here.
    planned_session_id       uuid,

    kind                     text        not null,
    source                   text        not null,
    status                   text        not null,
    started_at               timestamptz not null,
    ended_at                 timestamptz,
    planned_duration_minutes integer,
    actual_duration_minutes  integer,

    -- MEASURED means the app timed the session; SELF_REPORTED means the student
    -- entered it afterwards. Duration is the most basic evidence this context
    -- holds, and the two are not equally reliable, so they must be
    -- distinguishable in the data rather than silently mixed.
    duration_source          text,

    -- Four-level recall rating. This is self-report: it measures adherence and
    -- perception, not retention. See ADR 0008 and open decision D4.
    recall_rating            text,

    created_at               timestamptz not null default now(),

    constraint ck_session_kind   check (kind   in ('STUDY', 'REVISION')),
    constraint ck_session_source check (source in ('FROM_PLAN', 'SELF_DIRECTED')),
    constraint ck_session_status check (status in ('IN_PROGRESS', 'COMPLETED', 'ABANDONED')),
    constraint ck_session_recall check (
        recall_rating is null or recall_rating in ('AGAIN', 'HARD', 'GOOD', 'EASY')
    ),
    constraint ck_session_order check (ended_at is null or ended_at >= started_at),
    constraint ck_session_closed check (
        (status = 'IN_PROGRESS' and ended_at is null and actual_duration_minutes is null)
        or (status <> 'IN_PROGRESS' and ended_at is not null)
    ),
    constraint ck_session_recall_on_completion check (
        recall_rating is null or status = 'COMPLETED'
    ),
    constraint ck_session_duration_source check (
        duration_source is null or duration_source in ('MEASURED', 'SELF_REPORTED')
    ),
    constraint ck_session_duration_source_required check (
        (actual_duration_minutes is null) = (duration_source is null)
    ),
    constraint ck_session_source_consistency check (
        (source = 'FROM_PLAN')     = (planned_session_id is not null)
    )
);

-- At most one session in progress per account: a student cannot be studying two
-- things at once, and allowing it would corrupt the duration evidence.
create unique index ux_session_in_progress
    on study_session (account_id)
    where status = 'IN_PROGRESS';

-- Supports snapshot assembly: history per account over a time window.
create index ix_session_account_started
    on study_session (account_id, started_at desc);

create index ix_session_account_topic
    on study_session (account_id, topic_id, started_at desc);

-- ---------------------------------------------------------------------------
-- Append-only enforcement for closed sessions.
-- A session is mutable while IN_PROGRESS and frozen once closed.
-- ---------------------------------------------------------------------------

create or replace function fn_study_session_closed_immutable()
returns trigger as $$
begin
    if old.status <> 'IN_PROGRESS' then
        raise exception
            'study_session % is closed and cannot be modified', old.id;
    end if;
    return new;
end;
$$ language plpgsql;

create trigger tg_study_session_closed_immutable
    before update on study_session
    for each row execute function fn_study_session_closed_immutable();

create or replace function fn_study_session_no_delete()
returns trigger as $$
begin
    raise exception 'study_session rows cannot be deleted';
end;
$$ language plpgsql;

create trigger tg_study_session_no_delete
    before delete on study_session
    for each row execute function fn_study_session_no_delete();
