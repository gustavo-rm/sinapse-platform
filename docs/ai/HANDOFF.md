# HANDOFF — requests between sinapse-platform and exam-optimizer-application

This repository writes only here. A request to the Core is opened below; the Core answers in its
own `docs/ai/HANDOFF.md`, citing the ID. A request from the Core is answered below, citing its ID
and the SHA at which it was read. IDs opened here use the prefix `SP-H`.

Status values: `open` | `acknowledged` | `done`.

## Open requests to the other repository

### SP-H1 — confirm by execution that the GA keys are ignored, and say whether effective values will be reported

| Field | Value |
|---|---|
| Date | 2026-10-08 (registered here; raised earlier by the planner: UNVERIFIED(planner, 2026-10-08)) |
| What changes or is needed | (a) Confirm **by execution** that `generations`, `population-size` and `mutation-rate` in `algorithmParams` have no effect (Core EOA-13 item 0). (b) Say whether the response will report the effective `importance`, `precedence` and `provenance` (EOA-13 item 0b) |
| Why | The platform records those three keys in `algorithm_params` as if they ran (STATE.md K3); SP-8 item 3b must know what to send and what to persist |
| Interface affected | `PlanRequest.algorithmParams` (open map; no contract version change); `PlanResponse.fitness` |
| What the Core must do | Execute EOA-13 item 0; answer item 0b in its HANDOFF.md citing SP-H1 |
| Depends on this | SP-8 item 3b |
| Status | open |

Partial answer already visible in the Core, by reading only: the three keys are not read in
`src/main`, and `fitness` already carries `engine`, `prerequisite-provenance` and (GA engines only)
`importance-strategy`, `precedence-policy`. other-repo@40e6061:docs/ai/INTEGRATION.md
"algorithmParams — index" and "Effective values in the response". Item (a) is still unexecuted.

### SP-H2 — `metadata.elapsedMillis` is always 0

| Field | Value |
|---|---|
| Date | 2026-10-08 (registered here; raised by the 2026-10-05 audit) |
| What changes or is needed | A real run duration, reported somewhere that does not break byte-for-byte reproducibility (Core EOA-13 Part A) |
| Why | The platform stores `elapsed_millis` for the cost side of the GA × greedy comparison; today it is always 0 (STATE.md K4) |
| Interface affected | `PlanResponse.metadata.elapsedMillis` (or a new field: that would be a contract change) |
| What the Core must do | Decide and implement under EOA-13; announce any contract change here first |
| Depends on this | research use of `plan_generation_request.elapsed_millis` |
| Status | open |

The Core tracks it as other-repo@40e6061:docs/ai/STATE.md K5 and EOA-13 (blocked on its D4).

### SP-H3 — GA engines on the real catalogue

| Field | Value |
|---|---|
| Date | 2026-10-08 (registered here; observed by the 2026-10-05 audit, PRONTIDAO G2) |
| What changes or is needed | `ga` schedules 2–4 of 35 topics and `ga-timeline` answers `422 plan-would-be-empty` on the real catalogue |
| Why | Only `greedy-baseline` produces a usable plan on the product's real input (STATE.md K13) |
| Interface affected | `POST /plans` behaviour (no shape change) |
| What the Core must do | EOA-10 (diagnosis) and EOA-12 (repair), using the instances `src/test/resources/instances/*.json` from `sinapse-platform@cbb5529` |
| Depends on this | any use of a GA engine by the platform |
| Status | open |

The Core's EOA-10 diagnosis is merged and EOA-12 is blocked on its D1, D2:
other-repo@40e6061:docs/ai/STATE.md "Work".

## Responses to requests from the other repository

| Their ID | Read at | Response | Status |
|---|---|---|---|
| EOA-H1 | other-repo@40e6061 | Agreed. When SP-8 item 3b is written, it will reference the Core README `algorithmParams` section instead of restating it; this repository's [INTEGRATION.md](./INTEGRATION.md) already points to the Core's INTEGRATION.md for the keys. SP-8 is pending (STATE.md) | acknowledged |
| EOA-H2 | other-repo@40e6061 | Agreed: a regeneration of `src/test/resources/instances/*.json` will be announced here with the new SHA. The files have not changed since `cbb5529` (READ, `git log -- src/test/resources/instances`@e4c0be0), and all three SHA-256 values match the Core's copies. OBSERVED(e4c0be0 / other-repo@40e6061, `sha256sum` on both clones). `RealCatalogInstancesTest` checks here that they still come out of the generator (READ; needs DB, not run) | acknowledged |

Answers to items the Core lists as unknown about this repository (not requests; for its next read):

- The platform does **not** drop `generations` and `elapsedMillis`: it stores both on the job.
  READ(`planning/orchestration/GeneratedPlanWriter.java:61`@e4c0be0)
- The platform runs a twin of the Core's golden test against its own copy:
  `coreclient/CoreContractGoldenTest`. READ@e4c0be0
- What the platform assumes about the Core: [INTEGRATION.md](./INTEGRATION.md) "Assumptions about the Core".

## Cross-repository log

Newest first, at most 20 entries; older ones leave (git keeps them).

| Date | Change | Interface affected | Action required |
|---|---|---|---|
| 2026-10-08 | Core PR #35 merged: Core `docs/ai/` layer (`other-repo@40e6061`) | documentation only | platform: read it under the protocol (done in AI-1) |
| 2026-10-08 | Core PR #33/#34 merged: engine selection pinned over HTTP; `ga-timeline` registered | `algorithmParams.engine` (documentation and tests) | platform: INTEGRACAO_CORE.md §1 engine list is stale (STATE.md K12) |
| 2026-10-05 | Platform PR #20 merged: `validated` enforces topic, whole-window, overlap, contiguity | response validation (no shape change) | none for the Core |
| 2026-10-05 | Core PR #32 merged: EOA-10 real-scale diagnosis | `POST /plans` behaviour | platform: do not rely on GA engines (SP-H3) |
| 2026-10-05 | Platform commit `cbb5529`: three real-catalogue instances exported; copied by the Core in `e9e09d1` | test inputs | see EOA-H2 |
