# Auditoria imutável — SPEC-007

Implementação autorizada de OPEN-007-01 em 07/11/2026, acompanhada na [Issue #101](https://github.com/Knzo25e/orquestrador-finops/issues/101). Baseada no Draft #102, preservando a revisão #97 → #100 → #102. As decisões de orçamento e revisão técnica das Specs 008/009 aguardam aprovação.

## Executar e consultar

Aplique V001 a V004, com a conta de migração, no banco escolhido. O servidor continua usando as variáveis `FINOPS_DB_*` e o HTTPS do [roteiro local](../../executar-mvp-local.md). Inicie com `java -jar target/orquestrador-finops-0.1.0-SNAPSHOT.jar` após configurar o JDK 25 e empacotar com `.\mvnw.cmd -B verify`.

Autenticado como Arquiteto Cloud, consulte `GET /api/v1/departamentos/{id}/intervencoes?limite=25`. Use `proximoCursor` em `apos` na página seguinte e acrescente `requisicaoId` para filtrar um pedido. A coleção vazia tem `proximoCursor` nulo. O limite máximo é 100. Parâmetros desconhecidos, repetidos ou inválidos recebem `400 ENTRADA_INVALIDA`. Departamento inexistente recebe `404 DEPARTAMENTO_NAO_ENCONTRADO`. Sessão ausente/expirada, CSRF e autorização seguem os contratos vigentes. Respostas do controller usam `no-store`.

```json
{
  "intervencoes": [{
    "operacaoId": "0116c1b1-c55e-4ce7-b9e2-0dad46269507",
    "responsavelId": "10000000-0000-4000-8000-000000000003",
    "acao": "MUTACAO_TESTE",
    "instante": "2026-11-07T12:34:56.123456Z",
    "justificativa": "Cenário exclusivo do teste automatizado",
    "departamentoId": "10000000-0000-4000-8000-000000000001",
    "requisicaoId": null,
    "resultado": "MUTACAO_EFETIVADA",
    "dados": {"saldoAnterior": "1000.00", "saldoPosterior": "999.00"}
  }],
  "proximoCursor": null
}
```

O exemplo demonstra o formato com uma mutação sintética exclusiva dos testes. Esta entrega expõe a consulta e fornece o coordenador interno aos futuros adaptadores das Specs 008/009. A repetição recupera o evento e o status HTTP originais. A serialização de respostas específicas de cada intervenção pertence aos contratos futuros. O banco registra o horário UTC com precisão de microssegundos. A administração pode alterar/remover dados, e o relógio do banco deve ser mantido sincronizado.

## Privilégios

Use uma conta de execução separada da migração. Depois de o administrador criar a conta com senha fornecida fora do repositório, conceda apenas os privilégios necessários. Para o banco descartável dos testes, os grants utilizados foram os seguintes.

```sql
GRANT SELECT ON finops_test.usuario TO 'finops_runtime'@'127.0.0.1';
GRANT SELECT, UPDATE ON finops_test.departamento TO 'finops_runtime'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE ON finops_test.requisicao TO 'finops_runtime'@'127.0.0.1';
GRANT SELECT, INSERT ON finops_test.item_requisicao TO 'finops_runtime'@'127.0.0.1';
GRANT SELECT ON finops_test.recurso_catalogo TO 'finops_runtime'@'127.0.0.1';
GRANT SELECT, INSERT ON finops_test.intervencao TO 'finops_runtime'@'127.0.0.1';
```

No ambiente de execução, ajuste o nome do banco e restrinja UPDATE às colunas necessárias. Confira `SHOW GRANTS` na própria conexão e `CURRENT_ROLE()` antes de usar a conta. Em 07/11, os grants efetivos correspondiam aos seis acima, com `USAGE` global e `CURRENT_ROLE() = NONE`. A conta não recebeu privilégios de escrita no schema, privilégios administrativos, DDL ou opções de concessão. Grants globais, de schema ou herdados por roles podem ampliar os privilégios de uma conta existente e devem ser removidos pelo administrador quando incompatíveis.

A migração não altera privilégios de contas existentes. A proteção é a negação de UPDATE/DELETE/DDL pelo MySQL à conta runtime, comprovada na suíte. As FKs usam a restrição padrão, sem cascatas. A administração do banco mantém acesso ao histórico e pode remover essa proteção. Limpeza de testes usa a identidade administrativa em base exclusiva.

## Testes reproduzíveis

Prepare um `finops_test` isolado com V001 a V004. Configure `FINOPS_DB_URL`, `FINOPS_DB_USER` e `FINOPS_DB_PASSWORD` para a conta administrativa da fixture. Configure `FINOPS_RUNTIME_DB_USER` e `FINOPS_RUNTIME_DB_PASSWORD` para a conta restrita no mesmo banco. Informe as senhas por prompt protegido na sessão, sem arquivos versionados. Com `JAVA_HOME` apontando para o JDK 25, execute.

```powershell
.\mvnw.cmd -B "-Dtest=*Test,CriacaoHttpMysqlIT,SessaoMysqlIT,CriacaoIdentidadeMysqlIT,ValidacaoAutorizadaMysqlIT,AuditoriaMysqlIT" verify
```

O ambiente medido usa Windows, Adoptium JDK 25.0.2, Maven Wrapper 3.10.0 e MySQL 8.0.44/InnoDB isolado em loopback. A suíte integrada aprovou 64 testes, sem falhas, erros ou testes ignorados, e empacotou o JAR. Inclui 13 cenários de auditoria, um de erros HTTP e um cenário HTTPS adicional. O HTTPS usa a conta restrita, certificado de teste confiável, sessão real e consultas do histórico com os dois perfis.

Uma rodada anterior encontrou o controller ausente da importação explícita do servidor. A composição foi corrigida e o cenário HTTPS passou na suíte final. A revisão estática do Astra também levou à preservação da negativa original quando o rollback da consulta falha, com teste específico. A comparação entre responsáveis distintos foi acrescentada e os 13 cenários de auditoria passaram novamente.

Os cinco verificadores JDBC anteriores aprovaram mais 164 verificações, sendo 47 de criação transacional, 84 de validação, 13 de consultas autorizadas, 10 de criação concorrente e 10 de vínculo coordenado. Esses verificadores exigem a conta de fixture `finops_test_app@localhost`, com leitura e escrita no banco descartável. Uma execução inicial foi recusada pela checagem dessa identidade, antes dos cenários. Após configurar a conta de teste exigida, todos passaram. Essa identidade é separada da runtime restrita. Para reproduzir, configure `FINOPS_DB_*` com a conta da fixture e execute após a compilação.

```powershell
$conector = Join-Path $env:USERPROFILE '.m2/repository/com/mysql/mysql-connector-j/9.7.0/mysql-connector-j-9.7.0.jar'
$classpath = "target/classes;$conector"
foreach ($verificador in @(
    'VerificarCriacaoTransacionalJdbc', 'VerificarValidacaoTransacionalJdbc',
    'VerificarConsultasAutorizadasJdbc', 'VerificarConcorrenciaCriacaoAutorizadaJdbc',
    'VerificarVinculoCoordenadoJdbc'
)) {
    & "$env:JAVA_HOME/bin/java.exe" -cp $classpath "br.mackenzie.finops.infraestrutura.persistencia.$verificador"
    if ($LASTEXITCODE -ne 0) { throw "Falha em $verificador" }
}
```

Depois das suítes e dos verificadores, `departamento`, `recurso_catalogo`, `requisicao`, `item_requisicao`, `usuario` e `intervencao` retornaram a zero registros na base isolada.

Os cenários verificam os comportamentos abaixo.

- Fotografia imutável, parâmetros comparados por conteúdo, justificativa normalizada, resposta original e ausência de novo efeito na repetição.
- Conflitos por parâmetros, ação, justificativa, alvo e responsável diferentes.
- Falha da inserção desfazendo saldo e estado na mesma transação, sem evento parcial.
- Commit incerto antes e depois da efetivação, para sucesso, negativa `409` e operação sem mudança. A exceção conserva o UUID, e a ação não é repetida automaticamente.
- Colisão concorrente real entre departamentos, com rollback integral do perdedor e nova tentativa somente de reconciliação. Falhas injetadas no rollback ou fechamento impedem nova tentativa.
- Revogação entre colisão simulada e reconciliação, com alteração de perfil/atividade em outra JVM.
- Identidade, departamento e requisição bloqueados, observados com `FOR UPDATE NOWAIT` em outra JVM. Repetição em processo independente recupera o recibo sem executar a ação.
- UPDATE, DELETE, TRUNCATE, DROP e ALTER negados ao runtime pelo MySQL com código 1142. A fotografia confirmada continua recuperável.
- Paginação descendente por instante e UUID, incluindo empates na precisão de microssegundos, filtros antes do limite, páginas finais, cursor inválido e reautorização por página.
- Erros públicos de entrada, autorização, recurso ausente, indisponibilidade e falha interna. Falha do rollback da consulta preserva a negativa original.

O teste de confirmação desconhecida injeta a falha de confirmação antes/depois de chamar o commit real. Não simula o transporte interrompido com commit remoto ainda em andamento. A consulta vazia continua sem servir como prova de rollback, e a reconciliação requer a entrada original. A cobertura usa mutações sintéticas em testes, enquanto os comandos reais de 008/009 exigirão testes próprios. O RNF-01 representativo continua pendente conforme seu [protocolo](../rnf-01-mysql.md).

## Próxima aprovação

Implementar SPEC-008 após aprovar OPEN-008-01, incluindo o contrato HTTP de ajuste e o resultado `TETO_INALTERADO`. Reutilizar esta base para confirmar ajuste e recibo juntos. Depois, aprovar as transições e os contratos de SPEC-009. Manter a meta de congelamento em 21/11 e entrega em 26/11.
