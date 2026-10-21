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

## Limite: não há CI

O repositório **não tem pipeline de CI**. Não existe `.github/` nem outro arquivo de CI
versionado, em nenhum branch, e o GitHub Actions do repositório não tem nenhum workflow e
nenhuma execução (consultado em 19/10/2026). O piso, portanto, só protege quem roda
`./mvnw verify` na própria máquina antes de abrir o PR. Criar um CI que rode `./mvnw verify` é
uma tarefa à parte (registrada em `docs/ai/STATE.md`).

## Registro de mudanças do piso

| Data | Instruções | Ramos | Decisão e motivo |
|---|---|---|---|
| 19/10/2026 | 0,950 | 0,809 | piso inicial (SP-9), medido como acima |
