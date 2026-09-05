# ADR 0011 — Direitos do titular: eliminação, exportação e a exceção ao append-only

**Data:** 04 de setembro de 2026
**Status:** aceita, sujeita a confirmação jurídica
**Resolve:** decisão pendente D5

## Contexto

O status `ANONYMIZED` existia no esquema desde a migração V1 sem semântica definida. Três
tabelas possuem *trigger* proibindo exclusão — `consent_record`, `study_session` e
`enrollment` — enquanto o titular tem direito à eliminação dos dados tratados mediante
consentimento.

A tensão é real, não formal. Parte dos registros precisa sobreviver à eliminação e parte
não pode sobreviver.

## Decisão

### 1. O que sobrevive e o que é apagado

| Registro | Destino | Fundamento |
|---|---|---|
| `consent_record` | **sobrevive**, com `evidence` higienizado | prova da base legal do tratamento já ocorrido; o ônus dessa prova é do controlador |
| `enrollment` encerrada | **sobrevive**, apontando para a conta anonimizada | auditoria de acesso do professor |
| `account` | esvaziada; `email`, `passwordHash`, `dateOfBirth`, `timeZone` anulados; status `ANONYMIZED` | resta apenas a casca que ancora os registros acima |
| `guardian` | **apagado** | dado de terceiro, sem base de retenção após a eliminação |
| `user_session`, `account_token` | **apagados** | — |
| `study_session` | **apagado** | não é anonimizável (ver abaixo) |
| `study_availability`, `study_goal` | **apagados** | — |
| `study_plan`, `planned_session` | **apagados** | — |
| `plan_generation_request`, incluindo `snapshot` | **apagado** | o *snapshot* contém o histórico inteiro; é o artefato mais sensível do sistema |

### 2. Histórico de estudo não é anonimizável no lugar

Uma sequência longitudinal de tópicos com horários é, na prática, uma impressão digital
comportamental. Cruzada com a lista de uma turma e com a grade de aulas, reidentifica.
Anonimização que não resiste a cruzamento é pseudonimização, e dado pseudonimizado continua
sendo dado pessoal.

Portanto não existe a opção "anonimizar as sessões e preservá-las". Existe apagá-las.

### 3. Anonimização ocorre na exportação, não na eliminação

O conjunto de dados de pesquisa é produzido por **exportação anonimizada e desvinculada**,
executada no momento da coleta com finalidade de pesquisa, e não no momento do pedido de
eliminação.

Consequência: quando o pedido de eliminação chega, o conjunto de pesquisa já não é dado
pessoal e está fora do escopo. Os registros operacionais são apagados sem tocá-lo.

**Isto precisa estar dito no termo de consentimento de pesquisa.** O titular precisa saber
que a contribuição já anonimizada não é reversível. Descobrir isso depois é problema
jurídico e ético, não técnico.

O `ACADEMIC_RESEARCH` **não** é motivo para preservar registros identificáveis. Revogar
consentimento de pesquisa encerra o uso; não cria base de retenção.

### 4. A exceção ao append-only

As *triggers* não são removidas. Elas passam a consultar um sinalizador transacional:

```sql
current_setting('sinapse.erasure', true) = 'on'
```

Ativado apenas pelo serviço de eliminação, dentro da transação de eliminação.

`consent_record` mantém a proibição de exclusão em qualquer circunstância, e ganha
permissão para anular `evidence` durante a eliminação.

### 5. Prazo e reversibilidade

Ao receber o pedido: suspensão imediata da conta e revogação de todas as sessões.

Eliminação efetiva após 7 dias, reversível pelo titular nesse intervalo. O prazo protege
contra pedido acidental ou sob coação.

A eliminação é **uma transação**. Eliminação parcial é pior que nenhuma.

### 6. Escopo do contexto

Não é apenas eliminação. Os direitos do Art. 18 tratados aqui são confirmação e acesso
(exportação estruturada dos próprios dados), eliminação, e informação sobre
compartilhamento (quais professores tiveram acesso e em que período). Correção já é atendida
pelos endpoints normais de cada módulo.

## Justificativa da arquitetura

Este componente conhece todos os módulos, o que o torna o **segundo** componente do sistema
nessa condição, ao lado de `planning.orchestration`.

Para não violar as fronteiras, cada módulo expõe em seu próprio `api` as operações de
eliminação e exportação dos seus dados. O coordenador apenas sequencia as chamadas dentro de
uma transação. Ele nunca acessa tabela de outro módulo.

Isso mantém a regra de que a lógica de um contexto vive no contexto, e é o que impede o
coordenador de virar um serviço que conhece o esquema inteiro.

## Consequências

`erasure_request` registra o pedido e o resultado. Ela **não** guarda cópia do que foi
apagado nem o e-mail do solicitante — isso reintroduziria o dado eliminado.

O índice único de e-mail já foi desenhado como parcial sobre contas não anonimizadas, de
modo que o endereço fica livre para novo cadastro após a eliminação.

A *trigger* protege contra defeito da aplicação, não contra ator privilegiado. O sinalizador
de eliminação não enfraquece essa propriedade: quem tem acesso privilegiado ao banco poderia
remover a *trigger* de qualquer forma.

Cada novo módulo criado no futuro precisa implementar sua parte da eliminação. Um módulo que
guarda dado pessoal e não participa da eliminação é um vazamento silencioso. Recomenda-se um
teste que falhe quando existir módulo sem operação de eliminação registrada.

## Pendências

Confirmação jurídica de três pontos: se a retenção de `consent_record` e de `enrollment`
encerrada se sustenta como cumprimento de obrigação legal; se o prazo de 7 dias é adequado;
e se a redação do termo de pesquisa cobre a irreversibilidade da contribuição anonimizada.
