# Ajuste de teto orçamentário — SPEC-008

OPEN-008-01 e implementação aprovadas em 07/11/2026. [Issue #105](https://github.com/Knzo25e/orquestrador-finops/issues/105). [Contrato aprovado](../../casos-de-uso/SPEC-008-ajustar-teto.md).

## Comportamento entregue

`POST /api/v1/departamentos/{id}/ajustes-orcamentarios` exige sessão, CSRF e Arquiteto Cloud ativo relido do MySQL. O domínio calcula `comprometido = tetoAnterior − saldoAnterior` e `saldoNovo = novoTeto − comprometido`. Ajuste válido retorna `200 TETO_AJUSTADO`, teto igual retorna `200 TETO_INALTERADO` e redução abaixo dos compromissos retorna `409 TETO_ABAIXO_DO_COMPROMETIDO`. Teto igual ao comprometido é válido e zera o saldo. Cada decisão confirmada tem um recibo, inclusive negativa e ausência de mudança.

A transação reutiliza o coordenador da SPEC-007, em `READ COMMITTED`, com usuário `FOR SHARE` antes de departamento `FOR UPDATE`. O recibo é consultado antes de reavaliar o orçamento. Ajuste e evento são confirmados na mesma conexão. Repetição equivalente recupera fotografia e status originais após reautorização. UUID com teto ou justificativa incompatíveis recebe conflito. Preços históricos, estados e motivos dos pedidos são preservados pelo ajuste. A próxima validação utiliza o teto vigente para calcular a reserva.

O Astra revisou plano e implementação, incluindo preservação dos compromissos, envelopes de recusa, replay e confirmação desconhecida. A revisão estática terminou sem bloqueadores. Os testes foram executados pelo Codex.

## Reproduzir

Use JDK 25, o Maven Wrapper e um MySQL/InnoDB isolado chamado `finops_test`, com V001 a V004 aplicados. Esta entrega usa o esquema existente. Configure `FINOPS_DB_URL`, `FINOPS_DB_USER` e `FINOPS_DB_PASSWORD` para a conta administrativa da fixture, além de `FINOPS_RUNTIME_DB_USER` e `FINOPS_RUNTIME_DB_PASSWORD` para uma conta separada de execução no mesmo banco. Os testes removem seus próprios dados. Senhas são fornecidas fora do repositório. O [roteiro local](../../executar-mvp-local.md) descreve HTTPS e cadastro dos perfis.

Na conta de execução, use `SELECT` em `usuario`, `departamento`, `recurso_catalogo`, `requisicao` e `item_requisicao`, `INSERT` em `requisicao` e `item_requisicao`, `UPDATE (estado, motivo_revisao)` em `requisicao` e `UPDATE (teto_mensal, saldo_disponivel)` em `departamento`. Para `intervencao`, use somente `SELECT/INSERT`. Remova grants de escrita/DDL globais, de schema ou herdados que ampliem esses privilégios. A administração e a preparação usam identidades separadas.

```powershell
.\mvnw.cmd -B compile
.\mvnw.cmd -B "-Dtest=*Test,CriacaoHttpMysqlIT,SessaoMysqlIT,CriacaoIdentidadeMysqlIT,ValidacaoAutorizadaMysqlIT,AuditoriaMysqlIT,AjusteOrcamentarioMysqlIT" verify
```

Em 07/11/2026, com Windows, Adoptium JDK 25.0.2, Maven Wrapper 3.10.0 e MySQL 8.0.44 em loopback, passaram **79 testes**, sem falhas, erros ou testes ignorados. O JAR foi empacotado. A suíte contém dois testes novos de domínio, três de leitura da entrada, oito de integração do ajuste e dois cenários HTTPS adicionais. As conexões HTTPS usam a conta runtime com os privilégios por coluna descritos acima.

| Evidência | Resultado |
|---|---|
| Aumento, redução válida, teto igual ao comprometido, teto inalterado e recusa | Valores e recibos conferidos no MySQL |
| Decimal zero e máximo `99999999999999999.99` | Persistidos sem arredondamento ou ponto flutuante |
| Negativo, expoente, separador local, fração de centavo, número JSON, excesso de dígitos | Rejeitados antes da transação |
| Campos extras de identidade, UUID não canônico, JSON duplicado/trailing e corpo excessivo | Rejeitados pelo parser |
| Falha da inserção de auditoria após ajuste real | Teto e saldo revertidos, sem evento parcial |
| Perda de confirmação antes/depois do commit | JDBC cobre ajuste, negativa e teto inalterado. HTTPS cobre ajuste e negativa com `503`, UUID e reconciliação explícita |
| Repetição após outras operações e em outra sessão/JVM | Fotografia original e nenhum novo efeito ou evento |
| Conflito por teto/justificativa diferentes | `409 OPERACAO_ID_EM_CONFLITO` |
| Desenvolvedor, CSRF ausente, sessão ausente e Arquiteto desativado | Negados, com os códigos públicos vigentes |
| Ajuste primeiro, validação concorrente depois | Redução para 500 confirmada, pedido de 700 encaminhado por insuficiência, saldo 500 |
| Validação primeiro, redução concorrente depois | Débito de 700 confirmado, redução para 500 recusada, teto 1000 e saldo 300 |
| Reserva recalculada e preço histórico | Pedido de 112,50 criado antes do ajuste preserva o preço e, sob teto 125, é encaminhado por reserva |
| Imutabilidade do histórico | Regressão de SELECT/INSERT com UPDATE/DELETE/DDL negados ao runtime |

As corridas usam conexões independentes, pausa anterior ao commit, verificação do bloqueio por `FOR UPDATE NOWAIT` e espera da segunda operação antes de liberar a primeira. Os valores são conferidos após ambas terminarem. Uma JVM independente executa novo ajuste e recupera o recibo de uma operação anterior. A reautorização também é verificada após desativação por outra JVM.

`SHOW GRANTS` confirmou os privilégios por coluna, `SELECT/INSERT` no histórico, `USAGE` global e `CURRENT_ROLE() = NONE`. Após a suíte, os verificadores e o ensaio do JAR, as seis tabelas do cenário retornaram a zero registros na base isolada.

## Regressão financeira e JDBC

Além da suíte Maven, os cinco verificadores JDBC anteriores aprovaram 164 verificações. Use a conta de fixture `finops_test_app@localhost` em `finops_test`, conforme as restrições dos próprios verificadores, e o classpath descrito na [evidência da auditoria](../issue-101/auditoria-imutavel.md). A conta de fixture é distinta da runtime.

| Verificador | Aprovadas |
|---|---:|
| VerificarCriacaoTransacionalJdbc | 47 |
| VerificarValidacaoTransacionalJdbc | 84 |
| VerificarConsultasAutorizadasJdbc | 13 |
| VerificarConcorrenciaCriacaoAutorizadaJdbc | 10 |
| VerificarVinculoCoordenadoJdbc | 10 |
| VerificarValoresMonetarios | 34 |
| VerificarValidacaoOrcamentaria | 25 |
| VerificarReconstituicaoEntidades | 64 |
| VerificarConcorrenciaOrcamentaria | 3 cenários |

Os quatro últimos programas ficam em `br.mackenzie.finops.aplicacao`. Execute-os com `java -cp "target/classes;CAMINHO_DO_CONNECTOR_J"` e o nome completo da classe. Eles aprovam mais 123 verificações de domínio e três cenários concorrentes.

## JAR em HTTPS

Também foi iniciado `java -jar target/orquestrador-finops-0.1.0-SNAPSHOT.jar --server.port=18448`, com a conta runtime, certificado de teste confiável e dados efêmeros na base isolada. Foram usados três clientes com cookies independentes, representando Desenvolvedor e duas sessões do Arquiteto.

O fluxo criou e liberou um pedido de 700 sobre teto 1000, ajustou o teto para 1500 e confirmou saldo 800. Recusou teto 600, registrou teto inalterado 1500, reduziu para 900 com saldo 200 e recuperou a resposta original do aumento em outra sessão. O histórico retornou quatro eventos. O Desenvolvedor recebeu `403` ao tentar ajustar. O servidor de ensaio foi encerrado e os dados e credenciais efêmeros foram removidos.

Para reproduzir os valores, prepare uma base de demonstração exclusiva com teto/saldo 1000 e recurso unitário de 12,50, cadastre os dois perfis, crie um pedido de 56 unidades e valide-o. Na sessão do Arquiteto, use o exemplo de ajuste do roteiro local, com um UUID novo para cada decisão e o UUID original na repetição.

## Limites e próximas decisões

A perda de confirmação é injetada na chamada JDBC de commit, antes/depois da confirmação real, inclusive no teste HTTPS. O ensaio não interrompe o transporte com um commit remoto ainda em andamento. A ausência de evento continua sem comprovar rollback, e o resultado incerto exige reconciliação explícita. A administração do banco pode alterar ou remover a proteção do histórico. O RNF-01 representativo continua pendente no [protocolo existente](../rnf-01-mysql.md).

A concorrência com liberação automática está coberta. As transições e a liberação manual da SPEC-009 dependem de OPEN-009-01, OPEN-009-02 e OPEN-009-03 e terão testes próprios. Recomenda-se aprovar o estado `AGUARDANDO_LIBERACAO_FINANCEIRA`, a autorização de reserva restrita à tentativa atual, a negativa justificada e a matriz completa de revalidação, junto às rotas e ao mapeamento público propostos na [SPEC-009](../../casos-de-uso/SPEC-009-revisao-liberacao-manual.md).

Os PRs #97, #100, #102 e #103 foram integrados pelo GitHub nas respectivas bases. No início desta entrega, `main` em `001042f` continha #97 e #98, enquanto `codex/specs-manuais-rnf` em `7f065cf` reunia a cadeia até #103. A branch de implementação parte desse último ponto. A integração final da cadeia em `main` requer decisão do responsável. Manter 21/11 como meta interna de congelamento e 26/11 como entrega.
