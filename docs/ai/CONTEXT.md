# CONTEXT — sinapse-platform

Stable entry point for an agent. Read this, then [STATE.md](./STATE.md). Labels are against
`origin/main` at `e4c0be0` unless a line says otherwise. Protocol: [CLAUDE.md](../../CLAUDE.md),
"AI context protocol". Status labels:

- `OBSERVED(<sha>, <command or test>)` executed and seen; `READ(<file:line>@<sha>)` read, not executed;
- `DECISION(<ADR or "stated by project owner">)`; `PROPOSAL` not decided; `HYPOTHESIS` testable, untested;
- `UNVERIFIED(<source>)` from another AI, the other repository or a conversation, not checked here.

## Purpose

SINAPSE is an educational product for personalised study planning. This repository is its
backend: identity and consent, curriculum catalogue, teacher/classroom relations, learning
history, planning, and the boundary to the optimiser. It does not contain the optimiser: that is
the Core, a separate repository called over HTTP. READ([CLAUDE.md §1](../../CLAUDE.md)@e4c0be0)
This repository exists so that the student data, the consent gate and the record of every
generated plan live on one side, and the optimisation on the other (ADR 0002, ADR 0007).

## Architecture

Spring Modulith monolith, one deployable. Rules R1–R8 and the module list are in
[CLAUDE.md §3 and §5](../../CLAUDE.md); the full design (contexts, dependency direction, physical
schema) is [ARQUITETURA_BACKEND_SINAPSE.md](../ARQUITETURA_BACKEND_SINAPSE.md). Not repeated here.

Modules actually present under `src/main/java/br/com/sinapse/platform/`: `identity`,
`curriculum`, `curation`, `educational`, `learningrecord`, `planning` (with `orchestration/`),
`coreclient`, `datarights`, `readmodel`, `shared`. READ(`find src/main/java -maxdepth 6 -type d`@e4c0be0)
CLAUDE.md §3 lists only seven of them (see STATE.md K10).

Main flow, plan generation (READ, files under `planning/orchestration/` unless noted):

1. `POST /api/v1/study-plans/generation-requests` queues a job and answers `201` at once
   (`GenerationRequestController.java:62-86`).
2. `PlanGenerationWorker` polls the table queue every 10 s (`application.yml`, `poll-cron`). Each
   pass first requeues or fails jobs left `RUNNING` by a dead worker (`OrphanedJobRecovery`, one
   conditional UPDATE), then claims `PENDING` rows with `for update skip locked`
   (`planning/internal/persistence/PlanGenerationRequestRepository.java`). READ@12a384e
3. `PlanGenerationOrchestrator.run` (`:83-105`): `SnapshotAssembler` composes four modules into a
   `PlanRequest`; the snapshot, `algorithmParams` and seed (drawn once per job, reused by every
   attempt) are stored before the call; the Core is called through `coreclient`
   (`RestSinapseCore`); `GeneratedPlanWriter` stores plan and job completion in one transaction.
4. The client polls `GET …/generation-requests/{id}` and reads the plan from `study-plans` routes.

The Core side of this flow is in [INTEGRATION.md](./INTEGRATION.md). The API surface for a
frontend: [CONTRATO_API_SINAPSE.md](../CONTRATO_API_SINAPSE.md) plus the live OpenAPI document.

## Stack and commands

| Item | Value | Status |
|---|---|---|
| Build | Maven wrapper, artifact `br.com.sinapse:platform:0.1.0-SNAPSHOT` | READ(pom.xml:14-16@e4c0be0) |
| Runtime | Java 21, Spring Boot 3.5.16, Spring Modulith 1.4.13, springdoc 2.9.0 | READ(pom.xml:10,21,40-41@e4c0be0) |
| Database | PostgreSQL, Flyway migrations in `src/main/resources/db/migration`; Hibernate `validate` only | READ(application.yml:13-31@e4c0be0) |
| Full verification | `./mvnw verify` (needs Docker for Testcontainers, or `SINAPSE_TEST_DB_URL`) | READ([README.md](../../README.md)) |
| Module boundaries | `./mvnw test -Dtest=ModularityTests` | OBSERVED(e4c0be0, 1 test, 0 failures) |
| Run locally | `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` | READ([README.md](../../README.md)) |
| Real-Core end to end | `./mvnw verify -Pcore-e2e` (tag `core-e2e`, excluded by default) | READ(pom.xml:29,224@e4c0be0) |
| Profiles | `local` (dev), `catalog` (catalogue importer, no web server), `test` | READ(src/main/resources/*.yml, src/test/resources/application-test.yml@e4c0be0) |
| Ports | HTTP `${SINAPSE_HTTP_PORT:8080}`; management `${SINAPSE_MANAGEMENT_PORT:8081}` on 127.0.0.1 | READ(application.yml:44,61-62@e4c0be0) |
| API docs | `/api-docs`, `/swagger-ui.html` | READ(application.yml:87-91@e4c0be0) |

Quality gates, exactly as the build has them:

- JaCoCo 0.8.13 runs `prepare-agent` and `report` only: **no coverage floor**, nothing fails the
  build on coverage. READ(pom.xml:50,191-210@e4c0be0)
- `ModularityTests` (Spring Modulith `verify`) is the boundary gate. READ(src/test/java/br/com/sinapse/platform/ModularityTests.java@e4c0be0)
- No Checkstyle, SpotBugs or other static-analysis plugin. READ(pom.xml@e4c0be0)
- CLAUDE.md §8 asks for "no decrease in JaCoCo coverage"; the build does not enforce it.

## Repository map

| Path | What an agent needs to know |
|---|---|
| `src/main/java/br/com/sinapse/platform/<module>/{api,internal}` | Never import another module's `internal` (R5) |
| `src/main/java/…/coreclient/contract/` | Platform copy of the Core contract records |
| `src/main/resources/application.yml` | All tunables, with the reasoning in comments |
| `src/main/resources/db/migration/` | Flyway; append-only tables are trigger-protected (R8) |
| `src/test/resources/contract/` | Reference contract JSON, byte-identical with the Core. **Never edit.** |
| `src/test/resources/instances/` | Three synthetic real-catalogue `PlanRequest`s, also copied into the Core |
| `catalog/` | Example catalogue (`MED-ANAT`), loaded by the `catalog` profile |
| `docs/` | Team documents, Portuguese; [adr/](../adr/) holds the ADRs |
| `docs/prompts/` | Numbered implementation prompts (English) |
| `docs/ai/` | This layer |

## Conventions

- Language: English for code, comments, commits, PRs, branch names and documents for agents;
  Portuguese for team documents and everything under `docs/` outside `docs/ai/` and
  `docs/prompts/`; `README.md` in both. READ([CLAUDE.md §4.1](../../CLAUDE.md)@e4c0be0)
- Conventional Commits with module scope. READ([CLAUDE.md §4.2](../../CLAUDE.md)@e4c0be0)
- Branches `<type>/<version>/<short-description>`, e.g. `docs/1.0/ai-context-layer`.
  READ([CLAUDE.md §4.3](../../CLAUDE.md)@e4c0be0)
- PR bodies are written by hand and checked against `git diff --stat` before opening.
  DECISION(stated by project owner)
- ADRs: `docs/adr/NNNN-titulo-em-portugues.md`, header `Data` / `Status` / optional `Emenda`,
  `Substitui`, `Complementa`; a change is a new ADR, never an edit. READ(docs/adr/0015-*.md, 0016-*.md@e4c0be0;
  CLAUDE.md §11)

## The other repository

`exam-optimizer-application` (the Core), https://github.com/gustavo-rm/exam-optimizer-application,
read at `other-repo@40e6061`. It provides `POST /plans`; this repository consumes it and offers
nothing back except the shared test instances. Detail: [INTEGRATION.md](./INTEGRATION.md);
requests between the two: [HANDOFF.md](./HANDOFF.md).

## Decisions recorded here

ADRs live in [docs/adr/](../adr/). The two owner decisions below have no ADR yet; recording them
as ADRs is proposed in STATE.md (D-AI1). This PR may only touch `docs/ai/` and `CLAUDE.md`.

- Frontend is Angular, served on the same origin as the API behind a reverse proxy; the teacher
  area of the frontend is planned, not implemented; SINAPSE is two repositories, not a monorepo.
  DECISION(stated by project owner). Backend consequences already documented:
  [PROXY_REVERSO.md](../PROXY_REVERSO.md) and ADR 0009 (CSRF off, `SameSite=Strict`).
- LGPD: no student or personal data in documents, logs or commits; test accounts are synthetic.
  DECISION(stated by project owner); the code-level rules are [CLAUDE.md §10](../../CLAUDE.md).

## Where to find what

| Question | File |
|---|---|
| What may I not do / what is undecided? | [CLAUDE.md §6, §7](../../CLAUDE.md) |
| What is the state of each task right now? | [STATE.md](./STATE.md) |
| How is the Core called, and what does this side assume about it? | [INTEGRATION.md](./INTEGRATION.md) |
| What did we ask the Core, and what did it ask us? | [HANDOFF.md](./HANDOFF.md) |
| Why is the architecture like this? | [ARQUITETURA_BACKEND_SINAPSE.md](../ARQUITETURA_BACKEND_SINAPSE.md), [adr/](../adr/) |
| How does a Core failure become a job state? | [INTEGRACAO_CORE.md §2–3](../INTEGRACAO_CORE.md) |
| Which response invariants are checked? | [CORE_CONTRACT_SURVEY.md §3](../CORE_CONTRACT_SURVEY.md) |
| Which routes does a frontend use? | [CONTRATO_API_SINAPSE.md](../CONTRATO_API_SINAPSE.md), `/api-docs` |
| How do I create and activate a local account? | [DESENVOLVIMENTO_LOCAL.md](../DESENVOLVIMENTO_LOCAL.md) |
| How is the catalogue loaded? | [CARGA_DO_CATALOGO.md](../CARGA_DO_CATALOGO.md) |
| What must the reverse proxy configuration be? | [PROXY_REVERSO.md](../PROXY_REVERSO.md) |
| What did the 2026-10-05 audit find (dated snapshot)? | [PRONTIDAO_INTEGRACAO_AG.md](../PRONTIDAO_INTEGRACAO_AG.md) |

## Non-negotiables

- Never edit `src/test/resources/contract/*.json` to make a test pass; a contract change follows
  the Core's protocol (INTEGRATION.md). READ(ADR 0015, ADR 0016)
- Never weaken `ModularityTests` to make it pass (R5); never break R1–R8.
- Never build anything in CLAUDE.md §6; never decide anything in CLAUDE.md §7.
- Never update or delete consent or enrollment rows (R8).
- Never put secrets, tokens, student data or personal data in docs, logs, commits or examples.
- Never add per-student engine assignment on your own: today the engine is one deployment-wide
  setting (READ(planning/internal/config/PlanningProperties.java:88@e4c0be0)), and assigning
  conditions to students is a research decision (ethics, LGPD; CLAUDE.md §7 J2), not an
  engineering one. UNVERIFIED(planner, 2026-10-08) for the framing.
- Never run code from the Core repository; read it only, as the protocol says.
