# ADR 0002 — Integração com o Sinapse Core

**Data:** 04 de setembro de 2026
**Status:** aceita, com a premissa do contexto emendada pela ADR 0015
**Emendada por:** ADR 0015, que corrige a premissa de que o Core já seria consumível.

## Emenda de 17 de setembro de 2026

A segunda frase do contexto abaixo — "Já existe como repositório separado e funcional" — não
se sustenta contra o código e permanece no texto porque ADR aceita não se reescreve. O Core
executa, mas não é consumível por esta plataforma: não expõe `POST /plans` nem conhece
`contractVersion`, `randomSeed` ou tópico em UUID, e por isso todo job de geração termina em
`FAILED`. Leia a frase como "existe como repositório separado, ainda não integrável".

A decisão registrada aqui — processo separado, geração assíncrona por job, snapshot
persistido — não depende dessa premissa e continua valendo integralmente. O que a ADR 0015
decide é como os dois lados passam a falar o mesmo protocolo.

## Contexto

O Sinapse Core executa otimização por algoritmo genético para produzir planos de estudo.
Já existe como repositório separado e funcional. É necessário decidir se ele será
consumido como biblioteca embarcada no backend ou como processo independente, e como o
backend exporá a geração de plano em sua API.

Fato técnico determinante: a execução do AG é *CPU-bound* e tem tempo de resposta na
ordem de segundos a minutos, não de milissegundos.

## Decisão

O Core permanece em **processo separado**. A geração de plano é exposta como operação
**assíncrona baseada em job**, e não como requisição síncrona.

```
POST /study-plans/generation-requests      → cria job (PENDING)
GET  /study-plans/generation-requests/{id} → estado do job
GET  /study-plans/{id}                     → plano, quando READY
```

Estados do job: `PENDING`, `RUNNING`, `READY`, `FAILED`.

O backend persiste o **snapshot de entrada** enviado ao Core junto do job.

## Justificativa

**Isolamento de falha e de recurso.** Embarcado no backend web, o AG competiria com as
*threads* de atendimento a requisições, e uma execução patológica degradaria a API
inteira. Processo separado isola a falha e permite escalar os dois independentemente.

**A assincronia é consequência do tempo de execução, não da topologia.** Mesmo que o
Core fosse biblioteca embarcada, um POST síncrono devolvendo o plano seria inviável. A
consequência entra no modelo de dados de qualquer forma.

**Reprodutibilidade.** Sem o snapshot persistido, um plano gerado hoje não é
reproduzível amanhã, porque disponibilidade e histórico do aluno terão mudado. Isto é
requisito de produto (auditoria de por que um plano foi gerado assim) e requisito
científico (reprodutibilidade experimental).

**Fila em PostgreSQL.** A tabela de job em PostgreSQL é suficiente para a escala do
piloto e elimina a dependência de um *message broker*. Se a escala exigir, a troca é
localizada.

## Consequências

O cliente precisa lidar com estado de espera. A interface do usuário precisa de estado
de carregamento explícito para a geração de plano.

O snapshot cresce em volume. Exige política de retenção, a definir.

A comunicação passa a ser HTTP entre processos, com os modos de falha correspondentes
(*timeout*, indisponibilidade, resposta parcial). O adaptador `coreclient` concentra o
tratamento.

O contrato de entrada e saída do Core torna-se artefato versionado. Mudança no contrato
é mudança quebrável e exige versionamento explícito.
