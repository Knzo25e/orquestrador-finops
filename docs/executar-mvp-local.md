# Executar a aplicação local com sessão

Entrega da [Issue #96](https://github.com/Knzo25e/orquestrador-finops/issues/96), conforme OPEN-004-02 e OPEN-004-03 da [SPEC-004](casos-de-uso/SPEC-004-interface-aplicacao.md).

## Preparar MySQL e HTTPS

1. Em um banco local `finops_dev`, aplique [V001, V002 e V003](../src/main/resources/db/mysql) em ordem. Em uma base já migrada, aplique somente as migrações faltantes. Cadastre pelo menos um departamento e um recurso. Exemplo para uma base vazia:

   ```sql
   INSERT INTO departamento (id, nome, teto_mensal, saldo_disponivel)
   VALUES ('10000000-0000-4000-8000-000000000001', 'Engenharia', 1000.00, 1000.00);
   INSERT INTO recurso_catalogo (id, nome, custo_mensal)
   VALUES ('10000000-0000-4000-8000-000000000002', 'Recurso de demonstração', 12.50);
   ```

2. Configure JDK 25 em `JAVA_HOME` e `FINOPS_DB_URL`, `FINOPS_DB_USER` e `FINOPS_DB_PASSWORD` na sessão. Exemplo de URL local `jdbc:mysql://localhost:3306/finops_dev?sslMode=REQUIRED`. A conta que roda o servidor precisa de leitura de usuários, departamentos e catálogo e leitura e inserção de pedidos e itens. Use uma conta de preparação com permissão de inserir usuários para o cadastro das contas. Alterações administrativas de atividade, perfil e departamento escrevem na mesma linha de `usuario` protegida pelas operações, em uma transação.
3. Use um PKCS12 externo ao repositório com certificado para `localhost`. Para gerar um certificado local, em PowerShell:

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

Abra [https://localhost:8443/login](https://localhost:8443/login). O login leva ao catálogo. Ambos os perfis podem consultá-lo. Somente o Desenvolvedor ativo com departamento pode criar pedidos, e o servidor determina autoria e departamento. O POST persiste em `INICIAL`. Submissão, consulta de pedidos e ações manuais aguardam os respectivos contratos.

No console do navegador, na mesma origem e já autenticado, o exemplo abaixo cria um pedido com o recurso do exemplo. Guarde o UUID e a sequência de itens ao repetir.

```javascript
const pedido = {
  requisicaoId: crypto.randomUUID(),
  itens: [{ recursoId: "10000000-0000-4000-8000-000000000002", quantidade: 2 }]
};
const { cabecalho, token } = await fetch("/api/v1/csrf").then(r => r.json());
const resposta = await fetch("/api/v1/requisicoes", {
  method: "POST",
  headers: { "Content-Type": "application/json", [cabecalho]: token },
  body: JSON.stringify(pedido)
});
console.log(resposta.status, await resposta.json());
```

Na navegação, a sessão ausente redireciona ao formulário. A API retorna provisoriamente `403 SESSAO_NECESSARIA` para sessão ausente ou expirada, `403 CSRF_INVALIDO` para token ausente ou inválido em mutação com sessão, e `403 ACESSO_NEGADO` por falta de permissão. Login e logout por POST também exigem token. Em `/logout`, o navegador exibe a confirmação do Spring Security. O identificador da sessão muda no login, a sessão expira após 30 minutos de inatividade e o cookie usa `Secure`, `HttpOnly` e `SameSite=Lax`.

## Regressão com MySQL

Use um `finops_test` isolado, com V001 a V003 e uma conta de teste com leitura e escrita nas tabelas do cenário. Os testes criam e removem os próprios registros. A suíte prepara um certificado temporário e verifica a sessão em HTTPS.

```powershell
.\mvnw.cmd -B "-Dtest=*Test,CriacaoHttpMysqlIT,SessaoMysqlIT,CriacaoIdentidadeMysqlIT" verify
```

Os testes da criação usam transações reais, incluindo disputa pelo UUID, mudança de vínculo entre tentativas e bloqueios observados por outra JVM. A perda de confirmação é simulada antes e depois de efetivar o commit.

Em 06/11/2026, passaram 39 testes com JDK 25.0.2 e MySQL 8.0.44. Também passaram os verificadores JDBC de criação (47), validação financeira (84), concorrência de criação autorizada (10) e vínculo coordenado anterior (10). O JAR empacotado cadastrou as duas contas por terminal e iniciou em HTTPS. Em sessões distintas, ambos consultaram o catálogo, o Arquiteto recebeu 403 na criação e o Desenvolvedor obteve 201 e 200 na repetição. O MySQL confirmou dois hashes bcrypt, um pedido e o saldo inicial preservado.
