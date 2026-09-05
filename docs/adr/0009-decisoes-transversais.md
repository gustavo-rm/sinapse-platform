# ADR 0009 — Decisões transversais de API e infraestrutura

**Data:** 04 de setembro de 2026
**Status:** aceita

## Contexto

Cinco preocupações atravessam todos os módulos: formato de erro, versionamento da API,
limitação de taxa, fuso horário e observabilidade. Nenhuma pertence a um contexto
delimitado, e todas tocam praticamente cada endpoint.

Decidi-las depois da implementação exigiria varredura completa do código. Por isso entram
antes do primeiro módulo de domínio.

## Decisão

### 1. Contrato de erro — RFC 7807

Toda resposta de erro é `application/problem+json`, com `type`, `title`, `status`,
`detail`, `instance` e, para falhas de validação, um arranjo `errors` com campo e
mensagem.

`type` é URI estável, funcionando como código de erro versionável.

**`detail` nunca contém dado pessoal, identificador interno, nome de entidade, nome de
coluna ou fragmento de consulta.** Esta regra é a razão de o contrato ser decidido aqui e
não caso a caso: o vazamento acontece justamente quando cada endpoint improvisa a
mensagem.

`GlobalExceptionHandler` estende `ResponseEntityExceptionHandler`. Sem isso, exceções do
próprio Spring escapam do tratamento e corpos de requisição contendo dado pessoal chegam
ao log.

### 2. Versionamento — prefixo na URI

`/api/v1/...`

Versionamento por cabeçalho ou por tipo de mídia oferece granularidade que este produto não
tem como consumir, com custo de descoberta e de ferramental. O prefixo é visível, trivial
de rotear e funciona bem com springdoc.

### 3. Limitação de taxa

Mecanismo único, aplicado por filtro, com política declarada por rota.

Chave: identificador da conta quando autenticado; endereço de origem quando anônimo.

**O endereço de origem vem da infraestrutura, não do cabeçalho enviado pelo cliente.**
`X-Forwarded-For` só é considerado quando a requisição vem de um *proxy* explicitamente
confiável, configurado por lista. Um contorno de limitação por cabeçalho forjado já foi
encontrado no repositório do Core; não repetir.

Implementação em memória por instância para a v1. Isto é adequado a um único artefato
executável e **deixa de valer** sob escala horizontal, quando o contador precisa de
armazenamento compartilhado. Registrado como limite conhecido.

Rotas que exigem limitação desde o início: resgate de convite, autenticação, criação de job
de geração, verificação de e-mail e recuperação de senha.

### 4. Fuso horário

`Account.timeZone` é obrigatório e guarda identificador IANA
(por exemplo, `America/Sao_Paulo`).

Regras:

- Instantes são sempre `timestamptz`.
- Horários locais, como as janelas de `study_availability`, são interpretados no fuso da
  conta.
- O fuso vai explicitamente no *snapshot* enviado ao Core.
- Nenhum código depende do fuso padrão da JVM ou do servidor.

Sem isso, "estudo das 19h às 21h" não tem significado determinado, e o plano é gerado em
instantes absolutos. O Brasil não adota horário de verão desde 2019, o que reduz a
probabilidade de falha, mas não resolve a ambiguidade — e o produto não é
geograficamente limitado por desenho.

**Esta era uma omissão do esquema original**, corrigida na migração V1 antes de qualquer
aplicação.

### 5. Observabilidade

Spring Boot Actuator com `health`, `info` e `metrics`, expostos em porta separada e não
públicos.

Métricas próprias do job de geração: profundidade da fila, duração de execução, taxa de
falha, tentativas. O job é o componente com maior probabilidade de falhar em silêncio.

Log estruturado em JSON, com identificador de correlação por requisição.
**Nenhum dado pessoal em log**: nem e-mail, nem nome, nem data de nascimento, nem conteúdo
de estudo do aluno.

## Consequências

O filtro de limitação de taxa precisa rodar antes da autenticação para rotas anônimas e
depois dela para rotas autenticadas. A ordem da cadeia de filtros é decisão explícita, não
acidente de configuração.

O prefixo `/api/v1` aparece em toda rota desde o primeiro endpoint. Introduzi-lo depois
quebraria todos os clientes.

`timeZone` obrigatório acrescenta um campo ao cadastro. O cliente pode sugerir o valor a
partir do navegador, mas o servidor não infere.

A limitação em memória é ponto de revisão obrigatório antes de qualquer escala horizontal.
