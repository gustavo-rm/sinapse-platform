# Prontidão para integração do AG e para o frontend do fluxo de plano

**Data da auditoria:** 05 de outubro de 2026
**Natureza:** auditoria. Nada em `src/`, `pom.xml`, migrações ou configuração foi alterado. Os
serviços foram executados só em `localhost`, com dados sintéticos, e encerrados ao final.

> ## ⚠️ Regra de parada acionada
>
> No Passo 3 (C2), um stub local do Core devolveu uma sessão que **começa dentro da janela de
> disponibilidade e termina 20 minutos depois do fim dela**. A plataforma aceitou a resposta, o
> job terminou em `READY` e o plano foi gravado como `ACTIVE`:
>
> | sequence_index | scheduled_start (UTC) | duração | fim (UTC) | janela enviada (UTC) |
> |---|---|---|---|---|
> | 0 | 2026-10-05 23:50 | 30 min | 2026-10-06 00:20 | 2026-10-05 22:00 → 2026-10-06 00:00 |
>
> É uma falha de validação: `RestSinapseCore.validated`
> (`src/main/java/br/com/sinapse/platform/coreclient/internal/RestSinapseCore.java:96-142`)
> continua com as **oito** checagens antigas e nenhuma das cinco que faltavam. Hoje o plano
> inválido só não chega ao aluno porque o Core real faz a mesma checagem antes de responder
> (`PlanOutputInvariants.fitsAWindow`). A plataforma, sozinha, não protege nada.
>
> **O que ficou sem auditar por execução depois da parada** (os cinco casos restantes do stub):
> sessão totalmente fora de janela, sessões sobrepostas, `topicId` desconhecido, `sequenceIndex`
> não contíguo e início fora do horizonte. Os cinco estão confirmados como **não verificados** pela
> plataforma por leitura de código (linha A2c). Todos os outros passos já tinham sido executados
> antes do caso que acionou a parada, porque os casos de invariante foram deixados de propósito
> para o fim.

---

## 1. Veredito

**(a) O frontend pode começar contra o contrato hoje? Parcial.** Metas, disponibilidade,
`/me/state`, disparo, polling, plano atual, resumo, agenda e conclusão de sessão existem, respondem
e têm forma estável. Mas não há rota de catálogo (GET de disciplinas e tópicos), e sem ela a tela de
metas não tem de onde escolher a disciplina.

**(b) O AG é integrável ponta a ponta hoje? Parcial.** A cadeia funciona e é reprodutível com os
três motores no cenário pequeno do teste E2E. Com o catálogo real (35 tópicos), porém, o `ga`
agenda só 2 a 4 tópicos, o `ga-timeline` é recusado com 422, o motor só pode ser escolhido por
implantação e a validação da plataforma aceita plano fora da janela.

---

## 2. Ambiente (Passo 0)

| Item | Valor |
|---|---|
| HEAD da plataforma | `928eb56c942c2b70089b0c2dc25631949ac92862` (= `origin/main`; o `main` local estava defasado em `fbb3861`, e a auditoria usou o `origin/main`) |
| HEAD do Core (`gustavo-rm/exam-optimizer-application`, `main`) | `61233adc6e5c6ad506ecc443902b3079733a65f1` |
| Java | OpenJDK 21.0.11 (Ubuntu, build 21.0.11+10-1-24.04.2) |
| Maven (`./mvnw -v`) | Apache Maven 3.9.11 |
| SO | Ubuntu 24.04.4 LTS, kernel 6.18.44, x86_64, 4 vCPU, 15 GiB |
| Docker | Cliente 29.6.2 instalado, daemon parado. Subi o `dockerd` e ele respondeu, mas `docker pull postgres:16-alpine` falhou com `429 Too Many Requests` do Docker Hub. **Na prática, sem imagens.** |
| PostgreSQL dos testes | Os testes pedem `postgres:16-alpine` via Testcontainers (`IntegrationTest.java:64`). Como não havia imagem, usei o caminho que a própria classe oferece (`SINAPSE_TEST_DB_URL`) contra o **PostgreSQL 16.14 local** (Ubuntu, com `citext`), escutando só em `localhost`. As mesmas migrações rodaram; o que não foi exercitado é o ciclo de vida do contêiner. |
| `./mvnw verify` em `origin/main` | **BUILD SUCCESS. 609 testes, 0 falhas, 0 erros, 0 ignorados.** `ModularityTests`: 1/1. |
| Gates de qualidade/cobertura | **Não há gate configurado.** O JaCoCo só executa `prepare-agent` e `report` (`pom.xml:192-207`), sem `check`, e não há Checkstyle nem SpotBugs. Cobertura medida no relatório: instruções 95,1%, ramos 80,6%, linhas 95,0%. |
| Teste E2E (`-Pcore-e2e`) | Executado sem Docker: `SINAPSE_E2E_CORE_URL` apontando para o Core em `127.0.0.1:8090` e `SINAPSE_TEST_DB_URL` para o PostgreSQL local. |

---

## 3. Tabela principal

Níveis de evidência: **EXECUTADO** (rodei e vi), **LIDO** (arquivo:linha), **DOCUMENTADO** (só
documento afirma), **NÃO VERIFICÁVEL**. Caminhos de código da plataforma abreviados a partir de
`src/main/java/br/com/sinapse/platform/`. Caminhos do Core a partir de
`src/main/java/com/ia/project/dynamicstudyplanner/`.

| # | Área | O que foi verificado | Status | Evidência (nível + referência) | O que falta | Impacto no frontend | Repositório do conserto |
|---|---|---|---|---|---|---|---|
| A1 | Build | `./mvnw verify` em `origin/main` | ✅ | EXECUTADO: `SINAPSE_TEST_DB_URL=… ./mvnw -B verify` → `Tests run: 609, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`, `ModularityTests` 1/1. | Gate de cobertura não existe (só `report`). O caminho Testcontainers não rodou (Docker Hub 429). | Não bloqueia | plataforma |
| A2a | Contrato | JSON de referência e teste com o `ObjectMapper` da aplicação | ✅ | EXECUTADO: `CoreContractGoldenTest` 7/7 no `verify`. LIDO: `src/test/resources/contract/plan-{request,response}-v1.0.json`, `@Autowired ObjectMapper` em `CoreContractGoldenTest.java:81-82`, classe estende `IntegrationTest` (contexto real). | — | Não bloqueia | — |
| A2b | ADR | ADR novo: contrato duplicado + JSON de referência; adaptador no Core; Core sem auth exige rede privada; 0002 e 0007 corrigidos | 🟠 | LIDO: `docs/adr/0015-ponte-de-contrato-com-o-core.md` cobre as três decisões (§1, §2, §3). Emendas no topo de `0002` e `0007`. | Existem **dois ADRs com o número 0015** (`0015-espelhamento-do-contrato-do-core.md` e `0015-ponte-de-contrato-com-o-core.md`). As emendas de 0002 e o §Consequências de 0015-ponte ainda afirmam que o Core "não expõe `POST /plans`" e que "todo job termina em `FAILED`" — **DIVERGENCE:** o Core expõe `/plans` e jobs chegam a `READY` (G1). O ADR 0007 ainda trata `algorithm_params` como "parâmetros do AG usados na execução", o que o Core não sustenta (ver X3). | Não bloqueia | plataforma (novo ADR; ADR aceito não se edita) |
| A2c | Validação | `validated`: 8 antigas + 5 novas, cada uma com teste; sessão que começa dentro da janela e termina fora | ❌ | LIDO: `coreclient/internal/RestSinapseCore.java:96-142` verifica só as oito (corpo, versão, sessões não vazias, `coreVersion`, semente, sessão completa, duração > 0, `sequenceIndex` repetido). `RestSinapseCoreTest` tem testes só para essas oito (`:172-254`). EXECUTADO: stub `ends-outside-window` → job `READY`, plano `ACTIVE` com sessão terminando 20 min após a janela (ver aviso no topo). | As cinco: início no horizonte, dentro de alguma janela (a sessão inteira, não só o início), sem sobreposição, `topicId` entre os enviados, `sequenceIndex` contíguo. E um teste que viole só cada uma. **A sessão que começa dentro e termina fora é aceita.** | Bloqueia a integração real | plataforma |
| A2d | Persistência | Fitness inteiro, sem lista de nomes compilada; `generations` e `elapsedMillis` persistidos | 🟠 | EXECUTADO: no banco, `study_plan.fitness` com 19 chaves (guloso) e 34 (ga), idênticas ao que o Core devolveu (replay G3); `generations`=0/60 e `elapsed_millis`=0 gravados. LIDO: coluna `study_plan.fitness jsonb` (`V5__planning.sql:122`), escrita em `orchestration/GeneratedPlanWriter.java:58-59` → `internal/domain/StudyPlan.java:71,117`. `generations`/`elapsed_millis` em `V8__generation_cost.sql:15-17`, escritos em `GeneratedPlanWriter.java:60-61` → `PlanGenerationRequest.java:280-287`. Guarda `FitnessTermNamesAreNotCompiledTest` passou. **DIVERGENCE** com o estado anterior: os dois campos não são mais descartados. | O dado de custo é inútil: os três motores do Core enviam `elapsedMillis = 0` de propósito (`sinapse/GeneticPlanEngine.java:84`, `sinapse/TimelinePlanEngine.java:102`, `baseline/GreedyBaselineScheduler.java:99`). | Não bloqueia | otimizador |
| A2e | E2E | Teste fim a fim contra o Core real; executar | ✅ | EXECUTADO: `./mvnw -B verify -Pcore-e2e` com `SINAPSE_E2E_CORE_URL=http://127.0.0.1:8090`, três vezes: `greedy-baseline`, `ga` e `ga-timeline` (`SINAPSE_E2E_CORE_ENGINE`). Nas três, 2/2 testes passaram (`theCoreAnswersTheReferenceRequestWithinTheContract`, `aRequestedPlanComesBackFromTheRealCoreAndTheSameSeedReproducesIt`). LIDO: tag `core-e2e`, excluída por padrão (`pom.xml:29`, perfil `:224-227`). | O fixture tem 4 tópicos. Com o catálogo real, `ga` e `ga-timeline` se comportam muito pior (G2), e o E2E não detecta isso. | Não bloqueia | — |
| B1 | Contrato | JSON de referência byte-idênticos nos dois repositórios | ✅ | EXECUTADO: `sha256sum`. `plan-request-v1.0.json` = `2cfaf210961e980d9a8be3bd4660a78fd30c71938c1fc9fa355d074e4dbbd2a8` nos dois lados; `plan-response-v1.0.json` = `528fcd6025924e084e2d1a415f2d336e9131f45aa813f7a6194b42b37b285429` nos dois lados. | — | Não bloqueia | — |
| B4 | Invariantes | Invariantes da plataforma × do Core, linha a linha | 🟠 | LIDO: plataforma `RestSinapseCore.java:96-142`; Core `plan/PlanOutputInvariants.java:208-342`, chamado por cada motor antes de responder (`baseline/GreedyBaselineScheduler.java:173`, `sinapse/GeneticPlanEngine.java:192`, `sinapse/TimelinePlanEngine.java:304`). Comparativo na seção 3.1. | Seis invariantes só o Core verifica: horizonte, sessão inteira dentro de janela, sem sobreposição, tópico enviado, `sequenceIndex` contíguo e pré-requisito `HARD` antes do dependente. Nenhuma só a plataforma verifica. Há também uma diferença de semântica: o Core lê o horizonte em **UTC** (`plan/HorizonBounds.java:37-41`), enquanto a plataforma expande janelas pelos dias do horizonte **no fuso da conta** (`SnapshotAssembler.java:149-156`) — ver X7. | Bloqueia a integração real | plataforma |
| B5 | effortTier | Mesmo conjunto fechado nos dois lados; a plataforma só envia valores dele | ✅ | EXECUTADO: o snapshot real trazia `effortTier` ∈ {`EXTENDED`,`LONG`,`SHORT`,`STANDARD`}. LIDO: plataforma `curriculum/api/EffortTier.java:23-32`, envio por `.name()` (`SnapshotAssembler.java:191`), `check` no banco (`V2__curriculum.sql:51-52`); Core `sinapse/EffortTierBands.java` (SHORT, STANDARD, LONG, EXTENDED → 1..4). Trafega como `String` nos dois `PlanRequest` (confirma o estado anterior). | — | Não bloqueia | — |
| B6 | Motor | Chaves de `algorithmParams` enviadas × lidas pelo Core × motor padrão | 🟠 | LIDO: a plataforma envia `population-size`, `generations` e `mutation-rate` (`application.yml:328-331`). O Core lê `engine` (`plan/PlanEngineSelector.java:46,97-101`), `importance`, `provenance` e `precedence`, e **ignora as três chaves que a plataforma manda** (o orçamento do AG vem de `plan.engine.ga.*`, `sinapse/GeneticSearchBudget.java:29-31`). Motor padrão: `plan.engine.default=greedy-baseline` (`application.properties` do Core). Motores: `greedy-baseline`, `ga`, `ga-timeline`. EXECUTADO: sem `engine`, o fitness gravado diz `"engine":"greedy-baseline"`; com `SINAPSE_PLANNING_GENERATION_ALGORITHMPARAMS_ENGINE=ga`, o job gravou `{"engine":"ga",…}` e o fitness `"engine":"ga"`. | A plataforma só escolhe motor **por implantação** (uma chave de configuração global), não por aluno nem por pedido. Nenhuma chave enviada hoje tem efeito no Core. | Bloqueia a integração real | ambos |
| B2 | Contrato | `PlanRequest` montado pelo caminho real × JSON de referência | ✅ | EXECUTADO: snapshot do job gravado pela execução HTTP real comparado chave a chave com `plan-request-v1.0.json`. Mesmas 9 chaves de topo, sem diferença de tipo. Chegam: tópicos com UUID (35), arestas com `strength`/`provenance` (35: 24 HARD, 11 SOFT, CURATED), `goals[].priority`, `targetDate`, 25 janelas em instantes absolutos (`19:00 America/Sao_Paulo` → `22:00Z`), `estimatedMinutes`, `algorithmParams`, `randomSeed`. No segundo job, `history` trouxe `recallRatings` e `lastStudiedAt` (E6). | Nenhuma ausência. Como no JSON de referência, campos `null` são omitidos (`default-property-inclusion: non_null`). | Não bloqueia | — |
| B3 | LGPD | Classificação de cada campo do `PlanRequest`; nenhum identificador direto | ✅ | EXECUTADO: o snapshot real não contém e-mail, nome, data de nascimento, fuso nem id de conta. LIDO: `coreclient/contract/PlanRequest.java:38-47`; a chamada só envia corpo, `Content-Type` e `Accept` (`RestSinapseCore.java:61-66`). Classificação na seção 3.2. | Nada a corrigir. Registro: disponibilidade em instantes e histórico com `lastStudiedAt` são dados comportamentais pseudonimizados; o Core não pode religá-los à pessoa, a plataforma pode (pelo id do job). | Não bloqueia | — |
| B7 | Semente | Quem gera, persistida antes, retry reaproveita, eco validado | 🟠 | LIDO: gerada pela plataforma com `SecureRandom` (`orchestration/RandomSeedSource.java`), **a cada tentativa** (`PlanGenerationOrchestrator.java:85`), gravada **antes** da chamada (`:86` → `PlanGenerationRequest.recordSubmission`), eco validado (`RestSinapseCore.java:121-123`). EXECUTADO: stub `seed` (eco diferente) → `FAILED/CORE_REJECTED`. | O retry **não** reaproveita a semente: cada tentativa sorteia outra e sobrescreve snapshot, parâmetros e semente da anterior. O plano gravado continua reprodutível; perde-se o registro das tentativas que falharam (já registrado em `docs/INTEGRACAO_CORE.md` §3.1). | Não bloqueia | plataforma |
| C1 | Implantação | `SINAPSE_CORE_URL` externalizado sem default público; timeouts; documento de rede privada | ✅ | LIDO: `application.yml:291` `${SINAPSE_CORE_URL:http://localhost:8090}` (default local, não público); `connect-timeout: 5s`, `read-timeout: 10m` (`:293,297`; `coreclient/internal/CoreProperties.java:73-79`), aplicados em `CoreClientConfiguration.java:38-43`. Rede privada: ADR `0015-ponte…` §3 e README do Core ("Deployment: private network only"). EXECUTADO: o timeout configurado é respeitado (stub `timeout` com `read-timeout=3s` → `CORE_UNAVAILABLE`). Estado anterior confirmado. | `docs/INTEGRACAO_CORE.md` (o documento operacional) não menciona a rede privada. Ela está só no ADR. | Não bloqueia | plataforma |
| C2 | Falhas | Falha → estado do job → tipo de erro (stub local) | 🟠 | EXECUTADO com stub Python em `127.0.0.1:8099`, retry acelerado por variável de ambiente (`RETRYBACKOFF=2s`, `POLLCRON=*/2`): Core fora do ar (Core real derrubado, G4) → `FAILED/CORE_UNAVAILABLE`, 3 tentativas; timeout → idem; 500 e 503 → idem; 400 → `FAILED/CORE_REJECTED`, 1 tentativa; 422 (`prerequisite-cycle`) → idem; corpo malformado, tipo errado e HTML → idem; versão errada, sessões vazias, semente divergente, sem `coreVersion`, sessão incompleta, duração 0 e `sequenceIndex` repetido → idem. Para o cliente, nenhum desses vira RFC 7807: o `GET` do job responde `200` com `status=FAILED` e `failureReason`. | **Resposta que viola invariante → `READY`** (ver aviso no topo; os outros cinco subcasos não rodaram por causa da regra de parada). **DIVERGENCE** com `docs/INTEGRACAO_CORE.md` §2: um campo desconhecido no corpo **não** vira `CORE_REJECTED` — o stub `unknown-field` terminou `READY`. Um 500 do Core por violação de invariante (`plan-invariant-violation`) é tratado como indisponibilidade e gasta 3 tentativas (`RestSinapseCore.java:85-88`). | Bloqueia a integração real | plataforma |
| C3 | Vazamento | Mensagem ao cliente sem URL interna, corpo do Core ou pilha | ✅ | EXECUTADO: o stub devolvia `detail` com URL interna e pilha falsas; o job exposto trazia só `attemptCount, failureReason, finishedAt, horizonEnd, horizonStart, id, requestedAt, startedAt, status`. LIDO: `server.error.include-*: never` (`application.yml`), motivo como enum fechado (`PlanGenerationRequest.java:299-304`). | — | Não bloqueia | — |
| C4 | Idempotência | Reenviar não cria dois planos nem dois "current" | ✅ | EXECUTADO: 5 `POST` simultâneos → `[201, 409, 409, 409, 409]` e 1 linha no banco; retries (G4, C2) nunca geraram plano duplicado; após dois `READY`, só um plano `ACTIVE` (D6). LIDO: `ux_request_active` (`V5__planning.sql:98-100`), `study_plan.generation_request_id unique` (`:117-118`), `ux_plan_active` (`:132-134`), escrita em uma transação (`GeneratedPlanWriter.java:52-63`). | — | Não bloqueia | — |
| D1 | Job | Disparo assíncrono? rota e código HTTP reais | ✅ | EXECUTADO: `POST /api/v1/study-plans/generation-requests` → **`201`** (não 202), corpo com `id` e `status: PENDING`, sem cabeçalho `Location`, em milissegundos. LIDO: `orchestration/GenerationRequestController.java:62-86`. É assíncrono; o read timeout de 10 min corre no worker, não na requisição do navegador. | O frontend deve tratar `201` (não `202`). Sem `Location`, a URL de polling é montada com o `id`. | Não bloqueia | — |
| D2 | Job | Estados, transições e como o cliente observa | 🟠 | EXECUTADO: transições `PENDING → RUNNING → READY`; em falha retentável, `RUNNING → PENDING (attemptCount+1) → … → FAILED`. Observação só por **polling** em `GET /api/v1/study-plans/generation-requests/{id}`; também `GET /me/state` → `activeGenerationJobId`. Campos: `id, status, horizonStart, horizonEnd, requestedAt, startedAt, finishedAt, attemptCount, failureReason, planId, progress` (nulos omitidos). LIDO: `progress` sempre `null` (`GenerationRequestService.java:244-245`); `CANCELLED` existe no `check` (`V5__planning.sql:88-89`), sem rota que leve a ele. | Sem SSE, sem `Retry-After` nem outra dica de intervalo. Durante o retry o job volta a `PENDING` e o cliente não distingue "na fila" de "aguardando nova tentativa". | Não bloqueia | plataforma |
| D3 | Job | Artefatos gravados e escritos | ✅ | EXECUTADO (banco após o Passo 7): `snapshot` 13.773 bytes, `core_version='2.0.1'`, `algorithm_params`, `random_seed`, `generations` (0 no guloso, 60 no ga), `elapsed_millis=0`, `study_plan.fitness` (547 bytes no guloso). LIDO: colunas em `V5__planning.sql:68-90` e `V8__generation_cost.sql:15-17`. | O conteúdo de `algorithm_params` não corresponde ao que rodou (X3) e `elapsed_millis` é sempre 0 (A2d). | Não bloqueia | ambos |
| D4 | Job | Dois disparos simultâneos da mesma conta | ✅ | EXECUTADO: 5 threads → um `201` e quatro `409` `urn:sinapse:problem:conflict`; 1 job no banco. LIDO: `GenerationRequestService.java:85-104` (checagem + índice parcial + `saveAndFlush`). | O 409 de "job em andamento" usa o tipo genérico `conflict`. O frontend deve ler `/me/state.activeGenerationJobId` para retomar o polling. | Não bloqueia | — |
| D5 | Job | Job órfão após reinício | ❌ | EXECUTADO: com o job em `RUNNING`, a plataforma foi derrubada (SIGKILL) e reiniciada. Depois de 2 min o job continuava `RUNNING`, e novo `POST` → `409`. `/me/state` exibe para sempre `activeGenerationJobId`. LIDO: `claimPending` só seleciona `status = 'PENDING'` (`internal/persistence/PlanGenerationRequestRepository.java:65-78`); não há recuperação. | Recuperação de `RUNNING` abandonado (por exemplo, devolver à fila depois de um prazo maior que o read timeout). Hoje o aluno fica **bloqueado sem saída** e só uma intervenção no banco libera. | Bloqueia a integração real | plataforma |
| D6 | Plano atual | O anterior sai de "current"? Há histórico? | ✅ | EXECUTADO: segundo `READY` → o plano anterior ficou `SUPERSEDED`, com `supersededAt` e `supersededByPlanId`; `/current` passou a servir o novo; `GET /api/v1/study-plans` lista os dois (`ACTIVE`, `SUPERSEDED`). | — | Não bloqueia | — |
| E1 | Catálogo | GET de disciplinas e de tópicos por disciplina; currículo semeado ou importável | ❌ | EXECUTADO (autenticado): `GET /api/v1/subjects`, `/subjects/{id}/topics`, `/catalog/subjects` e `/topics` → `404`. Não existe nenhum controller em `curriculum/` (LIDO). Importação: `catalog/MED-ANAT` existe (35 tópicos, 35 arestas), mas `java -jar platform.jar --spring.profiles.active=catalog catalog apply` **falhou ao subir**: `BeanInstantiationException … CatalogRunner: No default constructor found` (dois construtores públicos sem `@Autowired`, `curation/internal/CatalogRunner.java:58,74`). Contornei semeando o catálogo por SQL a partir dos CSV. GAP-01 confirmado. | Rotas de leitura do catálogo, e um importador que funcione no jar. | Bloqueia o desenvolvimento | plataforma |
| E2 | Metas | Criar/editar com prioridade 1–5 e `targetDate` | ✅ | EXECUTADO: `POST /goals` com `priority=6` → `400 validation-failed` (`errors[].field=priority`); disciplina desconhecida → `404 resource-not-found`; sem `priority` → `201`, padrão 3; meta duplicada → `409 conflict`; `PATCH /goals/{id}` com `priority=5` e `targetDate` → `200`; com `priority=0` → `400`. | Não testei `targetDate` no passado (o caso ficou mascarado pela meta duplicada). | Não bloqueia | — |
| E3 | Disponibilidade | Rota, fuso da conta e conversão em instantes | ✅ | EXECUTADO: `POST /availability` → `201`; sobreposição → `409 conflict`; fim antes do início → `400` (`field=timeRangeOrdered`). Conta em `America/Sao_Paulo`: `19:00–21:00` virou `22:00Z–00:00Z` no snapshot. LIDO: `SnapshotAssembler.java:149-156`. | A resposta devolve `HH:mm:ss` (`"19:00:00"`); `CONTRATO_API_SINAPSE.md` §1 diz `HH:mm`. | Não bloqueia | plataforma (documento) |
| E4 | LGPD | Consentimento como pré-condição; resposta quando falta | ✅ | EXECUTADO: revogar `LEARNING_DATA_PROCESSING` suspende a conta e derruba a sessão (`401` em seguida). Depois de novo login, `/me/state.accountStatus=SUSPENDED` e `POST generation` → `403 urn:sinapse:problem:access-denied`. LIDO: `identity/internal/service/ConsentService.java:134-144`. | O 403 é genérico. O frontend deve usar `/me/state.accountStatus` para explicar a situação. | Não bloqueia | — |
| E5 | Estado | `/me/state`: roles, onboarding, plano e job ativo | 🟠 | EXECUTADO: `accountStatus, timeZone, pendingConsents, requiresMajorityReaffirmation, setupComplete` e, quando existem, `activePlanId`, `activeGenerationJobId`, `openSessionId`. LIDO: `readmodel/api/StudentStateView.java:46-54`. | **Sem `roles`** (GAP-02 confirmado). Campos nulos são **omitidos**, não enviados como `null`, ao contrário de `uuid \| null` no contrato. | Não bloqueia | plataforma |
| E6 | Histórico | Conclusão com `RecallRating` entra no próximo `PlanRequest` (90 dias) | ✅ | EXECUTADO: `POST /study-sessions` → `POST …/completion` com `GOOD` e `POST /study-sessions/retroactive-entries` com `HARD`; o snapshot do job seguinte trouxe dois `history` com `sessionCount`, `totalMinutes`, `lastStudiedAt` e `recallRatings` (`["GOOD"]`, `["HARD"]`). Conclusão sem `recallRating` → `400`. LIDO: janela `history-window: 90d` (`application.yml:324`, usada em `SnapshotAssembler.java:107`). | — | Não bloqueia | — |
| E7 | Leitura | `/current` e `/{planId}/summary`: campos, nomes, motor, `core_version`, fitness | 🟠 | EXECUTADO: `/current` → `id, generationRequestId, horizonStart, horizonEnd, status, fitness, createdAt` (+ `supersededAt`, `supersededByPlanId`), sem sessões; `/{id}/sessions` → `id, planId, topicId, kind, scheduledStart, durationMinutes, sequenceIndex`, **só UUID**; `/{id}/summary` → `planId, status, horizonStart, horizonEnd, createdAt, totalPlannedMinutes, bySubject[subjectId, subjectName, plannedMinutes, sessionCount], adherence, fitness`; `/me/agenda?from&to` (instantes) traz `topicName` e `subjectName`. O motor aparece em `fitness.engine`. | `core_version` não é exposto em nenhuma rota. **DIVERGENCE:** `CONTRATO_API_SINAPSE.md` §3.3 diz que "o mesmo objeto" do resumo sai em `/current`; não sai. Os termos de fitness chegam opacos (GAP-07: a plataforma não interpreta; o Core se descreve com chaves `objective.*`/`constraint.*` e pesos). | Não bloqueia | plataforma |
| E8 | Lacunas | GAP-03 e GAP-05 no fluxo de plano | ✅ | EXECUTADO: `account` não tem coluna de nome (`\d account`), e o fluxo inteiro (cadastro → plano → sessão) rodou sem ela. LIDO: nenhum código concede `TEACHER` (só o `check` em `V1__identity.sql:42`). **Confirmado: não bloqueiam o AG.** | GAP-03 afeta só a saudação ou a exibição de nome. GAP-05 afeta só a área do professor. | Não bloqueia | plataforma |
| F1 | Contrato API | Artefato OpenAPI × rotas reais × documento de contrato | 🟠 | EXECUTADO: `GET /api-docs` (OpenAPI 3.1.0) = **61 operações em 53 caminhos**. LIDO: 61 mapeamentos de método nos controllers (62 anotações, uma delas de classe em `ProbeController`): mesma contagem. `CONTRATO_API_SINAPSE.md` só lista 6 rotas de leitura, todas existentes, e delega as escritas ao springdoc. | **Não há snapshot OpenAPI versionado na plataforma** (o Core tem `openapi-snapshot.json`): um cliente Angular tipado precisa da aplicação no ar para ser gerado, e mudança de forma não quebra build. O documento diverge em `/current` (E7) e em `HH:mm` (E3). As 3 rotas `/probe/*` aparecem no OpenAPI público. | Não bloqueia | plataforma |
| F2 | Erros | Catálogo RFC 7807 cobre as falhas do fluxo de plano | 🟠 | LIDO: `shared/web/problem/ApiErrorType.java` (17 tipos, entre eles `setup-incomplete`, `conflict`, `access-denied`, `rate-limit-exceeded` com `Retry-After` em `RateLimitFilter.java:82`). EXECUTADO: `409 setup-incomplete`, `409 conflict`, `403 access-denied` e `429 rate-limit-exceeded` (11º disparo na mesma hora) vistos no fluxo. | As falhas do Core **não são RFC 7807**: chegam como `failureReason` (`CORE_UNAVAILABLE`, `CORE_REJECTED`, `NOTHING_TO_PLAN`, `INTERNAL`) dentro de um `200`. Isso é intencional, mas não está no catálogo de erros e precisa estar no contrato do frontend. O 409 de job em andamento não tem tipo próprio. | Não bloqueia | plataforma |
| F3 | Sessão | Cookie, anti-CSRF, XSRF do Angular, CORS em mesma origem | ✅ | EXECUTADO: login → `Set-Cookie: sinapse_session=…; Path=/api/v1; Max-Age=2592000; HttpOnly; SameSite=Strict` (sem `Secure` no perfil `local`; `cookie-secure: true` na base, LIDO `application.yml:262`). Nenhum cookie `XSRF-TOKEN` emitido. `X-XSRF-TOKEN` enviado é ignorado. Preflight `OPTIONS` de outra origem → `403` sem `Access-Control-Allow-*`. LIDO: CSRF desabilitado (`shared/config/SecurityConfiguration.java:120`), `SameSite=Strict` (`identity/internal/security/SessionCookies.java:86-88`). | Compatível com Angular em mesma origem: o `HttpXsrfInterceptor` não envia nada quando não há cookie `XSRF-TOKEN`, e não há CORS a configurar. A ressalva do ADR 0009 continua valendo: nenhum `GET` pode mudar estado. | Não bloqueia | — |
| G1 | E2E real | Motor padrão: `READY`, plano gravado, `/current` 200 com sessões | ✅ | EXECUTADO via HTTP real (plataforma `127.0.0.1:18080`, Core `127.0.0.1:8090` perfil `baseline-core`, catálogo MED-ANAT): consentimento no cadastro → meta → 6 janelas → `POST` → `READY` em 10,4 s → `/current` 200 → `/{id}/sessions` 200 com 33 sessões. Checagem das invariantes a partir do banco: 0 violações. **DIVERGENCE** com o estado de 17/09: planos agora são gerados. | — | Não bloqueia | — |
| G2 | E2E real | Outros motores pelo mecanismo de produção | 🟠 | EXECUTADO, mecanismo de produção = variável `SINAPSE_PLANNING_GENERATION_ALGORITHMPARAMS_ENGINE` (B6): **`ga`** → `READY`, mas o plano tem **3 sessões, 2 de 35 tópicos, 125 de 3.000 minutos**, `fitness.aggregate=0.0`, `partial=true`. Reenvio direto do primeiro snapshot com `ga` → 7 sessões, 4 tópicos. **`ga-timeline`** → `FAILED/CORE_REJECTED`; o Core respondeu `422 plan-would-be-empty` ("No session fits… cannot hold even the first topic") com 25 janelas de 2 h e o primeiro tópico de 25 min. No E2E de 4 tópicos os dois passam (A2e). | A plataforma escolhe o motor só por implantação, não por pedido. Com o catálogo real, o `ga` produz plano quase vazio e o `ga-timeline` recusa — comportamento do otimizador a investigar. | Bloqueia a integração real | ambos |
| G3 | E2E real | Mesma semente → plano persistido idêntico | ✅ | EXECUTADO de duas formas: (1) o E2E compara os **planos gravados** de duas gerações com semente fixa, nos três motores, e passou; (2) reenviei ao Core o `snapshot` gravado de cada um dos 4 jobs `READY` (3 guloso, 1 ga) e comparei com `planned_session` e `study_plan.fitness`: **idênticos nos 4**. | Em produção a semente é aleatória por geração: a reprodutibilidade vale para o snapshot gravado, não para "gerar de novo". | Não bloqueia | — |
| G4 | E2E real | Derrubar o Core no meio de um job | ✅ | EXECUTADO: Core com `ga` lento (`--plan.engine.ga.generations=300000`); job em `RUNNING`, Core derrubado com SIGKILL. Transições: `RUNNING(1)` → `PENDING(1)` → `PENDING(2)` → **`FAILED(3)/CORE_UNAVAILABLE` em ~115 s**. O plano anterior continuou em `/current` e `/me/state` respondeu normalmente. | Com a configuração padrão (backoff 30 s/60 s, poll de 10 s), o aluno espera **cerca de 2 minutos** para ver a falha. | Não bloqueia | — |
| G5 | Ordem de grandeza | Tempo, tamanho e sessões por motor. **NÃO é benchmark.** | ✅ | EXECUTADO (1 execução cada, máquina de 4 vCPU, catálogo de 35 tópicos, 25 janelas): Core direto — guloso 0,016 s / 5.472 bytes / 33 sessões; `ga` 0,021 s / 3.603 bytes / 7 sessões; `ga-timeline` 0,33 s / 422. No worker (`finishedAt − startedAt`): 0,19 s (guloso), 0,23 s (ga). **Do clique ao estado final: 7 a 10 s, dominados pelo `poll-cron` de 10 s.** `/current` 791 bytes, `/summary` 960 bytes, `/{id}/sessions` 8.306 bytes (33 sessões). | Para os estados de carregamento: espere de 1 a 12 s no caso feliz, até ~2 min numa falha retentável, e infinito no job órfão (D5). | Não bloqueia | — |
| X1 | Catálogo | Comando `catalog` no jar empacotado | ❌ | EXECUTADO: `catalog apply` → `No default constructor found` (ver E1). LIDO: os testes instanciam `CatalogRunner` com `new` (`CatalogImportIntegrationTest.java:336`, `CatalogRunnerOutputIntegrationTest.java:71`); nenhum teste sobe o perfil `catalog` pelo Spring. | Construtor de injeção inequívoco e um teste que suba o perfil. | Bloqueia a integração real | plataforma |
| X2 | Cadastro | Verificação de e-mail em ambiente local | 🟠 | EXECUTADO: `POST /accounts` → `PENDING_VERIFICATION`; login → `401`. Não há transporte, e o `LoggingAccountNotifier` não registra o token (corretamente). Contornei ativando a conta por SQL. | Sem meio de verificar e-mail fora do banco, o frontend não consegue exercitar o cadastro completo contra um backend local. | Bloqueia a integração real | plataforma |
| X3 | Reprodutibilidade | `algorithm_params` gravado × o que rodou | ❌ | EXECUTADO: o job `ga` gravou `generations: 400, population-size: 120`; o Core informou `generations=60` (a configuração dele). LIDO: `GeneticSearchBudget.java:29-31` (Core). Já registrado em `docs/INTEGRACAO_CORE.md` §5.2. | O registro do job afirma parâmetros que não rodaram; o ADR 0007 fica sem sustentação neste ponto. | Não bloqueia | ambos |
| X4 | Retry | 500 de invariante do Core tratado como indisponibilidade | 🟠 | LIDO: `RestSinapseCore.java:85-88` (todo não-2xx que não é 4xx → `CoreUnavailableException`); Core `plan/PlanController.java:105-116` (500 `plan-invariant-violation`). EXECUTADO: stub 500 → 3 tentativas. | Um defeito determinístico gasta o orçamento de retry e atrasa a falha em ~90 s. | Não bloqueia | plataforma |
| X5 | Documentação | Javadoc e documentos desatualizados | 🟠 | LIDO: `planning/internal/domain/PlanGenerationRequest.java:22` ("Mapped, not executed. Nothing in this version claims a job…") e `PlanGenerationRequestRepository.java:16-19` ("deliberately absent") contradizem o código; `docs/INTEGRACAO_CORE.md` §1 lista só `greedy-baseline` e `ga` (**DIVERGENCE:** o Core tem `ga-timeline`). | Atualizar. | Não bloqueia | plataforma |
| X6 | Proxy reverso | Endereço do cliente atrás do proxy | 🟠 | LIDO: `trusted-proxies: []` (`application.yml:106`) e `forward-headers-strategy: none`. Atrás do proxy reverso do Angular, todo tráfego anônimo chega com o IP do proxy, e os limites por origem (cadastro 5/h, login 10/5 min…) passam a ser **globais**. | Configurar `trusted-proxies` na implantação. Decisão de implantação, não de código. | Bloqueia a integração real | plataforma (configuração de implantação) |
| X7 | Horizonte | Horizonte em UTC (Core) × dias no fuso da conta (plataforma) | 🟡 | LIDO: o Core recorta as janelas ao horizonte em UTC (`plan/AvailabilityAllocator.java:69-71,100-102`; `HorizonBounds.java:37-41`); a plataforma começa o horizonte em `LocalDate.now(clock)` no fuso da aplicação (`GenerationRequestService.java:90`) e expande janelas pelos dias locais. Para fusos a leste de UTC, janelas do primeiro dia caem antes do horizonte e são descartadas; nas noites do último dia, a oeste, também. Também são enviadas janelas de hoje que já passaram. | Não executei um caso de borda. Efeito esperado: perda silenciosa de disponibilidade nas bordas, não plano inválido (o Core recorta). | Não bloqueia | ambos |

### 3.1 B4 — invariantes, linha a linha

| Invariante | Plataforma (`RestSinapseCore.validated`) | Core (`PlanOutputInvariants.check`) |
|---|---|---|
| corpo não vazio | ✔ `:97` | — (o Core é quem produz) |
| `contractVersion` = 1.0 | ✔ `:100` | ✔ `:218` |
| ao menos uma sessão | ✔ `:105` | ✔ `:221` |
| metadados com `coreVersion` | ✔ `:118` | ✔ `:224-226` |
| semente ecoada | ✔ `:121` | ✔ `:227` |
| sessão com tópico, tipo e início | ✔ `:129` | ✔ `:265` |
| duração > 0 | ✔ `:133` | ✔ `:267` |
| `sequenceIndex` sem repetição | ✔ `:136` | ✔ `:243` |
| `sequenceIndex` contíguo 0..n-1 | ✘ | ✔ `:246` |
| `topicId` entre os enviados | ✘ | ✔ `:268` |
| início dentro do horizonte (UTC) | ✘ | ✔ `:270` |
| sessão **inteira** dentro de uma janela | ✘ (EXECUTADO: aceita) | ✔ `:272,283-290` |
| sem sobreposição (em ordem de sequência) | ✘ | ✔ `:293-305` |
| pré-requisito `HARD` antes do dependente | ✘ | ✔ `:316-342` |

### 3.2 B3 — classificação dos campos do `PlanRequest`

| Campo | Classe |
|---|---|
| `contractVersion`, `algorithmParams`, `randomSeed` | técnico (nem pessoal nem currículo) |
| `horizon.start/end` | técnico (datas do planejamento) |
| `availability[].start/end` | pseudônimo (rotina do aluno, em instantes) |
| `goals[].subjectId` | dado de currículo |
| `goals[].targetDate`, `goals[].priority` | pseudônimo (preferência do aluno) |
| `topics[].id/subjectId/position/effortTier` | dado de currículo |
| `topics[].estimatedMinutes` | pseudônimo (calibrado pelo histórico do aluno) |
| `prerequisites[]` (ids, `strength`, `provenance`) | dado de currículo |
| `history[].topicId` | dado de currículo |
| `history[].sessionCount/totalMinutes/lastStudiedAt/recallRatings` | pseudônimo (comportamento de aprendizagem) |
| — identificador direto (nome, e-mail, id de conta, nascimento, fuso) | **nenhum enviado** |

---

## 4. Superfície de API do fluxo de plano

Todas sob `/api/v1`. "Auth" = sessão (cookie `HttpOnly` ou `Bearer`). Forma "estável" quer dizer
observada igual em várias chamadas e coerente com o OpenAPI gerado. Nenhuma dessas formas tem
snapshot versionado (F1).

| Rota | Verbo | Auth | Existe | Forma de request/resposta estável? | Erros (tipos) | Documentada no contrato | Coberta por teste (qual) |
|---|---|---|---|---|---|---|---|
| `/subjects`, `/subjects/{id}/topics` (catálogo) | GET | — | ❌ (404) | — | — | Não (o contrato só lista operações administrativas de currículo) | — |
| `/goals` | POST, GET | sim | ✅ | Sim (`subjectId`, `targetDate?`, `priority?` padrão 3) | `validation-failed` 400, `resource-not-found` 404, `conflict` 409 | Só no OpenAPI; o documento remete aos prompts | `PlanningEndpointIntegrationTest` (HTTP); `GoalIntegrationTest` (serviço) |
| `/goals/{id}` | PATCH | sim | ✅ | Sim (`targetDate?`, `priority` obrigatório) | `validation-failed` 400 | OpenAPI | `PlanningEndpointIntegrationTest` (HTTP); `GoalIntegrationTest` (serviço) |
| `/availability` | POST, GET | sim | ✅ | Sim (`dayOfWeek`, `startTime`, `endTime`, `effectiveFrom`, `effectiveUntil?`; horas voltam `HH:mm:ss`) | `validation-failed` 400, `conflict` 409 | OpenAPI (o documento diz `HH:mm`) | `PlanningEndpointIntegrationTest` (HTTP); `AvailabilityIntegrationTest` (serviço) |
| `/me/state` | GET | sim | ✅ | Sim; campos nulos omitidos; sem `roles` | `unauthenticated` 401 | Sim (§3.1) | `ReadModelEndpointIntegrationTest` |
| `/study-plans/generation-requests` | POST | sim | ✅ | Sim; **201**, sem `Location`; limite de 10/h por conta | `setup-incomplete` 409, `conflict` 409, `access-denied` 403, `rate-limit-exceeded` 429 | OpenAPI | `GenerationEndpointIntegrationTest`, `CoreUnreachableIntegrationTest` (HTTP); `GenerationQueueConcurrencyIntegrationTest` (serviço); `CoreEndToEndIntegrationTest` (só com `-Pcore-e2e`) |
| `/study-plans/generation-requests/{id}` | GET | sim | ✅ | Sim; `progress` sempre ausente; falhas do Core em `failureReason` | `resource-not-found` 404 | OpenAPI | `GenerationEndpointIntegrationTest`; `CoreEndToEndIntegrationTest` (só com `-Pcore-e2e`) |
| `/study-plans/current` | GET | sim | ✅ | Sim (plano sem sessões; `fitness` opaco) | `resource-not-found` 404 sem plano ativo (LIDO `StudyPlanController.java:68`) | Documento diverge (§3.3 diz "mesmo objeto" do resumo) | `PlanningEndpointIntegrationTest`; `CoreEndToEndIntegrationTest` (só com `-Pcore-e2e`) |
| `/study-plans/{id}/sessions` | GET | sim | ✅ | Sim; só UUID, sem nomes | `resource-not-found` 404 | OpenAPI | `PlanningEndpointIntegrationTest`; `CoreEndToEndIntegrationTest` (só com `-Pcore-e2e`) |
| `/study-plans/{id}/summary` | GET | sim | ✅ | Sim (`bySubject` com `subjectName`, `fitness`) | `resource-not-found` 404 | Sim (§3.3) | `ReadModelEndpointIntegrationTest`, `ReadModelOpenApiIntegrationTest` (HTTP); `PlanSummaryReadModelIntegrationTest` (serviço) |
| `/me/agenda?from&to` | GET | sim | ✅ | Sim; `from`/`to` são **instantes** (data simples → 400); traz `topicName`/`subjectName` | `malformed-request` 400, `time-window-invalid` 400 | Sim (§3.2; os parâmetros não dizem que são instantes) | `ReadModelEndpointIntegrationTest` |
| `/study-plans` (histórico) | GET | sim | ✅ | Sim | — | OpenAPI | `PlanningEndpointIntegrationTest`, `FitnessReportIntegrationTest` |
| `/study-sessions` → `/study-sessions/{id}/completion` | POST | sim | ✅ | Sim (`recallRating` obrigatório) | `validation-failed` 400 | OpenAPI | `StudySessionEndpointIntegrationTest` (HTTP); `StudySessionLifecycleIntegrationTest` (serviço) |
| `/study-sessions/retroactive-entries` | POST | sim | ✅ | Sim | `validation-failed` 400, 429 | OpenAPI | `StudySessionEndpointIntegrationTest` |

Os testes da coluna final passaram no `verify` executado, exceto `CoreEndToEndIntegrationTest`, que
fica fora do build padrão e passou nas três execuções com `-Pcore-e2e`. A correspondência rota↔teste
foi LIDA (o teste chama a rota); não auditei asserção por asserção.

---

## 5. Bloqueadores, por impacto no frontend

O esforço é estimativa (P/M/G), não medição.

| # | O quê | Por que bloqueia | Repositório | Esforço |
|---|---|---|---|---|
| 1 | **Sem rota de catálogo** (E1) | A tela de metas não tem como listar disciplinas nem tópicos; sem meta não há plano. **Bloqueia o desenvolvimento.** | plataforma | M |
| 2 | **Validação aceita plano fora da janela** e não checa as outras quatro invariantes (A2c, B4, C2) | Um Core com defeito, ou um motor novo, põe plano inválido na tela do aluno, e o plano é imutável. Bloqueia a integração real. | plataforma | P–M |
| 3 | **Job órfão fica em `RUNNING` para sempre** (D5) | Qualquer reinício durante uma geração deixa o aluno sem poder gerar plano e com a tela em "gerando…" indefinidamente. Bloqueia a integração real. | plataforma | M |
| 4 | **Motores do AG com o catálogo real** (G2, B6) | `ga` agenda 2–4 de 35 tópicos; `ga-timeline` recusa (422). E o motor só muda por implantação. Bloqueia a integração real do AG (o guloso funciona). | otimizador (qualidade dos motores); plataforma (seleção por pedido, se desejada) | G |
| 5 | **Importador de catálogo quebrado no jar** (X1) | Num ambiente implantado o catálogo só entra por SQL manual. Bloqueia a integração real. | plataforma | P |
| 6 | **Sem verificação de e-mail fora do banco** (X2) | O frontend não consegue fazer cadastro → login contra um backend local sem acesso ao banco. Bloqueia a integração real. | plataforma | P–M |
| 7 | **`trusted-proxies` vazio atrás do proxy reverso** (X6) | Os limites anônimos de login e cadastro passam a valer para todos os usuários juntos. Bloqueia a integração real (é configuração de implantação). | plataforma | P |
| 8 | Sem snapshot OpenAPI versionado; documento de contrato diverge em `/current` e em `HH:mm` (F1, E7, E3) | Não bloqueia o início: o OpenAPI vivo bate com as rotas. Mas o cliente tipado não tem um artefato estável para fixar. | plataforma | P |
| 9 | Falhas do Core fora do catálogo RFC 7807; sem dica de intervalo de polling; `PENDING` no retry (F2, D2) | Não bloqueia. O frontend precisa mapear `failureReason` e escolher o próprio intervalo. | plataforma | P |
| 10 | `algorithm_params` falso, `elapsedMillis` sempre 0, semente nova por tentativa, 500 de invariante tratado como indisponível (X3, A2d, B7, X4) | Não bloqueia o frontend; afeta a pesquisa e o tempo até a falha. | ambos | M |

---

## 6. O que não foi possível verificar, e por quê

- **Cinco subcasos de C2 por execução** (sessão totalmente fora de janela, sobreposição, `topicId`
  desconhecido, `sequenceIndex` não contíguo, início fora do horizonte): interrompidos pela regra
  de parada. Leitura de código confirma que a plataforma não verifica nenhum deles. Para o `topicId`
  desconhecido, a chave estrangeira `planned_session.topic_id → topic` provavelmente faria a
  gravação falhar (`INTERNAL`) em vez de `READY`, mas isso não foi executado.
- **Caminho Testcontainers**: o Docker Hub respondeu `429 Too Many Requests` ao `docker pull`. A
  suíte inteira e o E2E rodaram contra o PostgreSQL 16.14 local, pelo mecanismo
  `SINAPSE_TEST_DB_URL` que a própria suíte oferece. O que não foi exercitado é o ciclo de vida do
  contêiner e a imagem `postgres:16-alpine`.
- **E1 com importação real**: o importador oficial não sobe (X1). O catálogo MED-ANAT foi semeado
  por SQL gerado a partir dos CSV do repositório. Isso mantém E1 em ❌.
- **Cadastro completo por HTTP**: sem transporte de e-mail, as contas sintéticas foram ativadas por
  `UPDATE account SET status='ACTIVE'` (X2).
- **Execução de C2**: o retry foi acelerado por variáveis de ambiente
  (`SINAPSE_CORE_READTIMEOUT=3s`, `SINAPSE_PLANNING_GENERATION_RETRYBACKOFF=2s`,
  `SINAPSE_PLANNING_GENERATION_POLLCRON`) só nessa execução, sem alterar arquivo. A primeira rodada
  do stub foi **descartada**: o stub não lia corpo `chunked` e todas as respostas viraram
  `CORE_UNAVAILABLE` por erro meu. A rodada válida é a da tabela.
- **E2 com `targetDate` no passado**: o caso ficou mascarado pela regra de meta duplicada e não foi
  refeito.
- **X7 (borda do horizonte entre fusos)**: só leitura. Nenhum caso de borda executado.
- **Cabeçalho `Retry-After` no 429**: o 429 foi observado, o cabeçalho não foi inspecionado. A
  emissão foi LIDA em `RateLimitFilter.java:82`.
- **G5** é ordem de grandeza com uma execução por motor, em máquina compartilhada, com um único
  catálogo. **Não é benchmark.**

---

## Apêndice — como foi executado

- Core: `./mvnw -B package -DskipTests` no clone em diretório temporário;
  `java -jar target/DynamicStudyPlanner-2.0.1.jar --spring.profiles.active=baseline-core
  --server.port=8090 --server.address=127.0.0.1`. O clone não foi alterado.
- Plataforma: `java -jar target/platform-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
  --server.address=127.0.0.1`, porta 18080 (gestão 18081), banco `sinapse_e2e` local,
  `SINAPSE_CORE_URL=http://127.0.0.1:8090`.
- Contas sintéticas `aluno.sintetico{N}@example.test`, nascidas em 1999/2000, fuso
  `America/Sao_Paulo`. Nenhum dado real usado ou encontrado; nenhum segredo reproduzido aqui.
- Ao final: plataforma, Core, stub, PostgreSQL e `dockerd` encerrados; bancos e papel de teste
  removidos; diretório temporário apagado; `target/` removido.
