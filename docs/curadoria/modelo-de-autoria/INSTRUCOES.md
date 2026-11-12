# Modelo de autoria do catálogo — instruções para quem cura uma disciplina

Este diretório é um **modelo vazio**: quatro planilhas só com o cabeçalho e estas instruções.
Nenhuma linha de conteúdo, de propósito. Copie o diretório inteiro para `catalog/<CÓDIGO>/`
(por exemplo `catalog/DISC-X/`), abra cada arquivo numa planilha, preencha, e exporte de
volta para CSV com o mesmo nome. A ferramenta (`catalog validate`) diz linha por linha o que
estiver errado e o que fazer.

O formato e o porquê de cada escolha estão nas ADRs 0006, 0012, 0014 e 0019. Os comandos estão
em [`docs/CARGA_DO_CATALOGO.md`](../../CARGA_DO_CATALOGO.md).

| Arquivo | Obrigatório | O que guarda |
|---|---|---|
| `subject.csv` | não | o nome da disciplina, numa célula |
| `topics.csv` | sim | os tópicos, na ordem em que a disciplina é ensinada |
| `prerequisites.csv` | não | o que precisa vir antes do quê |
| `reviews.csv` | não | quem revisou o grafo da disciplina, e quando |

---

## `subject.csv`

| Coluna | O que escrever |
|---|---|
| `name` | O nome da disciplina como o aluno vai lê-lo. Uma linha só. Sem este arquivo, a disciplina aparece com o código do diretório. |

## `topics.csv` — os tópicos

Uma linha por tópico.

| Coluna | O que escrever | Valores aceitos |
|---|---|---|
| `code` | Um apelido curto e estável do tópico, que nunca muda depois de usado: as arestas e o histórico de estudo dos alunos apontam para ele. Letras minúsculas, números e hífen funcionam bem (`t01`, `t02`). | único dentro da disciplina; sem `:` |
| `name` | O nome do tópico como o aluno vai lê-lo. | texto livre; pode ter vírgula se a planilha puser aspas |
| `position` | A ordem em que a disciplina é ensinada: 1 para o primeiro tópico, 2 para o segundo, e assim por diante, sem pular número e sem repetir. | 1, 2, 3 … n |
| `effort_tier` | O tamanho do tópico **comparado aos outros tópicos da mesma disciplina** (ver abaixo). | `SHORT`, `STANDARD`, `LONG`, `EXTENDED` |

### Faixa de esforço: comparação, nunca minutos

A pergunta não é "quanto tempo leva", e sim "este tópico é maior ou menor que o tópico típico
desta disciplina?". Ninguém consegue afirmar com fundamento que um tópico leva 47 minutos;
comparar dois tópicos é um julgamento que um professor faz bem.

| Faixa | Leia como |
|---|---|
| `SHORT` | menor que o tópico típico: uma ideia só, ou revisão de algo conhecido |
| `STANDARD` | o tópico típico da disciplina. Na dúvida, é este |
| `LONG` | claramente maior que o típico |
| `EXTENDED` | tão grande que normalmente é estudado em mais de uma sessão |

Quantos minutos cada faixa vale é **configuração da plataforma**, calibrada depois contra o
tempo que os alunos de fato levam. Não escreva minutos em lugar nenhum: se a calibração mudar,
o catálogo não precisa mudar.

## `prerequisites.csv` — o que vem antes do quê

Uma linha por relação "estude A antes de B". Não precisa de linha para pares que não têm
relação, e não precisa repetir o que já decorre de outras linhas (se A vem antes de B e B antes
de C, não escreva A antes de C).

| Coluna | O que escrever | Valores aceitos |
|---|---|---|
| `prerequisite` | O tópico que vem antes (A). | `DISCIPLINA:code`, por exemplo `DISC-X:t01` |
| `dependent` | O tópico que depende dele (B). | idem; pode ser de outra disciplina |
| `strength` | Quão obrigatória é a ordem (ver abaixo). | `HARD`, `SOFT` |
| `provenance` | Se a relação já foi revisada (ver abaixo). | `PROPOSED`, `CURATED`, `TEXTBOOK_ORDER`, `DERIVED` |
| `source_reference` | Onde a dependência está afirmada: livro e capítulo, programa da disciplina, diretriz. | texto livre; obrigatório em `CURATED` |

A relação pode cruzar disciplinas: a linha fica no arquivo de qualquer uma das duas, e as duas
precisam ser carregadas juntas.

### `HARD` ou `SOFT`: a decisão mais sutil

Faça a pergunta assim: **um aluno consegue estudar B com proveito sem ter visto A?**

- **Não consegue** — B simplesmente não faz sentido sem A — é `HARD`. O plano **nunca** põe B
  antes de A. Se A não couber no calendário do aluno, B também fica de fora.
- **Consegue, mas aprende melhor tendo visto A** — é `SOFT`. O plano prefere A antes de B, mas
  pode inverter quando o calendário obriga, e paga um custo por isso.

`HARD` custa caro: cada aresta `HARD` a mais é um tópico que pode sair do plano de um aluno com
pouco tempo, porque o que ele exige não coube. Reserve `HARD` para o que é realmente
impossível; o resto é `SOFT`. Na dúvida, `SOFT`.

### Procedência: proposto não é revisado

| Valor | Quando usar |
|---|---|
| `PROPOSED` | **Toda relação que alguém escreve começa assim.** Quer dizer: uma pessoa afirmou, ninguém que ensina a disciplina conferiu ainda. |
| `CURATED` | Só depois que alguém que ensina a disciplina revisou o grafo e a revisão foi registrada em `reviews.csv`. Exige `source_reference` preenchida. |
| `TEXTBOOK_ORDER` | Não escreva à mão: é gerado pelo comando `catalog seed-order` a partir da ordem dos tópicos. |
| `DERIVED` | Não escreva à mão: reservado para relações produzidas por derivação automática. |

A ferramenta recusa `CURATED` sem fonte, e recusa `CURATED` numa disciplina sem revisão
registrada. Quem lê `CURATED` conclui que um especialista conferiu — e essa conclusão acaba
num resultado de pesquisa.

## `reviews.csv` — o registro da revisão

Uma linha por revisão do grafo da disciplina inteira.

| Coluna | O que escrever | Valores aceitos |
|---|---|---|
| `reviewer` | Quem revisou, pelo identificador que o projeto adota para os revisores. | texto livre, não vazio |
| `reviewed_on` | A data da revisão. | ano-mês-dia, por exemplo `2026-11-30` |

Como revisar: rode `catalog report --subject=<CÓDIGO>` e leia o relatório (tópicos de entrada,
tópicos-folha, profundidade, arestas que saem da disciplina, faixas de esforço). Cada relação
que o revisor confirmar passa de `PROPOSED` para `CURATED`, com fonte; as que ele não confirmar
ficam `PROPOSED` ou saem. Depois acrescente a linha da revisão.

**Relação acrescentada ou alterada depois da revisão volta a ser `PROPOSED`** até a próxima
linha em `reviews.csv`. A ferramenta não consegue verificar isso sozinha: é um compromisso de
quem edita.

O identificador do revisor fica no repositório git, para sempre. Se for o nome de uma pessoa,
ela precisa concordar em constar ali.

---

## Se uma coluna sair

A força da aresta (`strength`) e a faixa de esforço (`effort_tier`) estão sendo medidas no
repositório do otimizador: se uma delas não mudar o plano, ela sai da planilha antes de custar
curadoria. Sair quer dizer: a coluna some deste modelo e destas instruções, e a ferramenta deixa
de exigi-la. Ninguém precisa reescrever o que já foi curado nas outras colunas.
