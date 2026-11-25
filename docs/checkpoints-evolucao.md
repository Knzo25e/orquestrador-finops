# Checkpoints de Acompanhamento e Evolução Incremental

Este documento consolida a linha do tempo de construção do **Orquestrador FinOps**, demonstrando a evolução progressiva do repositório ao longo do semestre e o vínculo entre especificações, migrações de banco e evidências de verificação (`docs/evidencias/`).

## 1. Marcos de Evolução do Projeto

| Etapa / Checkpoint | Escopo Entregue | Artefatos e Migrações | Evidências Registradas |
| :--- | :--- | :--- | :--- |
| **Marco 1 — Especificação e Modelagem SDD** | Delimitação do problema FinOps, requisitos em notação EARS, casos de uso, diagramas UML e decisões arquiteturais iniciais. | `docs/spec.md`, `SPEC-002` a `SPEC-009`, `docs/plan.md`, `docs/tasks.md`, `docs/uml/`, `ADR-001` a `ADR-004` | `docs/rastreabilidade-requisitos.md` |
| **Marco 2 — Núcleo de Domínio em Memória** | Estrutura Maven em Java puro, entidades de domínio (`Departamento`, `Requisicao`, `ItemRequisicao`), precisão com `BigDecimal` e repositórios em memória sem dependência de banco. | `br.mackenzie.finops.dominio`, `repositorios/memoria/` | `docs/evidencias/issue-41/` a `issue-57/` |
| **Marco 3 — Persistência MySQL e Transações** | Esquema relacional inicial, adaptadores JDBC (`*Jdbc`) e validação orçamentária transacional (`commit`/`rollback`) sob concorrência. | `V001__criar_tabelas.sql`, `ValidacaoRequisicaoJdbc.java` | `docs/evidencias/issue-59/` a `issue-67/` |
| **Marco 4 — Identidade, Perfis e Autoria** | Autenticação por sessão HTTP com TLS local, segregação entre `DESENVOLVEDOR` e `ARQUITETO` (`PoliticaAutorizacao`) e isolamento de pedidos por autor. | `V002__adicionar_autoria_requisicao.sql`, `V003__criar_usuarios.sql`, `ADR-005` | `docs/evidencias/issue-72/` a `issue-84/` |
| **Marco 5 — Auditoria Imutável e Fluxo Manual** | Trilha de auditoria *append-only* com paginação por cursor (`CursorIntervencao`), reajuste de teto orçamentário e liberação/rejeição manual com justificativa. | `V004__criar_intervencoes.sql`, `V005__ampliar_estados_requisicao.sql`, `ADR-004` | `docs/evidencias/issue-101/` a `issue-109/` |
| **Marco 6 — Portal Web, RNF-01 e Governança** | Interfaces HTML/CSS/JS nativas (`login`, `desenvolvedor`, `arquiteto`), benchmark de latência no MySQL, homologação ponta a ponta e consolidação da estratégia de testes e revisões. | `src/main/resources/portal/`, `MedirDesempenhoOrcamentario.java`, `docs/estrategia-testes.md`, `docs/review/` | `docs/evidencias/issue-118/`, `rnf-01-mysql.md`, capturas em `docs/evidencias/portal-navegacao/` |

## 2. Garantias de Rastreabilidade Contínua
- **Ausência de *Spec Drift*:** Cada alteração nas regras de negócio ou estados da requisição (`V005`) foi acompanhada da atualização correspondente em `docs/spec.md` e nos casos de uso.
- **Fechamento com Evidência:** Todas as Issues concluídas possuem vínculo direto com Pull Requests revisados e saídas de execução versionadas em `docs/evidencias/`.
