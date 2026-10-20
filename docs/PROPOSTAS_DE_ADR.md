# Propostas de ADR — aguardando a redação aprovada pelo dono

**Estado: PROPOSTA.** Nada aqui é ADR, e nenhum arquivo foi criado em `docs/adr/`. São quatro
decisões que o dono do projeto **já tomou** e que hoje vivem só como linhas DECISION em
[`docs/ai/CONTEXT.md`](ai/CONTEXT.md) ("Decisions recorded here"). O que falta aprovar é a
**redação**. Aprovada uma redação, o ADR correspondente entra num PR à parte, com o próximo
número livre (hoje 0018; o 0017 foi ocupado pela renumeração da ponte de contrato) e no formato
dos demais (`Data` / `Status` / `Complementa`). Registrado como D-AI1 em
[`docs/ai/STATE.md`](ai/STATE.md).

Cada proposta diz só o que a decisão já diz, mais as consequências que o código atual mostra,
com evidência. Onde o texto precisar de uma escolha que o dono não fez, ela está marcada
**[a decidir]**.

---

## Proposta A — Frontend Angular na mesma origem, em repositório próprio

**Data:** (a da aprovação) · **Status:** proposta · **Complementa:** ADR 0009, ADR 0010

**Contexto.** A plataforma precisa de um cliente web. Três escolhas afetam o backend: em que
origem o cliente roda (o que decide CORS e o cookie de sessão), em que repositório ele vive e
quais áreas existem.

**Decisão.**
1. O frontend é em Angular.
2. É servido na **mesma origem** da API, atrás de um proxy reverso.
3. O SINAPSE são **dois repositórios** (esta plataforma e o Core), não um monorepo; o frontend
   não mora neste repositório. **[a decidir:** se o frontend é um terceiro repositório ou outro
   arranjo; a linha DECISION só diz "dois repositórios, não monorepo".**]**
4. A área do professor no frontend está planejada e não implementada.

**Consequências.**
- A mesma origem é o que sustenta CSRF desligado com o cookie de sessão `SameSite=Strict`
  (ADR 0009; [CLAUDE.md §9.5](../CLAUDE.md)).
- O proxy reverso precisa repassar o endereço do cliente de forma confiável
  ([PROXY_REVERSO.md](PROXY_REVERSO.md); `sinapse.rate-limit.trusted-proxies`).
- O backend já tem rotas de professor (`educational`, leituras de turma em `readmodel`), mas
  nada concede o papel `TEACHER` (CLAUDE.md §7, P2).
- O contrato com o frontend é o snapshot [`openapi/openapi.json`](openapi/openapi.json) (SP-13).

---

## Proposta B — Nenhum dado de aluno ou pessoal em documentos, logs ou commits

**Data:** (a da aprovação) · **Status:** proposta · **Complementa:** ADR 0011

**Contexto.** A plataforma trata dados de estudantes sob a LGPD. Documentação, logs e
histórico do git são lidos e copiados por mais gente, por mais tempo, do que o banco.

**Decisão.**
1. Nenhum dado de aluno ou dado pessoal entra em documentos, logs, commits, descrições de PR
   ou exemplos.
2. Contas de teste são sempre sintéticas.

**Consequências.**
- As regras de código dessa decisão já estão em vigor: sem dado pessoal em log, logs
  estruturados e `detail` de erro sem dado pessoal
  ([CLAUDE.md §9.5 e §10](../CLAUDE.md); `GlobalExceptionHandler` estende
  `ResponseEntityExceptionHandler`).
- Exemplos de documentação usam dados obviamente fictícios. Hoje o único e-mail de exemplo da
  API é `aluno@example.com`, de domínio reservado ([openapi/README.md](openapi/README.md)).
- **[a decidir:** se o ADR também deve fixar o tratamento de dados reais em ambiente de
  homologação, que a linha DECISION não cobre.**]**

---

## Proposta C — Os hiperparâmetros do AG pertencem ao Core (D4 do Core, Variante 1)

**Data:** 08/10/2026 (data da decisão) · **Status:** proposta · **Complementa:** ADR 0007

**Contexto.** A plataforma enviava em `algorithmParams` `generations`, `population-size` e
`mutation-rate`. O Core as ignora: confirmado por execução no Core (EOA-13,
`HiperparametrosDoCoreHttpTest`, lido em `exam-optimizer-application@3b54ba4`) e decidido lá no
ADR-0009 do Core. O registro do job (`algorithm_params`) afirmava como usados valores que não
rodaram.

**Decisão.**
1. Os hiperparâmetros de busca do AG são do Core.
2. A plataforma para de enviá-los.
3. `algorithm_params` registra o que a plataforma **enviou**, e o que rodou é lido da resposta
   (`fitness`, `metadata.generations`).

**Consequências.**
- A retirada das três chaves foi feita no SP-8b (20/10/2026): a configuração padrão envia
  `algorithmParams` vazio, e as instâncias de catálogo real compartilhadas com o Core foram
  regeneradas sem elas e anunciadas (decisão SP-8-3b, opção A).
- Os jobs gravados antes disso registram as três chaves como **solicitadas**, não como usadas
  (nota de 19/10/2026 no ADR 0007).
- A escolha do motor continua sendo da implantação, pela chave `engine` do mesmo mapa
  ([INTEGRACAO_CORE.md §1.1](INTEGRACAO_CORE.md)).

---

## Proposta D — `elapsedMillis` é reservado; a plataforma mede a chamada (DT-2)

**Data:** 08/10/2026 (data da decisão) · **Status:** proposta · **Complementa:** ADR 0007

**Contexto.** `metadata.elapsedMillis` chega sempre 0, de propósito: um tempo medido tornaria
diferentes duas respostas ao mesmo pedido com a mesma semente (ADR-0008 do Core, lido em
`exam-optimizer-application@3b54ba4`).

**Decisão.**
1. O Core mantém `elapsedMillis = 0`.
2. A plataforma mede o tempo de parede da chamada e o grava em coluna própria.

**Consequências.**
- `plan_generation_request.core_call_millis` (migração `V9`, anulável): latência da chamada,
  com rede, serialização e fila do Core. **Não** é o custo do motor e não serve para comparar
  motores; esse custo sai do *harness* do Core.
- `elapsed_millis` continua gravando o que o Core devolve, sempre 0
  ([INTEGRACAO_CORE.md §1.2](INTEGRACAO_CORE.md); `CoreCallMillisIntegrationTest`).
- O pedido SP-H2 ao Core se fecha quando essa medição estiver em `origin/main` — ela está desde
  o #23.
