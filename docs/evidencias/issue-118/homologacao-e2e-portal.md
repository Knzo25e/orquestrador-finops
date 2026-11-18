# Evidências de Homologação Ponta a Ponta — Portal FinOps

Este documento registra os cenários de aceitação funcional executados no portal web (`login.html`, `desenvolvedor.html` e `arquiteto.html`) para validar a integração entre interface HTTP, casos de uso de aplicação, controle de acesso por perfil e persistência transacional no MySQL.

---

## Matriz de Cenários Validados

| Cenário | Perfil Utilizado | Especificação Coberta | Comportamento Esperado | Status |
| :--- | :--- | :--- | :--- | :--- |
| **CT-01:** Autenticação e isolamento de sessão | Desenvolvedor / Arquiteto | SPEC-006 | Redirecionamento para a visão correspondente ao perfil e bloqueio de rotas não autorizadas. | Aprovado |
| **CT-02:** Submissão dentro do teto orçamentário | Desenvolvedor | SPEC-002, SPEC-003, SPEC-005 | Persistência atômica da requisição e itens no MySQL, transição automática para estado aprovada e abatimento imediato do saldo. | Aprovado |
| **CT-03:** Submissão acima do teto disponível | Desenvolvedor | SPEC-002, SPEC-003 | Bloqueio da aprovação automática e retenção da requisição para análise manual sem estourar o teto do departamento. | Aprovado |
| **CT-04:** Ajuste de teto departamental | Arquiteto | SPEC-006, SPEC-008 | Atualização do limite orçamentário do departamento refletida nas consultas seguintes. | Aprovado |
| **CT-05:** Intervenção manual e trilha de auditoria | Arquiteto | SPEC-007, SPEC-009 | Liberação ou rejeição manual mediante justificativa obrigatória, gerando registro imutável na trilha de auditoria. | Aprovado |

---

## Resumo da Verificação Arquitetural

- **Integridade Transacional:** A criação de requisições com múltiplos itens grava cabeçalho e itens na mesma transação JDBC, evitando registros parciais em caso de falha.
- **Precisão Financeira:** Os cálculos de custo total e saldo disponível operam com valores decimais exatos (`BigDecimal`), garantindo consistência entre o portal e o banco relacional.
- **Rastreabilidade:** Todas as decisões manuais permanecem acessíveis apenas para leitura na trilha de auditoria, sem operações de alteração ou exclusão.
