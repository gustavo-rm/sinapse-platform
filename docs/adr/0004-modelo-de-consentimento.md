# ADR 0004 — Modelo unificado de consentimento

**Data:** 04 de setembro de 2026
**Status:** aceita

## Contexto

A plataforma atenderá dois públicos com regimes de consentimento distintos: alunos de
ensino médio e pré-vestibular, majoritariamente menores de 18 anos, e um piloto com
estudantes de medicina, todos maiores de idade.

O cadastro é feito pelo próprio aluno, e o ingresso em turma ocorre por convite. Não há
provisionamento de contas pela escola. Isso transfere integralmente para a plataforma a
obrigação de obter consentimento de responsável quando aplicável.

A tentação natural é modelar dois fluxos distintos: "menor, com responsável" e "maior,
sem consentimento".

## Decisão

Um único fluxo, com um portão configurável. Toda conta gera `ConsentRecord`. A diferença
entre menor e maior é o valor de `grantedBy`:

- `SELF` — o próprio titular consente; a conta ativa imediatamente
- `GUARDIAN` — responsável consente; a conta aguarda em `PENDING_GUARDIAN_CONSENT`

O limiar etário que exige `GUARDIAN` é parâmetro de configuração.

Finalidades são separadas e consentidas independentemente:
`LEARNING_DATA_PROCESSING` (essencial), `INSTITUTION_SHARING` (condicional),
`ACADEMIC_RESEARCH` (opcional, recusável sem qualquer prejuízo ao serviço).

Na transição de maioridade, a conta **não** é bloqueada. Solicita-se reafirmação do
titular e concede-se 30 dias de carência; esgotado o prazo sem reafirmação, a conta
transita para `SUSPENDED`.

## Justificativa

**Maior de idade não é ausência de consentimento, é consentimento com o próprio titular
como outorgante.** O ônus da prova do consentimento é do controlador (LGPD, Art. 8 §2).
Tratando o adulto como "não precisa de registro", não há o que apresentar. Com o modelo
unificado, a prova existe para 100% das contas e é obtida pela mesma consulta.

**O limiar vira parâmetro, não estrutura.** O Art. 14 §1 exige consentimento de
responsável para dados de **crianças**, definidas em lei como menores de 12 anos. O
tratamento de dados de adolescentes é ponto controvertido e sem consenso consolidado.
Esta é decisão jurídica, não de engenharia. O modelo adotado é indiferente a ela: qualquer
que seja a orientação recebida, o esquema não muda.

**Um único caminho de código a testar.**

**Sobre a carência de maioridade.** O consentimento do responsável foi validamente obtido
e não se torna nulo no instante do aniversário; mantê-lo indefinidamente, porém, é
indefensável, porque o outorgante deixou de ser a pessoa adequada. A carência equilibra
defensabilidade jurídica e experiência de uso. A condição é derivável de `dateOfBirth` e
do histórico de consentimento, sem coluna adicional.

**Sobre `ACADEMIC_RESEARCH` ser recusável sem prejuízo.** Se o produto degradar diante da
recusa, o consentimento não é livre e os dados não são utilizáveis em publicação.

## Consequências

`ConsentRecord` é registro legal e *append-only*. Exige *trigger* de imutabilidade no
banco, defesa contra defeito próprio de mapeamento ORM.

A invariante "conta `ACTIVE` possui consentimento vigente" cruza dois agregados. Em
monólito com banco único é garantida transacionalmente por um `ConsentService` que é o
único escritor de ambos. Se `identity` um dia virar serviço separado, a invariante passa
a ser eventualmente consistente e o modelo precisa ser revisto.

O campo `evidence` (IP, *user agent*, método de verificação) é dado pessoal coletado sob
hipótese de cumprimento de obrigação legal, não de consentimento. Exige política de
retenção própria.

A data de nascimento é autodeclarada. Isto é compatível com a exigência legal de esforços
razoáveis, mas o método de coleta precisa ficar registrado para que o esforço seja
demonstrável.

**Consequência para a v1.** Apenas o ramo `SELF` é implementado. O cadastro de menor de
18 anos é bloqueado com mensagem clara. A estrutura completa entra desde já; o ramo
`GUARDIAN` entra na versão seguinte. Retrofitar o portão seria caro; deixar um ramo do
fluxo sem implementação é barato.

## Questão externa bloqueante

O uso dos dados do piloto em tese ou publicação caracteriza pesquisa com seres humanos e
provavelmente exige aprovação de CEP via Plataforma Brasil. Com adultos, o processo é
substancialmente mais leve; com menores, exige TCLE do responsável e termo de
assentimento do aluno. Coleta realizada antes da aprovação normalmente não é aproveitável.
Confirmar com o comitê da instituição antes de iniciar a coleta com finalidade de pesquisa.
