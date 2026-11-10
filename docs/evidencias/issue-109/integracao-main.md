# Integração controlada do MVP — Issue #109

Preparação autorizada em 10/11/2026. O merge final em `main` aguarda autorização explícita. [Issue #109](https://github.com/Knzo25e/orquestrador-finops/issues/109).

## Revisão em ordem

Primeiro foi revisado o [PR #106](https://github.com/Knzo25e/orquestrador-finops/pull/106), de `7f065cf` a `12e3d3e`, contra a SPEC-008. Em seguida, o [PR #108](https://github.com/Knzo25e/orquestrador-finops/pull/108), de `12e3d3e` a `2299aad`, contra a SPEC-009 e a auditoria da SPEC-007. Durante a preparação, o GitHub registrou #106 integrado em codex/specs-manuais-rnf e #108 integrado em codex/ajuste-teto-orcamentario. Ambos estão fechados como merged, enquanto main permanece na mesma base. As revisões do Codex e do Astra não identificaram bloqueador comprovado. O Astra fez análise estática, e o Codex executou as verificações abaixo.

No #106, foram conferidos os contratos decimais e HTTP, preservação de compromissos, teto inalterado, recusa auditada e transação coordenada. No #108, foram conferidos a matriz manual, autorização anterior à descoberta do alvo, locks, fotografia original no replay, histórico e compatibilidade de V005, reconstituição e DTOs. A aprovação técnica preserva saldo, e a autorização financeira vale para a tentativa atual.

## Integração executada

Foi criada uma worktree limpa e a branch `codex/integracao-mvp-main` a partir de `origin/main` em `001042f192ced878a5d3e0cdcee41a407d15576b`. Foram feitos cinco merges reais com `--no-ff`, sem conflito, preservando também os commits de merge existentes nas bases.

| Commit de integração | Referência incorporada | Ponta incorporada |
|---|---|---|
| `154160a` | `origin/codex/identidade-coordenada-mvp` — cadeia até #100 | `6d9d829` |
| `ab0f6d1` | `origin/codex/submissao-consulta-mvp` — cadeia até #102 | `49bc2ae` |
| `db9713a` | `origin/codex/specs-manuais-rnf` — cadeia até #103 | `7f065cf` |
| `27e43ba` | `origin/codex/ajuste-teto-orcamentario` — #106 | `12e3d3e` |
| `5f34da0` | `origin/codex/revisao-liberacao-manual` — #108 | `2299aad` |

Os registros adicionais foram preservados em 9180136 (#106) e 163486d (#108). O diff dos arquivos executáveis e dos testes para 5f34da0 é vazio.

O commit efetivamente compilado, testado e demonstrado foi `5f34da016a9eef76b1191344426edf061c4be0c1`, com árvore `b50f45db14f67b52d400a01665bcfa8a1f5772d0`. Este relatório foi acrescentado posteriormente. Os registros de merge do GitHub também foram incorporados depois dos testes, sem mudança de código, testes, dependências, Wrapper ou AGENTS. A igualdade do código, testes, dependências, Wrapper e AGENTS com a ponta do #108 foi conferida por diff. A ancestralidade de `main` e das cinco referências incorporadas foi conferida com `git merge-base --is-ancestor`. Os commits originais conservam seus objetos, autoria e histórico.

## Diff final

Antes deste relatório, o diff para `main` tinha 88 arquivos, com 61 adições e 27 modificações, sem exclusões. São 59 arquivos em `src/main`, 15 de testes, 12 documentos e dois outros arquivos, README e o script de medição do RNF-01. O conteúdo corresponde à submissão/consulta, auditoria, ajuste de teto e fluxo manual que ainda faltavam na `main`. Este relatório acrescenta um documento ao diff.

A única diferença de conteúdo entre a integração e a ponta `2299aad` é a preservação de `docs/review/contratos-submissao-consulta.md`, proveniente do #98 na `main`, além deste relatório. O documento preservado tem o mesmo blob `5501f02a12035f1d67ffe2c15094ae6d2f4757a1` na base e na integração. Não houve mudança inesperada no código, alteração de dependências, commits duplicados por cherry-pick ou exclusão de arquivos da base. `git diff --check` passou.

A alteração local preexistente em `.vscode/settings.json` permanece na cópia original e ficou fora da integração. As branches compartilhadas e `main` conservaram suas referências. A nova branch contém os merges e este registro operacional.

## Regressão completa na árvore integrada

Ambiente de 10/11/2026, Windows, Adoptium JDK 25.0.2, Maven Wrapper 3.10.0 e MySQL 8.0.44/InnoDB exclusivo em loopback. Foram usadas uma conta administrativa de fixture, a conta exigida pelos verificadores antigos e uma runtime separada, com privilégios por coluna e somente SELECT/INSERT em `intervencao`. As senhas e os certificados efêmeros ficaram fora do repositório.

Com `JAVA_HOME`, `FINOPS_DB_*` e `FINOPS_RUNTIME_DB_USER/PASSWORD` configurados, foram executados na worktree integrada os comandos abaixo.

```powershell
.\mvnw.cmd -B -version
.\mvnw.cmd -B "-Dtest=*Test,CriacaoHttpMysqlIT,SessaoMysqlIT,CriacaoIdentidadeMysqlIT,ValidacaoAutorizadaMysqlIT,AuditoriaMysqlIT,AjusteOrcamentarioMysqlIT,FluxoManualMysqlIT,MigracaoEstadosMysqlIT" verify
```

A execução completa aprovou **97 testes**, com zero falhas, erros e testes ignorados, e empacotou o JAR. Os 11 testes de `FluxoManualMysqlIT` foram executados juntos, incluindo os três cenários que antes haviam sido adicionados após a rodada inicial de 009. Os oito testes de sessão/HTTPS passaram com a conta runtime.

| Verificador executado na integração | Aprovadas |
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

Total adicional de 164 verificações JDBC, 123 de domínio e três cenários concorrentes. Os cinco primeiros programas usam `br.mackenzie.finops.infraestrutura.persistencia`, a conta `finops_test_app@localhost` em `finops_test` e o Connector/J no classpath. Os demais usam `br.mackenzie.finops.aplicacao`. Os comandos completos estão na [evidência da SPEC-008](../issue-105/ajuste-teto.md).

## V005 após V004 com dados anteriores

`MigracaoEstadosMysqlIT` passou na suíte integrada. O teste cria um schema descartável com V001 a V004, insere pedidos nos três estados anteriores, com autoria conhecida/desconhecida e itens, e então aplica a V005 completa. A comparação antes/depois preservou estados, motivos, autoria, custos, ordem, preços, quantidades, subtotais, teto e saldo.

Foram reconstituídos os quatro estados novos, e os checks rejeitaram estado desconhecido e motivo indevido com erro MySQL 3819. A coluna passou a VARCHAR(40), comportando o estado de 33 caracteres. A consulta final confirmou tamanho 40 e nenhum schema temporário de migração restante. A runtime não recebeu privilégio para migrar. V005 é aplicada uma vez pela conta de migração e envolve commit implícito de DDL.

## JAR integrado em HTTPS

O JAR produzido pelo build integrado foi iniciado separadamente com `java -jar target/orquestrador-finops-0.1.0-SNAPSHOT.jar --server.port=18450`, MySQL restrito e certificado de teste confiável. SHA-256 do artefato executado `1f0c6dfd59e01f0d2ba28188f33a1e8b379acda521830de2a91f6c97ac0ad0b8`.

Três clientes com cookies independentes autenticaram o Desenvolvedor e duas sessões do Arquiteto. O ensaio criou e submeteu pedidos de 900, 1200 e 1000 sobre teto 1000. Verificou revisão por reserva/insuficiência, aprovação técnica sem débito, rejeição justificada, reserva negada e exigência de autorização. A autorização explícita liberou o pedido de 900 e deixou saldo 100.

A repetição da aprovação e da liberação em outra sessão recuperou as respostas originais. Um novo comando sobre o pedido liberado retornou 409 auditado. O ajuste para teto 2500 preservou o compromisso de 900 e deixou saldo 1600. A reserva foi dispensada para o pedido de 1200, que exigiu outro comando LIBERAR_SEM_RESERVA e deixou saldo 400. O histórico retornou dez eventos, incluindo o motivo original. O Desenvolvedor recebeu 403 na tentativa manual.

O ensaio passou. Servidor, dados e certificados de teste foram encerrados/removidos. Após suíte, verificadores e JAR, as seis tabelas do cenário voltaram a zero registros. SHOW GRANTS confirmou privilégio por coluna e SELECT/INSERT no histórico, USAGE global e CURRENT_ROLE igual a NONE. A suíte comprovou negação de UPDATE, DELETE e DDL sobre o histórico.

## Limites e estratégia para autorização

Continuam pendentes o ensaio representativo do RNF-01 e o corte real do transporte com commit remoto em andamento. Os testes de confirmação desconhecida injetam a falha antes/depois do commit real. A administração do banco pode alterar a proteção do histórico. Esses limites não foram considerados integralmente comprovados.

Após autorização explícita do responsável, conferir se a `main` ainda corresponde à base `001042f` e se a branch integrada mantém o SHA revisado. Havendo nova alteração, incorporá-la por merge nessa branch e verificar o delta antes de liberar a integração. Recomenda-se merge do PR de integração com commit de merge, preservando os commits originais e os merges desta preparação, sem squash, cherry-pick ou rebase.

Os PRs #106 e #108 estão integrados nas respectivas bases. O PR de integração deve aguardar autorização para ficar pronto e ser integrado. Após a autorização e execução, conferir a ancestralidade na `main` e reconciliar o estado dos PRs dependentes com a integração comprovada.

Depois da integração definitiva, priorizar estabilização, ensaio da demonstração, revisão dos requisitos acadêmicos e preparação da apresentação. Metas mantidas de congelamento em 21/11 e entrega em 26/11.
