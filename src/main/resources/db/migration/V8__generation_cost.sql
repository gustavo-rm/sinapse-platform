-- Records what each optimisation run cost: how many generations the core ran
-- and how long it took, as the core reported them.
--
-- Both arrive in every response and were read and then discarded. They are the
-- cost side of the one comparison that justifies the genetic algorithm in the
-- thesis — whether it repays its complexity against the greedy scheduler — and
-- a cost not recorded at the moment of the run cannot be recovered afterwards
-- except by running everything again.
--
-- Nullable because jobs that finished before this migration never stored them,
-- and a failed or unfinished job has no response to take them from. Not
-- constrained beyond their type: they are stored as the core reported them, and
-- a plan is not refused over a measurement of how it was produced.

alter table plan_generation_request
    add column generations    integer,
    add column elapsed_millis bigint;
