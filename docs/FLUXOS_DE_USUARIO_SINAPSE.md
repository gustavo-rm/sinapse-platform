# Fluxos de usuário — insumo para o contrato da API

**Versão:** 0.2
**Data:** 04 de setembro de 2026
**Propósito:** derivar o contrato de leitura da API a partir do que o usuário precisa fazer,
e não a partir do modelo de domínio.

## Método

Escrita segue o agregado, porque escrita tem invariante e o agregado é a unidade de
consistência. Leitura segue a tela, porque leitura não tem invariante e a tela é a unidade
de utilidade.

Este documento descreve fluxos, não telas. Ele não decide layout, e decide o mínimo de
comportamento necessário para saber quais dados a API precisa entregar juntos.

## Convenções

| Marca | Significado |
|---|---|
| **[DERIVADO]** | consequência direta do que já está desenhado; não é escolha |
| **[INFERIDO]** | suposição minha sobre o produto; confirmar ou corrigir |
| **[DECISÃO]** | exige decisão sua; não implementar antes |
| **[FECHADO]** | decidido no ADR 0012 |
| **[LACUNA]** | falta algo no desenho atual; ver seção 5 |

---

## 1. Cadastro e ativação

**Gatilho:** pessoa acessa a plataforma pela primeira vez.

1. Informa e-mail, senha, data de nascimento e fuso horário. **[DERIVADO]** Fuso é
   obrigatório; o cliente pode sugerir a partir do navegador, o servidor não infere.
2. Menor de 18 é recusado com mensagem clara. **[DERIVADO]** v1 implementa apenas o ramo
   `SELF`.
3. Aceita os termos das finalidades essenciais. Aceita ou recusa `ACADEMIC_RESEARCH`
   separadamente, sem prejuízo. **[DERIVADO]**
4. Recebe e consome token de verificação de e-mail.
5. Conta transita para `ACTIVE`.

**A tela precisa ler:** as versões vigentes dos termos, por finalidade, com o texto
completo, antes do aceite.

**[FECHADO — F1]** `INSTITUTION_SHARING` é consentido apenas no resgate do convite.
Consentir no cadastro para algo que a pessoa talvez nunca use não é informado.

---

## 2. Configuração inicial

**Gatilho:** primeira sessão após ativação.

1. Declara disponibilidade semanal.
2. Declara metas: disciplinas, prazo, prioridade.
3. **[FECHADO — L1]** O esforço por tópico vem de `topic.effortTier`, faixa ordinal curada.
   O aluno não informa nada.

**A tela precisa ler:** catálogo de disciplinas e, dentro delas, os tópicos.

**[FECHADO — F2]** Exigir disponibilidade e ao menos uma meta. Nada além disso. Sem esses
dois o AG não tem o que otimizar, e um plano padrão seria ficção apresentada como
recomendação.

**[INFERIDO]** O aluno escolhe disciplinas inteiras como meta, não tópicos individuais. É o
que `study_goal` suporta hoje: ele referencia `subject_id`, não `topic_id`.

---

## 3. Ingresso em turma por convite

**Gatilho:** aluno recebe um código do professor.

1. Informa o código.
2. Vê a **prévia**: nome da turma, nome do professor e exatamente o que o professor
   passará a ver. **[DERIVADO]** É requisito, não cortesia: sustenta
   `INSTITUTION_SHARING` como consentimento informado, dado que o escopo é integral.
3. Concede `INSTITUTION_SHARING` e aceita.
4. Matrícula criada.

**A tela precisa ler:** a prévia do convite, em uma única chamada não autenticada quanto à
turma, mas autenticada quanto à conta.

**[DERIVADO]** Resgate é rota limitada por taxa, por conta e por origem.

---

## 4. Geração do plano

**Gatilho:** aluno pede um plano, ou pede um novo.

1. Envia o pedido. Recebe imediatamente o identificador do job. **[DERIVADO]** Nunca
   bloqueia.
2. Acompanha o estado por consulta periódica. **[FECHADO]** Sem notificação por evento na
   v1.
3. Job conclui. Plano fica disponível.
4. Segundo pedido enquanto há job ativo é recusado com mensagem clara, não com violação de
   restrição. **[DERIVADO]**

**A tela precisa ler:** estado do job com indício de progresso.
**[FECHADO — F4]** Campo de progresso opcional na API. Nulo significa indeterminado, e o
cliente mostra tempo decorrido. Não bloqueia por depender do repositório do Core.

**[FECHADO — F3]** Horizonte fixo de 4 semanas, configurável. O prazo da meta entra como
restrição de priorização, não como horizonte. Plano de um ano é caro e majoritariamente
errado, porque disponibilidade e conhecimento mudam muito antes disso.

---

## 5. Execução de sessão

**Gatilho:** chegou a hora de uma sessão planejada, ou o aluno quer estudar algo por conta.

1. Inicia a sessão. Estado `IN_PROGRESS`.
2. Encerra, informando a avaliação de recuperação em quatro níveis. **[DERIVADO]** A
   avaliação só existe em sessão `COMPLETED`.
3. Ou abandona, sem avaliação.

**[DERIVADO]** No máximo uma sessão em progresso por conta. A tela precisa saber disso e
oferecer retomar a sessão aberta em vez de iniciar outra.

**[FECHADO — F5]** Cronômetro como caminho principal, registro retroativo como exceção
marcada em `durationSource` (`MEASURED` ou `SELF_REPORTED`). A v1 já não tem medida objetiva
de retenção; aceitar duração autodeclarada sem distinguir deixaria o piloto sem nenhuma
medida confiável.

**A tela precisa ler:** a agenda do dia, com nome do tópico, nome da disciplina, duração
planejada, tipo (estudo ou revisão) e se já foi executada. Isso atravessa `planning`,
`curriculum` e `learningrecord`. **É o principal motivo pelo qual o contrato de leitura
não pode ser derivado dos agregados.**

---

## 6. Replanejamento

**Gatilho:** **[FECHADO — F6]** manual, disparado pelo aluno.

A aderência é calculada de qualquer forma, porque o painel do professor depende dela. Ela é
exibida ao aluno, e a decisão sobre sugestão automática fica para depois, com dado
observado.

**[DERIVADO]** Replanejar cria plano novo; o anterior vira `SUPERSEDED` e suas sessões
planejadas são preservadas, porque sessões executadas as referenciam.

---

## 7. Acompanhamento do professor

**Gatilho:** professor abre a turma.

1. Lista de alunos da turma.
2. Abre um aluno e vê o histórico e o plano completos. **[DERIVADO]** Escopo integral,
   decidido no ADR 0005.
3. Acesso cessa imediatamente se o aluno revogar `INSTITUTION_SHARING`, sem propagação.
   **[DERIVADO]** A tela precisa lidar com um aluno que some da lista.

**A tela precisa ler:** por aluno, aderência no período, tempo por disciplina e trajetória
das avaliações de recuperação. Atravessa três módulos.

**[FECHADO — F7]** A lista mostra nome, aderência no período e tempo total. Três campos,
uma consulta. Sem medida objetiva de retenção, aderência é a única métrica com significado.

---

## 8. Consentimento e direitos do titular

1. Ver consentimentos vigentes e histórico.
2. Revogar. **[DERIVADO]** Revogar finalidade essencial suspende a conta na mesma
   transação; a tela precisa avisar isso **antes**, de forma inequívoca.
3. Exportar os próprios dados.
4. Ver quais professores tiveram acesso e em que períodos.
5. Pedir eliminação. **[DERIVADO]** Suspende e revoga sessões imediatamente; efetiva em 7
   dias; cancelável na janela.
6. Listar e encerrar sessões ativas.

---

## 9. Autenticação

Login, logout, recuperação de senha, reafirmação de maioridade.

**[DERIVADO]** Trocar a senha revoga todas as sessões. Suspender a conta revoga todas as
sessões. A tela precisa tratar a sessão morrendo entre uma chamada e outra.

---

## 10. Modelos de leitura identificados

Cada um atravessa módulos e não corresponde a nenhum agregado:

| Modelo | Composto de | Usado em |
|---|---|---|
| `AgendaDoDia` | sessões planejadas + tópicos + disciplinas + execução | fluxo 5 |
| `PlanoResumido` | plano + contagem por disciplina + aderência | fluxos 4, 6 |
| `EstadoDoAluno` | conta + consentimentos + configuração + plano ativo + sessão aberta | tela inicial |
| `PreviaDoConvite` | convite + turma + professor + escopo de visibilidade | fluxo 3 |
| `PainelDoAluno` (visão do professor) | aderência + tempo por disciplina + trajetória | fluxo 7 |
| `ListaDaTurma` | matrículas + nome + aderência + tempo total | fluxo 7 |

Esses modelos são montados por um serviço de leitura que é o **terceiro** componente
autorizado a conhecer mais de um módulo, ao lado de `planning.orchestration` e
`datarights`.

Cada módulo continua expondo apenas o seu `api`. O serviço de leitura compõe, não consulta
tabela alheia.

---

## 11. Lacunas descobertas — todas fechadas

**L1 — estimativa de esforço por tópico.** Era a mais séria: o contrato do Core previa
"tópicos com esforço estimado" e nenhuma parte do desenho definia a origem do número.
Fechada com faixa ordinal `topic.effortTier`, mapeamento para minutos em configuração, e
ajuste por aluno derivado na montagem do *snapshot*, sem persistir. Ver ADR 0012.

**L2 — sessão cronometrada versus retroativa.** Fechada com `study_session.durationSource`.

**L3 — seleção de tópicos dentro de uma meta.** Fechada: todos os tópicos da disciplina
entram, sem exclusões na v1. O AG decide ordem e o que cabe no horizonte. Para preparação de
concurso o recorte usual é o edital inteiro, então exclusão manual é especulativa.

---

## 12. Estado das decisões

F1 a F7, L1, L2 e L3 estão fechadas no ADR 0012.

Alterações de esquema decorrentes, ambas em migrações ainda não aplicadas:
`topic.effort_tier` na V2 e `study_session.duration_source` na V4.

Próximo passo: contrato OpenAPI derivado dos fluxos e dos modelos de leitura da seção 10.
