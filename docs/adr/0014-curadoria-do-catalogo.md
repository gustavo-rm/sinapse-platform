# ADR 0014 — Curadoria do catálogo como artefato versionado

**Data:** 04 de setembro de 2026
**Status:** aceita

## Contexto

O contexto de Currículo foi desenhado sem definir como alguém popula o catálogo na prática.
São dois eixos por tópico: as arestas de pré-requisito (ADR 0006) e a faixa de esforço
(ADR 0012). Sem ferramenta, ambos viram `INSERT` manual, o que não escala nem para uma
disciplina.

As alternativas eram tela administrativa na plataforma, importação a partir de arquivos
versionados, ou migrações de semeadura escritas à mão.

## Decisão

**Autoria em planilha, fonte da verdade em arquivos CSV versionados em git, aplicação por
importador de linha de comando com validação.**

Sem tela administrativa. Sem endpoint HTTP de curadoria na v1.

Dois arquivos por disciplina:

```
catalog/<subject_code>/topics.csv
    code, name, position, effort_tier

catalog/<subject_code>/prerequisites.csv
    prerequisite, dependent, strength, provenance, source_reference
```

`prerequisite` e `dependent` usam a chave natural `subject_code:topic_code`, permitindo
arestas entre disciplinas.

O importador é declarativo e idempotente: o arquivo descreve o estado desejado. Possui modo
de simulação que exibe o diff antes de aplicar.

Cada aplicação registra uma linha em `catalog_import`, com o *commit* git de origem.
`plan_generation_request` referencia a importação vigente.

## Justificativa

**O argumento decisivo não é custo, é procedência.** O grafo curado e as faixas de esforço
são entrada do experimento de ablação da tese. Curadoria feita por tela que escreve direto no
banco não produz histórico, diff nem forma de afirmar qual estado do catálogo produziu qual
resultado. Em git, procedência, revisão, reversão e reprodutibilidade vêm de graça — as
mesmas propriedades que o restante do desenho persegue.

**Planilha como superfície de autoria.** Especialista de domínio trabalha em planilha; é
irreal esperar edição de YAML em git. A exportação para CSV e o *commit* podem ser feitos por
quem tem o hábito, sem que o curador aprenda ferramenta nova.

**CSV e não YAML.** Exportação direta de planilha, sem etapa de conversão que introduz erro.

**Sem tela administrativa.** Além do custo, ela dependeria de P2, que segue em aberto. A
linha de comando contorna a decisão pendente sem antecipá-la.

**Chave natural obrigatória.** Referenciar tópicos por UUID em planilha é inviável; por
posição é frágil, porque reordenar repontaria silenciosamente todas as arestas. `topic.code`
é único dentro da disciplina.

## Comportamento do importador

**Validação antes de escrever.** Aciclicidade é verificada em memória e, ao encontrar ciclo,
o importador imprime o **caminho completo** do ciclo. A *trigger* do banco levanta exceção na
primeira aresta ofensora, o que não ajuda um curador a corrigir o arquivo.

**Nunca apaga tópico.** Tópico ausente do arquivo é reportado, não removido: pode haver
sessões de estudo o referenciando, e evidência não é apagada por efeito colateral de
importação. Remoção exige sinalizador explícito e falha se houver referência.

**Arestas podem ser removidas**, por serem dado curado e corrigível.

**Semeadura por ordenação gera arquivo, não banco.** O comando de semeadura por ordem de
sumário **emite** as arestas `TEXTBOOK_ORDER` no próprio CSV, para que o arquivo permaneça o
estado desejado completo. Se semeasse direto no banco, o arquivo deixaria de descrever o
estado real.

**Aplicação em uma transação.** Importação parcial deixaria o catálogo em estado que nenhum
arquivo descreve.

## Consequências

O importador é ferramenta administrativa dentro do mesmo repositório, executada por linha de
comando contra o banco. Ele consome `curriculum.api` e não acessa tabelas diretamente.

Duas alterações de esquema, em migrações ainda não aplicadas: `topic.code` e a tabela
`catalog_import` na V2, e `plan_generation_request.catalog_import_id` na V5.

O diretório `catalog/` passa a ser conteúdo versionado do repositório. Uma alteração nele é
revisável como qualquer outra mudança.

**Limitação aceita:** não há edição concorrente. Duas pessoas curando ao mesmo tempo resolvem
conflito em git, como em código. Para uma equipe deste tamanho é adequado, e deixa de ser se
a curadoria for distribuída entre muitos professores.

**Recomendação de validação:** curar uma única disciplina do piloto de medicina, com cerca de
trinta arestas, antes de escalar. Verificar se o Core consome o DAG corretamente, se
`HARD`/`SOFT` produz planos diferentes e se as faixas de esforço alteram a alocação. Se
alguma dessas distinções não se pagar, ela sai antes de custar curadoria em escala.
