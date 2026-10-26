# Tarefas — Orquestrador FinOps

As seções registram o histórico de cada etapa. Pendências de recortes
antigos devem ser lidas junto das conclusões das etapas posteriores.
Itens sem comprovação explícita permanecem desmarcados.

## Issue #43 — Java 25 e Maven Wrapper

- [x] Confirmar adoção de Java 25 e Maven Wrapper.
- [x] Verificar a disponibilidade do JDK 25 e do compilador.
- [x] Criar a branch build/43-java25-maven-wrapper.
- [x] Registrar o plano desta preparação.
- [x] Definir e registrar as versões do Maven e dos plugins.
- [x] Criar pom.xml com compilação para Java 25 e UTF-8.
- [x] Adicionar os arquivos oficiais do Maven Wrapper.
- [x] Configurar .gitignore para os arquivos gerados.
- [x] Verificar a versão do Maven e o JDK usado pelo Wrapper.
- [x] Compilar pelo terminal.
- [x] Executar e conferir as 75 verificações existentes.
- [x] Atualizar README e arquitetura.
- [x] Registrar evidências e limitações.
- [x] Revisar o diff e criar commit.
- [x] Publicar a branch e abrir PR para revisão.

A conclusão desta issue não representa implementação da persistência ou atendimento integral à SPEC-003.

## Issue #45 — Especificação da persistência

- [x] Conferir o mapa de Specs e o ADR-002.
- [x] Criar a branch docs/45-spec-005-persistencia.
- [x] Elaborar a proposta da SPEC-005.
- [x] Registrar etapas, critérios de aceitação e questões em aberto.
- [x] Alinhar mapa, plano e tarefas.
- [x] Conferir a consistência do conjunto documental.
- [x] Criar commit e publicar a branch.
- [x] Abrir PR e submeter a proposta à revisão.

A elaboração da Spec não comprova implementação da persistência.

## Issue #47 — Contratos dos repositórios em memória

- [x] Conferir os casos de uso e a estrutura atual.
- [x] Criar a branch docs/47-contratos-repositorios-memoria.
- [x] Elaborar a proposta dos contratos de A1.
- [x] Explicitar identidade compartilhada e referências mutáveis.
- [x] Separar cadastro e consulta da integração financeira A2.
- [x] Definir critérios MEM-01 a MEM-14.
- [x] Conferir a consistência entre Spec, mapa, plano e tarefas.
- [x] Criar commit e publicar a branch.
- [x] Abrir PR para revisão.

A implementação de A1 é acompanhada na Issue #49. A2 depende de detalhamento adicional.

## Issue #49 — Implementação dos repositórios em memória

- [x] Confirmar aprovação do recorte A1.
- [x] Criar interfaces dos três repositórios.
- [x] Implementar cadastro e consulta em memória.
- [x] Validar duplicidade, vínculos e identidade dos objetos.
- [x] Verificar coleções retornadas e referências compartilhadas.
- [x] Executar 44 verificações funcionais dos repositórios.
- [x] Executar nove verificações concorrentes dos repositórios.
- [x] Preservar as 75 verificações anteriores.
- [x] Registrar evidências e limitações.
- [x] Conferir implementação e documentação finais.
- [x] Criar commit e publicar a branch.
- [x] Abrir PR para revisão.

A conclusão de A1 não conclui A2 nem a persistência MySQL.

## Issue #51 — Detalhamento da integração A2

- [x] Criar a branch docs/51-integracao-repositorios-a2.
- [x] Registrar a proposta de A2 na SPEC-005.
- [x] Definir entradas por identificadores e responsabilidades dos casos de uso.
- [x] Delimitar coordenação, repetição e tratamento de falhas.
- [x] Definir os critérios INT-01 a INT-14.
- [x] Alinhar mapa, plano e tarefas.
- [x] Conferir a consistência do conjunto documental.
- [x] Revisar e aprovar o recorte antes da implementação.
- [x] Criar commit e publicar a branch.
- [x] Abrir PR para revisão.

O recorte A2 foi aprovado; sua implementação é acompanhada na Issue #53. A aprovação não comprova rollback ou persistência MySQL.

## Issue #53 — Implementação da integração A2

- [x] Criar a branch feat/53-integracao-repositorios-a2.
- [x] Implementar ItemSolicitado.
- [x] Implementar criação integrada por identificadores.
- [x] Implementar validação integrada com conferência dos vínculos.
- [x] Executar 39 verificações funcionais da integração.
- [x] Executar nove verificações concorrentes da integração.
- [x] Preservar as 128 verificações anteriores.
- [x] Registrar aprovação, evidências e limites.
- [x] Atualizar README, mapa, plano e tarefas.
- [x] Conferir implementação e documentação finais.
- [x] Criar commit e publicar a branch.
- [x] Abrir PR para revisão.

A integração em memória não conclui a persistência MySQL nem a estratégia transacional completa da SPEC-005.

## Issue #55 — Detalhamento da persistência MySQL

- [x] Criar a branch docs/55-persistencia-mysql-transacoes.
- [x] Confirmar acesso ao servidor e identificar versão, porta e mecanismo.
- [x] Registrar proposta de acesso JDBC.
- [x] Detalhar dados, vínculos e preços históricos.
- [x] Definir proposta de reconstrução das entidades.
- [x] Delimitar transações, bloqueios e tratamento de falhas.
- [x] Especificar repetição da criação e reconciliação.
- [x] Definir configuração, ambientes e critérios DB-01 a DB-22.
- [ ] Conferir consistência entre Spec, arquitetura, mapa e plano.
- [ ] Revisar e aprovar os contratos necessários à implementação.
- [x] Criar commit e publicar a branch.
- [x] Abrir PR para revisão.

A implementação da etapa B é acompanhada nos recortes seguintes.
As decisões propostas não comprovam persistência, rollback
ou atendimento ao RNF-01 com MySQL.

## Issue #57 — Reconstituição das entidades

- [x] Criar a branch feat/57-reconstituicao-entidades.
- [x] Registrar contratos e critérios REC-01 a REC-13.
- [x] Implementar reconstituição de Departamento.
- [x] Preservar preço unitário histórico em ItemRequisicao.
- [x] Implementar criação com UUID explícito.
- [x] Implementar reconstituição de Requisicao.
- [x] Executar 64 verificações de reconstituição.
- [x] Preservar as 176 verificações anteriores.
- [x] Registrar evidências e limites.
- [x] Atualizar README, mapa, plano e tarefas.
- [x] Conferir o diff final de código e documentação.
- [x] Criar commit e publicar a branch.
- [x] Abrir PR para revisão.

A reconstituição prepara o domínio para a persistência.
A preparação da conexão JDBC é acompanhada na Issue #59.
Repositórios JDBC e transações financeiras permanecem pendentes.

## Issue #59 — Conexão JDBC e esquema MySQL

- [x] Criar a branch feat/59-jdbc-esquema-mysql.
- [x] Adicionar a dependência Connector/J 9.7.0.
- [x] Implementar ConfiguracaoBanco e FabricaConexoes.
- [x] Criar o script inicial das quatro tabelas.
- [x] Preparar finops_dev e finops_test.
- [x] Configurar contas próprias e conferir os grants.
- [x] Verificar conexão JDBC, banco, conta e TLS.
- [x] Conferir as 17 restrições em finops_test.
- [x] Exercitar três cenários de rejeição e executar ROLLBACK.
- [x] Executar 14 verificações de configuração.
- [x] Preservar as 240 verificações anteriores.
- [x] Registrar evidências e limites.
- [x] Conferir documentação e instruções de execução finais.
- [x] Conferir o diff final, incluindo os arquivos novos.
- [x] Criar commit e publicar a branch.
- [x] Abrir PR para revisão.

Conexão e esquema integrados pelo PR #60.
Os cadastros JDBC foram integrados pelo PR #62.
Requisições e itens são acompanhados na Issue #63.
Transações financeiras permanecem pendentes.

## Issue #61 — Cadastro e consulta JDBC

- [x] Criar a branch feat/61-repositorios-jdbc-cadastros.
- [x] Registrar contratos de conexão, cadastro, consulta e falhas.
- [x] Implementar validação dos limites do armazenamento.
- [x] Implementar repositório JDBC de departamentos.
- [x] Implementar repositório JDBC de recursos.
- [x] Executar 28 verificações de limites.
- [x] Executar 14 verificações JDBC.
- [x] Verificar recuperação de cadastros em outro processo Java.
- [x] Confirmar limpeza dos registros do cenário entre processos.
- [x] Preservar as 254 verificações anteriores.
- [x] Registrar evidências e limitações.
- [x] Conferir documentação e instruções de execução finais.
- [x] Revisar o diff final de código e documentação.
- [x] Criar commit e publicar a branch.
- [x] Abrir PR para revisão.

Cadastros JDBC integrados pelo PR #62.
Armazenamento de requisições e itens acompanhado na Issue #63.
Validação financeira transacional permanece pendente.

## Issue #63 — Armazenamento JDBC de requisições e itens

- [x] Criar a branch feat/63-requisicoes-itens-jdbc.
- [x] Registrar contratos de cadastro, consulta e transação.
- [x] Implementar cópia validada dos dados de cadastro.
- [x] Implementar conversão dos estados e motivos armazenados.
- [x] Implementar gravação de cabeçalho e itens.
- [x] Implementar busca e listagem com uma instrução SELECT por operação.
- [x] Preservar ordem, recursos repetidos e preços históricos.
- [x] Verificar rejeição de duplicatas.
- [x] Verificar bloqueio de cadastro com autocommit habilitado.
- [x] Verificar rollback após falha simulada no segundo item.
- [x] Verificar rejeição de dados armazenados inconsistentes.
- [x] Executar 35 verificações JDBC de requisições.
- [x] Preservar 282 verificações sem banco e 14 de cadastros JDBC.
- [x] Verificar persistência em outro processo Java.
- [x] Confirmar limpeza dos registros do teste.
- [x] Registrar evidências e limites.
- [x] Conferir documentação e instruções de execução finais.
- [x] Revisar o diff final, incluindo arquivos novos.
- [x] Criar commit e publicar a branch.
- [x] Abrir PR para revisão.

Armazenamento de requisições e itens integrado pelo PR #64.
Criação transacional e reconciliação acompanhadas na Issue #65.
Validação financeira transacional permanece pendente.

## Issue #65 — Criação transacional JDBC

- [x] Criar a branch feat/65-criacao-transacional-jdbc.
- [x] Registrar contratos de criação, repetição e confirmação desconhecida.
- [x] Implementar entrada validada e porta sem tipos JDBC.
- [x] Implementar caso de uso de criação persistente.
- [x] Controlar conexão e transação na infraestrutura.
- [x] Obter preços do catálogo para novos pedidos.
- [x] Confirmar cabeçalho e itens antes de retornar CRIADA.
- [x] Reconhecer repetição equivalente pelo UUID e conteúdo.
- [x] Rejeitar conteúdo divergente com o mesmo UUID.
- [x] Conferir duplicata concorrente em nova conexão.
- [x] Representar confirmação desconhecida com o UUID original.
- [x] Verificar reconciliação após falhas simuladas de commit.
- [x] Sinalizar falha de fechamento após criação confirmada.
- [x] Verificar rollback de gravação parcial.
- [x] Verificar preservação de pedidos já processados.
- [x] Executar 47 verificações da criação transacional JDBC.
- [x] Preservar as 331 verificações anteriores.
- [x] Verificar criação e repetição em processos Java distintos.
- [x] Confirmar limpeza do cenário entre processos.
- [x] Registrar evidências e limites.
- [x] Conferir documentação e instruções de execução finais.
- [x] Revisar o diff final, incluindo arquivos novos.
- [x] Criar commit e publicar a branch.
- [x] Abrir PR para revisão.

Criação transacional integrada pelo PR #66.
Validação financeira transacional integrada pelo PR #68.
Aprovação manual, API, autenticação, portal e provisionamento
real permanecem pendentes.

## Issue #67 — Validação financeira transacional JDBC

- [x] Detalhar o contrato na SPEC-005.
- [x] Implementar caso de uso por UUID e porta sem tipos JDBC.
- [x] Controlar conexão, transação e ordem dos bloqueios.
- [x] Reutilizar as regras financeiras do domínio.
- [x] Confirmar saldo e estado atomicamente.
- [x] Preservar preços históricos e pedidos em revisão.
- [x] Reconhecer repetição sem novo desconto.
- [x] Verificar rollback após falha entre atualizações.
- [x] Verificar concorrência do mesmo pedido.
- [x] Verificar disputa pela reserva entre pedidos do mesmo departamento.
- [x] Verificar isolamento entre departamentos.
- [x] Verificar confirmação desconhecida e reconciliação pelo UUID.
- [x] Verificar aviso de falha de fechamento após commit.
- [x] Executar 84 verificações transacionais JDBC.
- [x] Preservar as 378 verificações anteriores.
- [x] Verificar liberação e repetição em processos Java separados.
- [x] Confirmar limpeza do teste entre processos.
- [x] Integrar implementação pelo PR #68.
- [x] Integrar evidências e referência na SPEC-005 pelo PR #69.
- [x] Atualizar os critérios de aceite e encerrar a Issue #67.

Limites de cobertura e desempenho:
[evidências da Issue #67](evidencias/issue-67/validacao-financeira-jdbc.md).