# ADR 0006 — Grafo de pré-requisitos entre tópicos

**Data:** 04 de setembro de 2026
**Status:** aceita

## Contexto

A implementação da teoria de Ausubel no motor de otimização pressupõe conhecimento sobre
quais tópicos precedem quais. Essa estrutura não existe hoje: é a lacuna arquitetural
conhecida do Core.

Três questões precisavam de decisão: como a aciclicidade é garantida, quem popula o grafo,
e se a relação é binária, ponderada ou tipada.

## Decisão

**Estrutura.** Grafo dirigido único e global, com arestas podendo cruzar disciplinas.
Aciclicidade garantida por *trigger* no banco, com CTE recursiva e bloqueio consultivo de
transação.

**Curadoria.** Catálogo curado é a fonte primária. Cada aresta registra procedência
(`CURATED`, `TEXTBOOK_ORDER`, `DERIVED`) e referência da fonte. Derivação automática é
linha de pesquisa posterior, avaliada contra a base curada. Sobreposição do professor fica
fora deste contexto.

**Semântica.** Relação tipada em dois níveis: `HARD` e `SOFT`.

## Justificativa

### Aciclicidade no banco

Ordenação topológica só existe em grafo acíclico. Um ciclo alcançando o Core produz
comportamento indefinido, que se manifesta como travamento, laço ou ordem arbitrária
conforme a implementação. É defeito silencioso e caro de diagnosticar.

A validação em código de aplicação foi descartada por uma condição de corrida real: sob
isolamento *read committed*, duas inserções concorrentes não enxergam uma à outra, cada
aresta passa a validação isoladamente e juntas fecham o ciclo. Resolver isso exigiria
bloqueio explícito ou isolamento serializável, o que é mais trabalho que a *trigger*.

Pelo mesmo motivo, a *trigger* usa `pg_advisory_xact_lock` serializando as mutações do
grafo. Escrita de aresta vem de curadoria e é rara; o custo de serializar é irrelevante.

*Closure table* materializada foi descartada por complexidade sem justificativa: uma
disciplina de concurso tem ordem de 100 a 200 tópicos, e a CTE recursiva roda em
milissegundos nesse volume.

### Grafo global

Pré-requisitos entre disciplinas existem de fato (trigonometria precede cinemática).
Particionar o grafo por disciplina aparenta simplicidade e impede exatamente as arestas
mais informativas.

### Catálogo curado como fonte primária

O aluno não pode definir os pré-requisitos: se soubesse quais são, não precisaria do
sistema.

A derivação automática por mineração de dados de desempenho é o método cientificamente
sólido e é inviável agora, por circularidade: exige a plataforma em operação para produzir
o dado de que ela depende.

A geração por LLM produz grafo plausível e não verificado, o que o projeto proíbe. Sem
linha de base humana não há como avaliar o erro, de modo que a curadoria seria necessária
de qualquer forma.

O ponto que ordena a sequência: **derivação automática não substitui curadoria, ela precisa
da curadoria como linha de base para ser avaliada.** Curar primeiro, derivar depois e
comparar é método defensável e produz resultado publicável, o que "geramos o grafo com um
LLM" não produz.

Três medidas reduzem o custo de curadoria em ordem de magnitude:

1. **Curar arestas, não tópicos.** A maioria dos pares é independente. Grafo esparso com
   trinta arestas por disciplina é o estado normal, não uma versão incompleta.
2. **Usar ordenação existente como esqueleto.** Sumário de livro-texto é ordenação
   topológica válida, ainda que não mínima. Importar a ordem sequencial como arestas
   `SOFT` de procedência `TEXTBOOK_ORDER` e curar as exceções é muito mais barato que
   partir do zero.
3. **Registrar procedência.** É o que permite o experimento de ablação: o AG melhora com
   arestas curadas, com derivadas, com nenhuma? Sem procedência gravada esse experimento
   não existe.

### Relação tipada

A relação binária força decisões falsas: "trigonometria é pré-requisito de derivadas?" tem
como resposta honesta "parcialmente".

O peso contínuo em [0,1] foi descartado porque não há origem defensável para o número.
Curadoria humana produz peso arbitrário e não reproduzível, e o AG ganharia mais um
parâmetro a calibrar sem dado que o justifique.

A tipagem em dois níveis mapeia diretamente nos mecanismos que o AG já possui:

| Tipo | Mecanismo no Core |
|---|---|
| `HARD` | restrição; violação torna a solução inválida e aciona os operadores de reparo |
| `SOFT` | penalidade na função de aptidão; violação é permitida com custo |

A semântica fica definida pelo comportamento do algoritmo, e não por um número escolhido
por alguém. `strength` é enumeração e admite mais níveis no futuro sem mudança estrutural,
caso surja dado que os justifique.

## Consequências

Toda mutação do grafo serializa em um bloqueio consultivo. Aceitável dado o padrão de
escrita; deixaria de ser se a derivação automática passasse a gravar arestas em massa, o
que exigiria revisão.

`provenance` e `source_reference` entram no *snapshot* enviado ao Core, viabilizando a
ablação sem instrumentação adicional.

Aresta `SOFT` cíclica não quebraria o algoritmo, por ser apenas penalidade, e reforço mútuo
entre tópicos é fenômeno real. Ainda assim a v1 valida a união como acíclica: manter dois
regimes de validação é complexidade que só se paga se o caso aparecer. Fica registrado como
relaxamento possível.

**Correção em relação ao desenho inicial:** a procedência `TEACHER` foi removida deste
contexto. Uma aresta definida por professor precisa de recorte por turma, o que faria
`curriculum` referenciar `educational` e violaria a regra R3. A sobreposição do professor,
quando existir, será tabela própria em `educational`, fundida ao grafo global pela camada
de orquestração ao montar o *snapshot*. Depende de P2.

## Validação recomendada antes de escalar a curadoria

Curar uma única disciplina do piloto de medicina, com cerca de trinta arestas, e verificar
duas coisas antes de curar qualquer outra: se o Core consome o DAG corretamente, e se a
distinção `HARD`/`SOFT` produz de fato planos diferentes. Se não produzir, a distinção não
se paga e a relação volta a ser binária.
