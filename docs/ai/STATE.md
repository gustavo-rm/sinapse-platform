# STATE — sinapse-platform

Reviewed: 2026-10-08 UTC at `e4c0be0` (origin/main). Core read at `other-repo@40e6061`.

Present only; history is `git log` and the PR descriptions. Edit only your own row in **Work**.
Task IDs come from the project owner's planning document, which lives outside this repository;
this table is now the source of their status. What that document says about each ID, and which
finding each ID was meant to cover, is UNVERIFIED(planner, 2026-10-08) unless a row says otherwise.

## Work

| ID | Title | Status | PR | Depends on | Notes |
|---|---|---|---|---|---|
| SP-A | (planner's ID; scope not in this repository) | done | unknown | — | Merged per the planner: UNVERIFIED(planner, 2026-10-08). No PR in `git log --merges` names it |
| SP-6 | Core response validation | done | #20 (merge `e4c0be0`) | — | Topic, whole-window, overlap, contiguity enforced and tested (INTEGRATION.md "Response validation"). Horizon deliberately not checked (`RestSinapseCore.java:105-111`): whether that closes SP-6 is the owner's call. READ@e4c0be0; tests OBSERVED(e4c0be0) |
| SP-7 | Catalogue read routes and importer from the jar | done | #17 (merge `2956e0f`) | — | `GET /api/v1/subjects`, `/subjects/{id}/topics` (`curriculum/internal/web/CatalogController.java`); `CatalogRunner` gets `@Autowired` (`CatalogRunner.java:59`). READ@e4c0be0; docs: [CONTRATO_API_SINAPSE.md §7](../CONTRATO_API_SINAPSE.md), [CARGA_DO_CATALOGO.md](../CARGA_DO_CATALOGO.md) |
| SP-11 | Local account activation | done | #19 (merge `ef15b18`) | — | `DevOnlyLoggingAccountNotifier`, `local` profile only, fails closed. Same PR adds the empty-`trusted-proxies` startup warning. READ@e4c0be0; doc: [DESENVOLVIMENTO_LOCAL.md](../DESENVOLVIMENTO_LOCAL.md) |
| SP-10 | Orphaned `RUNNING` job recovery (and seed per attempt) | pending | — | — | Not in the code: K1, K2. Which findings SP-10 covers: UNVERIFIED(planner, 2026-10-08) |
| SP-8 | Engine by configuration; item 3b honest `algorithmParams` | pending | — | item 3b: SP-H1, D4 (Core) | K3. Per-student engine assignment is out of scope (CONTEXT.md "Non-negotiables") |
| SP-13 | Versioned OpenAPI snapshot | pending | — | — | K8 |
| SP-12 | Documentation reconciliation | pending | — | — | K10, K11, K12 |
| SP-9 | Coverage floor (optional) | pending | — | — | K9 |
| AUD-2 | Re-audit | pending | — | the SP rows above | Last audit: [PRONTIDAO_INTEGRACAO_AG.md](../PRONTIDAO_INTEGRACAO_AG.md), at `928eb56` |
| AI-1 | AI context layer (`docs/ai/`, CLAUDE.md protocol) | in-progress | branch `docs/1.0/ai-context-layer` | Core CTX-1 (merged, `other-repo@40e6061`) | ID assigned here; the planner had none |

The planner lists SP-8, SP-13, SP-12, SP-9 and AUD-2 as running in series; that order is
UNVERIFIED(planner, 2026-10-08).

## Open decisions

Owner of every row: project owner. The platform's own undecided questions (J1–J3, D4 retention
measurement, D5, P2, P3) are in [CLAUDE.md §7](../../CLAUDE.md) and are not repeated.
**Name clash:** CLAUDE.md §7 "D4" (objective retention measurement) is not the Core's D4 below.

| ID | Question | Blocks here | Options | Recommendation |
|---|---|---|---|---|
| Core D4 | Who owns the GA hyperparameters? | SP-8 item 3b | see other-repo@40e6061:docs/ai/STATE.md "Open decisions" | defined there; not redefined here |
| Core D1, D2, D3 | Repair criteria, repair scope, what the student sees when topics do not fit | nothing here directly; D1/D2 gate the Core's EOA-12, which SP-H3 waits on | see other-repo@40e6061:docs/ai/STATE.md | defined there |
| D-AI1 | Record the two owner decisions in CONTEXT.md "Decisions recorded here" as ADRs in `docs/adr/`? | nothing | ADR 0017+ in the existing Portuguese format; leave as DECISION lines | ADRs — PROPOSAL |

## Known issues and limitations

| ID | Description | Status | Evidence | Workaround | Resolved by |
|---|---|---|---|---|---|
| K1 | A job left `RUNNING` by a dead worker stays `RUNNING` forever; the account cannot ask again (`409`) | READ: claim selects only `PENDING`, no recovery code; executed by the audit: UNVERIFIED(PRONTIDAO D5, at 928eb56) | `PlanGenerationRequestRepository.java:65-78@e4c0be0` | manual DB intervention | SP-10 |
| K2 | Each retry draws a new seed and overwrites the previous attempt's snapshot, params and seed | READ | `PlanGenerationOrchestrator.java:85-87@e4c0be0` | stored plan stays reproducible; failed attempts are lost | SP-10 (UNVERIFIED mapping) |
| K3 | `algorithm_params` records `generations`/`population-size`/`mutation-rate` that the Core ignores | READ here; Core side READ, not executed (other-repo@40e6061:docs/ai/STATE.md K6) | INTEGRATION.md A9 | read `fitness`/`generations` for what ran | SP-8 item 3b, SP-H1 |
| K4 | `elapsed_millis` is stored but always 0 | READ (`GeneratedPlanWriter.java:61@e4c0be0`); Core K5 | INTEGRATION.md A11 | `finishedAt − startedAt` on the job | SP-H2 |
| K5 | A Core `500 plan-invariant-violation` is treated as unavailability and retried 3 times | READ | `RestSinapseCore.java:91-94@e4c0be0`; INTEGRATION.md A8 | none | unassigned |
| K6 | `trusted-proxies` is empty by default; behind the reverse proxy every anonymous limit becomes global | READ; mitigated: startup warning outside `local`/`test` | `application.yml:106`, `shared/ratelimit/TrustedProxiesStartupCheck.java@e4c0be0` | set it per deployment: [PROXY_REVERSO.md](../PROXY_REVERSO.md) | deployment configuration |
| K7 | Horizon: Core reads it in UTC, platform expands windows by days in the account's zone; the platform does not check session-in-horizon | READ; effect (silent loss of availability at the edges) is a HYPOTHESIS | [CORE_CONTRACT_SURVEY.md §3](../CORE_CONTRACT_SURVEY.md); PRONTIDAO X7 | none | unassigned |
| K8 | No versioned OpenAPI snapshot; a typed client needs the app running | READ (`find . -iname '*openapi*'`@e4c0be0: only config and a test) | — | `GET /api-docs` | SP-13 |
| K9 | No JaCoCo floor; CLAUDE.md §8 "no decrease" is not enforced | READ | `pom.xml:191-210@e4c0be0` | compare `target/site/jacoco` by hand | SP-9 |
| K10 | CLAUDE.md §3 layout omits `curation`, `datarights`, `readmodel` | READ | CLAUDE.md §3 vs `src/main/java` tree@e4c0be0 | CONTEXT.md "Architecture" | SP-12 (this PR may not rewrite CLAUDE.md) |
| K11 | Two ADRs numbered 0015 (`espelhamento`, `ponte`) | READ (`ls docs/adr`@e4c0be0) | — | cite by full file name | SP-12 |
| K12 | Team docs disagree with code: INTEGRACAO_CORE.md §1 lists engines `greedy-baseline` and `ga` (the Core also has `ga-timeline`); `PlanGenerationRequest.java:21-23` and `PlanGenerationRequestRepository.java:16-19` Javadoc say the job is not executed; ADR 0007 calls `algorithm_params` "parameters used in the run"; PRONTIDAO A2c/E1/X1/X2 describe `928eb56`, since fixed by #17, #19, #20 | READ@e4c0be0; Core engines: other-repo@40e6061:docs/ai/STATE.md (EOA-11b) | — | trust code, then this layer | SP-12 |
| K13 | `ga` and `ga-timeline` refuse or nearly empty the real catalogue; only `greedy-baseline` is usable | READ in the Core (other-repo@40e6061:docs/ai/STATE.md K2, K3) | INTEGRATION.md A13 | default engine | SP-H3 |
| K14 | `POST …/generation-requests` answers `201` without `Location`; `progress` is always absent | READ (`GenerationRequestController.java:84`, `GenerationRequestService.java:244-245@e4c0be0`); by decision F4 | — | build the poll URL from `id` | deliberate |
| K15 | Generation request rate limit 10/h per account; worker poll 10 s dominates time to `READY` | READ (`application.yml:199-203,321@e4c0be0`) | — | — | deliberate |

## Unknowns

- Current full-suite test count and coverage: the build was not run in full here (needs a
  database). The audit's 609 tests and 95.1 % instruction / 80.6 % branch coverage were at
  `928eb56`: UNVERIFIED([PRONTIDAO_INTEGRACAO_AG.md §2](../PRONTIDAO_INTEGRACAO_AG.md)).
- Whether `CoreEndToEndIntegrationTest` still passes against the Core at `other-repo@40e6061`:
  not run.
- Whether the Core ignores `generations`/`population-size`/`mutation-rate` when executed (A9):
  read on the Core side, never executed (Core EOA-13 item 0).
- The scope of SP-A, and which audit findings SP-10 covers. UNVERIFIED(planner, 2026-10-08)
- The frontend's state (Angular; teacher area planned): it lives outside this repository. The
  backend already has teacher routes (`educational`, `readmodel` classroom reads), and nothing
  grants the `TEACHER` role (CLAUDE.md §7 P2). READ@e4c0be0
