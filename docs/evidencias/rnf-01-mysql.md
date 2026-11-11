# RNF-01 — Sondagem concorrente com MySQL

Execução em 06/11/2026 sobre o commit `f98f75f` do [PR #100](https://github.com/Knzo25e/orquestrador-finops/pull/100). Windows, Ryzen 7 5700X (16 threads lógicas), JDK 25.0.2, Python 3.12.7 e MySQL/InnoDB 8.0.44 em instância isolada no mesmo host. Uma JVM da aplicação, HTTPS em IPv4 loopback e sessão real. **A comprovação representativa do RNF-01 permanece pendente.**

## Reprodução

1. Siga a [preparação local](../executar-mvp-local.md) com um banco descartável vazio, V001–V003 e a conta `desenvolvedor` ligada ao departamento do exemplo. Use saldo e teto de `10000.00` para esse departamento e o recurso do exemplo com custo unitário `12.50`. Prepare um PKCS12 com SAN para `localhost` e `127.0.0.1` e exporte o certificado público PEM correspondente.
2. Empacote com `.\mvnw.cmd -B verify`, inicie o JAR e execute `python -B scripts/medir-rnf01.py 8443 caminho-do-certificado.pem`. Informe a senha de teste no prompt. Execute uma vez por base preparada.
3. O script mede cinco conexões por endereço, cronometrando resolução/conexão TCP e handshake TLS separadamente. A validação é medida pelo tempo do POST até a leitura completa da resposta em uma conexão HTTPS persistente em IPv4, com verificação de que o socket permanece o mesmo. Esse intervalo inclui HTTP, aplicação, JDBC, espera por locks e serialização.
4. Em cada combinação, preparar pedidos em `INICIAL` e aquecer com um pedido por cliente. Medir 32 pedidos novos, distribuídos entre um ou oito clientes com início sincronizado por rodada. Todos disputam o mesmo departamento. Rodar reserva (720 unidades) e insuficiência (801) antes das liberações (1 unidade). Exigir os resultados esperados em todas as amostras. O p95 usa o posto mais próximo.

## Conexões

| Endereço e etapa | Amostras | Mediana (ms) | Máximo (ms) |
|---|---:|---:|---:|
| `localhost` TCP | 5 | 2.034,95 | 2.047,19 |
| `localhost` TLS | 5 | 13,95 | 70,40 |
| `127.0.0.1` TCP | 5 | 0,28 | 21,57 |
| `127.0.0.1` TLS | 5 | 8,04 | 8,63 |

O Python resolveu `localhost` como `::1` antes de `127.0.0.1`, enquanto o servidor escutava apenas no IPv4. A diferença observada é compatível com a tentativa de conexão IPv6 antes do fallback e está concentrada no TCP. O diagnóstico não incluiu rastreamento de rede.

## POST de validação em conexão estabelecida

| Resultado | Clientes | Amostras | Mediana (ms) | p95 (ms) | Máximo (ms) |
|---|---:|---:|---:|---:|---:|
| Revisão por reserva | 1 | 32 | 12,64 | 17,93 | 113,07 |
| Revisão por reserva | 8 | 32 | 28,65 | 43,12 | 43,44 |
| Revisão por insuficiência | 1 | 32 | 10,75 | 11,45 | 11,77 |
| Revisão por insuficiência | 8 | 32 | 26,68 | 38,84 | 41,19 |
| Liberada | 1 | 32 | 10,33 | 12,35 | 119,85 |
| Liberada | 8 | 32 | 27,13 | 44,44 | 48,24 |

Nenhuma das 192 medições atingiu 2 s. O MySQL confirmou 73 pedidos de cada resultado, incluindo 27 aquecimentos no total, e saldo de R$ 9.087,50, correspondente a 73 débitos de R$ 12,50. Esta rodada cobre um host, um departamento, um item por pedido e duas cargas. Para o aceite representativo, definir volume e distribuição de itens e departamentos, repetir o ensaio sob carga sustentada e medir no ambiente da demonstração, mantendo a separação de TCP, TLS e POST.

## Regressão e integração dos Drafts

O [PR #95](https://github.com/Knzo25e/orquestrador-finops/pull/95) já integra `main` em `7412f3f`. O [PR #97](https://github.com/Knzo25e/orquestrador-finops/pull/97) continua sobre a branch do #95, e o #100 sobre a branch do #97. A simulação com `git merge-tree --write-tree origin/main origin/codex/submissao-consulta-mvp` terminou sem conflito e produziu a mesma árvore do #100. O [PR #98](https://github.com/Knzo25e/orquestrador-finops/pull/98) conserva o contrato aprovado.

O build em cópia limpa do #100 aprovou 49 testes e 164 verificações nos cinco programas JDBC de regressão. A base de testes voltou a zero registros nos cinco grupos consultados. Conferi os limites dos diffs, a documentação das decisões de sessão e coordenação do #97 e a submissão e consultas contra os contratos do #98. As marcações históricas de fases anteriores nas Specs continuam sujeitas à leitura junto do registro de decisão e do código atual. Os Drafts #97 e #100 seguem em revisão.
