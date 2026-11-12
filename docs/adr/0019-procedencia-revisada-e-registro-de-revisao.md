# ADR 0019 — Procedência revisada e registro de revisão por disciplina

**Data:** 12 de novembro de 2026
**Status:** aceita
**Complementa:** ADR 0006, ADR 0014

## Contexto

A ADR 0006 grava em cada aresta uma procedência (`CURATED`, `TEXTBOOK_ORDER`, `DERIVED`) para
que o experimento de ablação diga se o otimizador melhora com arestas curadas. A ADR 0014
define a autoria em planilha e a carga por importador.

Nenhuma das duas separou **quem escreveu** de **quem conferiu**. `CURATED` cobria as duas coisas.
Medido em `catalog/MED-ANAT/` antes desta mudança: 35 arestas, 24 `HARD` e 11 `SOFT`, todas
`CURATED`; 13 citando capítulo de um livro-texto real, 17 com uma frase de justificativa no
lugar da fonte ("uma articulação exige seus ossos") e 5 sem `source_reference` — e o
`catalog/README.md` dizendo que ninguém que ensina a disciplina revisou o arquivo. Quem lê
`CURATED` com citação de capítulo conclui, com razão, que um especialista revisou. Com uma
disciplina de exemplo o aviso do README basta; com duas ou três disciplinas entrando num
experimento, a diferença entre grafo curado e grafo proposto termina numa tese.

## Decisão

Os itens 1 a 3 são do dono do projeto, no pedido da tarefa SP-17. O item 4 — a granularidade
do registro — o pedido deixou explicitamente para a implementação decidir e justificar; foi
decidido aqui, e muda com uma nova ADR se o dono discordar.

1. **Nova procedência `PROPOSED`**: aresta afirmada por uma pessoa e ainda não revisada por
   alguém que ensina a disciplina. Toda aresta escrita à mão começa assim.
2. **`CURATED` passa a significar revisada.** O importador recusa `CURATED` sem
   `source_reference` e recusa `CURATED` numa disciplina sem revisão registrada.
3. **As 35 arestas do exemplo foram rebaixadas para `PROPOSED`.** Nunca foram revisadas.
4. **O registro de revisão é por disciplina**, num arquivo opcional
   `catalog/<disciplina>/reviews.csv` com as colunas `reviewer` e `reviewed_on`, uma linha por
   revisão. A revisão que habilita `CURATED` numa aresta é a da disciplina em cujo
   `prerequisites.csv` a aresta está escrita: é o arquivo que o revisor assinou.

A identidade de quem revisou fica nos arquivos e no git, nunca no banco. O banco guarda só
contagens: cada linha de `catalog_import` registra em `notes` quantas arestas de cada procedência
os arquivos aplicados tinham.

## Alternativas consideradas

**Registro por aresta** (colunas `reviewed_by` e `reviewed_on` em cada linha de
`prerequisites.csv`). Mais preciso no papel, e por isso descartado: numa planilha, duas colunas
repetidas em cada linha são preenchidas arrastando a primeira célula para baixo, e o resultado
parece mais preciso do que a revisão que registra. Dobra o trabalho do curador, e a afirmação que
um resultado pode fazer é por disciplina ("o grafo de X foi revisado por quem ensina X em tal
data"), não por aresta. E não fecha a brecha que pareceria fechar: copiar uma data para uma
aresta nova é tão fácil quanto escrever `CURATED` nela.

**Registro por importação**, em `catalog_import`. Descartado: revisão é sobre conteúdo, não sobre
a aplicação de um arquivo — a mesma revisão vale para várias importações, e uma importação pode
não ter revisão nenhuma. E poria a identidade de uma pessoa no banco.

**Um status de revisão separado da procedência**, mantendo `CURATED` em arestas não revisadas.
Descartado: é exatamente a confusão que esta ADR existe para eliminar. O rótulo que diz
"curado" tem de significar revisado.

## Consequências

**Esquema.** A migração V10 só alarga a *check constraint* de `topic_prerequisite.provenance`
para aceitar `PROPOSED`. Nenhuma linha é reescrita por migração: procedência é dado curado, e o
único caminho que muda dado curado é o importador, que registra a mudança em `catalog_import`
contra uma revisão dos arquivos. Aplicar os arquivos novos registra as 35 arestas do exemplo
como 35 correções numa nova importação. As importações e os jobs anteriores não mudam:
`plan_generation_request.catalog_import_id` continua apontando para a importação que estava em
vigor, e os arquivos daquela revisão diziam `CURATED`, que era o que afirmavam na época.

**Contrato com o Core.** O contrato 1.0 não tem `PROPOSED`. O *snapshot* envia uma aresta
`PROPOSED` como `CURATED`, cujo significado documentado nos dois repositórios é "afirmada por um
curador humano" — origem, não revisão. O pedido enviado ao Core fica byte a byte igual ao de
antes, e as instâncias compartilhadas com o Core não mudam. A distinção se perde no *snapshot*
guardado com o job e na condição `curated` da ablação do Core, e sobrevive deste lado pelo
`catalog_import_id` do job. Levá-la ao Core é mudança de contrato, que pertence ao dono e ao
Core; está aberta como decisão SP-17-W e pedido SP-H5 (ver `docs/ai/STATE.md`, K35, e
`docs/ai/HANDOFF.md`).

**Limitação aceita: fonte não verificada.** A ferramenta exige que `source_reference` esteja
preenchida em `CURATED`; não consegue distinguir uma citação de uma frase de justificativa. Dizer
se a fonte sustenta a aresta é trabalho do revisor.

**Limitação aceita: edição depois da revisão.** Uma aresta acrescentada ou alterada depois da revisão deveria voltar a
`PROPOSED` até a próxima revisão. A ferramenta não verifica isso: compararia o arquivo com o
estado dele no git na data da revisão. Fica como compromisso de quem edita, escrito nas
instruções do modelo de autoria.

**Quando rever.** Se a revisão de uma disciplina passar a ser dividida entre várias pessoas, ou
feita por partes, o registro por disciplina deixa de dizer quem conferiu o quê, e a
granularidade por aresta passa a se pagar.
