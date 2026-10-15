# Plano Técnico — Orquestrador FinOps

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

Proposta elaborada; revisão e aprovação pendentes.