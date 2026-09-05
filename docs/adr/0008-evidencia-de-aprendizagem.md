# ADR 0008 — Evidência de aprendizagem na v1 e os limites do autorrelato

**Data:** 04 de setembro de 2026
**Status:** aceita, com decisão científica pendente (D4)

## Contexto

O motor de otimização precisa de evidência sobre o estado de aprendizagem do aluno para
reotimizar. A tese precisa de evidência para sustentar suas afirmações. Não são
necessariamente a mesma evidência.

Exercícios e tentativas de resposta estão explicitamente fora do escopo da v1. Sem eles, o
que o Registro de aprendizagem captura é: qual tópico, por quanto tempo, quando, se veio do
plano ou não, e uma avaliação subjetiva de recuperação informada pelo próprio aluno.

## Decisão

A v1 registra sessões de estudo executadas com avaliação estruturada de recuperação em
quatro níveis (`AGAIN`, `HARD`, `GOOD`, `EASY`), registrada no fechamento da sessão.

Este dado é tratado explicitamente como medida de **adesão e percepção**, e não de
**retenção**.

Sessões fechadas são imutáveis. Sessões fora do plano são estruturalmente idênticas às
planejadas.

## Justificativa

### Por que a avaliação em quatro níveis, e não nada

Custa quase nada e produz trajetória em vez de ponto isolado. A sequência de avaliações de
um mesmo tópico ao longo do tempo é sinal utilizável pelo Core para modular espaçamento,
mesmo sendo subjetiva. Uma escala ordinal curta é mais confiável que uma escala contínua ou
percentual, porque reduz a variação introduzida pela interpretação da escala.

### Por que ela não sustenta a afirmação central da tese

Julgamento de aprendizagem é sabidamente mal calibrado. A literatura sobre prática de
recuperação é consistente em mostrar que estudo passivo produz sensação de domínio superior
à retenção efetiva, enquanto recuperação ativa produz o oposto. Um sistema avaliado por
autorrelato pode, portanto, receber avaliação melhor justamente na condição que ensina pior.

Consequência direta: com autorrelato como única medida, o piloto pode sustentar

- que os planos gerados são seguíveis (adesão),
- que os alunos os percebem como úteis (satisfação),

e **não pode** sustentar que o AG melhora a retenção, que é a afirmação de interesse.

### Por que isso não bloqueia o código

A avaliação de recuperação e uma futura tentativa de exercício são sinais do mesmo tipo,
associados a um tópico e a um instante. O modelo atual é subconjunto do modelo com medida
objetiva; acrescentá-la depois não exige reescrever o Registro de aprendizagem.

## Consequências

**Risco científico explícito, registrado como decisão pendente D4.** A medida objetiva de
retenção exige instrumento desenhado junto com o protocolo experimental. Isto interage com
J2: dados coletados antes da aprovação do comitê de ética normalmente não são aproveitáveis,
e coletar com o instrumento errado desperdiça o parecer e a janela do piloto.

Recomendação de sequência: definir o instrumento de medida **antes** de submeter ao comitê,
e não depois de começar a coletar.

**No máximo uma sessão em progresso por aluno**, garantido por índice parcial. Permitir duas
corromperia a evidência de duração, que é o dado mais básico do contexto.

**`planned_session_id` sem chave estrangeira.** Nem `planning` nem `learningrecord` podem
depender um do outro (regra R2), e uma sessão registrada fora do plano precisa ser
estruturalmente idêntica a uma registrada a partir dele. O custo aceito é a possibilidade de
referência órfã, detectável por rotina de consistência. As alternativas eram criar
dependência entre os dois módulos, em qualquer das duas direções, ou duplicar o vínculo.

**Sessões fechadas são imutáveis**, por *trigger*. Correção de uma sessão encerrada é um
registro novo, não uma edição. Evidência editável não é evidência.
