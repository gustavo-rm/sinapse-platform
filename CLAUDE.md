# CLAUDE.md

Operating rules for AI-assisted development on the Sinapse platform backend.
Read this file before any task. It is authoritative over inference from the codebase.

---

## 1. What this repository is

The backend of the Sinapse platform: an educational technology product for personalised
study planning. It handles identity, educational relationships, learning history and
integration boundaries.

It does **not** contain the optimisation algorithm. The Sinapse Core (genetic algorithm)
is a separate, already functional repository, consumed over HTTP.

## 2. Stack

- Java 21, Spring Boot 3.x, Maven
- PostgreSQL, Flyway for migrations
- Spring Security (Argon2id for password hashing)
- springdoc-openapi for API documentation
- JUnit 5, Testcontainers, JaCoCo
- Spring Modulith for module boundary verification

## 3. Repository layout

```
src/main/java/br/com/sinapse/platform
├── identity/          account, credentials, roles, guardian, consent
├── curriculum/        subject, topic, prerequisites
├── educational/       teacher, classroom, invite, enrollment
├── learningrecord/    executed study sessions (append-only)
├── planning/          availability, goals, generation job, study plan
│   └── orchestration/ composes modules, calls the core, stores the plan
├── coreclient/        adapter for the Sinapse Core
└── shared/

src/main/resources/db/migration/   Flyway
docs/                              architecture and ADRs (Portuguese)
docs/adr/                          architecture decision records
docs/prompts/                      numbered implementation prompts
```

Every module has `api/` (public interfaces and DTOs) and `internal/`.
Nothing outside a module may import its `internal/` package.

## 4. Language and Git conventions

### 4.1 Language

**General principle:** engineering and technical implementation are in English;
communication, reports and instructions intended for the development team are in
Portuguese. Apply this consistently to all new and modified content.

| Artifact | Language |
|---|---|
| Code: classes, methods, variables, packages | English |
| Javadoc, code comments, SQL comments | English |
| Commit messages, pull requests, branch names | English |
| AI-only documents, files under `docs/prompts/`, this file | English |
| Documents for developers and the team: reports, instructions, informative material | Portuguese |
| Files under `docs/` (architecture, ADRs) | Portuguese |
| `README.md` | Always both: Portuguese and English |

Portuguese documents must read as Portuguese, not as translated English.

The most common slip: the report you write at the end of a task is a document for the
team, so it is in Portuguese, while the commit that accompanies it is in English. Both
rules apply at once.

### 4.2 Commits

Conventional Commits, in English, imperative mood, no trailing period:

```
type: subject
type(scope): subject
```

Types: `feat`, `fix`, `docs`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`,
`revert`, `style`.

Scope is the module name when the change is confined to one: `identity`, `curriculum`,
`educational`, `learningrecord`, `planning`, `coreclient`, `shared`. Omit the scope when
the change spans modules or is repository-wide.

Breaking changes use a `BREAKING CHANGE:` footer.

```
feat(identity): add consent revocation endpoint
fix(educational): reject invite redemption for archived classroom
test(identity): cover majority reaffirmation grace period
docs: record teacher access decision as ADR 0005
```

### 4.3 Branches

Branch names carry the type and the target version:

```
<type>/<version>/<short-description>
```

`<type>` uses the same vocabulary as commit types (`feature`, `fix`, `refactor`, `docs`,
`test`, `chore`). `<short-description>` is lowercase, hyphen-separated, English.

```
feature/1.0/identity-consent-model
fix/1.0/swagger-ui-version-mismatch
refactor/1.1/extract-access-policy
```

## 5. Architecture rules

These are settled. Do not re-open them while implementing.

**R1 — Cross-module references use identifiers only.**
`StudySession` holds `topicId`, never `@ManyToOne Topic`. No JPA association crosses a
module boundary.

**R2 — `planning` does not read `learningrecord`.**
Composition happens in `planning.orchestration`. This is what keeps the dependency graph
acyclic. Do not "simplify" it by injecting a `learningrecord` service into `planning`.

**R3 — `curriculum` and `identity` depend on nothing.**
They are the base. They must not import any other module.

**R4 — Foreign keys follow the module dependency direction.**
A foreign key pointing the way a module is already allowed to depend is fine
(`educational.teacher.account_id → account`). A foreign key against that direction is not.

**R5 — Boundaries are enforced by the build.**
`ModularityTests` must pass. If it fails, fix the design, not the test.

**R6 — Authorisation is derived, never stored as a direct link.**
A teacher can read a student's data if and only if there is an active enrollment of that
student in a classroom owned by that teacher AND the student's `INSTITUTION_SHARING`
consent is currently valid. There is no direct teacher→student relation.

**R7 — Write follows the aggregate, read follows the screen.**
Read models live in `readmodel`, compose through each module's `api`, never query another
module's tables, and never write. Modules expose batch lookups by id set, not only unit
lookups — otherwise composition produces N+1. See ADR 0013.

**R8 — Consent and enrollment records are append-only.**
Database triggers enforce this. Never write code that expects to update or delete them.
Ending something means writing a timestamp.

## 6. Hard prohibitions

Do not implement any of the following, even if the product context document mentions
them, even if the current architecture would support them:

- Payment, billing, subscriptions, invoices, payment webhooks
- RAG, LLM orchestration, AI-generated flashcards
- Spaced repetition algorithms
- Institutions as an entity, multi-tenancy, `tenant_id` columns
- Microservices, message brokers, Redis, object storage
- Analytics dashboards
- The optimisation algorithm itself
- Guardian consent flow (structure exists, branch is v2 — see §7)

If a task seems to require one of these, stop and report rather than building it.

## 7. Open decisions — never guess

These are unresolved and belong to a human. If a task touches one, stop and report.

| ID | Question |
|---|---|
| J1 | Age threshold that triggers guardian consent. Configurable parameter, currently unset. |
| J2 | Ethics committee approval before any research-purpose data collection. |
| J3 | Retention policy for `evidence`, generation snapshots and session metadata. |
| D4 | Objective retention measurement. v1 measures adherence and perception only. Blocking for the thesis, not for the code. |
| D5 | Article 18 erasure: what is deleted, what survives, and whether the outcome is anonymisation or key destruction. |
| P2 | How an account is granted the `TEACHER` role. |
| P3 | Structure of the teacher feedback instrument. |

**v1 scope note:** only the `SELF` consent branch is implemented. Registration by anyone
under 18 is rejected with a clear message. The `ConsentRecord` structure, the age field
and the access gate are all built now; the `GUARDIAN` branch is not.

## 8. Definition of done

A task is not done when it compiles. It is done when all of the following hold:

1. Implementation complete
2. Unit tests for business rules; integration tests with Testcontainers for anything
   touching the database
3. Every invariant listed in the architecture document has a test that fails when the
   invariant is violated
4. `./mvnw verify` passes, including `ModularityTests`
5. No decrease in JaCoCo coverage
6. OpenAPI annotations present on new endpoints
7. Javadoc on public module APIs

When fixing a bug, add the regression test first.

## 9. Verification

```bash
./mvnw verify                                  # full build, tests, coverage
./mvnw test -Dtest=ModularityTests             # module boundaries only
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Run the full verification before reporting a task complete. Report actual output, not
expected output.

## 9.5 Cross-cutting rules

Decided once in ADR 0009 and ADR 0010. Do not re-decide per endpoint.

- **Errors:** RFC 7807 `application/problem+json`. `detail` never contains personal data,
  internal identifiers, entity or column names, or query fragments.
- **Routes:** everything under `/api/v1`.
- **Rate limiting:** single filter, per-route policy. Client address comes from the
  infrastructure; `X-Forwarded-For` is trusted only from a configured trusted proxy.
- **Time:** instants are `timestamptz`; local times are interpreted in `Account.timeZone`.
  No code depends on the JVM or server default zone.
- **Sessions:** server-side opaque tokens, stored hashed. Not JWT. Suspending an account
  revokes its sessions in the same transaction.
- **Logs:** no personal data, ever. Structured JSON with a correlation id.

## 10. Security and privacy defaults

- Never log e-mail, name, date of birth, guardian data or any student learning content.
  `GlobalExceptionHandler` must extend `ResponseEntityExceptionHandler` to avoid leaking
  request bodies into logs.
- Error responses never expose internal structure, stack traces or entity names.
- Every read of student data passes through `AccountAccessPolicy` or
  `TeacherAccessPolicy`. Do not scatter status checks across services.
- No secrets in code, logs, commits or error messages.
- Rate limit invite redemption attempts, per account and per origin.

## 11. How to work

1. Read the relevant section of `docs/ARQUITETURA_BACKEND_SINAPSE.md` before starting.
2. Inspect the existing code before creating new structures. Do not assume behaviour.
3. Implement incrementally. Do not generate whole layers in one pass.
4. Prefer the simplest solution that satisfies the current requirement. YAGNI applies.
5. State uncertainty explicitly. Never invent APIs, schema, or component behaviour.
6. Do not refactor for aesthetics.

**On disagreement:** if you believe an architecture rule or ADR is wrong, say so and stop.
Explain the conflict and propose an alternative. Do not silently work around it, and do
not implement something that violates it while noting the violation in a comment.

Architectural decisions live in `docs/adr/`. A change to one is a new ADR, never an edit
to an existing one.
