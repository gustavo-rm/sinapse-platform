-- How long the call to the core took, measured by this side: the wall-clock
-- latency of the HTTP call that produced the plan, network, serialisation,
-- deserialisation and any queueing in the core included.
--
-- It is not the cost of the engine, and it is not a substitute for
-- elapsed_millis. elapsed_millis keeps holding what the core reported, and the
-- core reports a reserved constant 0 on purpose so that its answer stays
-- reproducible (decision DT-2 of 2026-10-08). The engine's own cost, for any
-- comparison of engines, is measured by the core's measurement harness, not
-- here: a latency that includes the network and the core's queue cannot rank
-- engines.
--
-- Nullable, with no default and no backfill. Jobs that finished before this
-- migration were never timed, and a failed or unfinished job has no successful
-- call to time; both stay NULL, so "not measured" never reads as "took zero".
-- Only the attempt that produced the plan is recorded, in the transaction that
-- records the plan.

alter table plan_generation_request
    add column core_call_millis bigint;
