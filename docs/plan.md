# Plano Técnico — Orquestrador FinOps

## Leitura do histórico

As seções abaixo registram os recortes e os limites de cada etapa.
Pendências descritas em etapas antigas devem ser interpretadas
naquele contexto; sua evolução é registrada nas etapas posteriores.

Criação transacional JDBC integrada pelo PR #66.
Validação financeira transacional integrada pelo PR #68,
com evidências integradas pelo PR #69.

A conclusão desses recortes não representa conclusão integral
da SPEC-005 nem comprovação de desempenho com MySQL.

## Preparação do build — Issue #43

### Objetivo
Compilar o projeto e executar suas verificações pelo terminal, sem depender do Run do VS Code.

### Base
- ADR-001: domínio independente de frameworks.
- ADR-003: Java e Spring Boot, com padronização do build anteriormente pendente.
- SPEC-002 e SPEC-003: preservar os comportamentos já implementados.
- Issue #43: adoção de Java 25 e Maven Wrapper confirmada pelo responsável pelo projeto.

### Decisões
- Java 25 como versão-alvo de compilação.
- Maven como ferramenta de build.
- Maven Wrapper versionado no repositório.
- Versões do Maven e dos plugins fixadas explicitamente.
- Código-fonte e relatórios em UTF-8.
- Seleção local do JDK sem caminhos pessoais obrigatórios nos arquivos versionados.

Versões previstas nesta preparação:
- Maven: 3.10.0.
- Maven Wrapper: 3.3.4.
- Maven Clean Plugin: 3.5.0.
- Maven Resources Plugin: 3.5.0.
- Maven Compiler Plugin: 3.16.0.
- Maven Surefire Plugin: 3.6.0.
- Maven JAR Plugin: 3.5.1.

A compilação utilizará release 25. A configuração será verificada pelo terminal antes de ser considerada concluída.

### Execução
1. Criar pom.xml para a estrutura src/main/java.
2. Adicionar os arquivos oficiais do Maven Wrapper.
3. Configurar os arquivos gerados que não devem ser versionados.
4. Compilar com Java 25 pelo terminal.
5. Executar as quatro classes de verificação existentes.
6. Atualizar README e arquitetura com as decisões e comandos.
7. Registrar os resultados e submeter o PR à revisão.

### Verificação
Preservar as 75 verificações:
- Main: 10.
- VerificarValoresMonetarios: 34.
- VerificarValidacaoOrcamentaria: 25.
- VerificarConcorrenciaOrcamentaria: 6, distribuídas em três cenários.

As classes com main não são descobertas automaticamente por mvn test. Sua execução deverá ser explícita e documentada.

O programa de desempenho permanece disponível. As evidências da Issue #41 descrevem o ambiente e os comandos daquela medição.

### Fora do escopo
- Integração com Spring Boot ou MySQL.
- Implementação da SPEC-005.
- Migração para um framework de testes.
- Alteração das regras de negócio.

### Situação
Build configurado e verificado localmente com Java 25 e Maven Wrapper. Limpeza, compilação e 75 verificações aprovadas, conforme docs/evidencias/issue-43/build.md. Alterações integradas à main pelo PR #44.

## Especificação da persistência — Issue #45

### Objetivo
Elaborar e revisar a SPEC-005 antes da implementação dos repositórios e da integração MySQL.

### Base
- ADR-001: separação entre domínio, aplicação e infraestrutura.
- ADR-002: MySQL e repositórios iniciais em memória.
- SPEC-002 e SPEC-003: preservar criação, roteamento e consistência financeira.
- Issue #43 / PR #44: build com Java 25 e Maven Wrapper.

### Escopo documental
- Definir dados, vínculos e comportamentos de armazenamento e recuperação.
- Separar a etapa em memória da persistência entre sessões.
- Especificar gravação consistente, recuperação após reinício e proteção contra comprometimento duplicado.
- Registrar decisões técnicas pendentes em OPEN-005-01 a OPEN-005-07.
- Alinhar o mapa de Specs e submeter a proposta à revisão.

### Limites
Esta issue não inclui código de implementação, instalação de banco ou novas dependências.

A etapa em memória não conclui a SPEC-005. A implementação de cada etapa depende da revisão de seu recorte e da resolução das questões que o bloqueiam.

### Situação
Proposta documental integrada à main pelo PR #46. As decisões necessárias à implementação continuam sujeitas ao detalhamento e à revisão de cada recorte.

## Contratos dos repositórios em memória — Issue #47

### Objetivo
Detalhar o primeiro recorte da etapa A da SPEC-005 antes da implementação.

### Recorte proposto
- A1: interfaces e implementações em memória para cadastro e consulta.
- A2: integração dos repositórios com os casos de uso, dependente de definição adicional sobre coordenação e falhas.

### Decisões propostas para A1
- Interfaces em aplicacao.portas.
- Implementações em infraestrutura.repositorios.memoria.
- Consultas por UUID com ausência explícita.
- Rejeição de identificadores duplicados.
- Preservação das instâncias cadastradas e dos vínculos compartilhados.
- Coleções de consulta não modificáveis, contendo referências às entidades.
- Proteção das estruturas internas contra acesso concorrente.
- Dados de teste cadastrados explicitamente em contextos independentes.

### Verificação prevista
- Exercitar os critérios MEM-01 a MEM-14 da SPEC-005.
- Preservar as 75 verificações existentes.
- Identificar as verificações como armazenamento em memória.
- Não apresentar A1 como evidência de rollback, persistência após reinício ou integração financeira completa.

### Limites
Esta issue é documental. Não inclui implementação Java, novas dependências ou MySQL.

A política de referências compartilhadas não oferece rollback automático. O tratamento de falhas da integração A2 permanece em OPEN-005-03.

### Situação
Detalhamento integrado pelo PR #48. O recorte A1 foi aprovado conforme confirmação do responsável pelo projeto. A integração A2 permanece pendente.

## Implementação dos repositórios em memória — Issue #49

### Base
SPEC-005, recorte A1 detalhado na Issue #47 e integrado pelo PR #48, com aprovação confirmada pelo responsável pelo projeto.

### Implementação
- Três interfaces em aplicacao.portas.
- Três implementações em infraestrutura.repositorios.memoria.
- Cadastro sem substituição de identificadores existentes.
- Consulta por UUID e listagem de requisições por departamento.
- Validação de vínculos no cadastro e preservação das instâncias.
- Proteção das estruturas internas contra acesso concorrente.

### Verificação
Compilação de 20 arquivos Java com release 25 concluída.

Foram aprovadas 44 verificações funcionais dos repositórios, nove verificações concorrentes e as 75 verificações anteriores: 128 no total.

Evidência: docs/evidencias/issue-49/repositorios-memoria.md.

### Limites
Sem integração dos casos de uso aos repositórios, rollback automático ou persistência após reinício.

A2 e MySQL permanecem pendentes.

### Situação
Implementação e verificações locais concluídas; alterações integradas à main pelo PR #50.

## Integração dos repositórios aos casos de uso — Issue #51

### Objetivo
Detalhar a integração A2 da SPEC-005 antes de implementar os casos de uso que acessam os repositórios.

### Base
- SPEC-002: criação de requisições.
- SPEC-003: validação orçamentária.
- SPEC-005: contratos e limites do armazenamento.
- Issue #49 / PR #50: repositórios em memória de A1 implementados e integrados.

### Recorte proposto
- Receber identificadores de departamentos, recursos e requisições.
- Introduzir casos de uso de criação e validação integrados aos repositórios.
- Preservar os casos de uso atuais e reutilizar as regras de domínio.
- Conferir os vínculos antes de executar alterações financeiras.
- Coordenar a validação pelo mesmo objeto Departamento.
- Propagar falhas anteriores à mutação sem informar sucesso.
- Não acrescentar uma gravação posterior ao desconto nas entidades compartilhadas.

### Verificação prevista
- Exercitar os critérios INT-01 a INT-14 da SPEC-005.
- Preservar as 128 verificações existentes.
- Verificar criação completa, roteamento, repetição e concorrência.
- Provocar falha controlada de consulta antes da mutação.
- Registrar resultados e limites do armazenamento em memória.

### Limites
Esta issue é documental e não inclui implementação Java ou novas dependências.

O recorte proposto não oferece rollback de falhas arbitrárias após iniciar a mutação. O critério C6 e a estratégia transacional completa permanecem pendentes em OPEN-005-03.

A idempotência da criação, a persistência MySQL, o login e o portal não fazem parte desta entrega.

### Situação
Detalhamento integrado pelo PR #52 e recorte A2 aprovado conforme confirmação do responsável pelo projeto. Implementação acompanhada na Issue #53.

## Implementação da integração A2 — Issue #53

### Base
SPEC-005, recorte A2 detalhado na Issue #51, integrado pelo PR #52 e aprovado pelo responsável antes da implementação.

### Implementação
- Entrada imutável ItemSolicitado, contendo identificador do recurso e quantidade.
- CriarRequisicaoArmazenadaUseCase com consulta dos registros e cadastro da requisição completa.
- ValidarRequisicaoArmazenadaUseCase com conferência dos vínculos e coordenação pelo departamento.
- Reutilização das regras existentes de criação e validação.
- Preservação dos casos de uso anteriores.

### Verificação
Compilação de 25 arquivos Java com release 25 concluída.

Foram aprovadas 39 verificações funcionais de integração, nove verificações concorrentes de integração e as 128 anteriores, totalizando 176.

Evidência: docs/evidencias/issue-53/integracao-repositorios.md.

### Limites
Integração em memória, em uma única instância, com entidades compartilhadas.

Não oferece rollback após iniciar a mutação, persistência após reinício ou idempotência da criação.

O critério C6 e a estratégia transacional completa permanecem pendentes em OPEN-005-03.

### Situação
Implementação e verificações locais concluídas; integrada à main pelo PR #54. Persistência MySQL e estratégia transacional completa permanecem pendentes.

## Persistência MySQL e transações — Issue #55

### Objetivo

Detalhar a etapa B da SPEC-005, permitindo implementar persistência
entre execuções com consistência financeira.

### Base

- ADR-002: MySQL como banco relacional do projeto.
- ADR-003: Java 25 e integração futura com Spring Boot.
- PR #54: integração A2 em memória implementada.
- Ambiente local confirmado: MySQL 8.0.44, porta 3306 e InnoDB.

### Proposta

- Acesso JDBC com Connector/J 9.7.0.
- Esquema para departamentos, recursos, requisições e itens.
- Valores monetários limitados a DECIMAL(19,2).
- Preservação dos preços históricos dos itens.
- Reconstituição das entidades sem novos efeitos financeiros.
- Criação completa em uma transação.
- Validação com bloqueios no departamento e na requisição.
- Confirmação conjunta do saldo e do estado.
- Identificador estável para repetição da criação.
- Tratamento explícito de confirmação desconhecida.
- Bancos separados de desenvolvimento e testes.
- Credenciais externas ao código e usuário próprio da aplicação.

### Verificação prevista

Exercitar DB-01 a DB-22, incluindo reinício, rollback,
concorrência entre conexões e processos, repetição e reconciliação.

Preservar as 176 verificações existentes.

O atendimento ao RNF-01 com MySQL exige medição própria.

### Limites

Esta issue é documental. A implementação Java, a adição da
dependência e a criação do esquema ocorrerão em etapa posterior.

Autorização, auditoria manual, renovação de períodos e integração
web permanecem nos respectivos recortes.

### Situação

Detalhamento integrado à main pelo PR #56.
Os contratos necessários à implementação são detalhados nos recortes seguintes.

## Reconstituição das entidades — Issue #57

### Objetivo

Preparar as entidades para recuperar identidade, saldo, preços
históricos e estados sem executar efeitos financeiros.

### Implementação

- Departamento.reconstituir preserva o saldo informado.
- ItemRequisicao mantém preço unitário explícito e admite preço histórico.
- Requisicao.criarComId permite criação com identidade definida.
- Requisicao.reconstituir valida itens, total, estado e motivo.
- Estado transitório Em Analise é rejeitado na reconstituição.
- Construtores e comportamentos anteriores preservados.

### Verificação

Compilação de 26 arquivos Java com release 25 concluída.
Foram aprovadas 64 verificações novas e as 176 anteriores,
totalizando 240.

Evidência: docs/evidencias/issue-57/reconstituicao-entidades.md.

### Limites

Sem JDBC, tabelas ou conexão MySQL.
Não comprova persistência, idempotência do armazenamento ou rollback.

### Situação

Implementação e verificações integradas à main pelo PR #58.

## Conexão JDBC e esquema MySQL — Issue #59

### Objetivo

Preparar a conexão e o esquema inicial para a persistência
da etapa B da SPEC-005.

### Implementação

- Dependência MySQL Connector/J 9.7.0.
- ConfiguracaoBanco com configuração por variáveis de ambiente.
- FabricaConexoes com fechamento sob responsabilidade do chamador.
- Script V001__criar_tabelas.sql com quatro tabelas InnoDB.
- Bancos separados finops_dev e finops_test.
- Contas próprias com permissões distintas para desenvolvimento e testes.

### Verificação

- Conexão com MySQL 8.0.44 utilizando conta de testes e TLS.
- Esquema criado nos dois bancos.
- Conferência de 17 restrições em finops_test.
- Três testes manuais de rejeição por CHECK e chave estrangeira.
- ROLLBACK dos cenários manuais, com ausência do departamento
  temporário confirmada após o terceiro cenário.
- 14 verificações de configuração e 240 anteriores aprovadas,
  totalizando 254 verificações sem conexão com MySQL.

Evidência: docs/evidencias/issue-59/jdbc-esquema-mysql.md.

### Limites

Não implementa repositórios JDBC nem transações financeiras.
Não comprova recuperação das entidades entre processos,
idempotência, reconciliação ou desempenho com MySQL.

### Situação

Implementação integrada à main pelo PR #60.

## Cadastro e consulta JDBC — Issue #61

### Objetivo

Persistir e recuperar departamentos e recursos do catálogo,
preservando identidade, nomes e valores armazenados.

### Implementação

- DepartamentoRepositoryJdbc e RecursoCatalogoRepositoryJdbc.
- Conexão e transação sob responsabilidade do chamador.
- Comandos SQL parametrizados.
- Validação dos limites do esquema por ValidacaoCadastroJdbc.
- FalhaPersistenciaException distingue falha de acesso de ausência.
- Recuperação do departamento sem reinicializar seu saldo.

### Verificação

- Compilação de 37 arquivos Java com release 25.
- 282 verificações sem banco aprovadas, incluindo as 254 anteriores.
- 14 verificações JDBC aprovadas em finops_test.
- Gravação, recuperação e limpeza executadas em processos Java distintos.

Evidência: docs/evidencias/issue-61/cadastros-jdbc.md.

### Limites

Não inclui persistência de requisições e itens,
validação financeira transacional ou avaliação de desempenho.
Os limites de cobertura estão registrados nas evidências.

### Situação

Implementação integrada à main pelo PR #62.

## Armazenamento JDBC de requisições e itens — Issue #63

### Objetivo

Persistir e recuperar requisições completas, preservando identidade,
ordem dos itens, preços históricos, estado e saldo.

### Implementação

- RequisicaoRepositoryJdbc implementa cadastro, busca e listagem.
- DadosCadastroRequisicaoJdbc prepara uma cópia validada dos dados.
- GravadorRequisicaoJdbc insere cabeçalho e itens na transação do chamador.
- LeitorRequisicaoJdbc utiliza uma única instrução SELECT por consulta.
- EstadoRequisicaoJdbc converte os códigos armazenados para o domínio.
- Cadastro exige autocommit desabilitado.
- Consultas exigem READ COMMITTED ou REPEATABLE READ.
- Conexão, commit e rollback permanecem sob responsabilidade do chamador.
- Recuperação não executa roteamento nem desconto.

### Verificação

- Compilação de 44 arquivos Java com release 25.
- 282 verificações sem banco aprovadas.
- 14 verificações de cadastros JDBC aprovadas.
- 35 verificações de requisições JDBC aprovadas.
- Cenário adicional de gravação, consulta e limpeza em processos distintos.
- Preços históricos preservados após alteração do catálogo.
- Rollback de gravação parcial exercitado com falha JDBC simulada.
- Total inconsistente, lacuna na ordem e cabeçalho sem itens rejeitados.

Evidência: docs/evidencias/issue-63/requisicoes-itens-jdbc.md.

### Limites

Não inclui validação financeira transacional, criação idempotente
ou reconciliação de commit. Os casos de uso A2 permanecem em memória.
Os demais limites de cobertura estão registrados nas evidências.

### Situação

Implementação integrada à main pelo PR #64.

## Criação transacional JDBC — Issue #65

### Objetivo

Criar requisições com controle transacional e reconhecer repetições
pelo UUID, preservando o conteúdo e os valores históricos do pedido.

### Implementação

- Caso de uso de criação persistente com porta sem tipos JDBC.
- Validação e cópia da entrada antes do acesso ao armazenamento.
- Conexão exclusiva e transação em REPEATABLE READ.
- Preços de novos pedidos obtidos do catálogo.
- Confirmação de cabeçalho e itens antes de retornar CRIADA.
- Repetição equivalente retorna JA_EXISTENTE.
- Comparação de departamento e sequência de recursos e quantidades.
- Duplicata concorrente conferida em nova conexão.
- Confirmação desconhecida representada por exceção com UUID.
- Reconciliação por nova chamada com o UUID e conteúdo originais.
- Falha de fechamento após sucesso sinalizada no resultado.
- Criação sem desconto de saldo ou roteamento.

### Verificação

- Compilação de 57 arquivos Java com release 25.
- Preservação das 331 verificações anteriores.
- 47 verificações da criação transacional JDBC aprovadas.
- Total de 378 verificações.
- Cenário adicional de criação, repetição e limpeza entre processos Java.
- Disputa controlada pelo mesmo UUID com conexões independentes.
- Falhas simuladas de inserção, commit e fechamento.
- Preservação de pedidos já processados em repetições equivalentes.

Evidência: docs/evidencias/issue-65/criacao-transacional-jdbc.md.

### Limites

Não inclui validação financeira transacional, aprovação manual,
API, autenticação, portal ou provisionamento real.

As falhas controladas não equivalem a testes de queda real de rede
ou reinício do MySQL. Os limites de cobertura estão nas evidências.

### Situação

Implementação e verificações integradas à main pelo PR #66.

## Validação financeira transacional JDBC — Issue #67

### Objetivo

Validar requisições persistidas, confirmando saldo e estado
na mesma transação e impedindo desconto duplicado.

### Implementação

- Caso de uso por UUID e porta de aplicação sem tipos JDBC.
- Conexão exclusiva por execução, com READ COMMITTED.
- Bloqueios na ordem departamento, depois requisição.
- Recuperação dos valores vigentes e preservação dos preços históricos.
- Reutilização das regras financeiras do domínio.
- Confirmação conjunta do saldo e do estado.
- Repetição de pedido liberado sem novo desconto.
- Preservação de pedidos em revisão.
- Tratamento explícito de confirmação desconhecida.
- Aviso de falha de fechamento após sucesso confirmado.

### Verificação

- 378 verificações anteriores preservadas.
- 84 verificações transacionais JDBC aprovadas.
- Total de 462 verificações aprovadas.
- Teste adicional em processos Java separados aprovado.
- Isolamento verificado com um departamento bloqueado enquanto
  outro concluía a validação.

Evidências:
[Issue #67](evidencias/issue-67/validacao-financeira-jdbc.md).

### Limites

Falhas de commit e fechamento simuladas.
Concorrência entre pedidos distintos demonstrada pela disputa
pela reserva, sem cenário JDBC separado de saldo insuficiente.
RNF-01 com MySQL não comprovado por esses testes.

Perfis de acesso, fluxo manual, API, autenticação, portal
e provisionamento real permanecem fora deste recorte.

### Situação

Implementação integrada pelo PR #68.
Evidências integradas pelo PR #69.
Issue #67 encerrada como concluída no recorte documentado.

## Perfis, identidade e controle de acesso — SPEC-006

### Objetivo

Restringir operações conforme identidade, perfil, autoria e vínculo
departamental, preservando o isolamento entre departamentos.

### Especificação inicial

- Dois perfis no MVP: Desenvolvedor e Arquiteto Cloud.
- Um departamento ativo por desenvolvedor para criação de requisições.
- Criação vinculada à identidade confiável e ao departamento do solicitante.
- Consulta do desenvolvedor limitada à própria autoria e departamento atual.
- Consulta do arquiteto abrangendo todos os departamentos.
- Criação de requisições negada ao arquiteto.
- Aprovação técnica e autorização financeira permanecem distintas.
- Repetição de criação sujeita à autorização e à conferência de autoria.
- Dados sem autoria confiável não são atribuídos a usuários por suposição.

Referência:
[SPEC-006](casos-de-uso/SPEC-006-perfis-acesso.md).

### Próximo recorte

Revisar os critérios de aceite e inspecionar o modelo Java, os casos de uso
e o esquema MySQL antes de definir a implementação e a migração.

Detalhar a origem confiável da identidade e a integração da autorização
com criação, consulta, listagem e repetição.

### Limites

A documentação não comprova autenticação ou autorização implementadas.
Os testes anteriores de domínio e JDBC não verificam acesso por usuário.

Login, provedor de identidade, gestão de usuários, fluxo manual,
auditoria e API possuem entregas próprias.

### Situação

Especificação inicial integrada pelo PR #71.
Base de identidade e política isolada implementadas e verificadas
localmente na Issue #72.

Integração aos casos de uso, autenticação real, persistência da autoria
e execução dos critérios de aceite dos fluxos integrados permanecem
pendentes.

## Base de identidade e autorização — Issue #72

### Objetivo

Implementar e verificar decisões de autorização na camada de aplicação,
conforme a SPEC-006.

### Implementação local

- Perfis Desenvolvedor e Arquiteto Cloud.
- Contexto imutável com identidade, perfil, situação ativa e departamento.
- Contrato ProvedorIdentidade para uma futura integração confiável.
- Política de acesso ao catálogo, criação e consulta de requisições.
- Verificador independente, executável sem banco.

### Verificação

- Compilação de 72 arquivos Java com release 25 aprovada.
- 34 verificações da política aprovadas.
- 282 verificações anteriores sem banco aprovadas.
- Total executado nesta etapa: 316 verificações.

Evidências:
[Issue #72](evidencias/issue-72/base-identidade-autorizacao.md).

### Limites

A política ainda não é chamada pelos casos de uso existentes.
O contrato de identidade não constitui autenticação implementada.

Persistência da autoria, listagens protegidas, autorização de repetição
e coerência transacional diante de mudanças de vínculo ficam para
entregas posteriores.

As verificações JDBC anteriores não foram reexecutadas nesta etapa.

### Situação

Implementação e verificações locais concluídas no recorte descrito.
Revisão final e integração à main pendentes.