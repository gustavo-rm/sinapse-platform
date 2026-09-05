# ADR 0012 — Decisões de produto derivadas dos fluxos de usuário

**Data:** 04 de setembro de 2026
**Status:** aceita
**Resolve:** F1 a F7, L1 e L3 de `docs/FLUXOS_DE_USUARIO_SINAPSE.md`

## Contexto

O percurso dos fluxos de usuário de ponta a ponta expôs sete decisões de produto em aberto e
uma lacuna crítica no modelo. Todas afetam o contrato da API e parte delas afeta o esquema,
por isso são fechadas antes da implementação dos módulos correspondentes.

## Decisões

### L1 — Estimativa de esforço por tópico

**Faixa ordinal em `topic.effortTier`: `SHORT`, `STANDARD`, `LONG`, `EXTENDED`.**

O mapeamento de faixa para minutos é parâmetro de configuração. O ajuste por aluno é
derivado da razão entre duração efetiva e planejada nas sessões concluídas, **calculado na
montagem do *snapshot* e não persistido**.

**Justificativa.** A proposta inicial era um valor curado em minutos. Ela repetia o erro
rejeitado no ADR 0006 quanto ao peso contínuo das arestas: não há origem defensável para o
número. Ninguém afirma com fundamento que um tópico leva 47 minutos, e a curadoria produziria
valores arbitrários e não reproduzíveis. Julgamento comparativo entre faixas é o tipo de
julgamento que humanos fazem de forma confiável.

Manter o mapeamento em configuração transfere o número para onde ele pode ser calibrado
contra dado observado, em vez de ser afirmado por antecipação.

O ajuste por aluno não é persistido porque seria dado derivado, sujeito a ficar obsoleto,
exigindo tabela e rotina de atualização sem ganho sobre calcular na montagem.

**Sem esta decisão o AG saberia o que priorizar e não quanto tempo reservar**, que é metade
do problema de alocação. A lacuna passou despercebida porque o Currículo foi desenhado
pensando em estrutura e o Planejamento em saída, e o esforço fica entre os dois sem dono
óbvio.

### F1 — `INSTITUTION_SHARING` é consentido no resgate do convite, não no cadastro

Consentir no cadastro para algo que a pessoa talvez nunca use não é consentimento informado.
No resgate ela vê a prévia da turma e sabe exatamente o que autoriza.

### F2 — Configuração obrigatória antes do primeiro plano: disponibilidade e ao menos uma meta

Sem esses dois o AG não tem o que otimizar, e um plano com valores padrão seria ficção
apresentada como recomendação. Nada além disso é exigido, para não penalizar a ativação.

### F3 — Horizonte fixo de 4 semanas, configurável. O prazo da meta é restrição, não horizonte

Preparação para concurso pode durar um ano. Gerar plano de um ano é caro e majoritariamente
errado, porque disponibilidade e estado de conhecimento mudam muito antes disso. O prazo da
meta entra no *snapshot* como pressão de priorização.

### F4 — Campo de progresso opcional na API

O cliente exibe indicador indeterminado com tempo decorrido quando o valor vier nulo. Custo
próximo de zero e não bloqueia por depender do repositório do Core.

### F5 — Cronômetro como caminho principal, registro retroativo como exceção marcada

`study_session.durationSource` distingue `MEASURED` de `SELF_REPORTED`.

A v1 já não possui medida objetiva de retenção (ADR 0008). Aceitar duração autodeclarada sem
distinguir deixaria o piloto sem nenhuma medida confiável. Distinguir permite analisar os
dois conjuntos separadamente e descartar o segundo se necessário.

### F6 — Replanejamento manual na v1

A métrica de aderência será calculada de qualquer forma, porque o painel do professor
depende dela. Exibi-la ao aluno e decidir depois, com dado observado, se a sugestão
automática se paga.

### F7 — Lista da turma com nome, aderência no período e tempo total

Três campos, uma consulta. Sem medida objetiva de retenção, aderência é a única métrica com
significado.

### L3 — Todos os tópicos da disciplina entram na meta, sem exclusões na v1

O AG decide ordem e o que cabe no horizonte. Para preparação de concurso o recorte usual é o
edital inteiro, de modo que exclusão manual é funcionalidade especulativa.

## Consequências

Duas alterações de esquema, ambas em migrações ainda não aplicadas: `topic.effort_tier` na
V2 e `study_session.duration_source` na V4.

`effortTier` acrescenta uma anotação por tópico à curadoria, que já é o gargalo do projeto.
É bem mais barata que estimar tempo, mas não é gratuita.

A calibração do mapeamento faixa/minutos é trabalho de análise a fazer com os dados do
piloto. Até lá os valores iniciais são suposição declarada, e devem ser tratados como tal.

O ajuste por aluno depende de histórico e não existe nas primeiras semanas de uso. O plano
inicial usa o valor da faixa sem ajuste.
