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

## Empacotar, cadastrar as contas e iniciar

```powershell
.\mvnw.cmd -B verify
$jar = 'target/orquestrador-finops-0.1.0-SNAPSHOT.jar'
& "$env:JAVA_HOME\bin\java.exe" -jar $jar cadastrar-demonstracao 10000000-0000-4000-8000-000000000001
& "$env:JAVA_HOME\bin\java.exe" -jar $jar
```

Execute o cadastro uma vez em terminal interativo. O comando pede as senhas de `desenvolvedor` e `arquiteto` sem eco, usa o codificador do Spring Security e grava as duas contas na mesma transação. Use senhas distintas com pelo menos 12 caracteres e até 72 bytes UTF-8. Para iniciar o servidor, use a conta de banco com os privilégios de execução.

Abra [https://127.0.0.1:8443/login](https://127.0.0.1:8443/login) com o certificado do exemplo. O login leva ao catálogo. Ambos os perfis podem consultá-lo. O Desenvolvedor ativo com departamento cria em `INICIAL` e submete e consulta pedidos próprios do departamento atual. O Arquiteto consulta pedidos por UUID ou lista por departamento, incluindo registros legados.

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
