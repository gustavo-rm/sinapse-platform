# Arquitetura do Backend da Plataforma Sinapse

**Versão:** 0.1 — linha de base
**Data:** 04 de setembro de 2026
**Status:** documento vivo. Decisões estruturais são registradas em `docs/adr/`.
**Escopo deste documento:** fronteiras de contexto, direção de dependências, estrutura
física do projeto e o modelo completo do contexto de Identidade. Os demais contextos
serão detalhados em documentos subsequentes.

---

## 1. Decisões consolidadas

| # | Decisão | ADR |
|---|---|---|
| 1 | Java + Spring Boot, PostgreSQL, Flyway, monólito modular | [0001](adr/0001-stack-e-estilo-arquitetural.md) |
| 2 | Sinapse Core permanece em processo separado; geração de plano é assíncrona por job | [0002](adr/0002-integracao-sinapse-core.md) |
| 3 | Cinco contextos delimitados com dependências acíclicas | [0003](adr/0003-fronteiras-de-contexto.md) |
| 4 | Consentimento unificado com outorgante `SELF` ou `GUARDIAN` | [0004](adr/0004-modelo-de-consentimento.md) |
| 5 | Aluno se cadastra sozinho e entra em turma por convite | §4.3 |
| 6 | Acesso de professor a aluno deriva exclusivamente de matrícula em turma | [0005](adr/0005-acesso-do-professor.md) |
| 7 | Escopo de visibilidade do professor é integral, exposto como política reversível | [0005](adr/0005-acesso-do-professor.md) |
| 8 | Grafo de pré-requisitos global, acíclico por trigger, curado, com aresta tipada HARD/SOFT | [0006](adr/0006-grafo-de-pre-requisitos.md) |
| 9 | Job de geração com snapshot, versão do Core, parâmetros e semente; replanejar cria plano novo | [0007](adr/0007-contrato-do-core-e-reprodutibilidade.md) |
| 10 | Evidência da v1 é autorrelato: mede adesão e percepção, não retenção | [0008](adr/0008-evidencia-de-aprendizagem.md) |
| 11 | Transversais: RFC 7807, `/api/v1`, limitação de taxa, fuso obrigatório, observabilidade | [0009](adr/0009-decisoes-transversais.md) |
| 12 | Sessão opaca no servidor, não JWT, por exigência de revogação imediata | [0010](adr/0010-autenticacao-e-sessao.md) |
| 13 | Direitos do titular: histórico apagado, consentimento higienizado, anonimização na exportação | [0011](adr/0011-direitos-do-titular.md) |
| 14 | Esforço por tópico é faixa ordinal; horizonte fixo de 4 semanas; replanejamento manual | [0012](adr/0012-decisoes-de-produto.md) |
| 15 | Escrita segue o agregado, leitura segue a tela; `readmodel` compõe sem materializar | [0013](adr/0013-servico-de-leitura.md) |
| 16 | Catálogo curado é artefato versionado em CSV, aplicado por importador de linha de comando | [0014](adr/0014-curadoria-do-catalogo.md) |

---

## 2. Contextos delimitados

### 2.1 Identidade

Conta, credenciais, papéis, data de nascimento, responsável e registros de
consentimento. Não conhece nenhum conceito educacional.

Detalhado na seção 5 deste documento.

### 2.2 Currículo

Disciplina, tópico e, em etapa futura, relações de pré-requisito entre tópicos.

Observação relevante para a pesquisa: o grafo de pré-requisitos (DAG) que hoje falta
ao motor de otimização pertence conceitualmente a este contexto. É dado curricular,
não parâmetro de algoritmo. Modelá-lo aqui permite que o Core o receba como entrada
em vez de embuti-lo.

### 2.3 Contexto educacional

Professor, turma, convite e matrícula. É a **única** fonte de acesso de um professor
aos dados de um aluno.

A entidade `Teacher` vive aqui e referencia `accountId`. O papel `TEACHER` vive em
Identidade. São coisas distintas: o papel autoriza o login como professor, a entidade
guarda o vínculo profissional e as turmas.

### 2.4 Registro de aprendizagem

Sessões de estudo efetivamente executadas, em regime *append-only*. Receberá
posteriormente tentativas de exercício e de revisão.

É a fonte de **evidência** consumida pelo Core.

### 2.5 Planejamento

Disponibilidade, metas, pedido de geração, plano gerado e sessões planejadas.

É o repositório da **saída** do Core.

### 2.6 Por que Planejamento e Registro são contextos separados

Um plano é artefato de saída de um processo de otimização e é essencialmente imutável
após a geração. Um registro de estudo é evidência e só cresce. Padrões de escrita,
ciclo de vida, volume e consumidores são diferentes. Uni-los produziria um agregado
que é ao mesmo tempo imutável e append-only, o que é contraditório.

---

## 3. Direção das dependências

```
              Orquestração de geração de plano ──────► Sinapse Core
                     │          │          │              (processo
        ┌────────────┘          │          └────────┐      separado)
        ▼                       ▼                   ▼
  Planejamento          Registro de           Contexto
                        aprendizagem          educacional
        │                       │                   │
        └───────────────────────┼───────────────────┘
                                ▼
                    ┌───────────────────────┐
                    │  Currículo   Identidade │   base compartilhada
                    └───────────────────────┘
```

### Regras

**R1 — Referência por identificador.**
Módulos referenciam entidades de outros módulos apenas por identificador. `StudySession`
guarda `topicId`, nunca `@ManyToOne Topic`. Sem esta regra, a fronteira vira decoração
em poucos meses.

**R2 — Planejamento não lê Registro de aprendizagem.**
A dependência óbvia (reotimizar exige histórico) criaria ciclo, porque o Registro
precisa referenciar a sessão planejada. Quem quebra o ciclo é a camada de orquestração,
que lê Currículo, Registro e Planejamento, monta o *snapshot*, chama o Core e grava o
plano resultante. Esta é a justificativa para a orquestração existir como componente
próprio, e não como método interno de Planejamento.

**R3 — A base não depende de ninguém.**
Currículo não sabe o que é aluno. Identidade não sabe o que é disciplina.

**R4 — Chaves estrangeiras seguem a direção das dependências de módulo.**
A regra R1 proíbe associação de objetos entre contextos, não integridade referencial.
No banco único, uma chave estrangeira que aponta na mesma direção em que o módulo já
pode depender (por exemplo `educational.teacher.account_id → identity.account`) entrega
integridade sem custo arquitetural. O que fica proibido é a chave estrangeira contrária
à direção permitida.

**R5 — As regras acima são testadas, não documentadas.**
O build falha quando um módulo importa o pacote `internal` de outro. Ferramenta:
Spring Modulith ou ArchUnit. Uma fronteira que não quebra o build dura até a primeira
pressa.

---

## 4. Estrutura física

### 4.1 Pacotes

```
br.com.sinapse.platform
├── identity
│   ├── api/          interface pública e DTOs
│   └── internal/
├── curriculum
├── educational
├── learningrecord
├── planning
│   └── orchestration/   monta snapshot, chama o core, grava o plano
├── coreclient           adaptador do Sinapse Core
└── shared
```

Um único artefato executável. Microsserviços estão explicitamente fora de escopo e não
há justificativa técnica atual para eles.

### 4.2 Persistência

PostgreSQL único. Flyway desde o primeiro *commit*. Schema único, com prefixo de tabela
por contexto quando houver ambiguidade de nome.

Sem Redis, sem *message broker*, sem armazenamento de objetos nesta fase. O processamento
assíncrono da geração de plano usa tabela de job em PostgreSQL, que é suficiente para a
escala do piloto e elimina uma dependência de infraestrutura.

### 4.3 Autorização

Modelo baseado em propriedade de recurso:

- Todo recurso de aprendizagem pertence a uma `Account`.
- Um professor acessa dados de um aluno **se e somente se** existir matrícula ativa
  desse aluno em turma de sua titularidade.
- Não existe relação direta professor → aluno.
- Aluno independente é simplesmente um aluno sem matrícula. Nenhuma chave estrangeira
  obrigatória força o aluno para dentro de estrutura institucional.

Esta é também a base que torna viável uma futura multilocação sem reescrita. Não há
coluna de *tenant* nesta fase; seria abstração especulativa.

---

## 5. Contexto de Identidade

### 5.1 Agregados

| Agregado | Conteúdo | Justificativa |
|---|---|---|
| `Account` (raiz) | e-mail, hash de senha, data de nascimento, status, papéis, `Guardian` como entidade interna | `Guardian` só existe em função de uma conta e nunca é acessado isoladamente |
| `ConsentRecord` (raiz) | referencia `accountId`, append-only | cresce sem limite e é consultado de forma independente |
| `TermsVersion` (raiz) | texto versionado do termo | sem ele prova-se *que* houve aceite, não *o que* foi aceito |

### 5.2 Máquina de estados da conta

```
                    registro
                       │
                       ▼
           PENDING_VERIFICATION
                       │ e-mail verificado
          ┌────────────┴────────────┐
      menor de idade            maior de idade
          │                          │
          ▼                          │
 PENDING_GUARDIAN_CONSENT            │
          │ responsável concede      │
          └────────────┬─────────────┘
                       ▼
                    ACTIVE ◄──────── reativação
                       │
        ┌──────────────┼──────────────┐
        │ revogação    │ decisão      │ Art. 18 LGPD
        ▼              ▼              ▼
    SUSPENDED     SUSPENDED      ANONYMIZED (terminal)
```

O adulto percorre o mesmo caminho do menor. A única diferença é que seu
`ConsentRecord` nasce com `grantedBy = SELF` e a transição para `ACTIVE` é imediata.

### 5.3 Invariantes

1. `ACTIVE` exige `ConsentRecord` vigente para toda finalidade essencial, com
   `grantedBy` compatível com a idade do titular na data da concessão.
2. `dateOfBirth` é imutável após a ativação. Antes disso pode ser corrigida. Depois,
   apenas por operação administrativa auditada. Sem isso, um menor altera a data e
   atravessa o portão.
3. `Guardian` é obrigatório se, e somente se, a idade na data do consentimento for
   inferior ao limiar configurado.
4. `ConsentRecord` nunca é atualizado, exceto pela gravação única de `revokedAt`.
5. Revogar consentimento de finalidade essencial move a conta para `SUSPENDED` na
   mesma transação.
6. E-mail é único entre contas não anonimizadas.

A invariante 1 cruza dois agregados. Em monólito com banco único, ela é garantida
transacionalmente por um `ConsentService` que é o único escritor de ambos. Se este
contexto um dia virar serviço separado, a invariante passa a ser eventualmente
consistente e o modelo precisa mudar. Registrado no ADR 0004.

### 5.4 Finalidades de consentimento

| Finalidade | Natureza | Efeito da recusa |
|---|---|---|
| `LEARNING_DATA_PROCESSING` | essencial | serviço indisponível |
| `INSTITUTION_SHARING` | condicional | aluno não pode ingressar em turma |
| `ACADEMIC_RESEARCH` | opcional | **nenhum** |

`ACADEMIC_RESEARCH` precisa ser recusável sem qualquer prejuízo ao serviço. Se o produto
degradar diante da recusa, não há consentimento livre e os dados não são utilizáveis em
publicação.

### 5.5 O portão de acesso

Ponto único de decisão, exposto pela API do módulo:

```java
public interface AccountAccessPolicy {
    boolean canProcessLearningData(UUID accountId);
    boolean canShareWithInstitution(UUID accountId);
    boolean canUseForResearch(UUID accountId);
}
```

`planning` e `learningrecord` consultam esta política na entrada de seus casos de uso.
A verificação de status não deve ser espalhada pelos serviços: quando a regra jurídica
mudar, o alvo da alteração precisa ser um arquivo, não trinta.

### 5.6 Transição de maioridade

**Decisão:** não bloquear no aniversário de 18 anos. Solicitar reafirmação do próprio
titular na sessão seguinte e conceder 30 dias de carência. Esgotado o prazo sem
reafirmação, a conta transita para `SUSPENDED`.

Racional: o consentimento do responsável foi validamente obtido e não se torna nulo no
instante da maioridade; ao mesmo tempo, mantê-lo indefinidamente é indefensável, porque
o outorgante deixou de ser a pessoa adequada. A carência equilibra defensabilidade e
experiência.

**Não exige nenhuma coluna adicional.** A condição é derivável:

> Conta precisa de reafirmação se `dateOfBirth + 18 anos <= hoje` e não existe
> `ConsentRecord` de finalidade essencial com `grantedBy = SELF` concedido em data
> igual ou posterior ao 18º aniversário.

Contas criadas já na maioridade satisfazem a condição trivialmente. Uma rotina agendada
diária identifica as contas em carência (notificação) e as vencidas (suspensão).

### 5.7 Verificação de idade

A data de nascimento é autodeclarada e não verificável. Isto é a prática padrão e é
compatível com a LGPD, que exige esforços razoáveis e não prova documental. O método de
coleta é registrado em `evidence` para permitir demonstrar o esforço empreendido.

O campo `evidence` (IP, *user agent*, método de verificação) é dado pessoal coletado sob
hipótese de cumprimento de obrigação legal, e não de consentimento. Exige política de
retenção própria, ainda a definir.

### 5.8 Senhas

Argon2id, via `Argon2PasswordEncoder` do Spring Security. BCrypt seria aceitável;
Argon2id é superior e o custo de escolhê-lo agora é nulo.

---

## 6. Contexto de Currículo

Módulo da camada base: não depende de nenhum outro e não possui chave estrangeira para
fora do próprio contexto.

### 6.1 Agregados

| Agregado | Conteúdo | Justificativa |
|---|---|---|
| `Subject` (raiz) | código, nome | catálogo global curado |
| `Topic` (raiz) | `subjectId`, nome, posição curricular | raiz própria, e não entidade interna de `Subject`: é referenciado por identificador a partir de três outros contextos e é a unidade primária de planejamento; carregar a disciplina inteira a cada leitura seria erro de desenho |
| `TopicPrerequisite` (raiz) | aresta dirigida, tipo, procedência | o grafo é consultado como conjunto, não a partir de um tópico específico |

`effortTier` é faixa ordinal obrigatória (`SHORT`, `STANDARD`, `LONG`, `EXTENDED`), **não
minutos**. O mapeamento para minutos é configuração, calibrável contra dado observado, e o
ajuste por aluno é derivado na montagem do *snapshot* sem ser persistido. Ver ADR 0012.
Curar um número em minutos repetiria o erro rejeitado no ADR 0006 quanto a peso contínuo:
não há origem defensável para o valor.

`position` é a ordenação curricular dentro da disciplina. É a origem para semeadura de
arestas `TEXTBOOK_ORDER` e tem restrição postergável, para permitir reordenação em
transação única.

### 6.2 O grafo de pré-requisitos

Aresta dirigida: o tópico pré-requisito precede o dependente. Grafo **único e global**;
arestas cruzam disciplinas porque pré-requisitos reais cruzam (trigonometria precede
cinemática).

**Tipo da aresta** — mapeia nos mecanismos que o Core já possui:

| `strength` | Mecanismo no Core |
|---|---|
| `HARD` | restrição; violação invalida a solução e aciona os operadores de reparo |
| `SOFT` | penalidade na função de aptidão; violação permitida com custo |

**Procedência** — `CURATED`, `TEXTBOOK_ORDER`, `DERIVED`, com `source_reference`. Entra no
*snapshot* enviado ao Core, o que viabiliza o experimento de ablação sem instrumentação
adicional.

`TEACHER` não existe aqui. Aresta definida por professor exige recorte por turma, o que
faria `curriculum` referenciar `educational` e violaria a regra R3. Quando existir, será
tabela própria em `educational`, fundida ao grafo global pela orquestração. Depende de P2.

### 6.3 Invariantes

1. A união das arestas é acíclica.
2. Não existe aresta de um tópico para ele mesmo.
3. Não existe aresta duplicada para o mesmo par ordenado.
4. Todo tópico pertence a exatamente uma disciplina.
5. `position` é única dentro da disciplina, com verificação postergada até o fim da
   transação.

A invariante 1 é garantida por *trigger* com CTE recursiva. A *trigger* adquire
`pg_advisory_xact_lock` antes de validar: sob isolamento *read committed*, duas inserções
concorrentes não se enxergam, cada aresta passa isoladamente e juntas fecham o ciclo.
Escrita de aresta vem de curadoria e é rara, de modo que serializar não tem custo relevante.

Diferentemente de consentimento e matrícula, arestas **não** são *append-only*. São dado
curado e corrigível. `createdBy` e `createdAt` preservam a auditoria.

### 6.4 Curadoria

`Topic.code` é chave natural estável, única dentro da disciplina. Existe porque o catálogo
curado vive em CSV versionado, onde referência por UUID é inviável para um humano e
referência por posição quebraria todas as arestas a cada reordenação.

Autoria em planilha, fonte da verdade em git, aplicação por importador de linha de comando
com validação. Sem tela administrativa. O argumento não é custo: o grafo curado e as faixas
de esforço são entrada do experimento de ablação, e curadoria feita direto no banco não
produz histórico nem permite atribuir um resultado a um estado do catálogo. Ver ADR 0014.

`catalog_import` registra cada aplicação com o *commit* de origem, e
`plan_generation_request` referencia a importação vigente.

### 6.5 Estratégia de curadoria

Curadoria é o custo real deste contexto, e ele é humano. Três medidas o reduzem em ordem
de magnitude:

1. **Curar arestas, não tópicos.** A maioria dos pares é independente. Grafo esparso, com
   cerca de trinta arestas por disciplina, é o estado normal.
2. **Semear a partir da ordenação existente.** Sumário de livro-texto é ordenação
   topológica válida. Uma operação em lote cria arestas `SOFT` de procedência
   `TEXTBOOK_ORDER` entre tópicos consecutivos; a curadoria trata as exceções.
3. **Registrar procedência sempre.** É o que torna a ablação possível.

Antes de escalar: curar uma única disciplina do piloto de medicina e verificar se o Core
consome o DAG corretamente e se `HARD`/`SOFT` produz planos diferentes. Se não produzir, a
distinção não se paga.

---

## 7. Contexto educacional

### 7.1 Agregados

| Agregado | Conteúdo | Justificativa |
|---|---|---|
| `Teacher` (raiz) | `accountId`, nome de exibição, instituição como texto livre | não existe entidade `Institution` nesta fase |
| `Classroom` (raiz) | `teacherId`, nome, conjunto de `subjectId`, status | — |
| `Invite` (raiz) | `classroomId`, código, expiração, limite de usos, revogação | o resgate é feito por aluno que ainda não tem acesso à turma, e a busca é por código |
| `Enrollment` (raiz) | `classroomId`, `accountId`, `enrolledAt`, `endedAt` | toda verificação de autorização consulta esta tabela; carregar `Classroom` inteiro a cada checagem seria erro de desenho |

O conjunto de disciplinas de `Classroom` não define o que o professor enxerga. Ele
declara quais disciplinas carregam prazo institucional, o que é entrada para o Core.

### 7.2 Ciclo de vida do convite

```
criado ──► ativo ──┬──► esgotado   (usos atingiram o limite)
                   ├──► expirado   (expiresAt no passado)
                   └──► revogado   (ação do professor)
```

Código de 10 caracteres em alfabeto Crockford base32, sem `I`, `L`, `O` e `U`, que se
confundem na leitura. Aproximadamente 50 bits de entropia. Expiração obrigatória, padrão
de 14 dias. Limite de usos opcional.

O código é armazenado em texto claro, e não em *hash*. Trade-off consciente: o *hash*
impediria o professor de reexibir o código após a criação, o que é requisito de uso real.
A compensação é o código ser longo, expirável, revogável e de baixo valor isolado —
resgatar um convite não concede acesso a nada, apenas coloca o aluno em uma turma.

**Isto exige limitação de taxa nas tentativas de resgate, por conta e por endereço de
origem.** Sem ela, a entropia do código não protege nada.

### 7.3 Invariantes

1. `Invite` só é resgatável se ativo, não expirado, dentro do limite de usos, e com a
   turma em `OPEN`.
2. O resgate exige conta `ACTIVE` **e** consentimento vigente de `INSTITUTION_SHARING`.
3. No máximo uma matrícula ativa por par (conta, turma).
4. Professor não pode se matricular na própria turma.
5. `Enrollment` nunca é excluída. Encerrar é gravar `endedAt`.
6. Arquivar turma encerra todas as matrículas ativas e revoga os convites pendentes.

As invariantes 1, 3, 5 e 6 são garantidas no banco (restrições, índice parcial, *trigger*).
A invariante 4 exige junção entre agregados e é verificada na aplicação; seu modo de falha
não corrompe registro legal, diferentemente do consentimento.

### 7.4 Autorização derivada

```java
public interface TeacherAccessPolicy {
    boolean canViewStudent(UUID teacherAccountId, UUID studentAccountId);
    VisibilityScope scopeFor(UUID teacherAccountId, UUID studentAccountId);
}
```

`VisibilityScope` é `ALL` ou `SUBJECTS(Set<UUID>)`. Hoje retorna sempre `ALL`.

`canViewStudent` é verdadeiro quando existe matrícula ativa do aluno em turma de
titularidade do professor **e** o consentimento de `INSTITUTION_SHARING` do aluno está
vigente no momento da consulta.

Consequência deliberada: se o aluno revogar o compartilhamento, o acesso do professor
cessa imediatamente, sem propagação de evento. Reconsentir restaura o acesso. A
alternativa (evento de revogação encerrando matrículas) foi descartada no ADR 0005.

### 7.5 Transparência no aceite do convite

O aluno precisa ver, em tela explícita no momento do aceite, exatamente quais dados o
professor passará a acessar. Isto não é cortesia de interface: é o que sustenta
`INSTITUTION_SHARING` como consentimento informado, dado que o escopo adotado é integral.

---

## 8. Registro de aprendizagem

Lado da **evidência** na interação com o Core.

### 8.1 Agregado

`StudySession` (raiz única): conta, tópico, tipo (`STUDY` ou `REVISION`), origem
(`FROM_PLAN` ou `SELF_DIRECTED`), início, fim, duração planejada, duração efetiva,
avaliação de recuperação.

`durationSource` distingue `MEASURED` de `SELF_REPORTED`. Cronômetro é o caminho principal
e o registro retroativo é exceção marcada: duração é a evidência mais básica deste contexto e
as duas origens não são igualmente confiáveis, então precisam ser distinguíveis no dado.

Sessão fora do plano é **estruturalmente idêntica** a sessão planejada. A diferença está em
`source` e na presença de `plannedSessionId`, não na forma do registro.

### 8.2 Limite científico do que este contexto captura

Sem exercícios, o único sinal de aprendizagem é a avaliação de recuperação em quatro níveis
informada pelo próprio aluno. Isso mede **adesão e percepção**, não retenção. Ver ADR 0008
e a decisão pendente D4. É a limitação mais séria do escopo atual e ela é científica, não
técnica.

### 8.3 Invariantes

1. No máximo uma sessão `IN_PROGRESS` por conta.
2. Sessão fechada é imutável. Correção é registro novo.
3. Sessão nunca é excluída.
4. `recallRating` só existe em sessão `COMPLETED`.
5. `source = FROM_PLAN` se e somente se houver `plannedSessionId`.

A invariante 1 protege o dado mais básico do contexto: permitir duas sessões simultâneas
corromperia a evidência de duração.

### 8.4 `plannedSessionId` sem chave estrangeira

Nem `planning` nem `learningrecord` podem depender um do outro (R2). Manter a evidência
independente do planejamento é o que permite que sessão planejada e sessão espontânea
tenham a mesma estrutura. O custo aceito é a possibilidade de referência órfã, detectável
por rotina de consistência. As alternativas eram criar dependência entre os módulos em
alguma das duas direções, ou duplicar o vínculo.

---

## 9. Planejamento

Lado da **saída** na interação com o Core.

### 9.1 Agregados

| Agregado | Papel |
|---|---|
| `StudyAvailability` | janelas semanais recorrentes, com validade temporal |
| `StudyGoal` | disciplina, prazo, prioridade |
| `PlanGenerationRequest` | o job; guarda o *snapshot* e os parâmetros da execução |
| `StudyPlan` | saída imutável, com `PlannedSession` como entidades internas |

Disponibilidade tem janela de validade (`effectiveFrom`, `effectiveUntil`) para que uma
mudança de rotina não destrua a disponibilidade contra a qual um plano passado foi gerado.

### 9.2 Job de geração

Estados: `PENDING`, `RUNNING`, `READY`, `FAILED`, `CANCELLED`.

A própria tabela é a fila, consumida com `SELECT ... FOR UPDATE SKIP LOCKED`. Um *broker*
seria infraestrutura adicional para operar e depurar sem problema que o justifique na escala
do piloto.

Índice parcial garante no máximo um job não terminal por aluno. Sem ele, um aluno impaciente
enfileira dezenas de otimizações, cada uma consumindo CPU por minutos.

### 9.3 Reprodutibilidade

O job persiste quatro campos, não apenas o *snapshot*: `snapshot`, `coreVersion`,
`algorithmParams` e `randomSeed`. O AG é estocástico, então mesmo *snapshot* e mesmos
parâmetros produzem plano diferente sem a semente fixada. Sem os quatro, um plano não é
reproduzível. Ver ADR 0007.

O *snapshot* é JSONB com a carga exata enviada, deliberadamente não normalizado.

### 9.4 Replanejamento

Replanejar cria plano novo. O anterior transita para `SUPERSEDED` e aponta para o sucessor.
`PlannedSession` é imutável por *trigger*; `StudyPlan` só admite alteração dos campos de
substituição.

Três razões: editar destrói a correspondência entre plano e *snapshot* que o gerou; sessões
executadas referenciam sessões planejadas, e mutá-las corromperia a evidência; e a cadeia de
planos substituídos é dado experimental gratuito sobre com que frequência e em que ponto do
horizonte o aluno replaneja.

### 9.5 Orquestração

`planning.orchestration` é quem lê `curriculum`, `learningrecord` e `planning`, monta o
*snapshot*, chama o `coreclient` e grava o plano. É o componente que quebra o ciclo previsto
na regra R2 e a única parte do sistema que conhece mais de um contexto.

---

## 10. Direitos do titular

Não é contexto delimitado: é coordenador, o **segundo** componente do sistema que conhece
mais de um módulo, ao lado de `planning.orchestration`.

Cada módulo expõe no próprio `api` as operações de eliminação e exportação dos seus dados.
O coordenador apenas sequencia as chamadas dentro de uma transação, e nunca acessa tabela
de outro módulo.

### 10.1 O que sobrevive à eliminação

| Registro | Destino |
|---|---|
| `consent_record` | sobrevive, com `evidence` e `guardianId` anulados |
| `enrollment` encerrada | sobrevive, apontando para a conta anonimizada |
| `account` | casca esvaziada, status `ANONYMIZED` |
| todo o resto | apagado |

### 10.2 Três conclusões que moldam o desenho

**Histórico de estudo não é anonimizável no lugar.** Sequência longitudinal de tópicos com
horários é impressão digital comportamental; cruzada com a lista de uma turma, reidentifica.
Anonimização que não resiste a cruzamento é pseudonimização, que continua sendo dado
pessoal. Logo, apagar.

**O registro de consentimento sobrevive porque é a prova da base legal do tratamento já
ocorrido.** Sobrevive o fato — finalidade, versão do termo, outorgante, datas. Não sobrevive
`evidence`, que guarda IP e agente de usuário e não é necessário para provar o fato.

**A anonimização acontece na exportação para o conjunto de pesquisa, não na eliminação.**
Anonimizar só quando alguém pede exclusão significa enfrentar o caso difícil sob prazo. Com
exportação anonimizada e desvinculada feita na coleta, o conjunto de pesquisa já está fora do
escopo quando o pedido chega. **Consequência que precisa constar do termo:** a contribuição
já anonimizada não é reversível.

### 10.3 Exceção ao append-only

As *triggers* não foram removidas. Passam a consultar um sinalizador transacional
(`sinapse.erasure`), ativado somente pelo serviço de eliminação. Append-only continua sendo
o padrão, e a eliminação vira exceção explícita em vez de buraco permanente.

`consent_record` mantém a proibição de exclusão em qualquer circunstância.

### 10.4 Ciclo

Pedido suspende a conta e revoga sessões imediatamente. Eliminação efetiva em 7 dias,
reversível nesse intervalo, executada em **uma** transação. Eliminação parcial é pior que
nenhuma.

---

## 11. Esquema físico inicial

```
V1__identity.sql
V2__curriculum.sql
V3__educational.sql
V4__learning_record.sql
V5__planning.sql
V6__data_rights.sql
```

`topic.effort_tier` e `study_session.duration_source` foram acrescentados às migrações V2 e
V4 antes de qualquer aplicação, por decisão do ADR 0012.

V6 substitui três funções de *trigger* criadas em migrações anteriores. Ler V6 antes de
alterar qualquer uma delas.

A ordem importa: `curriculum` precede `educational` (por `classroom_subject`),
`learningrecord` e `planning` (por `topic_id`).

Dois pontos merecem destaque:

- O índice parcial `ux_active_consent` transforma "no máximo um consentimento vigente por
  finalidade" em garantia do banco, não em disciplina de código.
- A *trigger* de imutabilidade de `consent_record` é defesa contra bug próprio, não contra
  atacante. Registro legal não deve ser alterável por acidente de mapeamento ORM.

---

## 12. Definição de concluído

Testes que precisam existir e passar:

- Adulto ativa imediatamente após verificação de e-mail.
- Menor não ativa sem consentimento de responsável.
- `dateOfBirth` é imutável após ativação e mutável antes.
- Revogação de finalidade essencial suspende a conta na mesma transação.
- Revogação de `ACADEMIC_RESEARCH` **não** suspende e não altera nenhuma capacidade.
- O índice parcial rejeita a inserção de segundo consentimento vigente.
- A *trigger* rejeita `UPDATE` em coluna que não seja `revoked_at`.
- `canProcessLearningData` retorna falso em todo estado diferente de `ACTIVE`.
- Conta que completou 18 anos permanece funcional durante a carência de 30 dias.
- Conta com carência vencida e sem reafirmação transita para `SUSPENDED`.
- Reafirmação dentro da carência gera novo `ConsentRecord` com `grantedBy = SELF`,
  preservando o registro anterior.

### Contexto de Currículo

- Aresta que fecharia ciclo é rejeitada pela *trigger*.
- Aresta de um tópico para ele mesmo é rejeitada.
- Aresta duplicada no mesmo par ordenado é rejeitada.
- Mudar `strength` de `SOFT` para `HARD` também passa pela validação de ciclo.
- Duas inserções concorrentes que juntas fechariam ciclo: uma falha.
- Reordenar `position` de todos os tópicos de uma disciplina em uma transação funciona,
  graças à restrição postergável.
- Semeadura `TEXTBOOK_ORDER` gera arestas `SOFT` entre tópicos consecutivos e é idempotente.

### Registro de aprendizagem

- Índice parcial rejeita segunda sessão `IN_PROGRESS` para a mesma conta.
- *Trigger* rejeita `UPDATE` em sessão fechada e `DELETE` em qualquer sessão.
- `recallRating` em sessão não `COMPLETED` é rejeitado.
- `source = FROM_PLAN` sem `plannedSessionId` é rejeitado, e vice-versa.
- Sessão espontânea e sessão do plano produzem o mesmo registro, exceto por `source`.

### Planejamento

- Índice parcial rejeita segundo job não terminal para a mesma conta.
- Índice parcial rejeita segundo plano `ACTIVE` para a mesma conta.
- *Trigger* rejeita qualquer `UPDATE` ou `DELETE` em `planned_session`.
- *Trigger* rejeita alteração de `horizon`, `fitness` ou vínculo em `study_plan`.
- Dois consumidores concorrentes não reivindicam o mesmo job (`SKIP LOCKED`).
- Reexecutar um job com o mesmo *snapshot*, parâmetros e semente produz plano idêntico.
- Replanejar move o plano anterior para `SUPERSEDED` e preserva suas sessões planejadas.

### Direitos do titular

- Eliminação remove todos os registros listados, verificado por consulta direta após o commit.
- `consent_record` sobrevive com `evidence` nulo e demais campos inalterados.
- `enrollment` encerrada sobrevive.
- O e-mail de conta eliminada fica livre para novo cadastro.
- `DELETE` em `study_session` fora do serviço de eliminação continua falhando.
- Falha no meio da eliminação reverte tudo, sem estado parcial.
- Cancelamento dentro da janela restaura a conta `ACTIVE` com os dados intactos.
- Teste de cobertura falha quando existe módulo sem operação de eliminação registrada.

### Contexto educacional

- Convite expirado, revogado ou esgotado é rejeitado no resgate.
- Convite de turma arquivada é rejeitado.
- Resgate por conta sem `INSTITUTION_SHARING` vigente é rejeitado.
- Índice parcial rejeita segunda matrícula ativa no mesmo par (conta, turma).
- Professor não consegue se matricular na própria turma.
- *Trigger* rejeita exclusão de `enrollment`.
- Arquivar turma encerra matrículas ativas e revoga convites pendentes na mesma transação.
- `canViewStudent` é falso após o fim da matrícula.
- `canViewStudent` é falso após revogação de `INSTITUTION_SHARING`, sem alteração na
  matrícula, e volta a ser verdadeiro após reconsentimento.
- Limitação de taxa bloqueia tentativas repetidas de resgate com código inválido.

---

## 13. Decisões pendentes

### Jurídicas

| # | Questão | Impacto |
|---|---|---|
| J1 | Limiar etário que dispara exigência de responsável. O Art. 14 §1 da LGPD trata de **crianças** (menores de 12 anos); o tratamento de dados de adolescentes é ponto controvertido. | Parâmetro de configuração. Não altera o modelo. |
| J2 | Necessidade de aprovação de CEP via Plataforma Brasil para uso dos dados do piloto em tese e publicações. Com adultos o processo é mais leve; com menores exige TCLE do responsável e termo de assentimento do aluno. | **Bloqueante para coleta com finalidade de pesquisa.** Dados coletados antes da aprovação normalmente não são aproveitáveis. |
| J3 | Política de retenção do campo `evidence` e dos *snapshots* de geração. | Rotina de expurgo. |

### De produto

| # | Questão | Impacto |
|---|---|---|
| ~~P1~~ | ~~Escopo do que o professor enxerga.~~ **Resolvida:** escopo integral, exposto como política reversível. | ADR 0005 |
| P2 | Como uma conta ganha o papel `TEACHER`. Auto-cadastro não pode concedê-lo, sob pena de qualquer pessoa criar turma e emitir convites. Recomendação para o piloto: concessão manual por operação administrativa auditada, evitando construir verificação institucional sem requisito. | Fluxo de cadastro de professor. |
| D4 | **Medida objetiva de retenção.** A v1 mede adesão e percepção por autorrelato, que não sustenta a afirmação de que o AG melhora retenção. Definir o instrumento antes de submeter ao comitê de ética, não depois de começar a coletar. | **Bloqueante para a tese.** Não bloqueia o código: o modelo atual é subconjunto do modelo com medida objetiva. |
| ~~D5~~ | ~~Eliminação sob Art. 18.~~ **Resolvida** no ADR 0011, sujeita a confirmação jurídica dos três pontos ali listados. | ADR 0011 |
| P3 | Instrumento de feedback do professor sobre a qualidade dos planos. Avaliação de plano por especialista é instrumento de pesquisa: sua estrutura precisa seguir o protocolo experimental da tese. Recomendação: manter fora da v1 e desenhar junto com o protocolo de avaliação, sob risco de os dados coletados não serem aproveitáveis. | Protocolo experimental e escopo da v1. |

---

## 14. Estratégia de pilotos

Os dois pilotos validam coisas diferentes e não devem ser confundidos.

**Piloto de medicina (adultos) — validação técnica e científica.**
O motor produz planos coerentes? Os alunos aderem ao plano? O AG melhora a retenção
medida frente a uma linha de base? É a origem dos dados da tese.

**Piloto Colégio Donaduzzi — validação de mercado e adequação pedagógica.**
Roda depois, com o ramo de consentimento de responsável já implementado.

**Risco explícito:** estudantes de medicina não constituem o mercado-alvo. Padrão de
estudo, volume e ciclos de avaliação diferem substancialmente de ensino médio e
pré-vestibular. Usar o piloto de medicina como validação de produto arrisca otimizar a
v1 para uma população que não é a compradora.

**Consequência para a v1:** implementar apenas o ramo `SELF`. O cadastro de menor de 18
anos é bloqueado com mensagem clara. A estrutura (portão, `ConsentRecord`, data de
nascimento) entra completa; o ramo `GUARDIAN` entra na versão seguinte. Retrofitar o
portão depois é caro; deixar um ramo do fluxo sem implementação é barato.

---

## 15. Pendências para fechar o backend

### Resolvidas

Contrato de erro, versionamento de API, limitação de taxa, fuso horário, observabilidade
(ADR 0009), autenticação (ADR 0010) e direitos do titular (ADR 0011). Duas omissões do
esquema foram corrigidas na V1 antes de qualquer aplicação: `Account.timeZone` e a tabela
`account_token`.

### Falta desenhar

**Contrato compartilhado com o Sinapse Core.** Artefato versionado a ser criado no
repositório do Core, com sequência de prompts própria. É o último item de código que não está
desenhado, e ele é trabalho do outro lado.

### Falta decidir (humano)

J1, J2, J3, D4, D5, P2 e P3, listados na seção 12.

D4 é o de maior consequência, porque define se os dados do piloto sustentam a tese.
J2 é o mais urgente em prazo, porque coleta feita antes do parecer do comitê normalmente não
é aproveitável.
