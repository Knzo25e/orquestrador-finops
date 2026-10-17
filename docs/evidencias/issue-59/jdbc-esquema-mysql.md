# Verificação da conexão JDBC e do esquema MySQL — Issue #59

## Objetivo

Preparar a conexão JDBC e o esquema inicial da etapa B da SPEC-005.

## Ambiente

- Data: 17/10/2026.
- Windows e PowerShell.
- Eclipse Adoptium JDK 25.0.2.
- Maven Wrapper, compilação com release 25.
- MySQL Server 8.0.44.
- MySQL Connector/J 9.7.0.
- Bancos separados: finops_dev e finops_test.

Este documento resume as saídas locais compartilhadas pelo
responsável pela alteração. Não é uma transcrição integral dos logs.

## Implementação

- Dependência com.mysql:mysql-connector-j:9.7.0.
- ConfiguracaoBanco recebe URL, usuário e senha por variáveis de ambiente.
- FabricaConexoes abre conexões; o chamador é responsável pelo fechamento.
- VerificarConfiguracaoBanco exercita a configuração sem acessar o banco.
- VerificarConexaoBanco verifica banco, conta, versões e uso de TLS.
- Script inicial: src/main/resources/db/mysql/V001__criar_tabelas.sql.

## Esquema e permissões

As quatro tabelas foram criadas em finops_test e finops_dev:

- departamento;
- recurso_catalogo;
- requisicao;
- item_requisicao.

O script utiliza InnoDB.

Em finops_test, foram conferidas 17 restrições:
quatro chaves primárias, três chaves estrangeiras e dez CHECK.

As permissões foram conferidas por SHOW GRANTS:

- finops_app@localhost: SELECT, INSERT e UPDATE em finops_dev.
- finops_test_app@localhost: SELECT, INSERT, UPDATE e DELETE em finops_test.

Os grants apresentados não incluem permissões de criação ou
exclusão de tabelas. Não foi realizado teste de tentativa de DDL
com essas contas.

## Conexão JDBC

A execução de VerificarConexaoBanco confirmou:

- Banco finops_test.
- Conta finops_test_app@localhost.
- Servidor MySQL 8.0.44.
- Driver Connector/J 9.7.0.
- Uso de TLS.
- Encerramento da conexão sem erro.

A conexão local utilizou sslMode=REQUIRED.
Esse modo exige criptografia, mas não comprova validação
da identidade do servidor.

A senha foi informada por entrada protegida no PowerShell,
sem inclusão no código. A variável FINOPS_DB_PASSWORD foi
removida ao terminar a execução.

## Verificações Java

A compilação de 30 arquivos Java com release 25 foi concluída.

| Classe | Verificações aprovadas |
|---|---:|
| Main | 10 |
| VerificarValoresMonetarios | 34 |
| VerificarValidacaoOrcamentaria | 25 |
| VerificarConcorrenciaOrcamentaria | 6 |
| VerificarRepositoriosMemoria | 44 |
| VerificarConcorrenciaRepositorios | 9 |
| VerificarIntegracaoRepositorios | 39 |
| VerificarConcorrenciaIntegracao | 9 |
| VerificarReconstituicaoEntidades | 64 |
| VerificarConfiguracaoBanco | 14 |
| Total | 254 |

As classes foram executadas explicitamente por seus métodos main.
Não são executadas automaticamente por mvn test.

Uma tentativa de execução falhou porque JAVA_HOME estava vazio.
Após configurar a variável, todas as classes terminaram sem erro.

O teste de conexão e os testes manuais de SQL não estão incluídos
na contagem das 254 verificações.

## Testes manuais de restrições

Executados no Workbench, exclusivamente em finops_test:

1. Saldo 101.00 para teto 100.00:
   rejeitado com erro 3819, ck_departamento_saldo.
2. Requisição vinculada a departamento inexistente:
   rejeitada com erro 1452, fk_requisicao_departamento.
3. Requisição em REVISAO_PENDENTE sem motivo:
   rejeitada com erro 3819, ck_requisicao_motivo.

Foi executado ROLLBACK após cada cenário.

No terceiro cenário, um departamento válido havia sido inserido
na transação. Após o ROLLBACK, a consulta pelo seu identificador
retornou contagem zero.

## Limites

- A conexão JDBC foi verificada no ambiente local descrito.
- Apenas três cenários de rejeição do esquema foram exercitados.
- Não há repositórios JDBC de entidades implementados nesta entrega.
- Não foi verificada recuperação de entidades entre processos.
- O ROLLBACK manual não comprova a transação financeira da aplicação.
- Não foram verificados idempotência, reconciliação de commit,
  concorrência financeira no banco ou desempenho com MySQL.
- A etapa B da SPEC-005 permanece incompleta.