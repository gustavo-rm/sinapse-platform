# Prompt 00 — Project bootstrap

**Scope:** project skeleton, build configuration, module structure, boundary enforcement.
**Out of scope:** any domain entity, any table, any endpoint. Do not create `Account`,
`Teacher`, or any other domain class in this prompt.

Read `CLAUDE.md` before starting.

---

## Task

Create the Maven project skeleton for the Sinapse platform backend.

### 1. Build

- Java 21, Spring Boot 3.x, Maven wrapper committed
- Group `br.com.sinapse`, artifact `platform`
- Main class `br.com.sinapse.platform.PlatformApplication` — it **must** be declared
  `public`, otherwise the jar cannot be packaged
- Dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`,
  `spring-boot-starter-security`, `spring-boot-starter-validation`,
  `flyway-core`, `flyway-database-postgresql`, `postgresql`,
  `springdoc-openapi-starter-webmvc-ui`, `spring-boot-starter-actuator`,
  `spring-modulith-starter-core`

**Do not add `spring-modulith-starter-jpa`.** It exists for the event publication registry,
and this architecture uses no inter-module events — ADR 0005 rejected event propagation
explicitly. Including it forces an `event_publication` table under `ddl-auto: validate` and
collides with `V1__identity.sql`. Boundary verification needs only `starter-core` and
`starter-test`.
- Test: `spring-boot-starter-test`, `spring-modulith-starter-test`,
  `testcontainers`, `junit-jupiter` (Testcontainers), `postgresql` (Testcontainers)
- JaCoCo plugin bound to `verify`

**Verify the springdoc version is binary compatible with the Spring Boot version you
select.** A mismatch produces a non-functional Swagger UI at runtime while the build
still passes. Report the versions you chose and how you confirmed compatibility.

### 2. Module skeleton

Create empty packages with `package-info.java` only:

```
br.com.sinapse.platform.identity           (+ api, internal)
br.com.sinapse.platform.curriculum         (+ api, internal)
br.com.sinapse.platform.educational        (+ api, internal)
br.com.sinapse.platform.learningrecord     (+ api, internal)
br.com.sinapse.platform.planning           (+ api, internal, orchestration)
br.com.sinapse.platform.coreclient
br.com.sinapse.platform.shared
```

Annotate each module root with `@ApplicationModule`, exposing only `api`.

### 3. Boundary test

Create `ModularityTests` using Spring Modulith's `ApplicationModules.of(...).verify()`.

Then **prove it works**: temporarily add a class in `educational` that imports something
from `identity.internal`, run the test, confirm it fails, remove the class. Report the
failure message you observed. A boundary test that has never failed is not evidence of
anything.

### 4. Configuration

- `application.yml` with a `local` profile
- Datasource from environment variables, no credentials in the file
- Flyway enabled, `baseline-on-migrate: false`
- `spring.jpa.hibernate.ddl-auto: validate` — never `update` or `create`
- OpenAPI title, version and description

### 5. Test infrastructure

An abstract `IntegrationTest` base class starting a PostgreSQL Testcontainer, reused by
all integration tests. Container reuse enabled to keep the suite fast.

### 6. Cross-cutting concerns

These touch every endpoint. Building them now avoids a full code sweep later.
See `docs/adr/0009-decisoes-transversais.md`.

**Error contract.** RFC 7807 `application/problem+json` for every error response, with
`type`, `title`, `status`, `detail`, `instance`, plus an `errors` array for validation
failures. `GlobalExceptionHandler` extends `ResponseEntityExceptionHandler` — required, so
that Spring's own exceptions do not escape handling and leak request bodies into logs.

`detail` must never contain personal data, internal identifiers, entity names, column names
or query fragments. Write the handler so that this is structurally hard, not a convention
each endpoint has to remember.

**API versioning.** Every route under `/api/v1`. No exceptions, from the first endpoint.

**Rate limiting.** A single filter with per-route policy. Key: account id when
authenticated, client address when anonymous.

The client address comes from the infrastructure. Trust `X-Forwarded-For` only when the
request arrives from an explicitly configured trusted proxy. A forged-header bypass has
already been found in the Sinapse Core repository; do not repeat it.

In-memory per instance is acceptable for v1. Document in the code that this stops holding
under horizontal scaling.

Filter ordering is an explicit decision: rate limiting runs before authentication for
anonymous routes and after it for authenticated ones. Make the ordering deliberate and
tested, not an accident of configuration.

**Observability.** Actuator with `health`, `info` and `metrics` on a separate management
port, not publicly exposed. Structured JSON logging with a per-request correlation id.

Configure logging so that no personal data is ever written: no e-mail, name, date of birth
or study content.

**Time zone.** No code may depend on the JVM or server default zone. Set an explicit
application zone for internal operations and configure Jackson to serialise instants as
ISO-8601 with offset. Local times are always interpreted in the account's zone, which
arrives in a later prompt.

### 7. Housekeeping

- `.gitignore` for Java, Maven, IDEs
- `README.md`, bilingual (Portuguese and English), containing only: what the project is,
  how to run it locally, how to run the tests. No roadmap, no feature list.

---

## Definition of done

- `./mvnw verify` passes
- `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` starts and Swagger UI loads
- `ModularityTests` passes, and you have demonstrated it fails when a boundary is violated
- A probe endpoint returns an RFC 7807 body on error, and a test asserts the media type and
  the absence of any stack trace or internal name in it
- Rate limiting blocks a burst on a probe route, and a test proves a forged
  `X-Forwarded-For` does not reset the counter
- Nothing domain-related exists yet

## Report

In Portuguese, covering: versions chosen and the compatibility check performed, the
observed boundary-violation failure message, anything you could not verify, and any
decision you had to make that was not specified here.

Work on `feature/1.0/project-bootstrap`. Commit in Conventional Commits format, in
English, following `CLAUDE.md` §4.
