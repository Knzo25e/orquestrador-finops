# Verificação dos cadastros JDBC — Issue #61

## Base

SPEC-005, etapa B.
Conexão e esquema inicial integrados pelo PR #60.

## Ambiente

- Data: 20/10/2026.
- Windows e PowerShell.
- Eclipse Adoptium JDK 25.0.2.
- Maven Wrapper, compilação com release 25.
- MySQL 8.0.44 e Connector/J 9.7.0, identificados na Issue #59.
- Banco utilizado: finops_test.
- Conta: finops_test_app@localhost.
- URL local configurada com sslMode=REQUIRED.

Este documento resume as saídas locais compartilhadas pelo
responsável pela alteração. Não é uma transcrição integral dos logs.

## Implementação

- DepartamentoRepositoryJdbc.
- RecursoCatalogoRepositoryJdbc.
- ValidacaoCadastroJdbc.
- FalhaPersistenciaException.

Os repositórios recebem a conexão do chamador.
Não executam commit, rollback ou fechamento dessa conexão.

Os comandos utilizam parâmetros. Comandos e resultados são
fechados com try-with-resources.

## Compilação e regressão

A compilação de 37 arquivos Java com release 25 foi concluída.

Foram aprovadas:

- 254 verificações anteriores, sem acesso ao banco.
- 28 verificações novas em VerificarValidacaoCadastroJdbc.

Total sem banco: 282 verificações.

Os testes de limites exercitaram UUIDs, nomes, pontos de código
Unicode, valores monetários, relação entre saldo e teto
e rejeição de conexão nula nos construtores.

Esses testes dos validadores não comprovam, isoladamente,
a rejeição de todas as entradas inválidas pelos repositórios
nem o armazenamento de todos os valores de fronteira no MySQL.

## Verificações JDBC

VerificarCadastrosJdbc aprovou 14 verificações:

- Ausência inicial dos identificadores gerados para o teste.
- Recuperação de departamento com UUID, nome, teto e saldo preservados.
- Recuperação de recurso com UUID, nome e custo preservados.
- Recuperação em novas instâncias Java.
- Rejeição de departamento duplicado.
- Rejeição de recurso duplicado.
- Preservação dos registros após rejeição de duplicatas.
- Preservação da conexão aberta e do autocommit desabilitado.
- Rollback dos dois cadastros pelo chamador.
- Ausência dos cadastros desfeitos confirmada em nova conexão.
- Falha ao consultar departamento com conexão fechada.
- Falha ao consultar recurso com conexão fechada.
- Falha ao cadastrar departamento com conexão fechada.
- Falha ao cadastrar recurso com conexão fechada.

Os testes com conexão fechada confirmaram FalhaPersistenciaException,
sem confundir falha de acesso com Optional.empty().

## Persistência entre processos

VerificarPersistenciaCadastros foi executado em três processos
Java sequenciais, utilizando os mesmos identificadores:

1. gravar: inseriu departamento e recurso e confirmou por commit.
2. consultar: recuperou os dados após o primeiro processo terminar.
3. limpar: removeu os registros do teste e confirmou a limpeza.

Foram preservados identidade, nome, teto 1000.00,
saldo 725.50 e custo do recurso 125.25.

Identificadores desta execução:

- Departamento: df2c80af-3970-4b73-92f8-3d83e662e467.
- Recurso: 2f037551-dd99-429d-80ac-b91a4abcc2fe.

A limpeza foi limitada aos identificadores e nomes do teste.
Nenhuma tabela foi apagada ou recriada.

Esse cenário é registrado separadamente das 282 verificações
sem banco e das 14 verificações JDBC.

## Ocorrência de ambiente

A primeira tentativa de conexão retornou SQLState 08S01.
O serviço MYSQL80 estava parado e a porta 3306 inacessível.

Após iniciar o serviço, as verificações JDBC passaram.
Não foi necessário alterar a senha ou o código para resolver
essa ocorrência.

## Limites

- As verificações utilizam classes com método main;
  não são executadas automaticamente por mvn test.
- Não houve reinício do servidor MySQL durante o cenário.
- A conexão fechada é um cenário controlado, não uma simulação
  de todas as falhas de rede ou de confirmação.
- Não foram exercitados todos os caminhos de dados armazenados
  inconsistentes.
- O rollback verificado abrange cadastros, não a operação financeira.
- Não há persistência de requisições e itens nesta entrega.
- Não foram verificados concorrência financeira, idempotência,
  reconciliação de commit ou desempenho com MySQL.
- A SPEC-005 permanece incompleta.