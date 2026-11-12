# Carga do catálogo

> **Este é o único caminho de carga do currículo.** Não há migração Flyway que insira
> disciplinas ou tópicos, não há rota HTTP que escreva no catálogo, e nenhum outro código de
> produção chama a escrita do módulo `curriculum` além do importador. Inserir linhas por SQL à
> mão produz um catálogo sem registro em `catalog_import`, que não pode ser atribuído a nenhuma
> revisão dos arquivos (ADR 0014).

O importador é a própria aplicação, empacotada no mesmo jar, rodando sob o perfil `catalog`:
sem servidor web, executa um comando e encerra o processo com o código de saída do comando.
Os arquivos ficam em `catalog/<código_da_disciplina>/` e o formato está em `catalog/README.md`.

## Comando

A partir da raiz do repositório, com o jar empacotado e as credenciais do banco no ambiente:

```bash
./mvnw package

export SINAPSE_DB_URL=jdbc:postgresql://<host>:5432/<banco>
export SINAPSE_DB_USERNAME=<usuário>
export SINAPSE_DB_PASSWORD=<senha>

java -jar target/platform-0.1.0-SNAPSHOT.jar --spring.profiles.active=catalog catalog validate
java -jar target/platform-0.1.0-SNAPSHOT.jar --spring.profiles.active=catalog catalog diff
java -jar target/platform-0.1.0-SNAPSHOT.jar --spring.profiles.active=catalog catalog apply
```

- `validate` lê e valida os arquivos sem tocar no banco.
- `diff` mostra o que `apply` mudaria.
- `apply` valida de novo e aplica tudo numa transação.
- `report` imprime, só a partir dos arquivos, o que um revisor precisa ver (seção "Relatório de
  revisão").
- `shape` calcula a forma da instância que um aluno produziria, nos termos do Core (seção
  "Calculadora de forma").
- `--subject=<código>` restringe `validate`, `diff`, `apply` e `report` a uma disciplina.
- `--allow-topic-removal` (só em `apply`) remove tópicos que sumiram dos arquivos. Sem ele,
  esses tópicos são mantidos e o relatório avisa.
- O diretório lido é `catalog`, relativo ao diretório de onde o comando roda. Para outro
  lugar: `--sinapse.catalog.directory=<caminho>`.

As migrações rodam na partida, como na aplicação web: num banco vazio, qualquer um dos
comandos cria o esquema antes de executar. O banco precisa da extensão `citext`.

**Atenção:** durante os segundos em que o comando roda, as tarefas agendadas da plataforma
(o worker de geração de planos e as varreduras diárias) também estão ativas no processo, como
estariam na aplicação web. O perfil `catalog` não as desliga.

Os nomes acentuados aparecem como `?` na saída se o terminal não estiver em UTF-8. É só a
exibição; o banco grava o texto correto. Para ver certo: `java -Dstdout.encoding=UTF-8 -jar …`.

## Códigos de saída

| Código | Significado |
|---|---|
| 0 | comando executado (avisos não mudam o código) |
| 1 | os arquivos não validam; nada foi escrito |
| 2 | linha de comando ilegível; o uso, ou o que escrever na opção, é impresso |

## O que a validação verifica

Tudo de uma vez, cada problema com arquivo, linha e o que fazer. **Erros** impedem `diff`,
`apply`, `report` e `shape`; **avisos** só são impressos.

| Verificação | Tipo |
|---|---|
| coluna faltando, linha malformada, valor fora do conjunto (`effort_tier`, `strength`, `provenance`) | erro |
| código de tópico repetido na disciplina | erro |
| `position` repetida, ou com buraco: as posições correm 1, 2, 3 … sem pular | erro |
| aresta para tópico inexistente, ou para disciplina que não está sendo lida (com `--subject`) | erro |
| aresta de um tópico para ele mesmo, ou o mesmo par duas vezes | erro |
| `CURATED` sem `source_reference` | erro |
| `CURATED` numa disciplina sem revisão em `reviews.csv` (vale a disciplina do arquivo onde a aresta está escrita) | erro |
| `reviews.csv` com revisor vazio ou data que não é ano-mês-dia | erro |
| ciclo, inclusive o que só fecha atravessando disciplinas, impresso como o caminho inteiro | erro |
| ciclo que só fecha através de arestas **já no banco** que a execução não lê (com `--subject`); só em `diff` e `apply`, que leem o banco | erro |
| tópico sem nenhuma aresta, de entrada ou de saída | aviso |

O último erro da tabela é o caso que escapava: com `--subject=A`, uma aresta nova de A, válida no
arquivo de A, podia fechar um laço com arestas de B já gravadas, e quem avisava era a *trigger*
do banco, nomeando uma aresta por identificadores. Agora a recusa vem antes, com o caminho:

```
catalog apply  refused: the files do not validate
  1 problem(s):
    SYN-A/prerequisites.csv:2  cycle detected through the catalogue:
      SYN-A:T01  (Topic 01) ->
      SYN-A:T02  (Topic 02) ->
      SYN-B:T01  (Topic 01) ->
      SYN-B:T02  (Topic 02) ->
      SYN-A:T01  (Topic 01)
      It closes through edge(s) already in the catalogue that this run does not read: …
```

(Dados sintéticos de `SeveralSubjectsImportIntegrationTest`.)

## Procedência e revisão

`PROPOSED` é toda aresta escrita por uma pessoa e ainda não revisada por quem ensina a
disciplina; `CURATED` só depois da revisão registrada em `catalog/<disciplina>/reviews.csv`
(colunas `reviewer`, `reviewed_on`) e com `source_reference` preenchida (ADR 0019). O
significado de cada nível está em `catalog/README.md`; o modelo vazio para o curador, com as
instruções, em [`curadoria/modelo-de-autoria/`](curadoria/modelo-de-autoria/INSTRUCOES.md).

Cada `apply` grava em `catalog_import.notes` quantas arestas de cada procedência os arquivos
aplicados tinham, por exemplo `edges in the files applied, by provenance: PROPOSED 35`. É o que
permite atribuir um plano a um grafo revisado ou não a partir do `catalog_import_id` do job,
porque o contrato 1.0 com o Core envia `PROPOSED` como `CURATED` (K35 em `docs/ai/STATE.md`).

**Rebaixamento do exemplo.** As 35 arestas de `MED-ANAT` passaram de `CURATED` para `PROPOSED`
nos arquivos. Num banco que já tinha o exemplo, o próximo `apply` registra isso como 35
correções numa importação nova; nada é reescrito por migração (a V10 só passa a aceitar o
valor). Observado em 12/11/2026, num PostgreSQL 16 local com o exemplo aplicado pelo jar
anterior:

```
$ java -jar target/platform-0.1.0-SNAPSHOT.jar --spring.profiles.active=catalog catalog diff
catalog diff
  MED-ANAT
    ~ edge   MED-ANAT:osteologia-geral -> MED-ANAT:cranio  (HARD, CURATED  ->  HARD, PROPOSED)
    …
  totals: topics +0 ~0 =35, edges +0 ~35 -0 =0
  1 warning(s), which do not stop an import:
    MED-ANAT/topics.csv:15  topic musculos-abdome has no prerequisite and nothing depends on it. …
```

Depois do `apply`: 35 arestas `PROPOSED` no banco, a importação nova com `edges_updated = 35`, e
um segundo `apply` sem nada a mudar.

## Relatório de revisão

```bash
java -jar target/platform-0.1.0-SNAPSHOT.jar --spring.profiles.active=catalog catalog report
```

Só lê os arquivos; recusa arquivos que não validam. Por disciplina: tópicos e arestas, a tabela
força × procedência, arestas sem fonte, a revisão registrada (ou a falta dela), arestas que
cruzam disciplinas, a profundidade (a maior cadeia de pré-requisitos dentro da disciplina), os
tópicos de entrada (sem pré-requisito nenhum), as folhas (de que nada depende) e a distribuição
das faixas de esforço. No fim, a maior cadeia do catálogo inteiro. É para um especialista olhar
e dizer "falta pré-requisito aqui" sem abrir o CSV. Trecho, sobre o exemplo:

```
  MED-ANAT  Anatomia Humana
    35 topic(s), 35 edge(s) declared in MED-ANAT/prerequisites.csv
                   CURATED        PROPOSED  TEXTBOOK_ORDER         DERIVED   total
    HARD                 0              24               0               0      24
    SOFT                 0              11               0               0      11
    edges citing no source: 5
    review: none recorded in MED-ANAT/reviews.csv. …
    depth: the longest prerequisite chain inside the subject has 5 topic(s): …
    entry topics, with no prerequisite at all: 8
    leaf topics, that nothing depends on: 14
    effort bands: SHORT 4 STANDARD 21 LONG 8 EXTENDED 2
```

## Calculadora de forma

```bash
java -jar target/platform-0.1.0-SNAPSHOT.jar --spring.profiles.active=catalog catalog shape \
  --availability="MON,WED,FRI 19:00-21:00; SAT 09:00-11:00" --goals=MED-ANAT:3 --start=2026-11-16
```

- `--availability`: janelas semanais separadas por `;`, cada uma com dias (`SAT`, `MON-FRI` ou
  `MON,WED,FRI`) e horário local `HH:MM-HH:MM`, sem atravessar a meia-noite.
- `--goals`: `disciplina:prioridade`, prioridade de 1 a 5, separadas por vírgula. Todos os
  tópicos de cada disciplina com meta entram no escopo, como na plataforma.
- `--start`: primeiro dia do horizonte. Sem ele, hoje no fuso da aplicação, como num job.

O horizonte é o da plataforma (`sinapse.planning.generation.horizon`, hoje `4w`), com o último
dia incluído, como o *snapshot* é montado: 29 dias. As definições são as do
`benchmarks/README.md` do Core (lido em `other-repo@9e13262`): `demanded_minutes` (soma das
faixas pelo mapeamento em vigor, só primeiras passagens, sem ajuste por aluno),
`available_minutes`, `tightness` = disponível ÷ demandado, `density` = arestas enviadas ÷
tópicos enviados, contagens `HARD`/`SOFT`, e ao lado de cada valor os níveis da matriz do Core
e se o valor cai dentro do intervalo medido. A dispersão de importância **não** é calculada: o
README do Core não a define. Em vez disso aparece a entrada da estratégia `goal-priority`
(quantos tópicos herdam cada prioridade).

A calculadora reproduz as três instâncias exportadas em `src/test/resources/instances/`
(`InstanceShapeCommandTest`): 760, 1620 e 3240 minutos disponíveis contra 2170 demandados.

Uma remoção de tópico recusada porque o tópico já tem histórico de estudo ou sessão planejada
desfaz a transação inteira e também não escreve nada.

## Idempotência

Rodar `apply` duas vezes seguidas com os mesmos arquivos não duplica disciplina, tópico nem
aresta. A segunda execução imprime `nothing to change: 35 topic(s) and 35 edge(s) already
match the files`. Ela **grava, sim, uma nova linha em `catalog_import`**, com contagens zeradas:
cada execução fica registrada, inclusive as que não mudaram nada.

A revisão registrada é o último commit do git que tocou o diretório do catálogo. Com
alterações não commitadas, a revisão ganha o sufixo `-dirty` e o comando avisa. Fora de um
repositório git, fica `unknown`.

## Verificado em 05/10/2026

Contra um PostgreSQL 16 local descartável, com o catálogo de exemplo `MED-ANAT`:

```
$ java -jar target/platform-0.1.0-SNAPSHOT.jar --spring.profiles.active=catalog catalog apply
catalog apply  (revision fbad00f86082bc9000d044c353361b49776755a6)
  MED-ANAT  (new subject)
    + topic  terminologia  Terminologia anatômica e planos de referência  (position 1, SHORT)
    …
  totals: topics +35 ~0 =0, edges +35 ~0 -0 =0
  recorded as import 10119424-e2ff-4a05-9623-ec4b55abdbb0
$ echo $?
0
$ java -jar target/platform-0.1.0-SNAPSHOT.jar --spring.profiles.active=catalog catalog apply
catalog apply  (revision fbad00f86082bc9000d044c353361b49776755a6)
  nothing to change: 35 topic(s) and 35 edge(s) already match the files
  recorded as import fac0e205-4d7a-4a7a-915c-346e0eaa1d37
$ echo $?
0
```

Resultado no banco: 1 disciplina, 35 tópicos, 35 arestas, 2 linhas em `catalog_import`. Com a
aplicação web no ar sobre o mesmo banco, `GET /api/v1/subjects` devolveu a disciplina e
`GET /api/v1/subjects/{id}/topics`, os 35 tópicos em ordem (ver `CONTRATO_API_SINAPSE.md`,
seção 7).

O teste `CatalogCommandLineStartupIntegrationTest` sobe a aplicação do mesmo jeito (perfil
`catalog`, argumentos `catalog apply`) e falha se o contexto do importador deixar de subir.
