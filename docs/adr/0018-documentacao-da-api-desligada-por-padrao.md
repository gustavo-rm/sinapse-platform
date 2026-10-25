# ADR 0018 — Documentação da API desligada por padrão

**Data:** 25 de outubro de 2026
**Status:** aceita

## Contexto

O springdoc publica a especificação OpenAPI em `/api-docs`, a configuração da interface em
`/api-docs/swagger-config` e a interface Swagger em `/swagger-ui` (`springdoc` em
`application.yml`). Até aqui isso valia para todo perfil, inclusive para nenhum, que é como uma
implantação sobe. A cadeia de segurança não reivindica esses caminhos e os entrega ao `permitAll`
final de `SecurityConfiguration`. Resultado: sem perfil, as três URLs respondiam `200` a qualquer
um, sem autenticação, na porta HTTP da aplicação (K21). O documento descreve toda rota, todo
parâmetro e todo erro da plataforma. [PROXY_REVERSO.md](../PROXY_REVERSO.md) não fixava se o
proxy os publica.

## Decisão

Decisão do dono do projeto, de 25/10/2026:

1. A documentação da API (`/api-docs`, `/api-docs/swagger-config`, `/swagger-ui`) fica
   **desligada por padrão**: `springdoc.api-docs.enabled` e `springdoc.swagger-ui.enabled` são
   `false` no `application.yml`.
2. Só os perfis `local` e `test` a ligam, com as duas propriedades em `true` nos seus arquivos.
3. A aplicação **continua subindo sem perfil**.

Fica de fora desta decisão, por não ter sido autorizado: fazer a aplicação recusar a subida sem
perfil, que exigiria criar um perfil de produção e mudar a implantação; e declarar
`securitySchemes` no documento, registrado como achado (K33 em
[`docs/ai/STATE.md`](../ai/STATE.md)).

## Consequências

- Sem perfil, as três URLs respondem `404` (`application/problem+json`). O que as fecha é não
  haver manipulador, não uma regra de segurança: o `permitAll` final continua existindo. Quem
  ligar as propriedades numa implantação volta a publicar a documentação sem autenticação.
- O perfil `catalog` não sobe servidor web e não publica nada; fica fechado, herdando o base.
- O snapshot `docs/openapi/openapi.json` continua sendo tirado sem perfil. O teste que o gera liga
  `springdoc.api-docs.enabled` só para si ([openapi/README.md](../openapi/README.md)), e o
  documento não mudou.
- O Swagger UI genérico da biblioteca, sem o documento do Sinapse, continua acessível em
  `/webjars/swagger-ui/`, servido pelo Spring Boot e não pelo springdoc (K34).
- `ApiDocumentationClosedByDefaultIntegrationTest` falha se uma das URLs voltar a responder sem
  perfil. `ApiDocumentationSwitchTest` falha se o base ligar a documentação, ou se `local` ou
  `test` deixarem de ligá-la.
