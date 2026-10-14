# Tarefas — Orquestrador FinOps

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
- [ ] Criar commit e publicar a branch.
- [ ] Abrir PR para revisão.

A integração em memória não conclui a persistência MySQL nem a estratégia transacional completa da SPEC-005.