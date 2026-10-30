# Issue #76 — Autorização das requisições

## Situação

Entrega integrada à main pelo PR #77, com merge commit 39f7e8a.
Issue #76 encerrada no recorte implementado e verificado.

Regressão sem banco e JDBC aprovada em 29/10/2026.
Verificação adicional de dois adaptadores controlados aprovada.

Após a integração, a main local foi atualizada para fe83867.
O comando Maven compile retornou BUILD SUCCESS, reutilizando
as classes atualizadas, e git diff --check não apontou problemas.
Essa execução não representa uma nova execução dos verificadores.

As contagens e evidências abaixo correspondem aos conjuntos
efetivamente executados. O encerramento não amplia as garantias
demonstradas nem comprova atendimento integral à SPEC-006.

## Implementação

- Identidade obtida por contrato da aplicação.
- Negativa para identidade ausente, inativa ou sem vínculo exigido.
- Falha do provedor impede o acesso ao armazenamento.
- Autor e departamento da criação derivados da identidade.
- Consulta e listagem do desenvolvedor filtradas por autor e departamento.
- Filtros JDBC aplicados antes da reconstituição dos pedidos.
- Arquiteto consulta departamentos distintos e pedidos sem autoria.
- Autorização da repetição antes da comparação de conteúdo.
- Nova conferência de autorização na reconciliação após duplicidade.
- Entrada coordenada abrangendo autorização e execução transacional.
- Entrada intermediária de criação restrita ao pacote da aplicação.
- Caminho legado documentado como compatibilidade interna.

## Ambiente e execução

- Windows e PowerShell.
- JDK Eclipse Adoptium 25.0.2, release 25.
- Maven Wrapper do projeto.
- Compilação de 98 arquivos Java aprovada.
- MySQL local: finops_test.
- Verificadores JDBC executados com finops_test_app@localhost.
- Esquema com V001 e V002 previamente aplicadas.
- Nenhuma nova migração nesta etapa.

Os verificadores foram executados pelos métodos main.
A compilação Maven não representa execução desses testes.

## Resultados sem banco

| Conjunto | Verificações |
|---|---:|
| Base anterior, incluindo autoria e política de autorização | 339 |
| VerificarBloqueioConsultasAutorizadas | 16 |
| VerificarEscopoConsultasAutorizadas | 18 |
| VerificarEntradaCriacaoAutorizada | 16 |
| VerificarIdentidadeCoordenada | 14 |
| VerificarAdaptadoresAutorizados | 12 |
| **Total sem banco** | **415** |

## Resultados JDBC

| Verificador | Verificações |
|---|---:|
| VerificarCadastrosJdbc | 14 |
| VerificarRequisicoesJdbc | 35 |
| VerificarCriacaoTransacionalJdbc | 47 |
| VerificarValidacaoTransacionalJdbc | 84 |
| VerificarAutoriaTransacionalJdbc | 12 |
| VerificarConsultasAutorizadasJdbc | 13 |
| VerificarCriacaoAutorizadaJdbc | 8 |
| VerificarConcorrenciaCriacaoAutorizadaJdbc | 10 |
| VerificarVinculoCoordenadoJdbc | 10 |
| **Total JDBC** | **233** |

Total executado: **648 verificações distintas**,
sendo 531 anteriores e 117 novas.

Reexecuções não foram somadas novamente.
Nos verificadores concorrentes, a contagem considera as verificações
individuais, não apenas o número de cenários.

VerificarAutoriaJdbc, com nove verificações da Issue #74, não foi
reexecutado: depende do cenário anterior à migração, já removido.
A migração V002 não foi reaplicada.

## Concorrência de criação

Uma barreira anterior ao INSERT forçou duas tentativas para o mesmo UUID.

- Mesmo autor: uma CRIADA e uma JA_EXISTENTE.
- Autores diferentes: uma CRIADA e uma negativa de acesso.
- Nos dois cenários: duas tentativas de inserção e três conexões,
  incluindo a conexão de reconciliação.
- Outra conexão confirmou pedido único, autoria do vencedor,
  itens e saldo preservados.
- Limpeza confirmada após cada cenário.

Os verificadores de criação e concorrência foram reexecutados
após sua migração para CriarRequisicaoCoordenadaUseCase.

## Mudança concorrente de vínculo

A fonte controlada mantém um bloqueio de leitura durante a operação.
Alterações da identidade utilizam o bloqueio de escrita da mesma fonte.

O teste JDBC pausou a criação antes do commit e verificou:

- Pedido ainda invisível para outra conexão.
- Alteração de vínculo aguardando a operação.
- Commit confirmado antes da conclusão da alteração.
- Pedido anterior preservando seu departamento original.
- Criação posterior utilizando o novo departamento.
- Repetição do pedido anterior negada após a mudança.
- Preservação dos dados e limpeza confirmadas por outra conexão.

Essa evidência vale para a fonte controlada no mesmo processo.
Não comprova coordenação entre JVMs ou com um provedor externo.

## Limpeza e falhas de ambiente

Os verificadores JDBC confirmaram rollback ou limpeza dos seus cenários.

Ocorreram tentativas com conexão recusada antes da preparação dos dados.
Após restabelecer o MySQL, os verificadores foram executados com sucesso.
Essas tentativas malsucedidas não entram na contagem de aprovações.

## Adaptadores distintos

VerificarAdaptadoresAutorizados executou 12 verificações sem banco,
distribuídas entre dois adaptadores de entrada controlados:

- Adaptador tipado, recebendo UUIDs e quantidade inteira.
- Adaptador textual, convertendo textos para os tipos da aplicação.

Ambos delegam aos mesmos casos de uso de criação coordenada,
consulta autorizada e listagem autorizada, sem duplicar a política.

Foram verificados encaminhamento da criação com autoria derivada,
escopo de consulta, listagem, proteção contra retorno de pedido de
colega, negativa de criação pelo arquiteto e bloqueio de identidade
inativa antes do armazenamento.

Os adaptadores e as portas de persistência desse verificador são
controlados. Essa evidência não representa API REST, portal ou
autenticação de produção.

## Limites e pendências

- Adaptadores de identidade dos testes não autenticam usuários.
- Um provedor real deve cumprir o contrato de coordenação com todos
  os caminhos que alteram os atributos usados na autorização.
- A coordenação da criação não transforma as consultas em operações
  coordenadas; elas usam a identidade obtida no início da chamada.
- Caminhos legados e portas de infraestrutura continuam acessíveis
  ao código interno. Documentação não constitui barreira técnica.
- Futuros adaptadores de usuário devem utilizar as entradas autorizadas,
  e a criação deve utilizar a entrada coordenada.
- API REST, portal e autenticação de produção não foram implementados.
- Não foram introduzidas operações públicas de contagem ou paginação.
- Esta entrega não comprova atendimento integral à SPEC-006.