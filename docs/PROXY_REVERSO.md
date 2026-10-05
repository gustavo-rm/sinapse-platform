# Implantação atrás de proxy reverso: `trusted-proxies`

## O que a propriedade faz

`sinapse.rate-limit.trusted-proxies` é a lista de endereços (IP ou bloco CIDR) cujo cabeçalho
`X-Forwarded-For` a plataforma aceita. É lida em um único lugar, `ClientAddressResolver`, que
decide a que endereço atribuir cada requisição:

- se o par direto da conexão (`getRemoteAddr()`) **não** está na lista, o endereço do cliente é
  esse par, e `X-Forwarded-For` é ignorado por inteiro;
- se o par direto **está** na lista, o cabeçalho é lido da direita para a esquerda, descartando
  os endereços que também são proxies confiáveis. O primeiro endereço não confiável é o cliente.
  Tudo que estiver à esquerda dele foi escrito pelo próprio cliente e não vale nada.

`server.forward-headers-strategy` continua `none`: o Tomcat não reescreve o endereço, e esse
componente é o único que consulta o cabeçalho.

O endereço resolvido é usado em três lugares:

| Uso | Onde |
|---|---|
| Limite de taxa das rotas anônimas, contado **por endereço** | `RateLimitFilter`, fase `BEFORE_AUTHENTICATION` |
| Evidência legal do consentimento (endereço de origem do ato) | `ConsentController`, `AccountController` |
| Hash do endereço guardado na sessão | `SessionController` |

As rotas autenticadas contam o limite por conta, não por endereço.

## O que quebra se a lista ficar vazia atrás de um proxy

O padrão é a lista vazia, e o padrão é seguro: nenhum cliente consegue escolher o próprio
endereço. Mas, atrás de um proxy reverso, o par direto de **toda** requisição é o proxy. Então:

1. **Todos os clientes anônimos caem no mesmo balde de limite de taxa, por rota.** Com a
   configuração base, isso significa, para a plataforma inteira:
   - 5 cadastros por hora (`identity-registration`);
   - 10 tentativas de login a cada 5 minutos (`identity-authentication`);
   - 10 verificações de e-mail e 10 confirmações de redefinição de senha a cada 5 minutos;
   - 5 pedidos de redefinição de senha por hora;
   - 10 resgates e 20 prévias de convite a cada 10 minutos, na contagem por origem.

   Basta um usuário errar a senha algumas vezes para bloquear o login de todos, e qualquer um
   pode fazer isso de propósito.
2. **A evidência de consentimento registra o endereço do proxy** como o do titular, e deixa de
   provar de onde veio o ato.
3. **O hash de endereço das sessões** passa a ser o mesmo para todas.

Isso foi comprovado em 05/10/2026, com a aplicação no perfil `local` e a lista vazia: 11
tentativas de login vindas do mesmo par, cada uma com um `X-Forwarded-For` diferente
(`198.51.100.1` a `198.51.100.11`), foram contadas como um cliente só. O limite de 10 estourou e
as últimas receberam `429`.

Na partida, quando nenhum perfil de desenvolvimento (`local`, `test`) está ativo e a lista está
vazia, a aplicação registra um **aviso** (`WARN`) dizendo isso. Ela não recusa a partida, porque
só a implantação sabe se há proxy na frente.

## Como configurar

Liste o endereço do proxy que está **de fato** na frente da aplicação, e nada além disso.

> **Exemplo FICTÍCIO.** `192.0.2.10` pertence ao bloco reservado para documentação (RFC 5737)
> e não é o endereço de nenhum proxy real. Substitua pelo endereço que o seu proxy usa para
> falar com a aplicação.

Em um arquivo de configuração da implantação:

```yaml
sinapse:
  rate-limit:
    trusted-proxies:
      - 192.0.2.10/32        # FICTÍCIO: endereço do proxy reverso
```

Ou por variável de ambiente:

```bash
SINAPSE_RATELIMIT_TRUSTEDPROXIES=192.0.2.10/32   # FICTÍCIO
```

Regras:

- **Nunca** use um bloco que cubra a internet (`0.0.0.0/0`, `::/0`) nem a rede inteira de um
  provedor. Quem estiver dentro do bloco passa a poder dizer qual é o endereço do cliente.
- Use o endereço que a **aplicação** vê como par direto. Se proxy e aplicação estão no mesmo
  host, costuma ser `127.0.0.1/32`. Se estão em contêineres, é o endereço do contêiner do
  proxy na rede interna. Confira antes de configurar.
- Com mais de uma camada (balanceador e proxy, por exemplo), liste todas: a leitura da direita
  para a esquerda descarta cada camada confiável até chegar ao cliente.
- O proxy precisa **acrescentar** o endereço do cliente ao cabeçalho, e não repassar o que o
  cliente mandou. No nginx: `proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;`.
- Uma entrada inválida faz a partida falhar, de propósito: um erro de digitação não pode
  ampliar a confiança sem ninguém perceber.

Depois de configurar, o aviso de partida deixa de aparecer.

Frontend e plataforma na mesma origem, atrás do mesmo proxy, não mudam nada disso: o que
importa é o endereço que a aplicação vê na conexão.
