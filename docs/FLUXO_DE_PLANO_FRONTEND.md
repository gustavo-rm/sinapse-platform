# Fluxo de plano para o frontend

O que o cliente precisa saber para pedir um plano, acompanhar a geração e mostrar o
resultado. Cada afirmação cita o código (`arquivo:linha`, contado a partir de
`src/main/java/br/com/sinapse/platform/` quando o caminho é de Java), o teste ou a execução
que a sustenta. O que não pôde ser conferido está marcado **a confirmar** e reunido em
[AFIRMACOES_A_CONFIRMAR.md](AFIRMACOES_A_CONFIRMAR.md).

Conferido no código de `origin/main` em 19/10/2026 (merge do #24). As rotas e as formas dos
corpos são as de [`openapi/openapi.json`](openapi/openapi.json), que é a fonte de verdade das
rotas (veja [`openapi/README.md`](openapi/README.md)).

---

## 1. Pedir o plano

`POST /api/v1/study-plans/generation-requests`, sem corpo, com a sessão do aluno.

- Responde **`201 Created`**, não `202`
  (`planning/orchestration/GenerationRequestController.java:84`;
  `GenerationEndpointIntegrationTest.askingForAPlanReturnsAJobToPollAndThenAPlan`).
- **Não há cabeçalho `Location`.** A resposta é construída só com status e corpo
  (`GenerationRequestController.java:84`). O identificador do job está no campo **`id`** do
  corpo (`GenerationRequestResponse`, esquema no snapshot). A URL de consulta é montada pelo
  cliente: `GET /api/v1/study-plans/generation-requests/{id}`.
- O corpo já vem com `status: PENDING` e `attemptCount: 0`, sem `planId` e sem `progress`
  (mesmo teste).

Respostas de erro do disparo (todas `application/problem+json`, RFC 7807; o snapshot declara
`403`, `409` e `429` para esta operação):

| Status | `type` | Quando |
|---|---|---|
| `409` | `urn:sinapse:problem:setup-incomplete` | ainda não há disponibilidade ou meta (`shared/web/problem/ApiErrorType.java:110`; `GenerationEndpointIntegrationTest.askingBeforeThereIsAnythingToPlanSaysSo`) |
| `409` | `urn:sinapse:problem:conflict` | a conta já tem um job `PENDING` ou `RUNNING` (§4) |
| `429` | `urn:sinapse:problem:rate-limit-exceeded` | limite de pedidos (§5) |
| `403` | — | os dados de aprendizagem da conta não podem ser processados (descrição da operação no snapshot) |

Os dois `409` só se distinguem pelo `type`. O cliente deve decidir pelo `type`, nunca só pelo
status.

## 2. Acompanhar o job

**Só por polling.** Não há evento, WebSocket nem SSE. O estado se lê em
`GET /api/v1/study-plans/generation-requests/{id}`, que devolve o mesmo
`GenerationRequestResponse` (descrição da operação no snapshot: "Polled, because there is no
event notification in this version"). **A resposta não traz dica de intervalo**: nenhum
`Retry-After` nem campo de intervalo no corpo (`GenerationRequestController.java`, método
`state`; esquema no snapshot).

Estados: `PENDING → RUNNING → READY` ou `FAILED`
(`planning/api/GenerationRequestStatus.java`). `CANCELLED` existe no enum e no banco, mas
nenhum código o atribui hoje (busca por `GenerationRequestStatus.CANCELLED` em `src/main`: sem
resultado). O cliente pode tratá-lo como terminal, mas não vai recebê-lo.

**O ciclo do worker é de 10 s.** O worker roda em `poll-cron: '0/10 * * * * *'`, isto é, nos
segundos 0, 10, 20… de cada minuto, e pega **um** job por passada (`batch-size: 1`)
(`application.yml:325,327`; `planning/orchestration/PlanGenerationWorker.java:50`). A passada
roda o job até o fim antes da próxima (`PlanGenerationWorker.runOnce`). O agendador
tem uma única thread: o único `TaskScheduler` do contexto tem pool 1 (executado em 19/10/2026
numa cópia descartável, com o perfil de teste), e nenhuma configuração do repositório muda isso
(busca por `spring.task.scheduling` em `src/main`: sem resultado). A mesma thread atende as
outras tarefas agendadas, como as varreduras noturnas. Por isso cada instância processa **um
job por vez**, e um job na fila espera os que chegaram antes (`order by requested_at`,
`planning/internal/persistence/PlanGenerationRequestRepository.java:74`).

**Quando o estado muda:**
- `PENDING → RUNNING` só numa passada do worker, ou seja, num múltiplo de 10 s;
- `RUNNING → READY`, `FAILED` ou de volta a `PENDING` a qualquer instante, quando a chamada ao
  Core termina.

**Intervalo de polling.** É uma recomendação derivada da configuração, não uma medição:
consultar a cada **2–3 s** enquanto o job não termina. Menos que isso não traz informação nova:
- o job só sai de `PENDING` de 10 em 10 s;
- a chamada ao Core leva no mínimo o tempo do motor.

Mais que ~5 s soma atraso perceptível ao fim do job. Ao chegar a `READY` ou `FAILED`, pare. O
`GET` de consulta não tem limite de taxa (§5).

**`progress` é sempre nulo, e no JSON vem ausente.** O serviço preenche o campo com `null`
(`planning/internal/service/GenerationRequestService.java:286-287`, "The core reports no
progress and nothing here invents one (decision F4)"). Com `default-property-inclusion:
non_null` (`application.yml:38`), a chave nem aparece
(`GenerationEndpointIntegrationTest`: `jsonPath("$.progress").doesNotExist()`). **O frontend
não deve renderizar barra de progresso.** O que dá para mostrar é o tempo decorrido desde
`requestedAt` ou `startedAt`, como o Javadoc de `GenerationRequestResponse` sugere.

## 3. Nova tentativa: o estado volta a `PENDING`

Quando o Core está indisponível, o job volta para `PENDING` e é tentado de novo, até 3
tentativas (`max-attempts: 3`, `application.yml:317`;
`GenerationRequestService.java:205-207`; `PlanGenerationRequest.releaseForRetry`, linha 350).
Durante a espera:

- `status` é `PENDING` de novo;
- `attemptCount` já conta a tentativa que falhou (a contagem sobe na reivindicação,
  `PlanGenerationRequest.claim`);
- `startedAt` é o da última tentativa;
- `failureReason` continua ausente.

Fonte: `PlanGenerationIntegrationTest.anUnreachableCoreIsAttemptedAgainAndThenGivenUpOn`.

Para o cliente, **`PENDING` com `attemptCount ≥ 1` significa "tentando de novo"**. Não é um job
novo, e o `id` não muda.

Só `CORE_UNAVAILABLE` volta para a fila; as outras falhas terminam o job na hora
(`planning/api/PlanGenerationFailure.java`, `isWorthRetrying`).

## 4. `409` com job ativo, antes e depois do SP-10

Cada conta tem no máximo um job não terminado. A garantia é um índice único parcial no banco
(`ux_request_active`, `db/migration/V5__planning.sql`) e a checagem em
`GenerationRequestService.java:105,266`. Um segundo `POST` responde `409`
`urn:sinapse:problem:conflict`
(`GenerationEndpointIntegrationTest.aSecondRequestWhileOneIsRunningIsRefusedClearly`).

O corpo do `409` **não** traz o `id` do job ativo. Para retomar o acompanhamento, leia
`activeGenerationJobId` em `GET /api/v1/me/state`
(`readmodel/api/StudentStateView.java:38,53`; esquema no snapshot).

**O que mudou com o SP-10 (#22).** Antes, um job que ficava `RUNNING` porque o worker morreu
ficava assim para sempre, e a conta recebia `409` indefinidamente. Hoje, no início de cada
passada, o worker recoloca na fila ou falha (`CORE_UNAVAILABLE`) todo job `RUNNING` há mais
tempo que o limite. O limite é connect timeout + read timeout + margem = 5 s + 10 min + 2 min =
**12 min 05 s** (`application.yml:293,297,324`; `planning/orchestration/OrphanedJobRecovery.java`;
`OrphanedJobRecoveryIntegrationTest`). Na prática o `409` dura no máximo isso depois de uma queda
do worker. Fora desse caso, ele dura enquanto o job está de fato em andamento.

## 5. Limite de pedidos

**10 pedidos de geração por hora por conta** (`application.yml:199-203`, rota
`planning-generation-request`, fase `AFTER_AUTHENTICATION`).

- Excedido, a resposta é **`429`**, corpo `application/problem+json` com `type`
  `urn:sinapse:problem:rate-limit-exceeded` e `detail` "Too many requests for this route. Retry
  after the indicated interval." (`ApiErrorType.java:131`;
  `GenerationEndpointIntegrationTest.askingForPlansRepeatedlyIsRateLimited`).
- **Há `Retry-After`**, em segundos, com o tempo que falta para a janela reabrir, no mínimo 1
  (`shared/ratelimit/RateLimitFilter.java:82`; `RateLimitIntegrationTest`, que confere o
  cabeçalho).
- A janela é **fixa**, de uma hora, contada em memória em cada instância
  (`shared/ratelimit/InMemoryRateLimiter.java:11-16`). Ela zera quando a instância reinicia; com
  várias instâncias, cada uma conta a sua parte.
- **Todo `POST` gasta a cota, inclusive os respondidos com `409`**
  (`askingForPlansRepeatedlyIsRateLimited`: dois `409` seguidos de `429`). O cliente não deve
  repetir o `POST` para "ver se já pode": consulte o job.
- O limite casa só o caminho exato `/api/v1/study-plans/generation-requests` (`PathPattern`,
  `shared/ratelimit/RateLimitPolicies.java:41`). O `GET` de consulta, que tem `/{id}` no fim,
  **não** é limitado.

## 6. Quanto tempo leva

| Cenário | Tempo | De onde vem |
|---|---|---|
| Core saudável, plano pequeno | 7–10 s do clique ao `READY` | **medido pela auditoria** de 05/10/2026 ([PRONTIDAO_INTEGRACAO_AG.md](PRONTIDAO_INTEGRACAO_AG.md)); não repetido nesta revisão, **a confirmar** |
| Core saudável, composição | 0–10 s até a próxima passada + a chamada ao Core | derivado de `poll-cron` (§2); o ciclo do worker domina num plano pequeno |
| Core fora do ar (conexão recusada) | ~115 s até `FAILED` | **medido pela auditoria**; não repetido, **a confirmar** |
| Core fora do ar, composição | (0–10 s) + (30–40 s) + (60–70 s) ≈ 90–120 s, mais até 5 s por tentativa se a conexão esperar o *connect timeout* | derivado: 3 tentativas, espera de 30 s · 2^(tentativas − 1) desde o início da última (`PlanGenerationRequestRepository.java:66-76`, `application.yml:317-318`), arredondada para a próxima passada de 10 s |
| Core que aceita e nunca responde | até ~31 min até `FAILED` | derivado: cada tentativa espera o *read timeout* de 10 min (`application.yml:297`), três vezes, mais as esperas acima |

## 7. Por que o job falhou: `failureReason`

**As falhas do Core não são erros RFC 7807 de uma chamada HTTP.** O `POST` já respondeu `201`
muito antes. Uma falha do Core, ou da geração, aparece no job consultado: `GET` responde
**`200`** com `status: FAILED` e `failureReason` (`PlanGenerationFailure`;
`CoreUnreachableIntegrationTest`).

O conjunto é **fechado**, com quatro valores (`planning/api/PlanGenerationFailure.java`):

| `failureReason` | O que significa | Nova tentativa automática | Orientação de mensagem ao aluno |
|---|---|---|---|
| `CORE_UNAVAILABLE` | o otimizador não respondeu: conexão recusada, *timeout*, ou status que não é 2xx nem 4xx | sim, até 3 tentativas; só aparece em `FAILED` depois da última | "Não conseguimos gerar seu plano agora. Tente de novo em alguns minutos." |
| `CORE_REJECTED` | o otimizador recusou o pedido (4xx), ou respondeu algo que a plataforma não aceita como plano (fora do contrato ou quebrando uma das checagens de `validated`) | não | "Não foi possível montar um plano com a sua disponibilidade e as suas metas atuais. Revise-as e tente de novo." |
| `NOTHING_TO_PLAN` | no momento da execução não havia meta ativa, ou não havia disponibilidade no horizonte, ou a conta não tinha fuso (`SnapshotAssembler.java:97,102,142`) | não | "Não há o que planejar: confira se você tem ao menos uma meta ativa e horários disponíveis nas próximas semanas." |
| `INTERNAL` | falha da própria plataforma durante a execução | não | "Algo deu errado do nosso lado. Tente de novo mais tarde." |

Sobre as mensagens:

- **Não mostre o código** (`CORE_REJECTED` e afins) nem tente detalhar a causa. A resposta não
  carrega endereço, status, corpo do Core nem pilha, de propósito
  (`CoreAnswerBreakingAnInvariantIntegrationTest`, que confere a ausência de endereço, `http`,
  `exception` e identificadores).
- Os textos acima são orientação, não uma decisão de produto.
- Um `CORE_REJECTED` também cobre o caso em que o Core responde que nada cabe na
  disponibilidade (`422 plan-would-be-empty`, no Core). O cliente não tem como distinguir isso,
  por isso a mensagem sugere revisar disponibilidade e metas.
- **Uma falha não apaga o plano em vigor**: o plano anterior continua `ACTIVE`
  (`PlanGenerationIntegrationTest.aFailedRunLeavesThePreviousPlanInForce`).

## 8. Plano em vigor e resumo: formas diferentes

As duas rotas falam do mesmo plano, mas com corpos diferentes. Esquemas do snapshot:
`StudyPlanResponse` e `PlanSummaryView`.

| Campo | `GET /study-plans/current` (e `/study-plans/{planId}`, `/study-plans`) | `GET /study-plans/{planId}/summary` |
|---|---|---|
| identificador do plano | **`id`** | **`planId`** |
| `generationRequestId` | sim | — |
| `status`, `horizonStart`, `horizonEnd`, `createdAt` | sim | sim |
| `supersededAt` | sim | — |
| `supersededByPlanId` | sim | sim |
| `fitness` (objeto opaco do Core) | sim | sim |
| `totalPlannedMinutes` | — | sim |
| `bySubject[]` (`subjectId`, `subjectName`, `plannedMinutes`, `sessionCount`) | — | sim |
| `adherence` (`plannedElapsed`, `executed`, `ratio`) | — | sim |

- **Quando usar cada uma:**
  - `/current` responde "qual plano vale agora", e dá o `id` para as outras rotas. Sem plano em
    vigor, ele responde `404` (snapshot).
  - O resumo é a tela de acompanhamento: totais por disciplina e aderência. A aderência só conta
    sessões que já venceram ([CONTRATO_API_SINAPSE.md §3.3](CONTRATO_API_SINAPSE.md)).
- **Cuidado com o nome do identificador:** quem lê os dois precisa mapear `id` ↔ `planId`.

## 9. Nomes de tópico: agenda e sessões

- **A agenda traz os nomes.** `GET /api/v1/me/agenda?from=&to=` (os dois parâmetros são
  obrigatórios, snapshot) devolve `days[].entries[]`. Cada entrada tem `topicId`, `topicName`,
  `subjectId` e `subjectName` (esquemas `DailyAgendaView`, `Day`, `Entry` no snapshot).
- **As sessões de um plano trazem só UUID.** `GET /api/v1/study-plans/{planId}/sessions` e
  `GET /api/v1/planned-sessions` devolvem `PlannedSessionResponse`: `id`, `planId`, `topicId`,
  `kind`, `scheduledStart`, `durationMinutes` e `sequenceIndex`. Não vem nome, nem
  `subjectId`.
- **Como resolver o nome pelo catálogo:**
  1. `GET /api/v1/subjects` lista as disciplinas.
  2. Para cada uma, `GET /api/v1/subjects/{subjectId}/topics` devolve `id`, `name` e
     `position` (`TopicResponse`).
  3. Monte um mapa `topicId → name`.

  O catálogo tem dezenas de itens, sem paginação ([CONTRATO_API_SINAPSE.md §7](CONTRATO_API_SINAPSE.md)),
  então carregar tudo uma vez é barato. Partir das metas (`GET /api/v1/goals`, que traz
  `subjectId`) também funciona, mas só cobre as disciplinas que o aluno persegue.

## 10. O que o cliente vê de cada execução

| Dado | O cliente vê? | Evidência |
|---|---|---|
| `core_version` | **não**: nenhuma rota o expõe | nenhuma ocorrência de `coreVersion` no snapshot; `GenerationRequestResponse` não tem o campo; a exportação de dados usa `GenerationRequestView`, que também não tem (`planning/internal/service/PlanningDataRights.java:125-131`) |
| motor usado | **sim, dentro de `fitness`**, chave `engine`, em `/study-plans/current`, `/study-plans/{planId}`, `/study-plans` e no resumo | o `fitness` é repassado inteiro (`FitnessReportIntegrationTest`) e o Core põe nele o motor que rodou (README do Core, lido em `exam-optimizer-application@3b54ba4`); conferido de ponta a ponta contra um Core simulado em `EngineSelectionIntegrationTest` (SP-8). Está registrado como STATE.md K17, para decisão do dono |
| identificador do build do Core | **sim, dentro de `fitness`**, chave `build` (SHA do commit), pelo mesmo caminho do motor | o Core passou a estampá-lo no EOA-13 (`docs/ai/STATE.md` do Core, K7, lido em `3b54ba4`); gravação conferida contra o Core simulado (`EngineSelectionIntegrationTest`); contra o Core real, **a confirmar** |

`core_version` é a versão Maven do Core e **não identifica o build**: muitos commits saem com
a mesma versão (Core K7, lido em `exam-optimizer-application@3b54ba4`). Quem precisa saber qual
build produziu um plano lê `fitness.build`. As chaves de `fitness` são do Core, e a plataforma
não conhece nenhuma pelo nome ([CONTRATO_API_SINAPSE.md §3.3](CONTRATO_API_SINAPSE.md)): o
frontend não deve construir lógica de tela em cima delas.
