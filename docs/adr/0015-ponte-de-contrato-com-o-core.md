# ADR 0015 — Ponte de contrato com o Core: duplicação e adaptador do lado do Core

**Data:** 17 de setembro de 2026
**Status:** aceita
**Emenda:** ADR 0001 e ADR 0002, cuja premissa de que o Core já é consumível não se sustenta.
**Substitui em parte:** ADR 0007, no ponto em que o contrato viveria em módulo compartilhado.

## Contexto

Os ADRs 0001, 0002 e 0007 foram escritos sobre duas afirmações que a verificação contra o
código mostrou serem falsas.

A primeira é que o Core "já existe como repositório separado e funcional" (ADR 0001 e
ADR 0002). Ele existe e executa, mas não é consumível por esta plataforma. O
`exam-optimizer-application` não expõe `POST /plans`, não conhece `contractVersion`,
`randomSeed` nem identificador de tópico em UUID. Do lado de cá o `coreclient` está
completo — `PlanRequest`, `PlanResponse`, validação da resposta e tradução das falhas — e
não tem com quem falar. Na prática, todo job de geração termina em `FAILED`: com
`CORE_UNAVAILABLE` quando não há nada no endereço configurado, com `CORE_REJECTED` quando
há algo que não entende a carga. **Nenhum plano de estudo foi gerado por este sistema até
hoje.**

A segunda é que "o contrato vive em módulo compartilhado versionado, consumido pelos dois
repositórios" (ADR 0007). Esse módulo nunca foi criado. O contrato vive apenas em
`coreclient/contract`, nesta plataforma, e o outro repositório não o conhece.

As duas afirmações estão no presente, em documentos com status "aceita". Corrigi-las é a
primeira coisa que este ADR faz, antes de decidir qualquer coisa: um registro que descreve
como existente algo que não existe deixou de ser registro.

O que falta decidir é como os dois repositórios passam a falar o mesmo protocolo e de que
lado mora o trabalho de tradução.

## Decisão

### 1. Contrato duplicado, validado contra JSON de referência versionado

Não haverá módulo compartilhado. Os *records* do contrato são escritos e mantidos nos dois
repositórios, e a correspondência entre eles é sustentada por documentos JSON de referência,
versionados junto do contrato e idênticos nos dois lados. Cada repositório carrega um teste
que serializa o que produz e desserializa o que recebe contra esses documentos. O detalhe
da estratégia está em SP-1.

O módulo compartilhado foi considerado e recusado. Ele acopla o ciclo de publicação dos dois
repositórios, exige um repositório de artefatos para operar e monitorar, e transforma
qualquer ajuste de contrato em três liberações coordenadas. Para dois repositórios sob um
único responsável técnico, esse custo é maior do que o da duplicação.

### 2. O adaptador `POST /plans` mora no `exam-optimizer-application`

O endpoint que fala este contrato é implementado no repositório do Core. Ele recebe o
`PlanRequest` desta plataforma, traduz para o modelo interno do otimizador, executa e
responde com `PlanResponse`.

A alternativa era manter o adaptador aqui, no `coreclient`, traduzindo do nosso modelo para
o que o Core aceita hoje. Ela foi recusada porque inverte quem perde. O modelo que o Core
entende hoje é mais pobre que o desta plataforma: disciplinas por nome e nenhuma noção de
pré-requisito entre tópicos. Traduzir aqui significaria empobrecer a carga antes de enviá-la
— descartar os UUID de tópico, achatar o grafo de pré-requisitos, abrir mão de `strength` e
`provenance` nas arestas. É precisamente o que se quer preservar: o grafo de pré-requisitos
é a contribuição da tese (ADR 0006), e `provenance` entrou no contrato para permitir a
ablação descrita lá.

Com o adaptador do outro lado, a plataforma continua falando o modelo rico e a dívida fica
onde ela é, no otimizador, que precisa aprender a receber um DAG. Enquanto não aprender, o
adaptador pode ignorar as arestas; o que se perde então é comportamento do Core, não o
contrato, e o dia em que ele passar a usá-las não exige mudança nenhuma aqui.

### 3. O Core continua sem autenticação e, por isso, obrigado a viver em rede privada

O Core não autentica quem o chama e não passará a autenticar agora. A consequência é uma
obrigação de implantação, não uma recomendação: ele só pode existir em rede privada, sem
rota de entrada a partir da internet, alcançável apenas por esta plataforma. Toda a
autorização acontece antes, aqui, em `AccountAccessPolicy` e `TeacherAccessPolicy`; o Core
nunca recebe requisição que não tenha passado por elas.

Autenticá-lo não compra nada enquanto ele tiver um cliente só e nenhuma rota pública. O
registro explícito é necessário porque a decisão desloca uma garantia de segurança do
código para a topologia de rede: expor o Core diretamente deixa de ser desleixo e passa a
ser falha de segurança, e quem opera a implantação precisa saber disso sem ler o código.

## Justificativa

**Sobre duplicar em vez de compartilhar.** O argumento do ADR 0001 a favor de Java dos dois
lados continua valendo para tudo menos este ponto: reuso de conhecimento, ferramental comum,
uma linguagem a menos na cabeça de quem mantém. O que não se confirmou foi o módulo
compartilhado, que o ADR 0001 tratava como consequência natural de Java dos dois lados e que
nunca chegou a ser criado. Ele era possível desde o primeiro dia e continuou não sendo feito
enquanto o `coreclient` inteiro era escrito; tratá-lo agora como pendência é adiar a
integração para construir infraestrutura que já teve sua chance.

**Sobre o JSON de referência ser o que sustenta a duplicação.** Dois *records* iguais por
disciplina divergem no primeiro campo renomeado às pressas. Dois *records* validados contra
o mesmo documento divergem e o build acusa. O documento é o contrato; os *records* são duas
leituras dele.

**Sobre a direção da tradução.** A regra é que a tradução mora do lado do modelo mais pobre.
Do contrário, o modelo mais rico é obrigado a se rebaixar na fronteira e nunca volta: o que
foi descartado na serialização não é recuperável do outro lado, e a plataforma perderia o
grafo sem que nada no código registrasse a perda.

## Consequências

**Divergência de contrato passa a ser detectada no build, não impedida na compilação.** É o
preço aceito, e é exatamente a classe de defeito que o ADR 0001 dizia eliminar. Com módulo
compartilhado, um campo renomeado de um lado quebra a compilação do outro imediatamente. Com
*records* duplicados, ele quebra um teste contra o JSON de referência — o que só acontece
quando aquele build roda, e apenas no repositório em que roda. A janela entre a mudança e a
detecção é real e não há como fechá-la inteiramente. O que a estreita é o JSON de referência
ser obrigatório na mesma alteração que muda um *record*, e nenhum dos dois lados conseguir
passar no build sem ele.

Em tempo de execução, o que resta é `contractVersion`: `RestSinapseCore` recusa como falha
de protocolo qualquer resposta que não venha na versão que este backend fala. Isso transforma
divergência não detectada em job `FAILED` com causa legível, o que é o melhor desfecho
possível depois que a detecção já falhou — não é substituto dela.

Enquanto o adaptador não existir no outro repositório, todo job continua terminando em
`FAILED`. Este ADR não muda esse estado; ele registra por quê, e de que lado está o trabalho
que o muda.

Nenhuma linha de código desta plataforma é alterada por este ADR. O `coreclient` já está do
tamanho certo, e é essa a evidência de que a fronteira foi desenhada no lugar certo.

A implantação ganha um requisito que não estava escrito em lugar nenhum: rede privada para o
Core. Um ambiente que não o satisfaça expõe um otimizador que aceita qualquer carga de quem
o alcançar.
