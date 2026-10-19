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

### 1.1 Qual motor responde

O Core escolhe o motor por requisição, pela chave `algorithmParams.engine`. A plataforma envia
essa chave a partir da configuração da **implantação**: é uma entrada do mapa
`sinapse.planning.generation.algorithm-params`, e nada além disso. Nenhuma rota, nenhum corpo de
requisição e nenhum dado do aluno chega a esse mapa.

**Isto não é atribuição de condição.** Todos os alunos de uma implantação recebem o mesmo motor.
Atribuir motores diferentes a alunos ou contas é desenho de experimento, com questões éticas e de
LGPD (CLAUDE.md §7, J2), e é decisão de pesquisa, não de engenharia. Não existe, e não deve ser
criado sem essa decisão.

Motores registrados no Core hoje (lidos em `exam-optimizer-application@3b54ba4`, README,
"Choosing the engine per request"): `greedy-baseline` (o padrão), `ga` e `ga-timeline`. A
plataforma **não** guarda essa lista em código: quem sabe quais motores existem é o Core.

**Como definir o motor.** As três formas abaixo funcionam; `AlgorithmParamsConfigurationTest`
sobe uma aplicação Spring de verdade com cada uma e confere o mapa resultante:

| Forma | Exemplo |
|---|---|
| Arquivo de configuração | `sinapse.planning.generation.algorithm-params.engine: ga-timeline` |
| `SPRING_APPLICATION_JSON` | `{"sinapse":{"planning":{"generation":{"algorithm-params":{"engine":"ga-timeline"}}}}}` |
| Variável de ambiente | `SINAPSE_PLANNING_GENERATION_ALGORITHMPARAMS_ENGINE=ga-timeline` |

A variável de ambiente funciona porque `engine` não tem hífen. Uma chave com hífen (por exemplo
`population-size`) **não** pode ser escrita como variável de ambiente: o hífen se perde e a chave
chega como outra (`populationsize`). Para essas, use o arquivo ou o `SPRING_APPLICATION_JSON`.

**Padrão.** Sem a chave `engine`, o corpo enviado não a contém e o Core roda o próprio padrão
(`plan.engine.default`, hoje `greedy-baseline`, o guloso). É a configuração padrão da plataforma.
Para usar o padrão, **omita** a chave: o Core não trata vazio nem nulo como padrão.

A tabela abaixo resume o README do Core, seção "Choosing the engine per request", lida em
`exam-optimizer-application@3b54ba4`. A fonte é aquela seção, que também documenta as outras três
chaves que o Core aplica (`importance`, `precedence`, `provenance`); se divergirem, vale o README.

| O que chegaria ao Core | Resposta do Core |
|---|---|
| sem a chave `engine` | `200`, motor padrão |
| nome registrado | `200`, aquele motor |
| `""` (texto vazio) | `422` `.../errors/unknown-engine` |
| nome desconhecido | `422` `.../errors/unknown-engine`, nunca o padrão |
| valor não textual, por exemplo `42` | `422` `.../errors/unusable-engine` |
| `null` | `400` `.../errors/malformed-body`, antes de o Core escolher o motor |

**O que a plataforma recusa na partida.** Para que vazio e nulo nunca cheguem ao Core, a
aplicação não sobe quando o mapa tem chave vazia, valor nulo ou texto vazio em qualquer chave, ou
um `engine` que não seja texto no formato `[a-z0-9-]+` (`PlanningProperties.Generation` e
`AlgorithmParamsStartupCheck`). Atenção ao YAML: `engine:` sem valor chega ao Spring como texto
vazio, e `"engine": null` no `SPRING_APPLICATION_JSON` simplesmente sumiria do mapa (o motor
padrão seria usado em silêncio); os dois casos derrubam a partida. Um nome bem formado mas
desconhecido passa pela partida e é recusado pelo Core: o job termina `FAILED` com
`CORE_REJECTED`, sem nova tentativa e sem expor ao aluno endereço, status ou corpo do Core
(`EngineSelectionIntegrationTest`).

### 1.2 O que fica gravado de cada execução

| O quê | Onde | Significado |
|---|---|---|
| Motor **pedido** | `plan_generation_request.algorithm_params` (`jsonb`), chave `engine` | O mapa **como a plataforma o enviou**. Registra o que foi pedido, não o que foi aplicado: o Core ignora chaves que não lê |
| Motor **efetivo** | `study_plan.fitness` (`jsonb`), chave `engine` | O motor que o Core declara ter rodado. O `fitness` é gravado inteiro desde antes desta mudança; não houve coluna nova para o motor |
| Build do Core | `study_plan.fitness`, chave `build` | O commit do Core que produziu o plano (Core EOA-13). Grava-se junto com o resto do `fitness`, sem código novo. `core_version` é a versão Maven e **não** identifica o build |
| `elapsed_millis` | `plan_generation_request.elapsed_millis` (`bigint`) | O que o Core devolveu em `metadata.elapsedMillis`: **reservado pelo Core, sempre 0, não mede tempo** (decisão DT-2). Continua sendo gravado como veio |
| `core_call_millis` | `plan_generation_request.core_call_millis` (`bigint`, anulável, `V9`) | Latência da chamada ao Core medida pela plataforma (abaixo) |

**`core_call_millis` é latência de chamada, não custo do motor.** O orquestrador lê o relógio da
aplicação logo antes de entregar a requisição ao adaptador e logo depois de a resposta ter sido
lida e validada. O número inclui rede, serialização e desserialização dos dois lados, a validação
da resposta e qualquer fila no Core. **Não use esta coluna para comparar motores.** O custo do
motor, para a tese, sai do *harness* de medição do Core (EOA-M, EOA-9b), que mede a chamada ao
motor sem rede nem fila.

Regras da coluna:

- cada tentativa mede a própria chamada; fica gravada só a da tentativa que produziu o plano, na
  mesma transação que grava o plano e o `READY`. Uma chamada que falha não grava nada;
- `NULL` quer dizer "não medido": jobs que terminaram antes da migração `V9`, jobs que falharam e
  jobs ainda não concluídos. Nunca se confunde com `0`;
- o relógio da aplicação é de parede (não há fonte monotônica no repositório). Se ele andar para
  trás durante a chamada, a duração negativa é gravada como `0`. Um ajuste de relógio durante a
  chamada distorce o valor; é uma limitação conhecida;
- a coluna não aparece em nenhuma rota de aluno, em nenhum DTO público nem no OpenAPI
  (`CoreCallMillisIntegrationTest`).

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

### 4.1 Os três motores contra um mesmo Core

`GreedyBaselineEngineEndToEndIntegrationTest`, `GaEngineEndToEndIntegrationTest` e
`GaTimelineEngineEndToEndIntegrationTest` (mesma tag `core-e2e`, base `EngineMatrixEndToEnd`)
são três implantações da plataforma, cada uma nomeando um motor no mapa, contra o mesmo Core.
Cada uma cria uma conta sintética nova, usa o cenário pequeno do teste acima (quatro tópicos, três
arestas `HARD`) e confere: job em `READY` (o plano passou por `validated`), motor efetivo gravado
em `study_plan.fitness.engine` igual ao pedido, sessões gravadas e `core_call_millis` preenchido e
não negativo. Não afirmam nada sobre qualidade de plano. Cada uma imprime uma linha
`ENGINE-MATRIX` com estado, motor efetivo, número de sessões e `core_call_millis`.

```bash
SINAPSE_E2E_CORE_URL=http://localhost:8090 ./mvnw verify -Pcore-e2e
```

Se o motor efetivo for diferente do pedido, a mensagem começa com `STOP`: o Core não estaria
honrando `algorithmParams.engine`.

---

## 5. Achados

1. **`elapsedMillis` chega sempre zerado, por decisão.** Os três motores do Core enviam `0` de
   propósito: medir o tempo quebraria a reprodutibilidade da resposta. O dono do projeto decidiu
   em 08/10/2026 (DT-2) que continua assim e que a plataforma mede a chamada. A plataforma segue
   gravando `elapsed_millis` como veio (sempre `0`) e grava a latência que ela mesma mede em
   `core_call_millis` (§1.2). Isso **não** fecha o lado do custo da comparação AG × guloso: esse
   número sai do *harness* do Core.
2. **`algorithm_params` grava parâmetros que o Core não usa.** A plataforma envia e grava
   `generations: 400`, `population-size: 120` e `mutation-rate: 0.05`. O Core ignora esses três
   (confirmado por execução no Core, EOA-13) e roda o AG com a própria configuração (60
   gerações, população 40). O dono decidiu em 08/10/2026 (D4 do Core, Variante 1) que os
   hiperparâmetros são do Core e que a plataforma para de enviá-los. **A retirada ainda não foi
   feita** (SP-8, item 3b adiado): as três instâncias de catálogo real em
   `src/test/resources/instances/`, copiadas byte a byte pelo Core, carregam essas três chaves, e
   `RealCatalogInstancesTest` exige que a configuração de produção as reproduza. Tirá-las exige
   regenerar e anunciar as instâncias (pedido EOA-H2 do Core), o que é decisão do dono. Enquanto
   isso, `algorithm_params` registra o que a plataforma **enviou**, não o que rodou; o que rodou
   está em `study_plan.fitness` (motor, build e, nos motores AG, `importance-strategy` e
   `precedence-policy`) e em `generations`.
3. **Um `500` por violação de invariante no Core é tratado como indisponibilidade.** Esse caso
   (`plan-invariant-violation`) é defeito, não instabilidade. Com o motor guloso, que é
   determinístico, as novas tentativas gastam o orçamento repetindo o mesmo erro.
4. **Resolvido:** um job cujo worker morria ficava em `RUNNING` para sempre e bloqueava a
   conta com `409`. Hoje o worker o recupera (§3.2).
5. **O motor efetivo já aparece em rotas de aluno.** O `fitness` é exposto inteiro, de propósito,
   em `GET /api/v1/study-plans/current`, `/study-plans/{id}`, no histórico e no resumo do plano
   (`FitnessReportIntegrationTest` exige isso), e o Core põe nele `engine` e `build`. A regra de
   não expor o motor ao aluno, portanto, não vale hoje para o `fitness`. Filtrar a chave exigiria
   nomear um termo do `fitness` em `src/main`, o que `FitnessTermNamesAreNotCompiledTest` proíbe.
   Mudar isso é decisão do dono (STATE.md K17).
