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
Build configurado e verificado localmente com Java 25 e Maven Wrapper. Limpeza, compilação e 75 verificações aprovadas, conforme docs/evidencias/issue-43/build.md. Revisão do PR pendente.