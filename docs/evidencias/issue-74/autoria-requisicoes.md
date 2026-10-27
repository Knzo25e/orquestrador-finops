# Issue #74 — Autoria das requisições

## Objetivo

Persistir e recuperar a autoria das requisições, preservando registros
sem autoria conhecida e considerando o autor na repetição da criação.

## Implementação

- Identificador do desenvolvedor imutável na entidade Requisicao.
- Criação explícita com autoria obrigatória.
- Compatibilidade com chamadas anteriores sem autoria.
- Reconstituição com autor conhecido ou ausente.
- Transporte da autoria pela entrada e pelo caso de uso de criação.
- Gravação e leitura de desenvolvedor_id pelo JDBC.
- Comparação da autoria nas tentativas repetidas de criação.
- Migração incremental V002, mantendo V001 inalterada.

Autores diferentes produzem conflito para o mesmo UUID de requisição.
Autoria conhecida e autoria ausente também produzem conflito.
Duas autorias ausentes preservam a compatibilidade anterior.

## Ambiente e execução

- Data: 27/10/2026.
- Windows e PowerShell.
- JDK Eclipse Adoptium 25.0.2, release 25.
- Maven Wrapper do projeto.
- Compilação de 76 arquivos Java aprovada.
- MySQL local, banco finops_test.
- Verificadores JDBC executados com finops_test_app@localhost.
- Migração executada pelo MySQL Workbench com root@localhost.

Os verificadores foram executados diretamente pelos métodos main.
O Maven Surefire não executou essas verificações.

## Resultados

| Conjunto | Verificações aprovadas |
|---|---:|
| Regressão anterior sem banco, incluindo política de autorização | 316 |
| VerificarAutoriaRequisicao | 16 |
| VerificarComparacaoAutoria | 7 |
| VerificarCadastrosJdbc | 14 |
| VerificarRequisicoesJdbc | 35 |
| VerificarCriacaoTransacionalJdbc | 47 |
| VerificarValidacaoTransacionalJdbc | 84 |
| VerificarAutoriaJdbc | 9 |
| VerificarAutoriaTransacionalJdbc | 12 |
| **Total** | **540** |

São 339 verificações sem banco e 201 verificações JDBC.
Dessas, 44 são novas nesta entrega e 496 pertencem aos conjuntos anteriores.

Nos programas concorrentes, a contagem considera as verificações
individuais, não somente o número de cenários.

VerificarAutoriaJdbc foi reexecutado após receber os UUIDs por argumentos.
Suas nove verificações passaram novamente, sem duplicação na contagem.

## Migração V002

Arquivo:

src/main/resources/db/mysql/V002__adicionar_autoria_requisicao.sql

A migração adiciona:

- desenvolvedor_id, CHAR(36), ASCII, collation ascii_bin, permitindo NULL;
- índice idx_requisicao_autor_departamento sobre
  desenvolvedor_id e departamento_id.

Não foi criada chave estrangeira de usuário, pois esta etapa não
introduz uma tabela de usuários.

### Cenário anterior à migração

O banco de teste possuía zero requisições.
Antes de aplicar V002, foi criado e confirmado um cenário controlado:

- Departamento com teto de 1000.00 e saldo de 800.00.
- Recurso com custo mensal de 12.50.
- Requisição INICIAL, total de 25.00 e motivo de revisão NULL.
- Um item de ordem 1, quantidade 2, preço unitário 12.50 e subtotal 25.00.

Identificadores utilizados:

- Requisição: afe1a6cf-d217-11f1-9e5f-0250fa5d78b8.
- Departamento: afe0d044-d217-11f1-9e5f-0250fa5d78b8.
- Recurso: afe185a7-d217-11f1-9e5f-0250fa5d78b8.

### Resultado após a migração

O ALTER TABLE terminou sem erros ou avisos.

A consulta posterior confirmou:

- Identificadores e vínculos preservados.
- Estado INICIAL e motivo NULL preservados.
- Total, teto, saldo, quantidade e preços preservados.
- desenvolvedor_id igual a NULL, sem atribuição presumida de autor.

A leitura pelo repositório JDBC também recuperou o pedido antigo
sem autoria e com seus dados preservados.

### Limpeza

Após as verificações, os registros desse cenário foram removidos.
Uma consulta após o COMMIT retornou:

- Pedidos: 0.
- Itens: 0.
- Departamentos: 0.
- Recursos: 0.

As contagens foram restritas aos identificadores do cenário.
A estrutura adicionada pela V002 permanece no banco.

## Cobertura dos testes de autoria

- Preservação da autoria na criação e reconstituição do domínio.
- Rejeição de autor nulo na criação explícita com autoria.
- Compatibilidade dos caminhos antigos com autoria desconhecida.
- Preservação das validações de itens e total.
- Comparação entre autores iguais, diferentes e ausentes.
- Busca e listagem JDBC com autoria preservada.
- Rollback do cadastro de teste confirmado por outra conexão.
- Criação transacional com autor.
- Repetição equivalente pelo mesmo autor.
- Conflito quando outro autor reutiliza o UUID.
- Conflito quando uma chamada sem autor reutiliza pedido com autor.
- Liberação financeira preservando autoria.
- Saldo de 800.00 para 775.00, sem desconto duplicado.
- Repetição de criação após liberação preservando autor e estado.
- Limpeza do cenário transacional confirmada por outra conexão.

## Condições de reprodução

Aplicar V001 e depois V002 em um banco de teste preparado.
V002 deve ser aplicada uma única vez por banco.
Compilar o projeto não executa a migração automaticamente.

Para reproduzir a preservação de dados anteriores à migração,
preparar o cenário descrito acima em um banco de teste que ainda
esteja em V001, confirmar os registros e então aplicar V002.

Não remover a coluna de um banco já migrado para repetir esse teste.

VerificarAutoriaJdbc recebe, nesta ordem:

1. UUID da requisição anterior à migração.
2. UUID do departamento.
3. UUID do recurso.

O cenário deve possuir os valores descritos nesta evidência.
O verificador desfaz seu pedido novo por rollback, mas preserva
os registros recebidos por argumento para limpeza posterior.

Os UUIDs registrados nesta execução já foram limpos.
Para repetir o teste, é necessário preparar outro cenário.

VerificarAutoriaTransacionalJdbc não recebe argumentos.
Ele gera seus próprios identificadores e remove seus registros.

## Limites

Persistir um UUID de autor não autentica o usuário.

A política de autorização ainda não está integrada aos casos de uso.
Consulta e listagem não passaram a aplicar filtros de acesso por usuário.

Permanecem pendentes autenticação real, integração da identidade
confiável e autorização dos fluxos de criação, consulta e repetição.

Os testes de concorrência e falhas anteriores foram reexecutados,
mas não constituem cenários novos de disputa entre autores diferentes.

O teste da migração utilizou um cenário controlado.
Não comprova migração de uma base de produção ou de grande volume.

Esta entrega não comprova atendimento integral à SPEC-006.

## Situação

Implementação e verificações locais realizadas.
Revisão documental, commit, PR e integração à main pendentes.