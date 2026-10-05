# Carga do catálogo

> **Este é o único caminho de carga do currículo.** Não há migração Flyway que insira
> disciplinas ou tópicos, não há rota HTTP que escreva no catálogo, e nenhum outro código de
> produção chama a escrita do módulo `curriculum` além do importador. Inserir linhas por SQL à
> mão produz um catálogo sem registro em `catalog_import`, que não pode ser atribuído a nenhuma
> revisão dos arquivos (ADR 0014).

O importador é a própria aplicação, empacotada no mesmo jar, rodando sob o perfil `catalog`:
sem servidor web, executa um comando e encerra o processo com o código de saída do comando.
Os arquivos ficam em `catalog/<código_da_disciplina>/` e o formato está em `catalog/README.md`.

## Comando

A partir da raiz do repositório, com o jar empacotado e as credenciais do banco no ambiente:

```bash
./mvnw package

export SINAPSE_DB_URL=jdbc:postgresql://<host>:5432/<banco>
export SINAPSE_DB_USERNAME=<usuário>
export SINAPSE_DB_PASSWORD=<senha>

java -jar target/platform-0.1.0-SNAPSHOT.jar --spring.profiles.active=catalog catalog validate
java -jar target/platform-0.1.0-SNAPSHOT.jar --spring.profiles.active=catalog catalog diff
java -jar target/platform-0.1.0-SNAPSHOT.jar --spring.profiles.active=catalog catalog apply
```

- `validate` lê e valida os arquivos sem tocar no banco.
- `diff` mostra o que `apply` mudaria.
- `apply` valida de novo e aplica tudo numa transação.
- `--subject=<código>` restringe qualquer um dos três a uma disciplina.
- `--allow-topic-removal` (só em `apply`) remove tópicos que sumiram dos arquivos. Sem ele,
  esses tópicos são mantidos e o relatório avisa.
- O diretório lido é `catalog`, relativo ao diretório de onde o comando roda. Para outro
  lugar: `--sinapse.catalog.directory=<caminho>`.

As migrações rodam na partida, como na aplicação web: num banco vazio, qualquer um dos
comandos cria o esquema antes de executar. O banco precisa da extensão `citext`.

**Atenção:** durante os segundos em que o comando roda, as tarefas agendadas da plataforma
(o worker de geração de planos e as varreduras diárias) também estão ativas no processo, como
estariam na aplicação web. O perfil `catalog` não as desliga.

Os nomes acentuados aparecem como `?` na saída se o terminal não estiver em UTF-8. É só a
exibição; o banco grava o texto correto. Para ver certo: `java -Dstdout.encoding=UTF-8 -jar …`.

## Códigos de saída

| Código | Significado |
|---|---|
| 0 | comando executado |
| 1 | os arquivos não validam; nada foi escrito |
| 2 | linha de comando ilegível; o uso é impresso |

Uma remoção de tópico recusada porque o tópico já tem histórico de estudo ou sessão planejada
desfaz a transação inteira e também não escreve nada.

## Idempotência

Rodar `apply` duas vezes seguidas com os mesmos arquivos não duplica disciplina, tópico nem
aresta. A segunda execução imprime `nothing to change: 35 topic(s) and 35 edge(s) already
match the files`. Ela **grava, sim, uma nova linha em `catalog_import`**, com contagens zeradas:
cada execução fica registrada, inclusive as que não mudaram nada.

A revisão registrada é o último commit do git que tocou o diretório do catálogo. Com
alterações não commitadas, a revisão ganha o sufixo `-dirty` e o comando avisa. Fora de um
repositório git, fica `unknown`.

## Verificado em 05/10/2026

Contra um PostgreSQL 16 local descartável, com o catálogo de exemplo `MED-ANAT`:

```
$ java -jar target/platform-0.1.0-SNAPSHOT.jar --spring.profiles.active=catalog catalog apply
catalog apply  (revision fbad00f86082bc9000d044c353361b49776755a6)
  MED-ANAT  (new subject)
    + topic  terminologia  Terminologia anatômica e planos de referência  (position 1, SHORT)
    …
  totals: topics +35 ~0 =0, edges +35 ~0 -0 =0
  recorded as import 10119424-e2ff-4a05-9623-ec4b55abdbb0
$ echo $?
0
$ java -jar target/platform-0.1.0-SNAPSHOT.jar --spring.profiles.active=catalog catalog apply
catalog apply  (revision fbad00f86082bc9000d044c353361b49776755a6)
  nothing to change: 35 topic(s) and 35 edge(s) already match the files
  recorded as import fac0e205-4d7a-4a7a-915c-346e0eaa1d37
$ echo $?
0
```

Resultado no banco: 1 disciplina, 35 tópicos, 35 arestas, 2 linhas em `catalog_import`. Com a
aplicação web no ar sobre o mesmo banco, `GET /api/v1/subjects` devolveu a disciplina e
`GET /api/v1/subjects/{id}/topics`, os 35 tópicos em ordem (ver `CONTRATO_API_SINAPSE.md`,
seção 7).

O teste `CatalogCommandLineStartupIntegrationTest` sobe a aplicação do mesmo jeito (perfil
`catalog`, argumentos `catalog apply`) e falha se o contexto do importador deixar de subir.
