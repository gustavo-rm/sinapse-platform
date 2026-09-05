# Contrato de API — Sinapse Platform

**Versão:** 0.1
**Data:** 04 de setembro de 2026
**Deriva de:** `FLUXOS_DE_USUARIO_SINAPSE.md`, ADR 0009 e ADR 0012

Este documento especifica o que não é derivável do modelo de domínio: as convenções
transversais e os modelos de leitura. As operações de escrita seguem os agregados e estão
descritas nos prompts 01 a 07; a especificação OpenAPI delas é gerada pelo springdoc a
partir do código.

---

## 1. Convenções

**Prefixo.** Toda rota sob `/api/v1`. Sem exceção.

**Erros.** RFC 7807 `application/problem+json`. `detail` nunca contém dado pessoal,
identificador interno, nome de entidade ou de coluna. Ver ADR 0009.

**Instantes.** ISO-8601 com deslocamento (`2026-09-04T19:00:00-03:00`). Horários locais,
como janelas de disponibilidade, trafegam como `HH:mm` e são interpretados no fuso da conta.

**Identificadores.** UUID em texto.

**Enumerações.** Trafegam como o literal do domínio em maiúsculas (`GOOD`, `HARD`,
`MEASURED`). O cliente é responsável pela tradução para exibição; a API não devolve texto
apresentável em português.

**Paginação.** Não há mecanismo genérico. Duas regras:

- Listas naturalmente pequenas e limitadas (turmas de um professor, alunos de uma turma,
  metas, disponibilidade) retornam completas, com teto rígido no servidor.
- Históricos, que crescem sem limite, exigem janela temporal obrigatória
  (`from` e `to`), com intervalo máximo configurado.

Paginação por cursor é acréscimo barato quando um caso concreto aparecer. Construí-la agora
seria abstração especulativa.

**Autenticação.** Sessão opaca. *Cookie* `HttpOnly` para navegador,
`Authorization: Bearer` para os demais. Ver ADR 0010.

---

## 2. Inventário de escrita

Resumo por módulo. Detalhe nos prompts.

| Módulo | Operações |
|---|---|
| `identity` | cadastro, verificação de e-mail, login, logout, troca e recuperação de senha, conceder e revogar consentimento, listar e encerrar sessões, reafirmação de maioridade |
| `curriculum` | disciplinas, tópicos, arestas de pré-requisito, semeadura por ordenação, reordenação — **todas administrativas** |
| `educational` | criar e arquivar turma, criar, listar e revogar convite, resgatar convite, sair da turma, remover aluno |
| `learningrecord` | iniciar sessão, encerrar com avaliação, abandonar, registrar sessão retroativa |
| `planning` | disponibilidade, metas |
| `planning.orchestration` | criar pedido de geração |
| `datarights` | exportar, pedir eliminação, cancelar pedido |

---

## 3. Modelos de leitura

Nenhum corresponde a um agregado. Todos atravessam módulos.

### 3.1 `EstadoDoAluno`

`GET /api/v1/me/state` — a chamada que a tela inicial faz.

```
accountStatus            enum
timeZone                 string
pendingConsents          [purpose]        finalidades ainda não decididas
requiresMajorityReaffirmation  boolean
setupComplete            boolean          disponibilidade e ao menos uma meta
activePlanId             uuid | null
activeGenerationJobId    uuid | null
openSessionId            uuid | null      sessão IN_PROGRESS, se houver
```

`openSessionId` existe porque há no máximo uma sessão em progresso por conta: a tela precisa
oferecer retomar em vez de iniciar outra.

Compõe: `identity`, `planning`, `learningrecord`.

### 3.2 `AgendaDoDia`

`GET /api/v1/me/agenda?from=&to=`

```
days[]
  date
  entries[]
    plannedSessionId
    topicId, topicName
    subjectId, subjectName
    kind                  STUDY | REVISION
    scheduledStart, plannedDurationMinutes
    execution | null
      sessionId, status, actualDurationMinutes, recallRating, durationSource
```

**É o principal argumento contra derivar o contrato dos agregados.** Uma tela, quatro
módulos. Sem este modelo o cliente faria cinco chamadas e replicaria a lógica de casamento
entre sessão planejada e executada.

Compõe: `planning`, `curriculum`, `learningrecord`.

### 3.3 `PlanoResumido`

`GET /api/v1/study-plans/{id}/summary`

```
planId, status, horizonStart, horizonEnd, createdAt
supersededByPlanId | null
totalPlannedMinutes
bySubject[]      subjectId, subjectName, plannedMinutes, sessionCount
adherence        plannedElapsed, executed, ratio
```

`adherence` considera apenas sessões planejadas já vencidas. Sessão futura não conta como
descumprida.

Compõe: `planning`, `curriculum`, `learningrecord`.

### 3.4 `PreviaDoConvite`

`GET /api/v1/invites/{code}/preview`

```
classroomName, teacherName, subjectNames[]
visibilityScope     descrição do que o professor passará a ver
requiresConsent     purpose = INSTITUTION_SHARING
expiresAt
```

`visibilityScope` é requisito, não cortesia: sustenta o consentimento informado, dado que o
escopo adotado é integral. Ver ADR 0005.

Rota autenticada e limitada por taxa.

Compõe: `educational`, `curriculum`, `identity`.

### 3.5 `PainelDoAluno` (visão do professor)

`GET /api/v1/classrooms/{id}/students/{accountId}/panel?from=&to=`

```
studentName
adherence            ratio, planned, executed
minutesBySubject[]   subjectId, subjectName, minutes
recallTrajectory[]   topicId, topicName, points[{ at, rating }]
recentSessions[]     janela limitada
```

Exige `TeacherAccessPolicy.canViewStudent`, que verifica matrícula ativa **e**
`INSTITUTION_SHARING` vigente no momento da consulta. Revogação corta o acesso na hora, sem
propagação.

Compõe: `educational`, `identity`, `learningrecord`, `curriculum`.

### 3.6 `ListaDaTurma`

`GET /api/v1/classrooms/{id}/students?from=&to=`

```
students[]   accountId, name, adherenceRatio, totalMinutes
```

Três campos por decisão F7. É a consulta mais pesada do sistema: agrega histórico de N
alunos. Precisa ser medida antes de ser otimizada, e otimizada apenas se a medição
justificar.

---

## 4. Consequência técnica: consultas em lote

Modelos de leitura compostos sem junção entre módulos criam risco de N+1. `AgendaDoDia` com
40 sessões faria 40 buscas de tópico.

**Cada módulo precisa expor busca em lote por conjunto de identificadores**, não apenas
busca unitária:

```java
Map<UUID, TopicView> findTopics(Set<UUID> topicIds);
Map<UUID, ExecutionView> findExecutions(Set<UUID> plannedSessionIds);
```

Isto é requisito do `api` de cada módulo, não detalhe do serviço de leitura. Um módulo que
só expõe busca unitária empurra o N+1 para quem o consome.

---

## 5. Onde os modelos de leitura vivem

Pacote `readmodel`, no mesmo nível de `datarights`. É o **terceiro** componente autorizado a
conhecer mais de um módulo.

Regras:

- Compõe chamando o `api` de cada módulo. Nunca consulta tabela alheia, nunca importa
  `internal`.
- Somente leitura. Nenhuma operação de escrita.
- Sem materialização, sem cache, sem projeção assíncrona na v1. Calcula sob demanda.

**Sobre não materializar.** Materialização introduz obsolescência e uma rotina de
atualização, e o volume do piloto não justifica nenhum dos dois. `ListaDaTurma` é a candidata
a precisar disso primeiro. A decisão de materializar deve vir de medição, não de suspeita.

---

## 6. O que fica deliberadamente de fora

Ordenação e filtro configuráveis, agregações não listadas acima, qualquer endpoint "para o
painel", exportação em formatos de terceiros, notificação por evento.

São exatamente os pontos onde backend sem cliente desperdiça esforço: o custo de acrescentar
depois é baixo, e o de manter algo que ninguém usa é permanente.
