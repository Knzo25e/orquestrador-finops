# Revisão Multidimensional — Performance e Concorrência

Registro dos achados técnicos na dimensão de **performance e controle de concorrência** do **Orquestrador FinOps**, vinculado ao requisito não funcional `RNF-01` e às evidências em `docs/evidencias/rnf-01-mysql.md`.

## 1. Achados Identificados

### Achado 1 — Contenção de Saldo Orçamentário sob Submissões Simultâneas
- **Diagnóstico:** Quando múltiplos desenvolvedores submetem requisições para o mesmo departamento ao mesmo tempo, leituras não sincronizadas do saldo disponível poderiam aprovar pedidos acima do teto mensal.
- **Risco:** Violação da regra central de governança financeira (`RF-02` / `SPEC-003`).

### Achado 2 — Sobrecarga de Abertura de Conexões JDBC em Validações Sequenciais
- **Diagnóstico:** Abertura descontrolada de conexões ou falta de fechamento determinístico em operações transacionais degrada o tempo de resposta da validação orçamentária (`RNF-01`).
- **Risco:** Aumento de latência p95/p99 e esgotamento de conexões no MySQL.

## 2. Ações Tomadas

| Achado | Ação Implementada | Evidências e Classes Relacionadas |
| :--- | :--- | :--- |
| **1. Controle de Concorrência** | Execução atômica da leitura de saldo, criação da requisição e abatimento orçamentário dentro de transações JDBC explícitas (`commit`/`rollback`), validada por testes concorrentes. | `ValidacaoRequisicaoJdbc.java`, `VerificarConcorrenciaOrcamentaria.java`, `ValidacaoAutorizadaMysqlIT.java` |
| **2. Instrumentação e Latência (`RNF-01`)** | Gerenciamento controlado de conexões via `FabricaConexoes` e criação de suíte dedicada de benchmark para medir latência sob carga no MySQL. | `FabricaConexoes.java`, `MedirDesempenhoOrcamentario.java`, `scripts/medir-rnf01.py`, `docs/evidencias/rnf-01-mysql.md` |
