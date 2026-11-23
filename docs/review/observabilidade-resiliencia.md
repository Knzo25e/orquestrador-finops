# Revisão Multidimensional — Observabilidade e Resiliência

Registro dos achados técnicos identificados durante a estabilização do **Orquestrador FinOps** na dimensão de **observabilidade e resiliência operacional**, juntamente com as ações implementadas no código.

## 1. Achados Identificados

### Achado 1 — Rastreabilidade de Decisões Humanas Excepcionais
- **Diagnóstico:** Aprovações ou rejeições manuais realizadas pelo Arquiteto FinOps poderiam perder contexto caso fossem registradas apenas como alteração de coluna na tabela `requisicoes`, dificultando auditoria posterior sobre quem liberou um estouro de teto e por qual motivo.
- **Risco:** Baixa rastreabilidade operacional e ausência de histórico cronológico confiável.

### Achado 2 — Paginação Instável em Consultas de Auditoria Sob Concorrência
- **Diagnóstico:** A listagem paginada tradicional baseada em `LIMIT` e `OFFSET` sofre deslocamento de registros quando novos eventos de intervenção são inseridos enquanto o operador navega entre páginas.
- **Risco:** Duplicação ou omissão de eventos na leitura da trilha de auditoria.

### Achado 3 — Exposição de Detalhes Internos de Exceções SQL/JDBC
- **Diagnóstico:** Falhas de indisponibilidade do MySQL ou violações de integridade poderiam vazar stack traces técnicos na resposta HTTP ou deixar transações abertas sem sinalização clara ao cliente.
- **Risco:** Degradação de diagnósticos e exposição de estrutura interna do banco.

## 2. Ações Tomadas

| Achado | Ação Implementada | Artefatos Relacionados |
| :--- | :--- | :--- |
| **1. Rastreabilidade de Intervenções** | Criação de modelo dedicado de eventos imutáveis (`EventoIntervencao`), exigindo justificativa obrigatória (`SolicitacaoIntervencaoRequisicao`) e persistência estritamente *append-only* (`V004__criar_intervencoes.sql`, `IntervencaoAuditadaJdbc`). | `SPEC-007`, `ADR-004`, `AuditoriaMysqlIT.java` |
| **2. Consistência de Paginação** | Substituição de paginação por deslocamento por cursor determinístico (`CursorIntervencao` e `ConsultaIntervencoesJdbc`). | `ADR-004`, `IntervencoesController.java` |
| **3. Mapeamento Semântico de Falhas** | Centralização do tratamento de exceções em `TratamentoErrosHttp`, traduzindo exceções de domínio e infraestrutura (`FalhaPersistenciaException`, `ServicoIndisponivelException`, `ConflitoOperacaoException`) em códigos HTTP padronizados sem vazamento de stack trace, além de instrumentação de latência para o `RNF-01`. | `TratamentoErrosHttp.java`, `MedirDesempenhoOrcamentario.java` |
