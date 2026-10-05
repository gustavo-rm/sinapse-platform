# Desenvolvimento local: criar e ativar uma conta

Este guia leva uma conta sintética do cadastro até a sessão autenticada, numa estação de
trabalho, sem tocar no banco.

## Por que existe

A plataforma ainda não tem transporte de e-mail. A porta `AccountNotifier` tem uma única
implementação de produção, `LoggingAccountNotifier`, que registra que havia uma mensagem a
entregar e a descarta. O link de verificação nunca sai do servidor. Sem outra saída, uma conta
cadastrada pela API não pode ser ativada pela API.

No perfil `local`, e só nele, entra no lugar `DevOnlyLoggingAccountNotifier`: em vez de enviar,
escreve o token no log, com a marca **`DEV ONLY`**, junto da chamada que o consome. O token é uma
credencial de uso único, e a regra do projeto proíbe credencial em log. Esta é a única exceção,
e vale por três motivos ao mesmo tempo: o perfil é de estação de trabalho, a conta é sintética e
o log é o console do processo, que ninguém coleta. A classe se recusa a subir se qualquer outro
perfil estiver ativo junto com `local`. Em produção, que roda sem perfil, ela não existe. O
token nunca é exposto por rota HTTP.

O e-mail do titular não vai para o log nem aqui.

## Antes de começar

- Um PostgreSQL local com um banco vazio e a extensão `citext`:

  ```bash
  createdb sinapse
  psql -d sinapse -c 'create extension citext;'
  ```

- A aplicação no ar com o perfil `local`:

  ```bash
  export SINAPSE_DB_URL=jdbc:postgresql://localhost:5432/sinapse
  export SINAPSE_DB_USERNAME=<usuário>
  export SINAPSE_DB_PASSWORD=<senha>
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
  ```

  Ou, com o jar empacotado:
  `java -jar target/platform-0.1.0-SNAPSHOT.jar --spring.profiles.active=local`.
  As migrações rodam na partida, e o perfil `local` publica termos de consentimento de
  desenvolvimento.

## Os três passos

**1. Cadastrar.** Pegue o id do termo de `LEARNING_DATA_PROCESSING` e cadastre a conta:

```bash
curl -s localhost:8080/api/v1/terms

curl -s -H 'Content-Type: application/json' localhost:8080/api/v1/accounts -d '{
  "email": "dev.sintetico@example.com",
  "password": "correct-horse-battery-staple",
  "dateOfBirth": "1996-01-01",
  "timeZone": "America/Sao_Paulo",
  "acceptedTerms": [
    {"purpose": "LEARNING_DATA_PROCESSING", "termsVersionId": "<id do termo>"}
  ]
}'
```

A resposta é `201` com `"status":"PENDING_VERIFICATION"`.

**2. Ler o token no console da aplicação.** Aparece uma linha como esta:

```
INFO ... DevOnlyLoggingAccountNotifier : DEV ONLY - not a delivery. Token for account
f410dce1-…, purpose EMAIL_VERIFICATION, valid until 2026-10-06T18:32:51Z. Redeem with:
POST /api/v1/email-verifications {"token":"zVNRID_btvg…"}
```

**3. Consumir o token.** Faça a chamada que a própria linha indica:

```bash
curl -s -w '%{http_code}\n' -H 'Content-Type: application/json' \
  localhost:8080/api/v1/email-verifications -d '{"token":"<token do log>"}'
```

A resposta é `204` e a conta fica `ACTIVE`. O token é de uso único: uma segunda tentativa
responde `404`. Daí em diante, o login funciona normalmente:

```bash
curl -s -H 'Content-Type: application/json' localhost:8080/api/v1/sessions \
  -d '{"email":"dev.sintetico@example.com","password":"correct-horse-battery-staple"}'
```

Use o `token` devolvido como `Authorization: Bearer <token>`.

Ainda não há "link para clicar": a plataforma não conhece o endereço do frontend, e a rota de
verificação do frontend não está definida. A linha do log traz o que o link carregaria, que é o
token e a chamada que o consome.

A recuperação de senha (`POST /api/v1/password-resets`) passa pela mesma porta, então o token
também aparece no log, com a rota de confirmação e o campo `newPassword`. Esse caminho é coberto
por teste unitário, mas não foi executado de ponta a ponta.

## Verificado em 05/10/2026

Fluxo executado com o jar e o perfil `local` contra um PostgreSQL 16 vazio: cadastro `201`,
linha `DEV ONLY` no console sem o e-mail, verificação `204`, segunda verificação `404`, login
com token de sessão e `GET /api/v1/me/state` com `"accountStatus":"ACTIVE"`. Nenhum comando SQL depois da
criação do banco.
