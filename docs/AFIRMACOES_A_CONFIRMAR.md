# Afirmações a confirmar

Afirmações da documentação que a revisão de 19/10/2026 (SP-12) **não conseguiu conferir** no
código atual ou por execução. Cada uma diz onde está, por que não foi conferida e o que a
confirmaria. Quando alguém conferir uma delas, corrija o documento de origem e tire a linha
daqui.

## Não conferidas

| # | Afirmação | Onde | Por que não foi conferida | O que a confirmaria |
|---|---|---|---|---|
| 1 | Do clique ao `READY` levam 7–10 s com o Core saudável | [FLUXO_DE_PLANO_FRONTEND.md §6](FLUXO_DE_PLANO_FRONTEND.md); medição da auditoria em [PRONTIDAO_INTEGRACAO_AG.md](PRONTIDAO_INTEGRACAO_AG.md) | precisa de um Core real em execução; não havia um nesta revisão, e o protocolo proíbe executar o código do Core a partir do clone | cronometrar um pedido contra um Core em execução. A composição derivada da configuração (0–10 s de espera mais a chamada) é compatível |
| 2 | Com o Core fora do ar, ~115 s até `FAILED` | mesmos lugares | a mesma razão do item 1, e a medição depende do instante do clique em relação à passada de 10 s | cronometrar com o Core desligado; o derivado do código dá 90–120 s, mais até 5 s por tentativa se a conexão esperar o *connect timeout* |
| 3 | O Core real grava `fitness.build`, e ele chega a `study_plan.fitness` | [FLUXO_DE_PLANO_FRONTEND.md §10](FLUXO_DE_PLANO_FRONTEND.md) | conferido só contra o Core simulado (`EngineSelectionIntegrationTest`); no Core, só por leitura (STATE K7 do Core, `exam-optimizer-application@3b54ba4`) | rodar os testes `core-e2e` do SP-8 contra um Core real e ler `study_plan.fitness ->> 'build'` |
| 4 | O Core expõe `POST /plans` (perfil `baseline-core`) e fala o contrato 1.0 | notas de atualização nos ADRs 0001, 0002, 0015 e 0017 | lido no código do Core (`PlanController.java:52-54,74`, `PlanProtocol.java:20,30`, `PlanRequest.java:75,78` em `3b54ba4`), sem execução nesta revisão; a execução que existe é a da auditoria (PRONTIDAO A2e) | `./mvnw verify -Pcore-e2e` com `SINAPSE_E2E_CORE_URL` |
| 5 | Os três motores (`greedy-baseline`, `ga`, `ga-timeline`) rodam de ponta a ponta escolhidos pela configuração | [INTEGRACAO_CORE.md §4.1](INTEGRACAO_CORE.md) | os testes existem (SP-8), mas não foram executados: não havia Core | o mesmo comando do item 4 (cada teste imprime uma linha `ENGINE-MATRIX`) |
| 6 | As 61 operações contadas pela auditoria correspondiam ao perfil `local` ou `test` | [openapi/README.md](openapi/README.md) e PR #24 | a auditoria não registrou o perfil; a conta (59 + 4 de probe − 2 do catálogo = 61) é inferência | repetir a contagem com o perfil usado pela auditoria, se ela for recuperável |

## Retiradas sem conferência

- **O proxy publica, ou não, `/api-docs` e a interface Swagger** (antigo item 7). Retirada em
  25/10/2026 porque perdeu a razão de ser no padrão, não porque alguém conferiu o proxy. Desde a
  [ADR 0018](adr/0018-documentacao-da-api-desligada-por-padrao.md), a plataforma sem perfil
  responde `404` nesses caminhos, então não há documentação para o proxy publicar. Para uma
  implantação que ligue as propriedades `springdoc`, vale a regra de
  [PROXY_REVERSO.md](PROXY_REVERSO.md): o proxy não deve publicá-los.

## Conferidas nesta revisão (saíram da lista)

- **Leitura tolerante.** `FAIL_ON_UNKNOWN_PROPERTIES` está desligado no `ObjectMapper` da
  aplicação, que lê sem erro uma resposta do Core com campos desconhecidos no topo e aninhados.
  Executado em 19/10/2026 numa cópia descartável, com a resposta de referência mais dois campos.
  Nada em `src/main` liga a opção (busca por `FAIL_ON_UNKNOWN` e `DeserializationFeature`: sem
  resultado). Antes disso, era só a palavra da auditoria (ADR 0016, Contexto).
- **O espelho do Core omite nulos por anotação.** O ADR 0016 §2 marcava como "não verificável
  daqui". Os dez *records* do contrato no Core têm `@JsonInclude(JsonInclude.Include.NON_NULL)`:
  `PlanRequest` e seis aninhados, `PlanResponse` e dois aninhados (`coreapi/contract/PlanRequest.java`
  e `PlanResponse.java` em `exam-optimizer-application@3b54ba4`, lidos). O texto do ADR 0016
  fica como foi aceito.
- **O agendador tem uma thread.** O único `TaskScheduler` do contexto tem pool 1. Executado em
  19/10/2026 numa cópia descartável.

## Fora do escopo desta revisão

Estas partes não foram conferidas linha a linha. Não há afirmação nova delas aqui, mas também não
há garantia de que estejam certas:

- [ARQUITETURA_BACKEND_SINAPSE.md](ARQUITETURA_BACKEND_SINAPSE.md): conferido só o índice de ADRs;
- [FLUXOS_DE_USUARIO_SINAPSE.md](FLUXOS_DE_USUARIO_SINAPSE.md): não cita rota;
- [CONTRATO_API_SINAPSE.md](CONTRATO_API_SINAPSE.md): as linhas "Compõe:" (quais módulos cada
  modelo de leitura atravessa) e as §4–6.
