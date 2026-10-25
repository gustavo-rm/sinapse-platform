# Snapshot do contrato OpenAPI

`openapi.json`, nesta pasta, é a especificação OpenAPI que a aplicação publica, congelada em
arquivo e versionada. É dele que o frontend gera o cliente tipado, e é o diff dele, num pull
request, que mostra ao frontend o que mudou no contrato.

**O diff do snapshot é a revisão de contrato com o frontend.** Quem revisa um PR que mexe em
`openapi.json` está revisando uma mudança de API, mesmo que o resto do PR pareça interno.

## De onde vem

- A especificação é gerada pelo **springdoc-openapi 2.9.0**
  (`springdoc-openapi-starter-webmvc-ui`, `pom.xml`) a partir dos controllers e das anotações
  `@Operation`, `@ApiResponse` e `@Schema`. Os metadados (título, descrição, licença, versão)
  vêm de `shared/config/OpenApiConfiguration.java`.
- A aplicação a serve em `GET /api-docs` (`springdoc.api-docs.path` em `application.yml`); a
  interface Swagger fica em `/swagger-ui.html`. Nenhum perfil liga ou desliga isso: está ativo
  em todos, produção incluída.
- O snapshot é tirado com **nenhum perfil ativo**, como numa implantação. O perfil `test`
  acrescenta as rotas `/api/v1/probe/**`, que produção não tem, e por isso não é usado.

## O fluxo

1. Alguém acrescenta, remove ou altera uma rota, um parâmetro, uma resposta ou um DTO.
2. `./mvnw verify` falha em `OpenApiSnapshotIntegrationTest`, com uma mensagem que diz como
   regenerar.
3. Se a mudança é intencional, regenere:

   ```bash
   ./mvnw test -Dtest=OpenApiSnapshotIntegrationTest -Dsinapse.openapi.regenerate=true
   ```

   O teste então escreve `docs/openapi/openapi.json` em vez de comparar. Sem a propriedade,
   ele nunca escreve nada.
4. Faça commit do `openapi.json` regenerado **no mesmo PR** da mudança de código.
5. No PR, revise o diff do snapshot como revisão de contrato: rota nova, campo removido,
   tipo alterado, `operationId` que mudou de nome. O frontend precisa saber de cada um.

Se o diff trouxer algo que não deveria estar no contrato, o problema está no código (uma
anotação, um DTO), não no snapshot: corrija lá e regenere.

## `operationId` faz parte da API

O gerador de cliente transforma cada `operationId` no nome de um método. Mudar um
`operationId` quebra todo cliente gerado de um snapshot anterior, mesmo que rota, parâmetros e
corpo continuem iguais. Por isso cada operação tem um nome único, em lowerCamelCase e sem
sufixo numérico. Quando dois handlers têm o mesmo nome de método Java, o nome da operação é
declarado com `@Operation(operationId = ...)`: um verbo para comando (`abandonGoal`), o
substantivo do que volta para consulta (`goals`). `OpenApiOperationIdIntegrationTest` falha o
build se alguma dessas regras for quebrada. O mesmo teste recusa `operationId` que seja palavra
reservada de ECMAScript ou TypeScript, porque o gerador a renomeia (`export` vira `_export`):
`break`, `case`, `catch`, `class`, `const`, `continue`, `debugger`, `default`, `delete`, `do`,
`else`, `enum`, `export`, `extends`, `false`, `finally`, `for`, `function`, `if`, `implements`,
`import`, `in`, `instanceof`, `interface`, `let`, `new`, `null`, `package`, `private`,
`protected`, `public`, `return`, `static`, `super`, `switch`, `this`, `throw`, `true`, `try`,
`typeof`, `var`, `void`, `while`, `with`, `yield` e `await`. Renomear de propósito é mudança de
contrato e vai no mesmo PR que o snapshot regenerado.

## Forma estável

Para que o diff mostre só o que mudou no contrato, o arquivo é escrito sempre do mesmo jeito:
chaves de objeto em ordem alfabética em todos os níveis, indentação de dois espaços,
`"chave": valor`, fim de linha `\n` e uma quebra de linha no fim. A ordem dos arrays é a
gerada (ela tem significado, por exemplo na ordem dos parâmetros).

Dois campos variam entre execuções sem relação com o contrato e são normalizados pelo teste:

| Campo | Por que varia | No snapshot |
|---|---|---|
| `servers` | o springdoc o deduz da requisição que buscou o documento: host e porta de quem pediu | removido |
| `info.version` | é a versão Maven do build (`sinapse.api.version`), que muda a cada versão sem que nenhuma rota mude | `"normalised"` |

O mesmo teste sobe a aplicação **duas vezes** (uma pelo contexto de teste, outra como
aplicação independente servindo HTTP numa porta livre) e exige o mesmo documento. Antes da
normalização, só `servers` difere entre as duas.

## O que o snapshot revelou (19/10/2026)

Relatado no PR do SP-13 e registrado em [`docs/ai/STATE.md`](../ai/STATE.md).

- **`operationId` com sufixo numérico.** Corrigido pelo SP-14b (25/10/2026): as 15 operações
  com `_1`, `_2`… têm nome declarado, e a regra está na seção acima.
- **Erros RFC 7807.** O esquema das 90 respostas de erro era o `ProblemDetail` do springdoc,
  que descrevia um campo `properties` ausente do corpo real e não descrevia `errors`.
  Corrigido pelo SP-14b: as respostas de erro usam o esquema `ProblemDetails`, que descreve o
  corpo como ele sai, e `OpenApiProblemSchemaIntegrationTest` compara com ele um corpo real de
  cada tipo de erro. **Continua aberto:** 9 operações `GET` não declaram nenhuma resposta de
  erro, embora 8 delas respondam `401` sem sessão; e só 11 das 53 operações que respondem `401`
  o declaram (K30 em [`docs/ai/STATE.md`](../ai/STATE.md)).
- **Exposição em produção.** Sem perfil ativo, `/api-docs`, `/api-docs/swagger-config` e
  `/swagger-ui/index.html` respondem `200` a qualquer um, sem autenticação, na porta HTTP da
  aplicação (a cadeia de segurança cai no `permitAll` final). `/v3/api-docs` responde `404`
  porque o caminho foi trocado. Se o proxy reverso publica esses caminhos depende da
  configuração dele, que [PROXY_REVERSO.md](../PROXY_REVERSO.md) não fixa. O documento não
  declara `securitySchemes`, então um cliente gerado não sabe como se autenticar.
