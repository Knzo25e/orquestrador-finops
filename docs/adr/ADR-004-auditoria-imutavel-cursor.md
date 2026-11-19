# ADR-004: Trilha de Auditoria Imutável com Paginação por Cursor

- **Status:** Aceito
- **Data:** 2026-11

## Contexto
Intervenções manuais realizadas por Arquitetos FinOps (liberação excepcional ou rejeição de requisições retidas) envolvem decisões financeiras sensíveis que exigem rastreabilidade histórica confiável e leitura consistente mesmo sob inserções concorrentes.

## Decisão
Modelar os eventos de intervenção (`EventoIntervencao` / `IntervencaoAuditadaJdbc`) como registros estritamente *append-only* (sem operações de atualização ou exclusão na aplicação) e expor a listagem paginada por **cursor** (`CursorIntervencao`) em vez de paginação tradicional por `OFFSET`.

## Consequências
- **Positivas:** Impede adulteração do histórico de decisões pela aplicação e evita duplicação ou salto de registros durante a navegação entre páginas enquanto novas intervenções ocorrem.
- **Negativas:** Não permite navegar diretamente para um número arbitrário de página, exigindo navegação sequencial pelo cursor.
