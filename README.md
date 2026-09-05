# Sinapse Platform — Backend

[Português](#português) · [English](#english)

---

## Português

### O que é

Backend da plataforma Sinapse, um produto de tecnologia educacional para planejamento
de estudos personalizado. Cuida de identidade, relações educacionais, histórico de
aprendizagem e das fronteiras de integração.

O algoritmo de otimização não está aqui. O Sinapse Core, motor de algoritmos genéticos, é
um repositório separado, consumido por HTTP.

Java 21, Spring Boot, PostgreSQL e Flyway, organizados como monólito modular em um único
artefato executável. As fronteiras entre os módulos são verificadas em tempo de build.

A arquitetura está descrita em [`docs/ARQUITETURA_BACKEND_SINAPSE.md`](docs/ARQUITETURA_BACKEND_SINAPSE.md);
as decisões estruturais, em [`docs/adr/`](docs/adr).

### Como executar localmente

Pré-requisitos: JDK 21 e um PostgreSQL acessível. O Maven vem com o repositório, pelo
*wrapper*.

As credenciais do banco vêm do ambiente; nenhuma delas está em arquivo do repositório.

```bash
export SINAPSE_DB_URL=jdbc:postgresql://localhost:5432/sinapse
export SINAPSE_DB_USERNAME=<usuário>
export SINAPSE_DB_PASSWORD=<senha>

./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

A aplicação sobe em `http://localhost:8080`:

- documentação da API: `http://localhost:8080/swagger-ui.html`
- documento OpenAPI: `http://localhost:8080/api-docs`

Os *endpoints* de gestão ficam em uma porta separada, ligada apenas ao *loopback*:
`http://127.0.0.1:8081/actuator/health`.

O esquema do banco pertence ao Flyway. As migrações rodam na subida e o Hibernate apenas
valida o que encontra — ele nunca cria nem altera tabela.

### Como executar os testes

```bash
./mvnw verify                          # build completo, testes e cobertura
./mvnw test -Dtest=ModularityTests     # apenas as fronteiras entre módulos
```

Os testes de integração sobem um PostgreSQL em contêiner, então é preciso ter um Docker em
execução. Para reaproveitar o mesmo contêiner entre execuções e encurtar a suíte:

```bash
export TESTCONTAINERS_REUSE_ENABLE=true
```

Onde não houver Docker — uma máquina sem ele, ou uma integração contínua que já publica o
PostgreSQL como serviço —, aponte a suíte para um banco existente. Nada mais muda: as mesmas
migrações rodam nele e as mesmas verificações valem.

```bash
export SINAPSE_TEST_DB_URL=jdbc:postgresql://localhost:5432/sinapse_test
export SINAPSE_TEST_DB_USERNAME=<usuário>
export SINAPSE_TEST_DB_PASSWORD=<senha>
```

O relatório de cobertura fica em `target/site/jacoco/index.html`.

---

## English

### What this is

The backend of the Sinapse platform, an educational technology product for personalised
study planning. It handles identity, educational relationships, learning history and the
integration boundaries.

The optimisation algorithm is not here. The Sinapse Core, a genetic algorithm engine, is a
separate repository consumed over HTTP.

Java 21, Spring Boot, PostgreSQL and Flyway, organised as a modular monolith in a single
executable artifact. The boundaries between modules are verified at build time.

The architecture is described in [`docs/ARQUITETURA_BACKEND_SINAPSE.md`](docs/ARQUITETURA_BACKEND_SINAPSE.md)
and the structural decisions in [`docs/adr/`](docs/adr). Both are written in Portuguese.

### Running it locally

Requirements: JDK 21 and a reachable PostgreSQL. Maven ships with the repository, through
the wrapper.

Database credentials come from the environment; none of them is in a file of this
repository.

```bash
export SINAPSE_DB_URL=jdbc:postgresql://localhost:5432/sinapse
export SINAPSE_DB_USERNAME=<user>
export SINAPSE_DB_PASSWORD=<password>

./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

The application listens on `http://localhost:8080`:

- API documentation: `http://localhost:8080/swagger-ui.html`
- OpenAPI document: `http://localhost:8080/api-docs`

Management endpoints live on a separate port, bound to the loopback address:
`http://127.0.0.1:8081/actuator/health`.

The database schema belongs to Flyway. Migrations run at startup and Hibernate only
validates what it finds — it never creates or alters a table.

### Running the tests

```bash
./mvnw verify                          # full build, tests and coverage
./mvnw test -Dtest=ModularityTests     # module boundaries only
```

Integration tests start PostgreSQL in a container, so a running Docker is required. To
reuse the same container across runs and shorten the suite:

```bash
export TESTCONTAINERS_REUSE_ENABLE=true
```

Where there is no Docker — a machine without it, or a continuous integration job that
already publishes PostgreSQL as a service — point the suite at an existing database.
Nothing else changes: the same migrations run against it and the same assertions apply.

```bash
export SINAPSE_TEST_DB_URL=jdbc:postgresql://localhost:5432/sinapse_test
export SINAPSE_TEST_DB_USERNAME=<user>
export SINAPSE_TEST_DB_PASSWORD=<password>
```

The coverage report is written to `target/site/jacoco/index.html`.
