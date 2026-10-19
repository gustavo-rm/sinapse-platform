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

## O que o snapshot revelou (19/10/2026, não corrigido)

Relatado no PR do SP-13 e registrado em [`docs/ai/STATE.md`](../ai/STATE.md); nada disso foi
alterado aqui.

- **`operationId` com sufixo numérico.** O springdoc usa o nome do método Java como
  `operationId` e, quando dois métodos têm o mesmo nome, acrescenta `_1`, `_2`… na ordem em que
  encontra os controllers. São 15 operações assim (por exemplo `list`, `list_1` … `list_7`).
  Geradores de cliente usam o `operationId` como nome de método: um controller novo com um
  método `list` pode renumerar os existentes e quebrar o cliente gerado. Entre duas partidas do
  mesmo código o resultado é estável.
- **Erros RFC 7807.** Todas as 90 respostas de erro declaradas usam
  `application/problem+json` com o esquema `ProblemDetail`. Esse esquema, porém, descreve um
  campo `properties` que não aparece no corpo real (o Spring escreve as extensões no nível de
  cima) e não descreve a extensão `errors` das falhas de validação. Além disso, 9 operações
  `GET` não declaram nenhuma resposta de erro, embora possam responder com erro (por exemplo
  `401` nas que exigem sessão).
- **Exposição em produção.** Sem perfil ativo, `/api-docs`, `/api-docs/swagger-config` e
  `/swagger-ui/index.html` respondem `200` a qualquer um, sem autenticação, na porta HTTP da
  aplicação (a cadeia de segurança cai no `permitAll` final). `/v3/api-docs` responde `404`
  porque o caminho foi trocado. Se o proxy reverso publica esses caminhos depende da
  configuração dele, que [PROXY_REVERSO.md](../PROXY_REVERSO.md) não fixa. O documento não
  declara `securitySchemes`, então um cliente gerado não sabe como se autenticar.
