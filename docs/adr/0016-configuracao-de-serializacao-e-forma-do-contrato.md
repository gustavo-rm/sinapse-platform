# ADR 0016 — Configuração de serialização é forma do contrato, e a leitura é tolerante

**Data:** 05 de outubro de 2026
**Status:** aceita
**Complementa:** ADR 0015 (espelhamento do contrato do Core por documento de referência), na
Regra 1 e na descrição dos mecanismos do teste.

## Contexto

A Regra 1 do ADR 0015 diz que qualquer mudança na forma do contrato — campo acrescentado,
removido, renomeado ou com tipo alterado, em `PlanRequest`, `PlanResponse` ou em qualquer
*record* aninhado — exige incrementar `PlanRequest.VERSION` e atualizar os dois documentos de
referência, nos dois repositórios, na mesma mudança lógica.

A regra fala só de *records*, e a forma na linha não depende só deles. O próprio ADR 0015
reconhece, ao exigir o `ObjectMapper` da aplicação no teste, que a configuração do Jackson
decide parte da forma. O caso concreto: um componente nulo é **omitido** da saída por causa de
`spring.jackson.default-property-inclusion: non_null` (`application.yml`, seção `spring.jackson`),
e isso não aparece em *record* nenhum. Trocar essa linha mudaria o que o Core recebe sem tocar
em `coreclient/contract`, e nada na Regra 1 pediria o incremento de versão.

A seção "Consequências" do ADR 0015 também descreve um mecanismo que não existe: diz que "a
desserialização pega campo removido, porque propriedade desconhecida é rejeitada". Não é
rejeitada. O `ObjectMapper` da aplicação deixa `FAIL_ON_UNKNOWN_PROPERTIES` desligado, que é o
padrão do Spring Boot, e a auditoria de 05/10/2026 executou uma resposta do Core com campo
desconhecido no corpo: foi aceita e o job foi a `READY`. Quem pega campo removido é a comparação
de `CoreContractGoldenTest`, que percorre a união das chaves do documento de referência e da
reserialização e acusa a chave que sumiu.

## Decisão

**1. Configuração de serialização é forma do contrato.** A Regra 1 do ADR 0015 passa a valer
também para a configuração do Jackson que alcança o contrato: alterar `spring.jackson.*`
(inclusão de nulos, formato de datas, nomes de propriedade, ou qualquer outra opção que mude a
saída) exige o mesmo incremento de `PlanRequest.VERSION` e a mesma ressincronização dos dois
documentos de referência, nos dois repositórios, na mesma mudança lógica.

**2. O espelho do otimizador reproduz a configuração por anotação.** O otimizador não lê o
`application.yml` desta plataforma. Segundo a coordenação entre os dois repositórios (não
verificável daqui), ele reproduz a omissão de nulos com `@JsonInclude(NON_NULL)` nos dez
*records* do contrato espelhado, os mesmos dez que existem aqui: `PlanRequest` e seus seis
aninhados, `PlanResponse` e seus dois aninhados. Quem "alinhar" aqueles *records* com estes, que não têm
a anotação, não pode apagá-la: aqui a regra vem da configuração, lá vem da anotação, e o efeito
na linha tem de ser o mesmo.

**3. A leitura continua tolerante.** Propriedade desconhecida é ignorada na desserialização, e
isso é decisão, não descuido: um campo aditivo do Core não deve derrubar a plataforma, e ligar
`FAIL_ON_UNKNOWN_PROPERTIES` seria uma mudança de comportamento com esse custo. Campo removido
continua sendo detectado no *build*, pela comparação sobre a união das chaves.

## Consequências

Os mecanismos do teste de contrato passam a ser descritos como de fato são: a ida e volta,
comparada sobre a união das chaves, pega campo renomeado, com tipo trocado, removido e desvio
de valor; a varredura de componentes pega campo acrescentado. O Javadoc de
`CoreContractGoldenTest`, o Javadoc do pacote `coreclient.contract` e `docs/INTEGRACAO_CORE.md`
foram corrigidos para dizer isso. O texto do ADR 0015 fica como foi aceito, e este ADR prevalece
onde os dois divergem.

Uma mudança em `spring.jackson.*` que o teste de contrato não acuse ainda pode mudar a forma: o
teste compara contra os documentos de referência, que só exercitam os valores que contêm. A
regra acima é o que cobre esse resto.
