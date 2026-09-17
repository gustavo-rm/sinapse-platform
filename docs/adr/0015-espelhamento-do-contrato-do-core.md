# ADR 0015 — Espelhamento do contrato do Core por documento de referência

**Data:** 17 de setembro de 2026
**Status:** aceita
**Complementa:** ADR 0007, que exigiu que o contrato do Core fosse artefato versionado
compartilhado pelos dois repositórios.

## Contexto

A ADR 0007 determinou que o contrato do Core vivesse em módulo compartilhado e versionado,
consumido tanto por esta plataforma quanto pelo `exam-optimizer-application`. Isso não foi
construído. O contrato existe apenas aqui, em `coreclient/contract`, e o otimizador mantém
a sua própria interpretação da forma dos dados.

A consequência já se manifestou: os dois lados divergiram em silêncio. Nada no otimizador
quebra quando um *record* muda aqui, porque não há nada ligando as duas formas. A
divergência só aparece quando um plano real falha em produção — tarde, e longe da causa.

O `exam-optimizer-application` hoje sequer expõe `POST /plans`, que é o caminho que esta
plataforma já chama. Antes de construir essa ponte é preciso decidir como as duas formas
passam a ser mantidas juntas.

## Decisão

**Não será publicado módulo Maven compartilhado.**

Cada repositório mantém a sua própria cópia dos *records* e valida a serialização contra um
**documento de referência versionado, idêntico nos dois lados** — o mesmo padrão do
`openapi-snapshot.json` que o otimizador já usa.

Os documentos vivem em `src/test/resources/contract/`:

```
plan-request-v1.0.json
plan-response-v1.0.json
```

São uma instância canônica e completa de cada *record*: todo campo preenchido, UUIDs e
instantes fixos e literais, nada gerado em tempo de execução. Não dependem de nenhuma classe
para serem lidos — qualquer analisador de JSON os abre.

`CoreContractGoldenTest` desserializa cada documento no *record* correspondente usando o
**`ObjectMapper` da própria aplicação**, serializa de volta e compara campo a campo.

### Regra 1 — mudança de forma exige versão nova e os dois lados no mesmo passo

Qualquer mudança na forma do contrato — campo acrescentado, removido, renomeado ou com tipo
alterado, em `PlanRequest`, `PlanResponse` ou em qualquer *record* aninhado — obriga a:

1. incrementar `PlanRequest.VERSION`;
2. atualizar os **dois** documentos de referência;
3. fazer isso **nos dois repositórios, na mesma mudança lógica**.

Não há passo opcional aqui. Um documento atualizado de um lado só é uma divergência com
aparência de acordo.

### Regra 2 — `effortTier` é conjunto fechado transportado como `String`

O conjunto é fechado e tem exatamente quatro valores:

```
SHORT   STANDARD   LONG   EXTENDED
```

O contrato o transporta como `String`, não como enumeração. Não existe tipo `EffortTier` em
`coreclient/contract`, e o documento de referência — que traz um valor por tópico — é o único
registro executável desse conjunto.

**Consequência:** acrescentar um valor novo não quebra a desserialização do consumidor —
quebra a interpretação dele, em silêncio.

Por isso **o consumidor é obrigado a rejeitar valor desconhecido, e não a assumir um
padrão**. Um `default` que mapeie o desconhecido para a banda mais próxima produz um plano
plausível e errado, que é pior do que um plano que não foi produzido.

## Justificativa

**O custo de publicação é real e recorrente.** Um módulo compartilhado exige repositório de
artefatos, ciclo de publicação, versionamento e coordenação de dependência entre os dois
lados a cada mudança. Na escala do piloto, esse custo é pago toda semana para resolver um
problema que aparece raramente.

**Detectar é suficiente aqui; impedir não é gratuito.** O módulo compartilhado impede a
divergência na compilação. O documento de referência a detecta no *build*. A diferença é
quando o erro aparece, e em ambos os casos ele aparece antes de chegar a produção. Esta é a
escolha consciente: custo zero de publicação, ao preço de a divergência ser pega no *build* e
não na compilação.

**O `ObjectMapper` tem de ser o da aplicação.** A forma na linha é decidida tanto pela
configuração quanto pelos *records*: `default-property-inclusion: non_null` decide se um
campo nulo aparece, e `write-dates-as-timestamps: false` decide se um instante é texto ou
número. Um `new ObjectMapper()` no teste afirmaria uma configuração que não existe em
produção.

**A `String` em `effortTier` é defensável e não é mexida agora.** Ela permite acrescentar uma
banda sem quebrar a desserialização de um consumidor antigo, que é exatamente a propriedade
que se quer num limite entre repositórios. Trocá-la por enumeração seria mudança de forma do
contrato por conveniência de um lado só.

## Consequências

A divergência passa a ser detectada pelo `./mvnw verify` de qualquer um dos dois lados, e não
por um plano errado em produção.

Três mecanismos do teste se somam, porque nenhum deles basta sozinho: a ida e volta pega
campo renomeado, campo com tipo trocado e desvio de valor; a desserialização pega campo
removido, porque propriedade desconhecida é rejeitada; e a varredura de componentes pega
campo **acrescentado** — que nenhum dos outros dois veria, já que sob `non_null` um componente
novo e nulo simplesmente não aparece na saída.

Os documentos de referência viram parte do contrato. Editá-los para fazer o teste passar, sem
o incremento de versão e sem o outro repositório, é desfazer a única proteção que existe.

Duas ausências no histórico continuam ambíguas por natureza, e o documento de referência traz
as duas para que o otimizador tenha exemplo de cada uma. Um tópico **ausente** de `history`
não teve sessão alguma dentro da janela configurada — nunca estudado, ou estudado antes de a
janela abrir. Um tópico **presente** sem `lastStudiedAt` e sem avaliações teve sessão na
janela, mas nenhuma que fechasse com duração registrada.

A ADR 0007 permanece como está: um módulo compartilhado continua sendo a solução correta se a
escala mudar. Esta decisão é sobre o que se constrói agora, não sobre o que seria ideal.
