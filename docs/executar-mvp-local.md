# Executar a aplicação local com sessão

Sessão e identidade na [Issue #96](https://github.com/Knzo25e/orquestrador-finops/issues/96). Submissão e consultas na [Issue #99](https://github.com/Knzo25e/orquestrador-finops/issues/99), conforme os contratos aprovados no [PR #98](https://github.com/Knzo25e/orquestrador-finops/pull/98).

## Preparar MySQL e HTTPS

1. Em um banco local `finops_dev`, aplique [V001 a V005](../src/main/resources/db/mysql) em ordem. Em uma base já migrada, aplique somente as migrações faltantes. V005 amplia a coluna de estado para os novos códigos e altera checks. Prepare backup antes de aplicar DDL no banco real. Cadastre pelo menos um departamento e um recurso. Exemplo para uma base vazia:

   ```sql
   INSERT INTO departamento (id, nome, teto_mensal, saldo_disponivel)
   VALUES ('10000000-0000-4000-8000-000000000001', 'Engenharia', 1000.00, 1000.00);
   INSERT INTO recurso_catalogo (id, nome, custo_mensal)
   VALUES ('10000000-0000-4000-8000-000000000002', 'Recurso de demonstração', 12.50);
   ```

2. Configure JDK 25 em `JAVA_HOME` e `FINOPS_DB_URL`, `FINOPS_DB_USER` e `FINOPS_DB_PASSWORD` na sessão. Exemplo de URL local `jdbc:mysql://localhost:3306/finops_dev?sslMode=REQUIRED`. A conta que roda o servidor precisa de leitura de usuários, departamentos, catálogo, pedidos e itens, inserção de pedidos e itens e atualização de `departamento.teto_mensal`, `departamento.saldo_disponivel`, `requisicao.estado` e `requisicao.motivo_revisao`. No histórico `intervencao`, conceda somente `SELECT/INSERT`, sem permissões herdadas de alteração, remoção ou DDL. Use uma conta de preparação com permissão de inserir usuários para o cadastro. Alterações administrativas de atividade, perfil e departamento escrevem na mesma linha de `usuario` protegida pelas operações, em uma transação.
3. Use um PKCS12 externo ao repositório com certificado para o endereço de loopback usado pelo cliente. O exemplo inclui `localhost` e `127.0.0.1`. Para gerar um certificado local, em PowerShell:

   ```powershell
   $chave = Join-Path $env:LOCALAPPDATA 'finops-local.p12'
   $senhaTls = Read-Host 'Senha do PKCS12 local' -AsSecureString
   $env:FINOPS_TLS_PASSWORD = [Net.NetworkCredential]::new('', $senhaTls).Password
   & "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -alias finops -keyalg RSA `
       -storetype PKCS12 -keystore $chave -storepass:env FINOPS_TLS_PASSWORD `
       -dname 'CN=localhost' -ext 'SAN=dns:localhost,ip:127.0.0.1' -validity 30
   $env:FINOPS_TLS_KEYSTORE = ([uri]$chave).AbsoluteUri
   ```

   Para o certificado local, configure a confiança no cliente usado na demonstração. O servidor escuta em loopback por padrão. Mantenha o PKCS12, as senhas e os dados de sessão fora do Git.

## Carregar o catálogo simulado (opcional)

Após aplicar V001–V005 e cadastrar o recurso original do exemplo, execute a [carga do catálogo](../scripts/sql/carregar-catalogo-demo.sql) na base de demonstração. Use uma conta de preparação que já tenha leitura e inserção em `recurso_catalogo`. No PowerShell, a partir da raiz do repositório, ajuste host, porta, usuário e banco para sua instalação. Informe a senha no prompt do MySQL.

```powershell
& 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' `
    --host=127.0.0.1 --port=3306 --user=usuario_de_preparacao --password `
    --database=finops_dev --default-character-set=utf8mb4 `
    --execute='SOURCE scripts/sql/carregar-catalogo-demo.sql'
```

Uma base que tinha apenas o recurso de **R$ 12,50** passa a exibir nove itens. Reexecutar a carga com os mesmos dados deixa o catálogo igual. Se um UUID da carga já estiver ocupado por um item com nome ou preço divergente, o MySQL informa conflito e reverte todas as inserções da execução. Confira o registro conflitante antes de tentar novamente. O UUID do recurso original `10000000-0000-4000-8000-000000000002` fica fora da carga. As requisições existentes continuam usando seus preços unitários gravados.

### Referências dos custos mensais simulados

Estimativas fixas para a demonstração acadêmica, consultadas em **12/11/2026** para **US East (N. Virginia), `us-east-1`**. Instâncias sob demanda com **730 horas por mês** e capacidade de armazenamento mantida por um mês. Usei a **PTAX de venda de 11/11/2026, R$ 5,5606 por US$ 1**, com arredondamento do resultado em reais para centavos. [Período do AWS Pricing Calculator](https://docs.aws.amazon.com/pricing-calculator/latest/userguide/migrate-SMC.html) · [Série PTAX de venda do Banco Central](https://api.bcb.gov.br/dados/serie/bcdata.sgs.1/dados?formato=json&dataInicial=11%2F11%2F2026&dataFinal=11%2F11%2F2026)

| Recurso | Premissas de cálculo | US$/mês | R$/mês |
|---|---|---:|---:|
| EC2 `t3.micro` | Linux, 2 vCPU, 1 GiB, US$ 0,0104/h | 7,592 | 42,22 |
| EC2 `t3.small` | Linux, 2 vCPU, 2 GiB, US$ 0,0208/h | 15,184 | 84,43 |
| EC2 `t3.medium` | Linux, 2 vCPU, 4 GiB, US$ 0,0416/h | 30,368 | 168,86 |
| RDS `db.t4g.micro` | MySQL 8.4 Single-AZ, 2 vCPU, 1 GiB RAM, US$ 0,016/h + 20 GiB gp3 | 13,980 | 77,74 |
| RDS `db.t4g.small` | MySQL 8.4 Single-AZ, 2 vCPU, 2 GiB RAM, US$ 0,032/h + 50 GiB gp3 | 29,110 | 161,87 |
| S3 Standard · 100 GB | US$ 0,023/GB-mês | 2,300 | 12,79 |
| S3 Standard · 500 GB | US$ 0,023/GB-mês | 11,500 | 63,95 |
| EBS gp3 · 100 GiB | US$ 0,08/GiB-mês, 3.000 IOPS e 125 MiB/s incluídos | 8,000 | 44,48 |

O gp3 do RDS foi calculado a US$ 0,115 por unidade de armazenamento/mês. As referências consideram somente a instância para EC2 e somente o armazenamento para S3. Ficam fora impostos, tráfego, operações S3, créditos excedentes de CPU, backups cobrados à parte e descontos. O FinOps lê os valores em reais do MySQL local. A carga e a operação do portal dispensam conexão com a AWS. O orçamento real do projeto permanece **R$ 0**.

- [Tarifas EC2 Linux sob demanda](https://b0.p.awsstatic.com/pricing/2.0/meteredUnitMaps/ec2/USD/current/ec2-ondemand-without-sec-sel/US%20East%20%28N.%20Virginia%29/Linux/index.json)
- [Lista regional do RDS](https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/AmazonRDS/20261029070635/us-east-1/index.json), SKUs `U4AUM7WC9VCSGAY9`, `T3Q9GS9SNACZAUSP` e `H4R2WBBMX6VRJG4S`
- [Lista regional do S3](https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/AmazonS3/20261109154310/us-east-1/index.json), SKU `WP9ANXZGBYYSGJEA`, primeira faixa de armazenamento
- [Preço e configuração do EBS gp3](https://aws.amazon.com/blogs/storage/migrate-your-amazon-ebs-volumes-from-gp2-to-gp3-and-save-up-to-20-on-costs/)

## Empacotar, cadastrar as contas e iniciar

```powershell
.\mvnw.cmd -B verify
$jar = 'target/orquestrador-finops-0.1.0-SNAPSHOT.jar'
& "$env:JAVA_HOME\bin\java.exe" -jar $jar cadastrar-demonstracao 10000000-0000-4000-8000-000000000001
& "$env:JAVA_HOME\bin\java.exe" -jar $jar
```

Execute o cadastro uma vez em terminal interativo. O comando pede as senhas de `desenvolvedor` e `arquiteto` sem eco, usa o codificador do Spring Security e grava as duas contas na mesma transação. Use senhas distintas com pelo menos 12 caracteres e até 72 bytes UTF-8. Para iniciar o servidor, use a conta de banco com os privilégios de execução.

Abra [https://127.0.0.1:8443/login](https://127.0.0.1:8443/login) com o certificado do exemplo. O login abre o painel do perfil vigente. O painel do Desenvolvedor permite consultar o catálogo, criar, submeter e acompanhar requisições. O painel do Arquiteto reúne orçamento, revisão, decisões financeiras e histórico de auditoria. O Desenvolvedor ativo com departamento cria em `INICIAL` e submete e consulta pedidos próprios do departamento atual. O Arquiteto consulta pedidos por UUID ou lista por departamento, incluindo registros legados.

No console do navegador, na mesma origem e autenticado como Desenvolvedor, execute o fluxo com o recurso do exemplo. Guarde o UUID e a sequência de itens ao repetir a criação.

```javascript
const pedido = {
  requisicaoId: crypto.randomUUID(),
  itens: [{ recursoId: "10000000-0000-4000-8000-000000000002", quantidade: 2 }]
};
const { cabecalho, token } = await fetch("/api/v1/csrf").then(r => r.json());
const criacao = await fetch("/api/v1/requisicoes", {
  method: "POST",
  headers: { "Content-Type": "application/json", [cabecalho]: token },
  body: JSON.stringify(pedido)
});
console.log(criacao.status, await criacao.json());
if (!criacao.ok) throw new Error("Criação falhou.");

const rota = `/api/v1/requisicoes/${pedido.requisicaoId}`;
const validar = () => fetch(`${rota}/validacao`, {
  method: "POST",
  headers: { [cabecalho]: token }
});
const validacao = await validar();
console.log(validacao.status, await validacao.json());
console.log(await fetch(rota).then(r => r.json()));
console.log(await fetch("/api/v1/requisicoes?limite=25").then(r => r.json()));
```

Em base nova, a quantidade 2 projeta R$ 25,00. A criação retorna `201`, a validação retorna `LIBERADA` e o detalhe mostra `PROVISIONAMENTO_LIBERADO`. Repita `validar()` para obter `JA_LIBERADA` sem outro desconto. Em outra sessão, o Arquiteto pode chamar `GET /api/v1/requisicoes?departamentoId=10000000-0000-4000-8000-000000000001`. Use `proximoCursor` no parâmetro `apos` quando houver outra página.

Na navegação, a sessão ausente redireciona ao formulário. A API retorna provisoriamente `403 SESSAO_NECESSARIA` para sessão ausente ou expirada, `403 CSRF_INVALIDO` para token ausente ou inválido em mutação com sessão, e `403 ACESSO_NEGADO` por falta de permissão. Login e logout por POST também exigem token. Em `/logout`, o navegador exibe a confirmação do Spring Security. O identificador da sessão muda no login, a sessão expira após 30 minutos de inatividade e o cookie usa `Secure`, `HttpOnly` e `SameSite=Lax`.

## Regressão com MySQL

Use um `finops_test` isolado, com V001 a V005 e uma conta administrativa de teste com leitura e escrita nas tabelas do cenário em `FINOPS_DB_USER` e `FINOPS_DB_PASSWORD`. Os testes criam e removem os próprios registros. A suíte prepara um certificado temporário e verifica a sessão em HTTPS. Configure também `FINOPS_RUNTIME_DB_USER` e `FINOPS_RUNTIME_DB_PASSWORD` com uma conta separada de execução. Ela deve ter somente `SELECT/INSERT` no histórico e os privilégios explícitos das demais tabelas. A suíte HTTPS usa essa conta restrita. `MigracaoEstadosMysqlIT` exige que a conta administrativa possa criar e remover um schema temporário exclusivo com prefixo `finops_test_migracao_`. [Privilégios e reprodução da auditoria](evidencias/issue-101/auditoria-imutavel.md)

```powershell
.\mvnw.cmd -B "-Dtest=*Test,CriacaoHttpMysqlIT,SessaoMysqlIT,CriacaoIdentidadeMysqlIT,ValidacaoAutorizadaMysqlIT,AuditoriaMysqlIT,AjusteOrcamentarioMysqlIT,FluxoManualMysqlIT,MigracaoEstadosMysqlIT" verify
```

Os testes exercitam transações reais, incluindo disputa pelo UUID, troca de vínculo, desativação, rollback da validação e bloqueios observados por outra JVM. A perda de confirmação é simulada antes e depois de efetivar o commit.

Em 06/11/2026, a suíte da Issue #99 aprovou 49 testes com JDK 25.0.2 e MySQL 8.0.44. Os cinco verificadores JDBC de regressão aprovaram 164 verificações. O JAR empacotado reproduziu o fluxo HTTPS com uma conta de banco restrita aos privilégios descritos acima.

Na sondagem local de três submissões por endereço, os POSTs levaram 30 a 43 ms via `127.0.0.1` e cerca de 2,05 a 2,07 s via `localhost`. A conexão TCP levou 4 ms via IPv4 direto e cerca de 2,04 s via `localhost` nessa máquina. Use IPv4 direto para reproduzir a demonstração local. O ensaio representativo do RNF-01 com MySQL e concorrência permanece no plano de verificação.

## Ajustar teto e consultar auditoria

Em uma sessão do Arquiteto Cloud, execute o exemplo abaixo com um departamento existente. O Desenvolvedor recebe `403 ACESSO_NEGADO` ao tentar ajustar. A operação preserva os compromissos existentes, e o histórico inclui ajustes, negativas de negócio e teto inalterado. Guarde o corpo completo para uma repetição ou reconciliação explícita.

```javascript
const departamentoAjuste = "10000000-0000-4000-8000-000000000001";
const ajusteTeto = {
  operacaoId: crypto.randomUUID(),
  novoTeto: "1500.00",
  justificativa: "Adequação à demanda do departamento"
};
const csrfAjuste = await fetch("/api/v1/csrf").then(r => r.json());
const enviarAjuste = () => fetch(`/api/v1/departamentos/${departamentoAjuste}/ajustes-orcamentarios`, {
  method: "POST",
  headers: { "Content-Type": "application/json", [csrfAjuste.cabecalho]: csrfAjuste.token },
  body: JSON.stringify(ajusteTeto)
});
const respostaAjuste = await enviarAjuste();
console.log(respostaAjuste.status, await respostaAjuste.json());
console.log(await fetch(`/api/v1/departamentos/${departamentoAjuste}/intervencoes?limite=25`).then(r => r.json()));
```

Repetir `enviarAjuste()` recupera a resposta original após reautorização. Um teto abaixo do comprometido recebe `409 TETO_ABAIXO_DO_COMPROMETIDO` e gera um recibo sem alteração financeira. Um novo comando de negócio usa outro UUID. Um `503 CONFIRMACAO_INTERVENCAO_DESCONHECIDA` requer reconciliação explícita com o mesmo UUID e corpo. Consulte os [resultados e limites da SPEC-008](evidencias/issue-105/ajuste-teto.md).

## Revisão técnica e liberação manual

Na sessão do Desenvolvedor, crie e submeta um pedido que alcance a reserva ou exceda o saldo, conforme o fluxo anterior. Na sessão do Arquiteto, use o UUID desse pedido e uma justificativa em cada comando. Cada nova decisão recebe um `operacaoId`. Guarde o corpo original para repetição ou reconciliação explícita.

```javascript
const pedidoManual = "UUID_DO_PEDIDO_EM_REVISAO";
const csrfManual = await fetch("/api/v1/csrf").then(r => r.json());
const aprovacaoTecnica = {
  operacaoId: crypto.randomUUID(), decisao: "APROVAR",
  justificativa: "Recurso tecnicamente adequado à demanda"
};
const autorizacaoFinanceira = {
  operacaoId: crypto.randomUUID(), decisao: "AUTORIZAR_RESERVA",
  justificativa: "Uso de reserva autorizado para a tentativa atual"
};
const enviarManual = (etapa, corpo) => fetch(`/api/v1/requisicoes/${pedidoManual}/${etapa}`, {
  method: "POST",
  headers: { "Content-Type": "application/json", [csrfManual.cabecalho]: csrfManual.token },
  body: JSON.stringify(corpo)
});
const revisaoManual = await enviarManual("revisao-tecnica", aprovacaoTecnica);
console.log(revisaoManual.status, await revisaoManual.json());
// Confirme o resultado técnico e o saldo antes de executar a etapa financeira.
const liberacaoManual = await enviarManual("liberacao-financeira", autorizacaoFinanceira);
console.log(liberacaoManual.status, await liberacaoManual.json());
```

Use `REJEITAR` na revisão para rejeição justificada. Na etapa financeira, `NEGAR_RESERVA` preserva a pendência. Se faltar saldo, ajuste o teto em outra operação. Quando a reserva for dispensada, use um comando novo com `LIBERAR_SEM_RESERVA`. A aprovação técnica preserva o saldo. O histórico por departamento e pedido permite conferir todas as decisões. [Roteiro demonstrado, testes e limites](evidencias/issue-107/fluxo-manual.md).

## Demonstração no notebook, sem custo

Execute o JAR com HTTPS e MySQL locais conforme o início deste guia. Conecte o notebook ao projetor e abra [https://127.0.0.1:8443/login](https://127.0.0.1:8443/login) com o certificado local confiável. Prepare dois perfis independentes do navegador para alternar entre Desenvolvedor e Arquiteto Cloud.

Use a base fictícia do exemplo, com teto e saldo iniciais de R$ 1.000,00 e recurso de R$ 12,50. O roteiro a seguir usa apenas a interface do portal.

| Etapa | Ação no portal e resultado |
|---|---|
| Desenvolvedor | Consulte o catálogo. Em **Nova requisição**, salve A com 72 unidades, B com 96 e C com 80. Confira os estados iniciais no detalhe e submeta cada pedido. A e C vão à revisão pela reserva. B vai à revisão por insuficiência de saldo. |
| Arquiteto | Selecione Engenharia. Aprove tecnicamente A e B e rejeite C, sempre com justificativa. Confira que o saldo continua em R$ 1.000,00. |
| Reserva | Em A, negue a reserva. Tente liberar sem reserva e confira a pendência. Depois autorize o uso da reserva. O saldo passa a R$ 100,00. Use **Repetir comando registrado** para recuperar o recibo com o mesmo UUID e confirmar que o saldo ficou igual. |
| Ajuste | Tente ajustar o teto para R$ 800,00 para mostrar a recusa auditada. Ajuste para R$ 2.500,00. Os R$ 900,00 comprometidos são preservados, e o saldo passa a R$ 1.600,00. |
| Liberação | Em B, escolha autorizar reserva para reavaliar a situação. O resultado indica reserva dispensada. Em seguida registre **Liberar sem uso da reserva**. O saldo final é de R$ 400,00. |
| Auditoria | Abra o histórico. O roteiro completo gera dez eventos, incluindo a decisão recusada de ajuste. Abra um evento para mostrar responsável, UTC, justificativa e fotografia financeira. Filtre pelo pedido aberto e volte ao histórico do departamento. |
| Desenvolvedor | Volte ao primeiro perfil e clique em **Atualizar requisições** para acompanhar as decisões finais. |

Quando uma resposta se perder, use **Consultar situação atual** e depois, se necessário, **Reenviar a mesma operação**. O portal conserva o UUID e o corpo original na aba. Para renovar a sessão sem perder a pendência, use **Entrar em outra aba** e retorne com a mesma conta, perfil e vínculo. Ao fechar ou recarregar a aba, os dados em memória se perdem. Consulte o pedido e o histórico pelo identificador antes de tomar outra decisão. Uma consulta sem resultado ainda não comprova rollback.

## Acesso opcional pelo computador da faculdade

O orçamento aprovado é **R$ 0**. A apresentação principal usa o notebook da equipe. O [Cloudflare Quick Tunnel](https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/) é uma opção gratuita e temporária para usar o navegador de outro computador. A URL muda a cada execução, o serviço não tem garantia de disponibilidade e a conexão depende da rede do notebook e da faculdade.

Após preparar o notebook, faça um ensaio antes de usar o túnel na apresentação. Use apenas o banco fictício e as contas de demonstração. Mantenha o Spring Boot e o MySQL em loopback, com a conta de execução restrita. Exponha somente a porta HTTPS da aplicação. Use o nome coberto pelo certificado e passe a CA confiável de origem ao conector. Para uma versão do cloudflared que suporte a restrição por e-mail, prefira o acesso por PIN aos participantes do ensaio. [Parâmetros TLS da origem](https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/configure-tunnels/origin-parameters/) · [Download oficial do cloudflared](https://developers.cloudflare.com/tunnel/downloads/)

No notebook, configure `$caOrigem` com o caminho do PEM da CA que assinou o certificado do Spring. Para o certificado autoassinado do guia, exporte o certificado público com `keytool -exportcert -rfc -alias finops -keystore $chave -storepass:env FINOPS_TLS_PASSWORD -file $caOrigem` e confirme no ensaio que ele é aceito como âncora de confiança. Configure `$emailParticipante` com o endereço autorizado para receber o PIN. Com o aplicativo em execução, o comando previsto é:

```powershell
.\cloudflared.exe tunnel --url https://127.0.0.1:8443 --origin-server-name localhost `
    --origin-ca-pool $caOrigem --allowed-mail $emailParticipante
```

No computador da faculdade, abra no navegador a URL HTTPS temporária que o comando imprimir. Confirme a validade do certificado público e ensaie login, redirecionamentos, cookie Secure, CSRF, logout e uma ação de cada perfil. Encerre o túnel ao terminar. Se o conector não validar o certificado de origem ou a rede não der acesso estável, use o notebook e o projetor. Mantenha a validação TLS ativa em todos os trechos. A compatibilidade do portal foi exercitada atrás de um proxy TLS local. O acesso real por Quick Tunnel ainda está pendente.

## Verificação do portal

Os testes da camada Java continuam no comando de regressão deste guia. Para verificar a proteção contra envio duplicado, a recuperação de operações incertas e a entrada monetária, execute:

```powershell
node --test scripts/testar-operacoes-portal.mjs
```

Na validação desta etapa, passaram 100 testes Java com MySQL, cinco testes JavaScript, 164 verificações JDBC, 123 de domínio e três cenários concorrentes. O ensaio no Edge com o JAR e proxy HTTPS local confirmou os dois perfis, a criação de múltiplos itens, o duplo clique, a reconciliação após perda simulada de resposta na criação e na liberação, a sessão ausente e o bloqueio do reenvio por outra conta. O saldo final foi de R$ 400,00 com dez eventos. O teste de corte real de transporte durante commit remoto e o ensaio representativo do RNF-01 permanecem pendentes.
