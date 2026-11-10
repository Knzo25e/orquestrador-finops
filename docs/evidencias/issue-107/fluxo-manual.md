# Fluxo manual — SPEC-009

OPEN-009-01, OPEN-009-02 e OPEN-009-03 aprovadas em 09/11/2026. Implementação na [Issue #107](https://github.com/Knzo25e/orquestrador-finops/issues/107), baseada no PR Draft #106. [Contrato aprovado](../../casos-de-uso/SPEC-009-revisao-liberacao-manual.md).

## Reprodução

Configure JDK 25 e MySQL/InnoDB exclusivo `finops_test`, com V001 a V005 aplicados. Use `FINOPS_DB_URL`, `FINOPS_DB_USER` e `FINOPS_DB_PASSWORD` para a conta administrativa da fixture, além de `FINOPS_RUNTIME_DB_USER` e `FINOPS_RUNTIME_DB_PASSWORD` para a conta restrita. O teste de migração cria e remove um schema exclusivo `finops_test_migracao_<UUID>`, exigindo esses privilégios da conta administrativa. A runtime conserva os [privilégios por coluna da SPEC-008](../issue-105/ajuste-teto.md) e somente SELECT/INSERT em `intervencao`. As senhas são fornecidas fora do Git.

```powershell
.\mvnw.cmd -B compile
.\mvnw.cmd -B "-Dtest=*Test,CriacaoHttpMysqlIT,SessaoMysqlIT,CriacaoIdentidadeMysqlIT,ValidacaoAutorizadaMysqlIT,AuditoriaMysqlIT,AjusteOrcamentarioMysqlIT,FluxoManualMysqlIT,MigracaoEstadosMysqlIT" verify
```

Em Windows, Adoptium JDK 25.0.2, Maven Wrapper 3.10.0 e MySQL 8.0.44 em loopback, a rodada integrada aprovou 94 testes e empacotou o JAR. Depois foram acrescentados três cenários JDBC identificados na revisão, e `./mvnw.cmd -B "-Dtest=FluxoManualMysqlIT" test` aprovou os 11 cenários dessa classe. São **97 testes distintos aprovados**, sem falhas, erros ou ignorados nos relatórios finais. A revisão do Astra ocorreu em leitura, e as suítes foram executadas pelo Codex.

## Cenários verificados

| Cenário solicitado | Evidência |
|---|---|
| Aprovação técnica sem débito | Três encaminhamentos da matriz no domínio, JDBC e HTTPS. Saldo anterior preservado |
| Rejeição justificada | Estado REJEITADO, justificativa e motivo original no evento; autoria desconhecida preservada em legado |
| Autorização explícita e débito único | Reserva negada, autorização ausente, autorização atual e liberação. Repetição e novo comando terminal verificados |
| Insuficiência seguida de ajuste | AGUARDA_AJUSTE, ajuste separado, revalidação e liberação explícita |
| Histórico das intervenções | HTTPS por departamento e requisição, responsável, UTC, decisões, fotografias e dez eventos no fluxo integrado |
| Negativa financeira | RESERVA_NEGADA preserva saldo e espera financeira |
| Condições financeiras alteradas | Nova insuficiência após outro débito; autorização anterior não reutilizável. Reserva dispensada exige outro comando |
| Concorrência | Duas liberações com UUID igual/diferente; ajuste versus liberação nas duas ordens; aprovação versus rejeição nas duas ordens; automática versus manual |
| Repetição, rollback e commit incerto | Fotografia técnica anterior recuperada após liberação posterior. Falha do recibo reverte revisão, rejeição e débito. Confirmação perdida prevalece sobre 200 e 409 |
| Segurança e regressão | Desenvolvedor recebe 403 para alvo existente/ausente antes da descoberta. CSRF, sessão, perfil revogado, bloqueio de identidade e fluxo automático preservados |

`FluxoManualTest` percorre 27 combinações de três estados de espera, três condições financeiras e três ações. A reconstituição e a guarda do automático são verificadas para os sete estados persistidos. O parser rejeita campos extras, JSON duplicado/trailing, decisão desconhecida e justificativa inválida. As rotas rejeitam decisões da etapa errada.

As disputas usam conexões independentes e pausa antes do commit. `FOR UPDATE NOWAIT` em outra JVM confirma os locks de usuário, departamento e requisição. Uma JVM adicional reconcilia o recibo de liberação, enquanto uma validação automática concorrente retorna JA_LIBERADA após o commit, sem segundo débito. Uma tentativa que aguardava o lock do usuário relê o perfil revogado e é negada.

A suíte HTTPS verifica detalhe, listagem resumida e repetição da criação em cada um dos quatro novos estados. O evento técnico conserva o motivo de reserva/insuficiência, e o DTO fora de revisão contém `motivoRevisao: null`. O callback usa preços históricos dos itens, inclusive após alteração do catálogo.

## Migração e compatibilidade

A primeira rodada detectou que AGUARDANDO_AUTORIZACAO_FINANCEIRA tem 33 caracteres e não cabia no VARCHAR(32) anterior. A V005 foi corrigida para VARCHAR(40). Após a correção, os testes passaram. Na base isolada já inicializada, a ampliação foi aplicada antes da nova rodada.

`MigracaoEstadosMysqlIT` parte de V001 a V004 em um schema novo, cria registros nos três estados anteriores, com autoria conhecida/desconhecida e itens, e aplica a V005 completa. A comparação anterior/posterior preservou estados, motivos, autoria, custos, sequência, preços e saldos. Os quatro estados novos foram reconstituídos. Estado desconhecido ou motivo indevido foram rejeitados pelo MySQL com código 3819. O schema temporário foi removido.

V005 é DDL e confirma implicitamente no MySQL. Prepare backup e aplique uma vez com a identidade de migração. Preserve os três códigos SQL anteriores. O código mantém as representações antigas do domínio e mapeia os sete códigos públicos por `EstadoRequisicao`.

## JAR em HTTPS e demonstração

O JAR empacotado foi iniciado separadamente com `java -jar target/orquestrador-finops-0.1.0-SNAPSHOT.jar --server.port=18449`, certificado de teste confiável e runtime restrita. Três clientes usaram cookies independentes, representando Desenvolvedor e duas sessões do Arquiteto.

Em base de demonstração exclusiva com teto/saldo 1000 e recurso de 12,50, crie e submeta pedidos de 72, 96 e 80 unidades. Os dois primeiros representam reserva e insuficiência, e o último serve à rejeição técnica. O [roteiro local](../../executar-mvp-local.md) traz os comandos no navegador.

1. Aprovar tecnicamente o pedido de 900, preservando saldo 1000 e encaminhando para autorização de reserva.
2. Rejeitar tecnicamente o pedido de 1000 e aprovar o de 1200, que aguarda ajuste.
3. Negar reserva para o pedido de 900. Tentar LIBERAR_SEM_RESERVA e obter AGUARDA_AUTORIZACAO.
4. AUTORIZAR_RESERVA em comando novo libera o pedido de 900 e deixa saldo 100.
5. Repetir a aprovação técnica e a autorização originais em outra sessão. Recuperar as fotografias originais, sem novo débito. Novo comando financeiro sobre o pedido liberado recebe 409 auditado.
6. Ajustar o teto para 2500 e preservar o compromisso de 900, obtendo saldo 1600.
7. AUTORIZAR_RESERVA para o pedido de 1200 retorna RESERVA_DISPENSADA, preservando saldo 1600.
8. LIBERAR_SEM_RESERVA em outro comando libera esse pedido e deixa saldo 400.
9. Consultar o histórico e conferir dez eventos. O Desenvolvedor recebeu 403 ao tentar a revisão técnica.

O ensaio do JAR aprovou esse fluxo. Os dados, os certificados e as credenciais efêmeros foram removidos. As seis tabelas do cenário voltaram a zero registros, sem schemas de migração restantes.

## Regressão e limites

Os cinco verificadores JDBC anteriores aprovaram 164 verificações, com a conta de fixture `finops_test_app@localhost`. Os programas de valores monetários, validação e reconstituição aprovaram mais 123 verificações, e o verificador concorrente aprovou três cenários. Os comandos e os nomes estão na [evidência da SPEC-008](../issue-105/ajuste-teto.md).

A perda de confirmação é injetada na chamada JDBC de commit antes/depois da efetivação, inclusive no HTTPS. Não foi interrompido o transporte com um commit remoto ainda em andamento. A ausência de evento continua sem comprovar rollback, e a reconciliação é explícita. Administradores podem alterar dados e privilégios do histórico. O RNF-01 representativo segue pendente no [protocolo existente](../rnf-01-mysql.md).

O #106 permanece Draft sobre a branch que reúne até #103 nas respectivas bases. A `main` em `001042f` contém apenas parte da cadeia. Esta entrega parte do #106 e mantém o restante da integração para decisão do responsável. Metas mantidas em 21/11 para congelamento e 26/11 para entrega.
