# ADR 0010 — Autenticação e sessão

**Data:** 04 de setembro de 2026
**Status:** aceita

## Contexto

O sistema precisa autenticar alunos e professores. As alternativas usuais são token JWT sem
estado, sessão opaca armazenada no servidor, ou JWT curto acompanhado de *refresh token*
com lista de revogação.

Há uma restrição específica deste sistema que decide a questão.

## Decisão

**Sessão opaca armazenada no servidor.**

Token de 256 bits gerado por fonte criptograficamente segura, armazenado apenas como
*hash* SHA-256 na tabela `user_session`. O valor em claro existe somente no cliente.

Expiração dupla: inatividade de 7 dias e limite absoluto de 30 dias. Ambas configuráveis.

Entrega: *cookie* `HttpOnly`, `Secure`, `SameSite=Lax` para clientes de navegador;
cabeçalho `Authorization: Bearer` com o mesmo token opaco para clientes não navegador.

## Justificativa

**A restrição decisiva é a revogação imediata.**

Revogar consentimento de finalidade essencial move a conta para `SUSPENDED` na mesma
transação (ADR 0004), e `AccountAccessPolicy` é o mecanismo que faz essa suspensão valer.
Com token sem estado, a sessão ativa continuaria funcionando até expirar, e o portão de
acesso ficaria inoperante durante esse intervalo. Isso não é aceitável, porque o portão é
justamente o mecanismo de conformidade.

Um JWT curto com *refresh* resolveria parcialmente, ao custo de introduzir lista de
revogação — ou seja, estado no servidor de qualquer forma — mais rotação de chave,
verificação de algoritmo e um segundo tipo de token. É mais peça móvel para chegar ao
mesmo lugar.

**O custo alegado do modelo com estado não existe aqui.** A objeção usual ao *token* opaco
é a consulta ao banco por requisição. Neste sistema, toda leitura de dado do aluno já
consulta o banco para verificar `AccountAccessPolicy` ou `TeacherAccessPolicy`. A consulta
de sessão, indexada por *hash* único, se soma a algo que já acontece.

**Por que armazenar o *hash* e não o valor.** Diferente do código de convite, que é
guardado em claro porque o professor precisa reexibi-lo, um token de sessão nunca precisa
ser mostrado de novo. Vazamento da tabela não produz sessões utilizáveis.

## Consequências

Suspender ou anonimizar uma conta revoga suas sessões na mesma transação. Isto é
implementação obrigatória, não melhoria futura.

Trocar a senha revoga todas as sessões da conta, exceto opcionalmente a que originou a
troca.

A tabela `user_session` cresce e exige expurgo periódico de sessões expiradas.

`ip_hash` e `user_agent` são guardados para permitir ao titular listar e encerrar sessões
ativas. São dados pessoais e entram na política de retenção pendente (J3). O endereço é
armazenado como *hash*, não em claro.

Autenticação é rota limitada por taxa, conforme ADR 0009.

Tokens de verificação de e-mail, recuperação de senha e reafirmação de maioridade usam a
tabela `account_token`, também apenas como *hash*, de uso único e com expiração. A
verificação do responsável mantém colunas próprias na tabela `guardian`, porque aquele
token é entregue a um terceiro e não ao titular da conta.

**Fora de escopo nesta decisão:** autenticação federada, segundo fator e login social.
Nenhum tem requisito atual.
