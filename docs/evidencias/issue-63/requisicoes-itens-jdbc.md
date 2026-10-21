# Armazenamento JDBC de requisições e itens — Issue #63

## Ambiente

- Data: 21/10/2026.
- Windows e PowerShell.
- Eclipse Adoptium JDK 25.0.2.
- Compilação pelo Maven Wrapper, com release 25 e UTF-8.
- Compilação de 44 arquivos Java concluída com BUILD SUCCESS.
- MySQL local, banco finops_test.
- Conta finops_test_app@localhost.
- Connector/J 9.7.0.
- URL configurada com sslMode=REQUIRED.
- Credenciais fornecidas pelo ambiente, sem armazenamento no repositório.

Este documento resume as saídas locais compartilhadas pelo responsável
pela alteração. Não é uma transcrição integral dos logs.

## Resultados

| Conjunto | Verificações aprovadas |
|---|---:|
| Regressão sem banco | 282 |
| VerificarCadastrosJdbc | 14 |
| VerificarRequisicoesJdbc | 35 |
| Total | 331 |

Um cenário adicional verificou persistência de requisição e itens
entre processos Java. Ele não está incluído na contagem acima.

As verificações são executadas por classes com método main.
Não são executadas automaticamente por mvn test.

## Cadastro e recuperação

As verificações JDBC exercitaram:

- Ausência para UUID não cadastrado.
- Lista vazia para departamento sem requisições.
- Preservação de identidade, departamento, estado, motivo nulo e total.
- Recuperação de novas instâncias.
- Preservação da ordem e de recursos repetidos em itens distintos.
- Preservação de preços históricos e subtotais.
- Cadastro e recuperação sem alteração do saldo.
- Listagem de pedido completo por departamento.
- Proteção da lista retornada contra inclusão.
- Preservação da conexão aberta e de sua configuração.
- Rejeição de UUID duplicado sem substituição do cabeçalho ou dos itens.

## Preço histórico

O pedido foi criado com dois itens do mesmo recurso:

- Quantidade 2, preço unitário 12.50 e subtotal 25.00.
- Quantidade 3, preço unitário 12.50 e subtotal 37.50.
- Total da requisição: 62.50.

Após alterar o custo do catálogo para 99.00, a recuperação apresentou
o custo atual no recurso e preservou o preço histórico 12.50 nos itens,
os subtotais, o total e o saldo do departamento.

## Transações e falha parcial

O cadastro com autocommit habilitado foi rejeitado sem inserir registros.

Uma conexão intermediária de teste simulou SQLException antes da
execução da inserção do segundo item. O cabeçalho e o primeiro item
foram efetivamente inseridos no MySQL dentro da transação.

O teste confirmou:

- Propagação da falha como FalhaPersistenciaException.
- Existência do cabeçalho e de exatamente um item antes do rollback.
- Preservação da conexão aberta e do controle da transação.
- Remoção dos registros pelo rollback executado pelo chamador.
- Ausência da gravação parcial em uma nova conexão.

A falha foi simulada no teste. Não representa queda real de conexão,
falha do servidor ou resultado desconhecido de commit.

## Dados armazenados inconsistentes

Foram preparados três cenários, sem desabilitar restrições do banco:

- Total diferente da soma dos subtotais.
- Lacuna na sequência das posições dos itens.
- Cabeçalho sem itens.

Busca por UUID e listagem por departamento rejeitaram os três casos
com FalhaPersistenciaException.

Savepoints permitiram restaurar o pedido entre os cenários.
O rollback final removeu os registros temporários.

## Persistência entre processos

A classe VerificarPersistenciaRequisicoes foi executada em três
processos Java separados:

1. gravar: inserção dos cadastros, requisição e dois itens, alteração
   do preço do catálogo e confirmação por commit.
2. consultar: recuperação dos dados, preços históricos, ordem,
   quantidades, total e saldo em outro processo.
3. limpar: exclusão dos registros do teste e confirmação por commit.

Identificadores utilizados:

- Departamento: 0009b54c-d44f-44c6-b195-60f1fd219c2a.
- Recurso: 0895402c-5723-4cf4-93d1-50f7d2cd85b4.
- Requisição: 0d1fc4e0-f4e5-49e4-a662-6f92cba4aadc.

As três operações terminaram com sucesso.

## Limites

- O cenário entre processos não incluiu reinício do servidor MySQL.
- Não houve teste concorrente de leitura durante atualizações.
- Os testes JDBC apresentados recuperaram pedidos em INICIAL;
  não exercitaram a recuperação dos demais estados pelo banco.
- Os cenários não constituem cobertura exaustiva de entradas inválidas,
  níveis de isolamento ou falhas de infraestrutura.
- Não foi implementada validação financeira transacional.
- Não foram implementadas criação idempotente ou reconciliação de commit.
- Os casos de uso A2 continuam utilizando repositórios em memória.
- Não houve medição do RNF-01 com MySQL.
- API, login, portal e provisionamento real permanecem fora deste recorte.

## Situação

Implementação e verificações locais realizadas.
Revisão final, commit e integração pelo PR pendentes.