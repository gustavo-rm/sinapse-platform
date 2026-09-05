-- Curriculum bounded context: subject, topic, prerequisite graph.
-- See docs/ARQUITETURA_BACKEND_SINAPSE.md section 6 and ADR 0006.
--
-- This module is part of the base layer: it depends on nothing and holds no
-- foreign key to any other context.

-- ---------------------------------------------------------------------------
-- subject
-- ---------------------------------------------------------------------------

create table subject (
    id         uuid        primary key,
    code       text        not null unique,
    name       text        not null,
    created_at timestamptz not null default now()
);

-- ---------------------------------------------------------------------------
-- topic
-- Separate aggregate root rather than an internal entity of subject: topics are
-- referenced by identifier from three other contexts and are the primary unit of
-- planning, so loading the whole subject on every read would be wrong.
--
-- `position` is the curricular ordering inside the subject. It is the source for
-- seeding TEXTBOOK_ORDER edges and is deferrable so that a reorder can be done
-- in a single transaction.
-- ---------------------------------------------------------------------------

create table topic (
    id          uuid        primary key,
    subject_id  uuid        not null references subject (id),

    -- Stable natural key, unique within the subject. Required because the
    -- curated catalogue lives in version-controlled CSV: referencing topics by
    -- UUID is unusable for a human curator, and referencing them by position is
    -- fragile, since reordering would silently repoint every prerequisite edge.
    code        text        not null,

    name        text        not null,
    position    integer     not null,

    -- Ordinal effort band, not minutes. Nobody can defensibly assert that a
    -- topic takes 47 minutes, and a curated number would be arbitrary and
    -- irreproducible -- the same argument that ruled out continuous weights on
    -- prerequisite edges. A comparative judgement is the kind humans make
    -- reliably. The band-to-minutes mapping is configuration, calibrated
    -- against observed data, not a claim about the world.
    effort_tier text        not null,

    created_at  timestamptz not null default now(),
    constraint ck_topic_effort_tier check (
        effort_tier in ('SHORT', 'STANDARD', 'LONG', 'EXTENDED')
    ),
    constraint ck_topic_position check (position >= 0),
    constraint uq_topic_position unique (subject_id, position)
        deferrable initially deferred,
    constraint uq_topic_code unique (subject_id, code)
);

create index ix_topic_subject on topic (subject_id, position);

-- ---------------------------------------------------------------------------
-- topic_prerequisite
-- Directed edge: prerequisite_topic_id must be studied before dependent_topic_id.
--
-- The graph is global. Edges may cross subjects, because real prerequisites do
-- (trigonometry precedes kinematics). Partitioning per subject would look
-- simpler and would forbid exactly the most interesting edges.
--
-- strength maps onto the two mechanisms the optimisation core already has:
--   HARD -> constraint, handled by the repair operators
--   SOFT -> penalty in the fitness function
--
-- provenance is what makes the ablation experiment possible without adding
-- instrumentation later. TEACHER is deliberately absent: a teacher overlay must
-- be scoped to a classroom, which would make curriculum depend on educational
-- and violate rule R3. That overlay belongs in the educational module.
-- ---------------------------------------------------------------------------

create table topic_prerequisite (
    id                    uuid        primary key,
    prerequisite_topic_id uuid        not null references topic (id),
    dependent_topic_id    uuid        not null references topic (id),
    strength              text        not null,
    provenance            text        not null,
    source_reference      text,
    created_by            uuid,
    created_at            timestamptz not null default now(),
    constraint ck_prerequisite_strength
        check (strength in ('HARD', 'SOFT')),
    constraint ck_prerequisite_provenance
        check (provenance in ('CURATED', 'TEXTBOOK_ORDER', 'DERIVED')),
    constraint ck_prerequisite_no_self_edge
        check (prerequisite_topic_id <> dependent_topic_id),
    constraint uq_prerequisite_edge
        unique (prerequisite_topic_id, dependent_topic_id)
);

create index ix_prerequisite_dependent
    on topic_prerequisite (dependent_topic_id);

create index ix_prerequisite_prerequisite
    on topic_prerequisite (prerequisite_topic_id);

-- ---------------------------------------------------------------------------
-- Acyclicity.
-- Topological ordering only exists on an acyclic graph. A cycle reaching the
-- optimisation core produces undefined behaviour, so this is enforced here and
-- not in application code.
--
-- The advisory lock is required, not defensive: under read committed two
-- concurrent inserts do not see each other, so each edge passes validation in
-- isolation while together they close a cycle. Edge writes come from curation
-- and are rare, so serialising them costs nothing.
-- ---------------------------------------------------------------------------

create or replace function fn_topic_prerequisite_acyclic()
returns trigger as $$
declare
    cycle_found boolean;
begin
    perform pg_advisory_xact_lock(hashtext('topic_prerequisite_graph'));

    with recursive reachable (topic_id) as (
        select new.dependent_topic_id
        union
        select tp.dependent_topic_id
          from topic_prerequisite tp
          join reachable r on tp.prerequisite_topic_id = r.topic_id
         where tp.id is distinct from new.id
    )
    select exists (
        select 1 from reachable where topic_id = new.prerequisite_topic_id
    ) into cycle_found;

    if cycle_found then
        raise exception
            'topic_prerequisite would introduce a cycle: % -> %',
            new.prerequisite_topic_id, new.dependent_topic_id;
    end if;

    return new;
end;
$$ language plpgsql;

create trigger tg_topic_prerequisite_acyclic
    before insert or update on topic_prerequisite
    for each row execute function fn_topic_prerequisite_acyclic();

-- ---------------------------------------------------------------------------
-- catalog_import
-- One row per applied import of the curated catalogue.
--
-- The curated graph and the effort tiers are inputs to the thesis ablation
-- experiment. Without a record of which catalogue revision was in effect, a
-- result cannot be attributed to a specific curation state. `source_revision`
-- is the git commit of the CSV files that produced this state.
-- ---------------------------------------------------------------------------

create table catalog_import (
    id                 uuid        primary key,
    source_revision    text        not null,
    applied_at         timestamptz not null default now(),
    subjects_affected  integer     not null default 0,
    topics_added       integer     not null default 0,
    topics_updated     integer     not null default 0,
    edges_added        integer     not null default 0,
    edges_updated      integer     not null default 0,
    edges_removed      integer     not null default 0,
    notes              text
);

create index ix_catalog_import_applied on catalog_import (applied_at desc);
