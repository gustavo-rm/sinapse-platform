# ADR 0013 — Serviço de leitura e separação entre leitura e escrita no contrato

**Data:** 04 de setembro de 2026
**Status:** aceita

## Contexto

O backend foi desenhado sem cliente. O risco previsível desse arranjo é produzir um endpoint
CRUD por agregado, formato que quase nunca corresponde ao que uma tela precisa.

O risco é mais alto aqui do que em um backend acoplado, e não menor, porque as fronteiras
entre módulos são duras: sem composição no servidor, o cliente faria várias chamadas e
replicaria no navegador a lógica de casamento entre módulos.

O percurso dos fluxos de usuário confirmou o problema. A tela mais básica do produto, a
agenda do dia, precisa de dados de `planning`, `curriculum` e `learningrecord` em uma única
apresentação coerente.

## Decisão

**Escrita segue o agregado. Leitura segue a tela.**

Escrita tem invariante, e o agregado é a unidade de consistência. Leitura não tem invariante,
e a tela é a unidade de utilidade. As duas coisas têm formatos diferentes, e o contrato
admite isso explicitamente.

Modelos de leitura vivem no pacote `readmodel`, no mesmo nível de `datarights`. É o
**terceiro** componente do sistema autorizado a conhecer mais de um módulo, ao lado de
`planning.orchestration` e `datarights`.

Regras: compõe chamando o `api` de cada módulo, nunca consulta tabela alheia, nunca importa
`internal`, nunca escreve.

Sem materialização, sem cache, sem projeção assíncrona na v1. Cálculo sob demanda.

Cada módulo passa a expor **busca em lote por conjunto de identificadores**, e não apenas
busca unitária.

## Justificativa

**Isto não é CQRS.** Não há barramento, projeção, armazenamento separado nem consistência
eventual. É apenas reconhecer que a forma da leitura difere da forma da escrita, o que já é
verdade no sistema. Introduzir o maquinário completo de CQRS seria complexidade sem problema
que a justifique.

**Por que a composição fica no servidor.** Fazê-la no cliente significaria replicar regra de
negócio no navegador, multiplicar chamadas de rede e quebrar a tela sempre que um módulo
mudar. A fronteira entre módulos existe para proteger o servidor de si mesmo, não para ser
exportada ao cliente.

**Por que busca em lote é requisito do `api` e não do serviço de leitura.** Sem junção entre
módulos, compor a agenda de 40 sessões faria 40 buscas de tópico. Um módulo que expõe apenas
busca unitária empurra o N+1 para todos os seus consumidores. O lote pertence ao contrato do
módulo.

**Por que não materializar.** Materialização introduz obsolescência e uma rotina de
atualização. O volume do piloto não justifica nenhum dos dois. A candidata a precisar disso
primeiro é `ListaDaTurma`, que agrega histórico de N alunos; a decisão deve vir de medição, e
não de suspeita.

## Consequências

`readmodel` concentra conhecimento de vários módulos e tende a crescer. Exige a mesma
vigilância que `planning.orchestration`: é candidato natural a virar um serviço que sabe tudo.

Seis modelos estão especificados em `docs/CONTRATO_API_SINAPSE.md`. Acrescentar um sétimo
deve exigir um fluxo de usuário que o justifique, não a conveniência de um endpoint.

Cada módulo ganha métodos de busca em lote, o que aumenta levemente a superfície do `api`.

A ausência de paginação genérica é decisão consciente. Históricos exigem janela temporal
obrigatória; listas pequenas têm teto rígido. Paginação por cursor é acréscimo barato quando
um caso concreto surgir.
