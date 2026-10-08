# Integração com o Sinapse Core

Como a plataforma chama o `exam-optimizer-application`, o que acontece quando a chamada dá
errado e como provar, contra o Core de verdade, que um plano sai do pedido do aluno e chega à
tela. Tudo aqui foi lido no código; onde há um achado, ele está marcado como tal.

---

## 1. Configuração

| Chave | Valor | Origem |
|---|---|---|
| `sinapse.core.base-url` | `${SINAPSE_CORE_URL:http://localhost:8090}` | `application.yml` |
| `sinapse.core.plan-path` | `/plans` | `application.yml` |
| `sinapse.core.connect-timeout` | `5s` | `application.yml` |
| `sinapse.core.read-timeout` | `10m` | `application.yml` |

Verificados no SP-0 e não alterados. Do lado do Core, `POST /plans` só existe com o perfil
`baseline-core` ativo; sem ele a rota responde `404`.

**Qual motor responde** é escolhido por requisição, em `algorithmParams.engine`. Do lado da
plataforma isso é uma única chave, `sinapse.planning.generation.algorithm-params.engine`. Sem
ela, o Core usa o próprio padrão (`plan.engine.default`, hoje `greedy-baseline`). Motores
registrados: `greedy-baseline` e `ga`. Um nome desconhecido é recusado com `422`, nunca
substituído pelo padrão.

---

## 2. Como uma falha do Core vira o motivo do job

Duas etapas. `RestSinapseCore` reduz todo desfecho a duas exceções, e
`PlanGenerationOrchestrator` traduz cada uma em um motivo de falha.

| O que aconteceu | Exceção | Motivo do job | Nova tentativa? |
|---|---|---|---|
| Conexão recusada, host desconhecido, *read timeout* | `CoreUnavailableException` | `CORE_UNAVAILABLE` | sim |
| Core respondeu `5xx` (ou outro status que não é `2xx` nem `4xx`) | `CoreUnavailableException` | `CORE_UNAVAILABLE` | sim |
| Qualquer outra `RestClientException` na chamada | `CoreUnavailableException` | `CORE_UNAVAILABLE` | sim |
| Core respondeu `4xx` | `CoreProtocolException` | `CORE_REJECTED` | não |
| Corpo ilegível pelo contrato (JSON inválido, tipo errado, corpo que não é JSON) | `CoreProtocolException` | `CORE_REJECTED` | não |
| Resposta legível que viola uma das doze checagens de `validated` (lista em `CORE_CONTRACT_SURVEY.md` §3) | `CoreProtocolException` | `CORE_REJECTED` | não |

**Campo desconhecido no corpo não é falha.** O `ObjectMapper` da aplicação deixa
`FAIL_ON_UNKNOWN_PROPERTIES` desligado, que é o padrão do Spring Boot: a chave a mais é ignorada
na leitura e a resposta segue para `validated`. É de propósito, porque um campo novo do Core não
deve derrubar a plataforma. Campo **removido** do contrato é pego no *build*, pela comparação de
`CoreContractGoldenTest` sobre a união das chaves, não pela desserialização (ADR 0016).

O critério é o de `PlanGenerationFailure.isWorthRetrying`: só `CORE_UNAVAILABLE` vale outra
tentativa. Um Core que recusou o payload vai recusar o mesmo payload de novo.

**O que o aluno vê.** Ao consultar o job, ele recebe `status`, `failureReason` (um valor de um
conjunto fechado) e `attemptCount`. Nunca vê mensagem, endereço, porta ou pilha de chamadas. O
detalhe técnico vai só para o log do servidor (`WARN` para indisponível, `ERROR` para
recusado). `CoreUnreachableIntegrationTest` prova isso com o adaptador real apontado para uma
porta fechada.

---

## 3. Política de nova tentativa

- `max-attempts: 3`, `retry-backoff: 30s`, exponencial. A segunda tentativa espera 30 s depois
  do início da primeira, e a terceira espera 60 s.
- A tentativa é contada no momento em que o job é reivindicado, não quando falha. Um worker que
  morre no meio da execução também gasta uma tentativa.
- Esgotadas as tentativas, o job termina em `FAILED` com o último motivo.

### 3.1 A nova tentativa é segura do ponto de vista do aluno?

Sim. **A semente pertence ao job, não à tentativa:** a primeira tentativa a chegar à gravação
sorteia a semente, e todas as tentativas seguintes do mesmo job (nova tentativa depois de Core
indisponível ou tentativa depois de um job órfão, §3.2) leem a semente gravada e enviam a mesma. O
*snapshot* continua sendo remontado a partir do estado atual a cada tentativa e gravado de novo
antes da chamada, então o documento gravado é sempre o último que foi enviado.

O efeito para o aluno é idempotente:

- o Core não guarda estado, então uma tentativa que expirou por *timeout* enquanto o Core
  ainda calculava não deixa nada para trás;
- o plano e o encerramento do job são gravados na mesma transação, e
  `study_plan.generation_request_id` é único, de modo que um job produz no máximo um plano;
- o registro que fica no job (snapshot, semente, parâmetros, versão do Core) é o da tentativa
  que produziu o plano, e a semente é a mesma em todas as tentativas. A reprodutibilidade do
  plano gravado se mantém.

**O que se perde:** o *snapshot* enviado pelas tentativas anteriores, que é sobrescrito. Para o
aluno isso não importa; para a análise de falhas, sim.

### 3.2 Job órfão

Se o processo cai no meio de uma tentativa, ninguém registra o desfecho e o job fica em
`RUNNING`. Sem tratamento, ficaria assim para sempre, e o índice parcial `ux_request_active`
faria toda nova solicitação da conta responder `409`.

O worker trata isso no mesmo ciclo em que já trabalha (`OrphanedJobRecovery`): antes de
reivindicar trabalho, considera órfão todo job em `RUNNING` cujo `started_at` seja anterior a
**agora − tentativa mais longa**, onde

```
tentativa mais longa = sinapse.core.connect-timeout
                     + sinapse.core.read-timeout
                     + sinapse.planning.generation.orphan-margin
```

Com os valores padrão, 5 s + 10 min + 2 min = 12 min 5 s. O limite é calculado, nunca
escrito como constante: aumentar o *read timeout* aumenta o limite junto, e um job legítimo à
espera de uma resposta de 10 minutos não é recuperado no meio. A margem cobre o trabalho em
volta da chamada (montar e gravar o *snapshot*, gravar o plano) e a diferença de relógio entre
duas instâncias. É um julgamento, não uma medição.

O *backoff* e o número de tentativas não entram na conta. O limite mede uma tentativa a partir
do seu próprio `started_at`, que toda reivindicação regrava; entre uma tentativa e outra o job
está em `PENDING`, e não em `RUNNING`.

A transição é uma única `UPDATE` com estado e limite na cláusula `WHERE`, sem leitura prévia:

- se ainda restam tentativas (`attempt_count < max-attempts`), o job volta a `PENDING` com o
  mesmo *snapshot*, os mesmos parâmetros e a mesma semente, e pode ser reivindicado no mesmo
  ciclo;
- se não restam, termina em `FAILED` com `CORE_UNAVAILABLE`, o motivo que já existe para uma
  chamada ao Core que não produziu resposta. Nenhum valor novo de `failureReason` foi criado.

A tentativa do worker que caiu já foi contada na reivindicação, então ela entra no orçamento.

**Mais de uma instância.** O repositório não diz se a plataforma roda com mais de uma
instância; o código é escrito para tolerar isso. Duas instâncias recuperando ao mesmo tempo não
agem duas vezes sobre o mesmo job: a segunda `UPDATE` espera o bloqueio de linha da primeira e,
ao reavaliar a condição, já não encontra o job em `RUNNING` (ou o encontra com um `started_at`
novo). O que a margem precisa cobrir é a diferença de relógio entre a instância que gravou o
`started_at` e a que julga o limite.

**Limitação conhecida.** Se uma tentativa legítima passar do limite (margem pequena demais para
o ambiente), o job é devolvido à fila enquanto a primeira chamada ainda corre, e o Core é
chamado duas vezes. O aluno continua recebendo no máximo um plano, porque
`study_plan.generation_request_id` é único; a segunda gravação falha. Esse caminho não tem teste.

---

## 4. Teste fim a fim contra o Core real

`CoreEndToEndIntegrationTest` (pacote `planning.orchestration.endtoend`) percorre o caminho
inteiro: o aluno pede o plano por `POST /api/v1/study-plans/generation-requests`, o worker
executa o job, e o plano é lido por `GET /api/v1/study-plans/current`. Só a semente é fixada.
Na mesma execução, o teste:

1. confere o contrato isoladamente, enviando ao Core o documento de referência que os dois
   repositórios mantêm;
2. confere que o job chega a `READY`, o plano é gravado e `/current` responde `200` com sessões;
3. confere que `snapshot`, `random_seed`, `core_version`, `algorithm_params`, `generations` e
   `elapsed_millis` estão gravados;
4. confere as oito checagens de `validated` sobre o plano **gravado**; se uma delas falhar com
   o job em `READY`, a mensagem diz que a validação tem furo;
5. confere quatro garantias do Core (tópico enviado, horizonte, janela de disponibilidade, sem
   sobreposição) e a ordem dos pré-requisitos `HARD`. Desde 05/10/2026, `validated` também
   checa tópico, janela e sobreposição; horizonte e pré-requisitos continuam só no Core;
6. pede o plano de novo com a mesma semente e exige snapshot, sessões e fitness idênticos. É a
   demonstração que o ADR 0007 exige.

### Como rodar

O teste tem a tag `core-e2e`, que o build padrão exclui. Precisa de Docker e de um Core:

```bash
# Core a partir do jar, em contêiner (eclipse-temurin:21-jre)
(cd ../exam-optimizer-application && ./mvnw -DskipTests package)
SINAPSE_E2E_CORE_JAR=../exam-optimizer-application/target/DynamicStudyPlanner-2.0.1.jar \
  ./mvnw verify -Pcore-e2e

# ou contra um Core já em execução
SINAPSE_E2E_CORE_URL=http://localhost:8090 ./mvnw verify -Pcore-e2e

# trocar de motor: um único parâmetro
SINAPSE_E2E_CORE_ENGINE=ga SINAPSE_E2E_CORE_JAR=... ./mvnw verify -Pcore-e2e
```

Sem nenhuma das duas variáveis, o teste **falha** dizendo o que falta. Ele não é pulado.

---

## 5. Achados

1. **`elapsedMillis` chega sempre zerado.** Os dois motores do Core enviam `0` de propósito
   (`GreedyBaselineScheduler.ELAPSED_MILLIS` e `GeneticPlanEngine.ELAPSED_MILLIS`): medir o
   tempo quebraria a reprodutibilidade byte a byte, e a decisão foi tomada quando a plataforma
   ainda descartava o campo. Hoje a plataforma grava `elapsed_millis` (SP-3), mas o valor é
   sempre `0`. **O lado do custo na comparação AG × guloso continua sem dado.** Resolver isso é
   decisão do Core, por exemplo informar o tempo fora da parte comparada da resposta, e não da
   plataforma.
2. **`algorithm_params` grava parâmetros que o Core não usa.** A plataforma envia e grava
   `generations: 400`, `population-size: 120` e `mutation-rate: 0.05`. O Core ignora esses três
   e roda o AG com a própria configuração (`plan.engine.ga.generations=60`,
   `population-size=40`). O registro do job afirma parâmetros que não rodaram, e os que rodaram
   não ficam em lugar nenhum além de `core_version` (que identifica o build, não a
   configuração implantada). Isso fere a reprodutibilidade do ADR 0007.
3. **Um `500` por violação de invariante no Core é tratado como indisponibilidade.** Esse caso
   (`plan-invariant-violation`) é defeito, não instabilidade. Com o motor guloso, que é
   determinístico, as novas tentativas gastam o orçamento repetindo o mesmo erro.
4. **Resolvido:** um job cujo worker morria ficava em `RUNNING` para sempre e bloqueava a
   conta com `409`. Hoje o worker o recupera (§3.2).
