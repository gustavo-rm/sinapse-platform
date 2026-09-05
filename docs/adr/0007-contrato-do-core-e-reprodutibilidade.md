# ADR 0007 — Contrato do Core, job de geração e reprodutibilidade

**Data:** 04 de setembro de 2026
**Status:** aceita
**Complementa:** ADR 0002, que decidiu o Core como processo separado e a geração assíncrona.

## Contexto

O ADR 0002 estabeleceu que a geração de plano é assíncrona e que o *snapshot* de entrada é
persistido. Faltava definir o conteúdo do contrato, o mecanismo de fila, o que exatamente
torna um plano reproduzível e o que acontece ao replanejar.

## Decisão

### Reprodutibilidade

O job persiste quatro coisas, e não apenas o *snapshot*:

| Campo | Papel |
|---|---|
| `snapshot` | estado do aluno enviado ao Core, em JSONB |
| `core_version` | versão do Core que executou |
| `algorithm_params` | parâmetros do AG usados na execução |
| `random_seed` | semente do gerador pseudoaleatório |

Sem os quatro, um plano gerado hoje não pode ser regenerado amanhã. Algoritmo genético é
estocástico: mesmo *snapshot* e mesmos parâmetros produzem plano diferente sem a semente
fixada. Isto é requisito da tese, não conforto de auditoria.

O `snapshot` guarda a carga exata enviada, em JSONB, deliberadamente não normalizada.
Normalizá-la criaria uma segunda cópia do modelo de domínio que precisaria ser mantida em
sincronia com a primeira, sem ganho.

### Fila

Tabela `plan_generation_request` em PostgreSQL, consumida com
`SELECT ... FOR UPDATE SKIP LOCKED`. Estados: `PENDING`, `RUNNING`, `READY`, `FAILED`,
`CANCELLED`.

Índice parcial garante no máximo um job não terminal por aluno.

### Replanejamento

Replanejar cria um **plano novo**. O anterior transita para `SUPERSEDED` e aponta para o
sucessor. Planos nunca são editados; `planned_session` é imutável por *trigger*.

### Conteúdo do contrato

Entrada: horizonte, janelas de disponibilidade, metas com prazo e prioridade, tópicos com
disciplina e esforço estimado, arestas de pré-requisito com `strength` e `provenance`,
histórico de sessões por tópico, parâmetros e semente.

Saída: sessões planejadas com tópico, início, duração e tipo; métricas de aptidão;
metadados da execução (versão, semente, gerações, tempo).

O contrato vive em módulo compartilhado versionado, consumido pelos dois repositórios.

## Justificativa

**Sobre a fila em PostgreSQL.** `FOR UPDATE SKIP LOCKED` resolve consumo concorrente sem
*broker*. Na escala do piloto, um *broker* seria infraestrutura adicional para operar,
monitorar e depurar, sem problema que o justifique. A troca, se a escala exigir, é
localizada no consumidor.

**Sobre o índice parcial de job ativo.** Sem ele, um aluno impaciente enfileira dezenas de
otimizações, cada uma consumindo CPU por minutos. É controle de recurso, não de interface.

**Sobre replanejar criando plano novo.** Três razões. Um plano é saída de um processo
estocástico e editá-lo destrói a correspondência com o *snapshot* que o gerou, quebrando a
reprodutibilidade. Sessões já executadas referenciam sessões planejadas, e mutá-las
corromperia a evidência. E a cadeia de planos substituídos é dado experimental gratuito:
com que frequência o aluno replaneja, em que ponto do horizonte, e o que havia mudado.

**Sobre `provenance` das arestas entrar no contrato.** É o que permite a ablação descrita
no ADR 0006 sem instrumentação adicional depois.

## Consequências

O cliente precisa lidar com espera. A interface exige estado de carregamento explícito.

`snapshot` cresce sem limite e é o maior consumidor de espaço do sistema. Exige política de
retenção. Recomendação inicial: preservar integralmente os *snapshots* de jobs cujo plano
ainda é `ACTIVE` ou que pertençam ao período do piloto, e comprimir ou arquivar os demais
após um prazo. Decisão pendente.

Falha do Core precisa de política de tentativa. `attempt_count` e `failure_reason` estão no
esquema; a política (número de tentativas, recuo exponencial, tempo limite) fica para a
implementação, com o limite máximo em configuração.

Mudança no contrato é mudança quebrável entre dois repositórios e exige versionamento
explícito. Um plano gerado por versão antiga do contrato precisa continuar legível.
