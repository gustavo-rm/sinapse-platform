# Instâncias do catálogo real

Três `PlanRequest` **sintéticos**, montados a partir do catálogo de exemplo em `catalog/` pelo
mesmo caminho que a produção usa para montar o documento enviado ao Core. Servem para
diagnosticar o otimizador sobre exatamente a entrada que o produto produz, no repositório do
Core, que não enxerga este catálogo.

Nenhum destes arquivos foi escrito à mão. Todos saem de `RealCatalogInstanceGeneratorTest`, e
`RealCatalogInstancesTest` confere que continuam saindo.

> **Catálogo de exemplo.** `catalog/MED-ANAT` é dado de exemplo, não um catálogo curado (veja
> `catalog/README.md`). Ele tem **35 tópicos**, todos em **uma única disciplina**, e 35 arestas
> de pré-requisito.

## Como os arquivos são montados

O que roda é o código de produção, com um banco PostgreSQL real (Testcontainers, ou
`SINAPSE_TEST_DB_URL` quando não há Docker):

1. o catálogo é aplicado pelo importador que o operador usa (`CatalogApplier`, lendo
   `catalog/` com `CatalogFiles`);
2. o aluno é registrado e ativado pelos serviços de identidade, com fuso `America/Sao_Paulo`;
3. a disponibilidade e a meta são declaradas pelos serviços de planejamento;
4. o job é enfileirado e reivindicado por `GenerationRequestService`, que é quem calcula o
   horizonte;
5. o documento é montado por `SnapshotAssembler.assemble`, a classe que a produção usa.

Só a chamada ao Core fica de fora.

O que é fixo:

| Item | Valor |
|---|---|
| Relógio da aplicação | `2026-10-12T00:00:00Z` (fixo) |
| Âncora do horizonte | segunda-feira `2026-10-12` |
| Duração do horizonte | `4w`, o padrão de produção (`sinapse.planning.generation.horizon`) |
| `randomSeed` | `20261012` |
| `algorithmParams` | os de `application.yml` (`population-size` 120, `generations` 400, `mutation-rate` 0.05), lidos do próprio arquivo; o perfil de teste os reduz e por isso é sobrescrito |
| Minutos por faixa de esforço | os de `application.yml`: `SHORT` 25, `STANDARD` 50, `LONG` 90, `EXTENDED` 150 |

### O que é reescrito depois da montagem

O currículo gera identificadores aleatórios para disciplinas e tópicos, e a consulta de arestas
não tem ordem. Para que a saída seja idêntica byte a byte, o gerador aplica **uma única**
canonicalização ao documento montado, e a nada mais:

- cada identificador de disciplina e de tópico é trocado por um UUID baseado em nome (versão 3),
  derivado do código no catálogo:
  `UUID.nameUUIDFromBytes("sinapse-real-catalog-instance/topic/MED-ANAT:<código>")` para tópicos
  e `.../subject/MED-ANAT` para a disciplina;
- tópicos e arestas são ordenados pela posição curricular dos seus extremos; metas, pelo código
  da disciplina;
- as chaves de `algorithmParams` são escritas em ordem alfabética (na produção a ordem é a de
  um `Map.copyOf`, que muda a cada JVM; em JSON a ordem dos membros não tem significado).

É uma troca de rótulos: nenhum valor além de identificadores muda, nada é acrescentado ou
removido, e um identificador que não seja disciplina ou tópico do catálogo faz a geração falhar
em vez de passar adiante. O id da conta do aluno, o e-mail e o id do job são gerados pela
produção e **não aparecem** no `PlanRequest`: o contrato não carrega identificador de pessoa, e
o teste confere que todo UUID do arquivo é de disciplina ou tópico do catálogo e que não há
nenhum `@`.

## Regras do cenário

**Metas.** No contrato, a meta é **por disciplina**, não por tópico: `goals[]` tem
`subjectId`, e todos os tópicos da disciplina entram no escopo com ela (decisão L3). Como o
catálogo tem uma disciplina só, há **uma** meta, e ela cobre os 35 tópicos.

- `targetDate`: o último dia do horizonte, `2026-11-09`.
- `priority`: `1 + (ordinal mod 5)`, onde `ordinal` é a posição da disciplina (a partir de 1)
  na ordem dos códigos do catálogo. Com uma disciplina só, `MED-ANAT` tem ordinal 1 e
  **prioridade 2**. A regra sugerida, `1 + (position mod 5)`, foi aplicada à disciplina porque
  não há meta por tópico a que aplicá-la.

**Sem histórico.** Nenhum dos três arquivos tem histórico: `history` é vazio, então não há
`recallRatings` nem `lastStudiedAt`, e `estimatedMinutes` é o valor da faixa sem ajuste
individual. É o cenário "primeiro plano". **Limitação:** estas instâncias não exercitam nada
que dependa de histórico (revisão, calibração de esforço, avaliações de recordação).

**Disponibilidade.** Os três perfis diferem **só** na disponibilidade. As janelas são horários
locais de `America/Sao_Paulo` (UTC−3, sem horário de verão em 2026): noite em dias úteis e
manhã/tarde no fim de semana. Nenhuma janela termina depois das 21h locais, então **nenhum
intervalo atravessa a meia-noite UTC** (essa fronteira é coberta por outro teste).

`rho` = minutos totais de disponibilidade no horizonte ÷ soma de `estimatedMinutes` dos tópicos.
Alvos: apertado ≈ 0,35, médio ≈ 0,75, folgado ≈ 1,5, com tolerância de ±5% relativa ao alvo.

**Horizonte inclusivo.** O horizonte vai de `2026-10-12` a `2026-11-09`. `SnapshotAssembler`
percorre os dias **incluindo** o último, então são 29 dias e cinco segundas-feiras, embora o
período configurado seja de 28 dias. Os números abaixo refletem o que a produção monta.

## Arquivos

### `plan-request-catalogo-real-apertado.json`

Janelas semanais: terça e quinta 19h30–20h30; sábado 9h00–10h10.

| Medida | Valor |
|---|---|
| Tópicos | 35 |
| Soma de `estimatedMinutes` | 2170 |
| Minutos de disponibilidade | 760 |
| rho | 760 / 2170 = 0,350230 |
| Horizonte | 2026-10-12 a 2026-11-09, 29 dias |
| Janelas (intervalos) | 12 |
| SHA-256 | `dce673984f6597743f49ff6051982bf5e42fbad4a93495b006386a8c5f9bd65f` |

### `plan-request-catalogo-real-medio.json`

Janelas semanais: segunda a sexta 19h00–20h00; sábado 9h00–10h30.

| Medida | Valor |
|---|---|
| Tópicos | 35 |
| Soma de `estimatedMinutes` | 2170 |
| Minutos de disponibilidade | 1620 |
| rho | 1620 / 2170 = 0,746544 |
| Horizonte | 2026-10-12 a 2026-11-09, 29 dias |
| Janelas (intervalos) | 25 |
| SHA-256 | `db27f88370605b0562da98d445a24344247af6dea4bfa27bc6a4e3b3a274f7ca` |

### `plan-request-catalogo-real-folgado.json`

Janelas semanais: segunda a sexta 18h30–20h30; sábado 9h00–11h00; domingo 14h00–15h00.

| Medida | Valor |
|---|---|
| Tópicos | 35 |
| Soma de `estimatedMinutes` | 2170 |
| Minutos de disponibilidade | 3240 |
| rho | 3240 / 2170 = 1,493088 |
| Horizonte | 2026-10-12 a 2026-11-09, 29 dias |
| Janelas (intervalos) | 29 |
| SHA-256 | `4d6dbc43bc6df47e2518b0e32ac21aea9f3806265a1bcaf1660c218c2da60341` |

### Cenário da auditoria de 05/10/2026

rho: **não disponível**. O cenário usado na auditoria não é reconstruível a partir deste
repositório: `docs/PRONTIDAO_INTEGRACAO_AG.md` não existe aqui.

## Regeneração

O gerador roda em dois modos.

- **Comparação (padrão).** Faz parte de `./mvnw verify`. Gera os três documentos em memória e
  falha se algum diferir byte a byte do arquivo commitado. Não escreve nada em `src/`.
- **Regeneração.** Só com a propriedade de sistema explícita:

  ```bash
  ./mvnw test -Dtest=RealCatalogInstanceGeneratorTest -Dsinapse.instances.regenerate=true
  ```

  Sem Docker, aponte para um PostgreSQL com a extensão `citext`:

  ```bash
  SINAPSE_TEST_DB_URL=jdbc:postgresql://localhost:5432/sinapse \
    ./mvnw test -Dtest=RealCatalogInstanceGeneratorTest -Dsinapse.instances.regenerate=true
  ```

Depois de regenerar, atualize à mão a linha SHA-256 de cada tabela acima;
`RealCatalogInstancesTest` falha enquanto o README e os arquivos não coincidirem.

## O que copiar para o repositório do otimizador

Os três JSON e este README. Nada de `src/test/resources/contract/`: aqueles são os documentos
de referência do contrato, idênticos byte a byte nos dois lados, e não fazem parte deste
conjunto.
