# ADR 0003 — Fronteiras de contexto e direção de dependências

**Data:** 04 de setembro de 2026
**Status:** aceita

## Contexto

O documento de contexto do produto lista funcionalidades de identidade, alunos,
professores, turmas, conteúdo, exercícios, planejamento, sessões, revisão, RAG e
instituições. Implementar tudo isso como escopo inicial contradiz o próprio princípio de
simplicidade declarado no projeto: seriam seis contextos delimitados construídos antes de
qualquer validação.

É necessário definir quais contextos existem, onde ficam suas fronteiras e em que direção
as dependências podem apontar.

## Decisão

Cinco contextos delimitados, com dependências acíclicas:

| Contexto | Responsabilidade |
|---|---|
| `identity` | conta, credenciais, papéis, responsável, consentimento |
| `curriculum` | disciplina, tópico, pré-requisitos |
| `educational` | professor, turma, convite, matrícula |
| `learningrecord` | sessões executadas, *append-only* — evidência |
| `planning` | disponibilidade, metas, job de geração, plano — saída do Core |

`curriculum` e `identity` formam a base e não dependem de nenhum outro módulo.

Uma camada de orquestração (`planning.orchestration`) compõe os módulos para executar a
geração de plano.

Conteúdo educacional, exercícios, revisão e RAG são contextos futuros. Suas fronteiras
estão reservadas, mas nada é implementado agora.

### Regras de dependência

**R1.** Módulos referenciam entidades de outros módulos apenas por identificador.
`StudySession` guarda `topicId`, nunca `@ManyToOne Topic`.

**R2.** `planning` não lê `learningrecord`. A composição é feita pela orquestração.

**R3.** `curriculum` e `identity` não dependem de nada.

**R4.** As regras são verificadas em tempo de build. O build falha quando um módulo
importa o pacote `internal` de outro.

## Justificativa

**Separação entre Planejamento e Registro de aprendizagem.** Um plano é saída de
otimização, essencialmente imutável após a geração. Um registro é evidência e só cresce.
Unir os dois produziria um agregado simultaneamente imutável e *append-only*, o que é
contraditório. A separação também nomeia com clareza os dois lados da interação com o
Core: Registro é entrada, Planejamento é saída.

**Justificativa de R2.** A dependência natural seria `planning → learningrecord`, já que
a reotimização exige o histórico. Mas `learningrecord` precisa referenciar a sessão
planejada, o que fecharia um ciclo. A camada de orquestração quebra o ciclo e ganha, com
isso, razão de existir independente.

**Justificativa de R4.** Fronteira arquitetural documentada mas não verificada
sobrevive até a primeira pressa de cronograma. A verificação automática é o que a torna
real.

**Justificativa da localização do DAG de pré-requisitos.** O grafo de pré-requisitos
entre tópicos é dado curricular, não parâmetro de algoritmo. Modelá-lo em `curriculum`
permite que o Core o receba como entrada, em vez de embuti-lo e torná-lo inauditável.

## Consequências

A ausência de associações JPA entre módulos impede junções diretas no ORM. Consultas que
cruzam contextos precisam de composição em serviço ou de projeções de leitura dedicadas.
É custo real e aceito.

A camada de orquestração concentra conhecimento de vários módulos e tende a crescer.
Precisa de vigilância para não virar um serviço monolítico disfarçado.

A separação Planejamento/Registro adiciona um módulo em relação à alternativa unificada.
