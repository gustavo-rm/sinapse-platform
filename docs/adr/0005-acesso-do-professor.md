# ADR 0005 — Acesso do professor aos dados do aluno

**Data:** 04 de setembro de 2026
**Status:** aceita

## Contexto

O aluno se cadastra sozinho e ingressa em turma por convite. O professor precisa
acompanhar os alunos da sua turma. É necessário decidir:

1. Como o acesso do professor a um aluno é estabelecido.
2. Qual o escopo desse acesso: todas as disciplinas do aluno ou apenas as da turma.
3. O que acontece quando o aluno revoga o consentimento de compartilhamento.

## Decisão

**Acesso derivado exclusivamente de matrícula.** Não existe relação direta
professor → aluno. Um professor acessa dados de um aluno se, e somente se, existir
matrícula ativa desse aluno em turma de sua titularidade **e** o consentimento de
`INSTITUTION_SHARING` do aluno estiver vigente no momento da consulta.

**Escopo integral.** O professor vê o histórico e o plano completos do aluno, não apenas
o recorte das disciplinas da turma.

**Verificação de consentimento na consulta, não por propagação de evento.**

O escopo é parâmetro de política, exposto por `VisibilityScope`, hoje sempre `ALL`.

## Justificativa

**Sobre o acesso derivado.** É mais simples que a alternativa (relação direta
professor → aluno modelada em paralelo), é o que o piloto realmente exige, e preserva a
regra de independência do aluno: aluno sem matrícula é simplesmente aluno sem matrícula,
sem nenhuma chave estrangeira obrigatória apontando para estrutura institucional.

**Sobre o escopo integral.** O argumento decisivo não é conveniência, é interpretabilidade.
O plano gerado pelo motor é global: ele aloca tempo entre todas as disciplinas do aluno
simultaneamente. Um professor que enxerga apenas a sua fatia não consegue interpretar por
que o aluno não estudou a disciplina dele, porque a explicação frequentemente está na
alocação feita para outra matéria. Recorte parcial não protege apenas menos, ele produz
leitura errada do comportamento do sistema.

Há um custo real em minimização de dados, e ele é assumido conscientemente. A compensação
é que o escopo seja apresentado ao aluno de forma explícita no momento do aceite do
convite, não enterrado em termos de uso. É isso que sustenta `INSTITUTION_SHARING` como
consentimento informado.

**Sobre verificar consentimento na consulta.** A alternativa considerada foi publicar
evento de revogação a partir de `identity` e encerrar as matrículas afetadas. Foi
descartada por três razões: exigiria dependência de `identity` para `educational`,
invertendo a direção estabelecida no ADR 0003; produziria comportamento pior, porque
reconsentir não restauraria a matrícula encerrada; e a verificação síncrona é mais simples
com o mesmo efeito.

## Consequências

Revogação de `INSTITUTION_SHARING` corta o acesso do professor imediatamente, sem
propagação. Reconsentir restaura.

A verificação de autorização roda em toda leitura do professor. Exige índice parcial sobre
matrículas ativas, previsto na migração V2.

A reversão do escopo integral para recorte por disciplina é uma alteração em
`VisibilityScope` e nos pontos que a consomem, não uma varredura de código. Este é o
objetivo de expor o escopo como política.

**Risco aceito:** um professor de uma disciplina enxerga o esforço do aluno em disciplinas
alheias, incluindo preparação para concursos ou instituições concorrentes. Se isso se
mostrar problema no piloto, a reversão está prevista.
