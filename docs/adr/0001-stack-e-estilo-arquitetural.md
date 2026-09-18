# ADR 0001 — Stack e estilo arquitetural do backend da plataforma

**Data:** 04 de setembro de 2026
**Status:** aceita, com a premissa do contexto emendada pela ADR 0015
**Emendada por:** ADR 0015, que corrige a premissa de que o Core já seria consumível e
descarta o módulo compartilhado.

## Emenda de 17 de setembro de 2026

Duas passagens abaixo não se sustentam contra o código e permanecem no texto porque ADR
aceita não se reescreve.

O contexto diz que o Core "já existe como repositório separado e funcional em Java". Ele
executa, mas não é consumível por esta plataforma: não expõe `POST /plans` nem conhece
`contractVersion`, `randomSeed` ou tópico em UUID. Leia como "existe como repositório
separado, em Java, ainda não integrável".

A justificativa "Contrato com o Core" diz que, com Java dos dois lados, o contrato "pode
viver em módulo compartilhado versionado". Podia; não vive, e não vai viver. A ADR 0015
descarta o módulo compartilhado em favor de *records* duplicados validados contra JSON de
referência, e com isso a classe de defeitos de integração que esta justificativa dizia
eliminar volta a existir, detectada no build em vez de impedida na compilação.

A decisão — Java, Spring Boot, PostgreSQL, Flyway, monólito modular — não depende de nenhuma
das duas passagens e continua valendo. A condição de reversão registrada no fim também
continua válida: o Core é Java, e é nisso que a escolha está ancorada.

## Contexto

O backend da plataforma Sinapse precisa ser construído do zero. O Sinapse Core (motor de
algoritmos genéticos) já existe como repositório separado e funcional em Java. O
subsistema de RAG é previsto em Python. O desenvolvimento é conduzido por equipe muito
pequena, com um único responsável técnico.

As alternativas consideradas foram Java + Spring Boot, TypeScript + Node.js e
Python + FastAPI.

## Decisão

Java + Spring Boot, PostgreSQL e Flyway, organizados como monólito modular em um único
artefato executável.

## Justificativa

**Redução do número de linguagens.** O sistema completo já terá Java (Core) e Python
(RAG). Escolher Node ou Python para a plataforma resultaria em três linguagens mantidas
por uma pessoa. Este é o custo dominante da decisão, acima de qualquer característica
técnica isolada das três opções.

**Natureza real do trabalho deste backend.** Apesar do domínio do produto, este backend
quase não executa IA. Ele faz identidade, autorização por propriedade de recurso,
relacionamentos educacionais, migrações, histórico *append-only* e transações. É o
terreno mais forte do ecossistema Spring (Security, Data JPA, Flyway, validação
declarativa). As alternativas resolvem isso, mas sem vantagem que compense a linguagem
adicional.

**Contrato com o Core.** A integração mais arriscada do projeto é a fronteira
plataforma/Core. Com Java dos dois lados, o contrato pode viver em módulo compartilhado
versionado contendo apenas DTOs de entrada e saída, em vez de ser reimplementado e
mantido em sincronia manual em outra linguagem. Isso elimina uma classe inteira de
defeitos de integração.

**Monólito modular.** Microsserviços estão fora de escopo por decisão de produto e não
há justificativa técnica atual. As fronteiras de contexto são obtidas por modularização
interna verificada em tempo de build, não por separação de processos.

## Consequências

**Negativas.** Java é a mais verbosa das três opções e a mais lenta para iterar. Diante
do cronograma de submissão ao Centelha, isso pesa contra. A decisão foi tomada
assumindo que o custo de reescrever um modelo de domínio incorreto supera o custo de
verbosidade, e que o ganho de manter duas linguagens em vez de três se acumula ao longo
de todo o projeto.

**Positivas.** Reuso direto do conhecimento já consolidado no Core. Ferramental maduro
para as invariantes transacionais exigidas pelo modelo de consentimento.

**Condição de reversão.** Se o Core fosse Python, a recomendação seria FastAPI, pelo
mesmo argumento de redução de linguagens. A decisão está ancorada no fato de o Core ser
Java; se essa premissa se mostrar incorreta, o ADR precisa ser revisto.
