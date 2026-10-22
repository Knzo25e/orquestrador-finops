# Evidências — Criação transacional JDBC — Issue #65

## Situação

Implementação e verificações locais realizadas na branch
feat/65-criacao-transacional-jdbc.

Revisão final da documentação e integração pelo PR pendentes.

## Ambiente

- Data das verificações: 22/10/2026.
- Windows e PowerShell.
- Eclipse Adoptium JDK 25.0.2.
- Compilação com release 25.
- MySQL 8.0.44, tabelas InnoDB.
- MySQL Connector/J 9.7.0.
- Banco exclusivo de testes: finops_test.
- Conta: finops_test_app@localhost.
- Conexões configuradas com sslMode=REQUIRED.
- Senha fornecida pelo ambiente durante a execução, sem registro neste documento.

## Implementação

- CriarRequisicaoPersistenteUseCase recebe UUID da requisição,
  UUID do departamento e lista ordenada de ItemSolicitado.
- SolicitacaoCriacaoRequisicao valida e copia a entrada.
- CriacaoRequisicaoPersistente define a porta sem tipos JDBC.
- CriacaoRequisicaoJdbc controla conexão, transação e encerramento.
- Os preços de novos pedidos são obtidos do catálogo.
- Cabeçalho e itens são gravados na mesma transação.
- CRIADA somente é retornado após confirmação do commit.
- Repetições equivalentes retornam JA_EXISTENTE.
- A equivalência considera departamento, número e sequência dos
  itens, identificadores dos recursos e quantidades.
- Preços históricos, estado e motivo do pedido existente são preservados.
- Conteúdo divergente com o mesmo UUID gera conflito.
- A criação não desconta saldo nem executa roteamento.

## Transações e falhas

Cada tentativa utiliza uma conexão exclusiva com REPEATABLE READ
e autocommit desabilitado.

Os repositórios continuam sem executar commit, rollback ou fechamento
da conexão.

A duplicata do cabeçalho é identificada por RequisicaoDuplicadaException.
Após encerramento da tentativa que perdeu a inclusão, a conferência
utiliza uma nova conexão.

Falhas durante commit são informadas por
ConfirmacaoCriacaoDesconhecidaException, contendo o UUID original.
Uma tentativa posterior de rollback não elimina essa incerteza.

A reconciliação utiliza nova chamada com o mesmo UUID e conteúdo.

Falha de fechamento após commit confirmado é indicada no resultado,
sem transformar a criação confirmada em operação abortada.

## Compilação e regressão

A compilação de 57 arquivos Java foi aprovada.

A regressão anterior foi executada novamente e aprovada:

| Grupo | Verificações |
| --- | ---: |
| Classes sem conexão com MySQL | 282 |
| VerificarCadastrosJdbc | 14 |
| VerificarRequisicoesJdbc | 35 |
| Total da regressão anterior | 331 |

VerificarCriacaoTransacionalJdbc teve 47 verificações aprovadas
na execução local confirmada pelo responsável.

Total: 378 verificações, além do cenário entre processos descrito abaixo.

As classes de verificação possuem método main.
Elas não são executadas automaticamente por mvn test.

## Cenários da criação transacional

Foram exercitados:

- Criação confirmada e recuperação por outra conexão.
- Estado INICIAL, total e saldo preservados.
- Repetição equivalente sem duplicação.
- Preservação de preços históricos após alteração do catálogo.
- Conflitos por departamento, ordem, recurso e quantidade.
- Repetição de pedidos liberados ou em revisão, preservando estado e motivo.
- Rejeição de departamento ou recurso inexistente.
- Falha durante a inserção do segundo item.
- Rollback da gravação parcial pelo caso de uso.
- Nova tentativa com o UUID original após rollback.
- Disputa concorrente pelo mesmo UUID com conexões independentes.
- Uma resposta CRIADA e uma JA_EXISTENTE na disputa equivalente.
- Falha simulada antes de efetivar commit.
- Commit efetivado seguido de falha simulada na resposta.
- Reconciliação dos dois cenários com o UUID original.
- Falha simulada de fechamento após criação confirmada.

Os estados de pedidos já processados foram preparados diretamente
como dados de teste. Esses cenários não implementam nem comprovam
validação financeira transacional.

## Cenário entre processos Java

Classe: VerificarPersistenciaCriacao.

Foram iniciados três processos Java distintos, utilizando os mesmos
identificadores:

- Departamento: 1120be1b-555c-4323-8cfa-4e4e8917f12c.
- Recurso: ac21a632-a6c4-444a-96bb-ddd76adb4825.
- Requisição: 7f682d6f-4e59-46bf-b683-c43ed11181d3.

### Processo 1 — Gravar

O caso de uso retornou CRIADA após confirmar a transação.

Depois da criação, o preço do recurso no catálogo foi alterado
de 12.50 para 99.00.

### Processo 2 — Repetir e consultar

A repetição retornou JA_EXISTENTE.

A recuperação preservou:

- UUID da requisição e vínculo com o departamento.
- Dois itens, nas quantidades 2 e 3.
- Preço unitário histórico de 12.50.
- Subtotais de 25.00 e 37.50.
- Total de 62.50.
- Estado INICIAL, sem motivo de revisão.
- Saldo do departamento de 800.00.

O recurso recuperado refletiu o preço atual de 99.00,
sem recalcular os valores históricos do pedido.

### Processo 3 — Limpar

A limpeza dos registros identificados como pertencentes ao teste
foi confirmada por commit.

A execução terminou com:

Teste de criacao entre processos concluido com sucesso.

## Limites das evidências

- As falhas de commit, inserção e fechamento foram simuladas
  por conexões ou comandos controlados nos testes.
- Não houve simulação de queda real de rede ou reinício do MySQL.
- O cenário entre processos comprova continuidade entre execuções
  Java com o banco em funcionamento.
- O cenário concorrente exercita a disputa controlada pelo mesmo UUID;
  não constitui teste de carga ou comprovação geral de desempenho.
- Não há repetição automática de deadlocks ou limites de espera.
- A regressão anterior não substitui testes específicos de todas
  as entradas inválidas e falhas de encerramento da nova operação.
- A validação financeira transacional permanece pendente.
- Não inclui aprovação manual, API, autenticação, portal ou
  provisionamento real na AWS.