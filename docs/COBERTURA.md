# Piso de cobertura

O `./mvnw verify` falha quando a cobertura de testes cai abaixo de um piso. Este documento diz
como o piso é medido, quanto ele vale e a regra para mudá-lo.

## Como funciona

O `jacoco-maven-plugin` 0.8.13 (`pom.xml`) tem três execuções:

| Execução | Fase | O que faz |
|---|---|---|
| `prepare-agent` | `initialize` (padrão do goal) | põe o agente do JaCoCo no `argLine` do surefire, com destino `target/jacoco.exec` |
| `report` | `verify` | gera `target/site/jacoco/` (`index.html`, `jacoco.xml`, `jacoco.csv`) a partir de `target/jacoco.exec` |
| `check` | `verify` | lê o mesmo `target/jacoco.exec` e falha o build se uma regra não for cumprida |

**Um único arquivo de execução, alimentado por todos os testes.** O projeto não usa failsafe:
o surefire roda na fase `test` tanto os testes unitários quanto os de integração (as classes
`*IntegrationTest`, que sobem o contexto contra PostgreSQL). Todos gravam no mesmo
`target/jacoco.exec`, e é sobre ele que o piso é calculado. Os testes de tag `core-e2e` ficam
fora do `verify` padrão (`test.excluded-groups` no `pom.xml`) e não contam para o piso. O perfil
`core-e2e`, que roda só esses testes, não mede cobertura (`jacoco.skip` no perfil), porque a
fatia de código que eles exercitam reprovaria um piso que é do `verify` padrão.

**Nenhuma classe é excluída da medição.** Não há `<excludes>` em nenhuma execução do JaCoCo.
O relatório analisa as 389 classes do pacote (`Analyzed bundle 'sinapse-platform' with 389
classes`, saída do `verify` de 19/10/2026). Excluir classe para alcançar o piso é proibido pela
regra da catraca abaixo.

**Rode com `clean`.** O agente do JaCoCo acrescenta ao `jacoco.exec` existente. Um `verify`
sem `clean` depois de uma execução parcial (`-Dtest=...`) mede a soma das duas.

## Valores

Medidos em 19/10/2026, no commit `271b30d` (`origin/main` depois do #25), com
`./mvnw clean verify` no perfil padrão. A suíte rodou com Testcontainers (sem
`SINAPSE_TEST_DB_URL`): 708 testes, 0 falhas. Foram duas execuções seguidas, com o mesmo
resultado nas duas (variação de 0,0 ponto percentual):

| Contador | Cobertos | Total | Razão | Piso |
|---|---|---|---|---|
| Instruções (`INSTRUCTION`) | 20.399 | 21.418 | 0,952423 | **0,950** |
| Ramos (`BRANCH`) | 1.015 | 1.251 | 0,811351 | **0,809** |

**Conta do piso:** a razão medida, arredondada para baixo na terceira casa, menos 0,002.
- Instruções: 0,952 − 0,002 = 0,950.
- Ramos: 0,811 − 0,002 = 0,809.

Os 0,002 são a margem de ruído entre execuções, não uma folga para piorar.

**O piso é medido com banco vazio**: o PostgreSQL novo do Testcontainers ou, com
`SINAPSE_TEST_DB_URL`, um banco recriado logo antes do `verify`. Um banco reaproveitado de
execuções anteriores podia medir abaixo do piso sem que nenhum código tivesse mudado. Em 21/10/2026
(SP-14), no mesmo código, o banco recriado mediu 0,952423 e 0,811351 (0,952657 numa segunda
vez, pela segunda causa abaixo), e o mesmo banco, reaproveitado, mediu 0,950462 e 0,809752 numa
execução e 0,950696 e 0,809752 na seguinte. Eram duas causas, independentes; a primeira foi
removida em 25/10/2026 (SP-15):

- **Dado deixado no banco (42 instruções, 2 ramos): removida pelo teste
  `TermsServicePublicationTest`.** Ao subir, a aplicação publica os textos de consentimento
  configurados (`TermsService.publishConfigured`, chamado pelo `ApplicationRunner` de
  `IdentityConfiguration`) e só insere os que ainda não existem. Num banco vazio o primeiro
  contexto de teste os inseria e cobria esse caminho (`TermsService` linhas 66–69 e 71, o ramo
  verdadeiro das linhas 63 e 70, e o construtor de `TermsVersion`); num banco reaproveitado eles
  já estavam lá e o caminho não rodava. O teste novo é de unidade: exercita a inserção, a
  idempotência e o caso misto com um dublê do repositório, sem tocar no banco. Resultado, com
  `./mvnw clean verify` no mesmo commit (735 testes): banco recriado 0,953077 instruções
  (20.413 / 21.418) e 0,811351 ramos (1.015 / 1.251); o mesmo banco, reaproveitado, 0,953077 e
  0,811351. As linhas de `TermsService` saíram cobertas nas duas, iguais linha a linha em
  `jacoco.xml`, e nenhum outro arquivo diferiu. A subida em relação à tabela acima é de 14
  instruções: 9 de três acessores de `TermsVersion` que o teste novo lê e 5 do `catch` da causa
  abaixo, que nas duas execuções caiu coberto.
- **Escalonamento de threads (5 instruções, nenhum ramo).** O `catch` de
  `StudySessionService.persist` (índice único violado) só é alcançado por
  `StudySessionLifecycleIntegrationTest.twoSimultaneousStartsLeaveExactlyOneSessionRunning`,
  que dispara dois inícios de sessão ao mesmo tempo. O que perde é recusado ou pela consulta
  feita antes do insert ou pelo índice, conforme a ordem em que as threads rodam; o teste
  aceita as duas saídas. Rodando sozinho, cobriu o `catch` em 8 de 8 execuções; na suíte
  completa, em 2 de 4. Isso não depende do banco, e só pode subir a medição (cerca de 0,0002)
  em relação aos valores acima, que foram medidos sem o `catch` coberto. **Esta causa
  permanece.**

Para medir, recrie o banco antes do `verify`. O CI mede com banco novo a cada execução, igual à
medição oficial.

Os pisos não mudaram. Sem o `catch` da segunda causa, a medição estável passa a 0,952843
instruções (20.408 / 21.418), que pela conta acima ainda dá 0,950; os ramos não mudaram. Subir o
piso continua sendo decisão do dono, registrada abaixo.

A regra é de `BUNDLE` (o projeto inteiro), métrica `COVEREDRATIO`. Os pisos ficam nas
propriedades `coverage.minimum.instruction` e `coverage.minimum.branch` do `pom.xml`.

## A regra da catraca

1. **O piso só sobe.** Quando uma mudança aumenta a cobertura de forma estável, quem quiser
   pode subir o piso no mesmo PR, pela mesma conta (medido, arredondado para baixo na terceira
   casa, menos 0,002), com os números no corpo do PR.
2. **Baixar o piso exige uma decisão registrada, com motivo.** O registro fica neste documento,
   na seção abaixo, com data, quem decidiu, os números antes e depois e o porquê. Exemplo de
   motivo aceitável: remover código testado que deixou de existir e deixa a razão menor sem que
   nada tenha ficado sem teste.
3. **Nunca "para o CI passar".** Se o `verify` falha no `check`, o caminho é escrever o teste
   que falta. Baixar a propriedade, sobrescrevê-la na linha de comando
   (`-Dcoverage.minimum.*`) num pipeline, ou excluir classes da medição são formas de desligar
   a proteção, e não estão permitidas.

Prova de que o piso morde (19/10/2026): com o piso sobrescrito logo acima do medido
(`-Dcoverage.minimum.instruction=0.953 -Dcoverage.minimum.branch=0.812`), a execução `check`
falha com "instructions covered ratio is 0.952, but expected minimum is 0.953" e "branches
covered ratio is 0.811, but expected minimum is 0.812". O `./mvnw verify` completo com
`-Dcoverage.minimum.instruction=0.953` termina em `BUILD FAILURE` pela mesma regra, depois dos
708 testes verdes. Com os valores comitados, o `verify` passa ("All coverage checks have been
met").

## Integração contínua

Desde 25/10/2026 (SP-15) o repositório tem um workflow do GitHub Actions,
`.github/workflows/ci.yml`, que roda em todo push para `main`, em todo pull request para `main` e
sob demanda (`workflow_dispatch`). Um push novo no mesmo ref cancela a execução anterior.

O que ele roda:

- `./mvnw -B --no-transfer-progress clean verify`, o mesmo comando de quem verifica localmente: a
  suíte padrão inteira (unidade e integração), o `ModularityTests`, o teste do snapshot do
  OpenAPI e o `check` do JaCoCo com os pisos do `pom.xml`. Nada é sobrescrito na linha de comando,
  nenhum teste é pulado e nenhuma classe é excluída.
- Um PostgreSQL 16 (imagem oficial `postgres:16`) como serviço do job, novo e vazio a cada
  execução, alcançado por `SINAPSE_TEST_DB_URL`. A extensão `citext` é criada pela primeira
  migração (`V1__identity.sql`). A senha desse banco é um valor fixo e não é segredo: o banco só
  existe dentro do job, num contêiner descartável, e só guarda dados sintéticos de teste.
- Publica `target/surefire-reports/` e `target/site/jacoco/` como artefatos, mesmo quando falha,
  e escreve no resumo do job e no log o número de testes e as razões de cobertura ao lado dos
  pisos.

O que ele **não** roda:

- O perfil `core-e2e`, que precisa de um Sinapse Core no ar; continua sendo executado à mão.
- A segunda causa de variação acima (o `catch` de `StudySessionService.persist`) continua
  presente: duas execuções do mesmo commit podem diferir em 5 instruções.

**O workflow sozinho não bloqueia merge.** Ele faz a verificação existir e aparecer no PR; um PR
vermelho ainda pode ser mergeado até que o dono marque o job como verificação obrigatória. Passo
a passo, para quem administra o repositório no GitHub:

1. Espere o workflow ter rodado ao menos uma vez (o nome do check só aparece na lista depois
   disso). O check se chama `verify` (no PR aparece como "CI / verify").
2. Em **Settings → Branches**, em "Branch protection rules", clique em **Add branch protection
   rule** (ou edite a regra existente de `main`). Em repositórios com **Settings → Rules →
   Rulesets**, o caminho equivalente é criar um ruleset para a branch `main`.
3. Em "Branch name pattern", escreva `main`.
4. Marque **Require status checks to pass before merging** (no ruleset: **Require status checks
   to pass**).
5. Na busca de checks, digite `verify` e selecione o check do GitHub Actions com esse nome.
6. Recomendado: marque também **Require branches to be up to date before merging**, para que o
   check rode sobre o código que de fato vai entrar.
7. Salve (**Create** ou **Save changes**). Para conferir, abra um PR: o botão de merge só fica
   disponível com o check `verify` verde.

## Registro de mudanças do piso

| Data | Instruções | Ramos | Decisão e motivo |
|---|---|---|---|
| 19/10/2026 | 0,950 | 0,809 | piso inicial (SP-9), medido como acima |
