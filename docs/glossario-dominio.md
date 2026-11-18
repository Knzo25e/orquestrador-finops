# Glossário de Domínio (Linguagem Ubíqua) — Orquestrador FinOps

Definição dos conceitos centrais modelados no núcleo da aplicação (`br.mackenzie.finops.dominio`) para alinhamento entre regras de negócio, diagramas UML e implementação.

| Termo | Definição no Domínio | Representação no Código |
| :--- | :--- | :--- |
| **Departamento** | Unidade organizacional solicitante que possui um limite financeiro definido para consumo de recursos em nuvem. | `Departamento.java` |
| **Teto Orçamentário** | Valor monetário máximo aprovado para um departamento, utilizado como barreira na validação automática de pedidos. | Atributo financeiro em `Departamento` (`BigDecimal`) |
| **Recurso de Catálogo** | Item de infraestrutura pré-aprovado (ex.: instância computacional, banco de dados) com custo unitário padronizado. | `RecursoCatalogo.java` |
| **Requisição** | Solicitação formal de recursos vinculada a um departamento e a um autor, governada por uma máquina de estados explícita. | `Requisicao.java` e `EstadoRequisicao.java` |
| **Item de Requisição** | Linha individual de uma requisição que associa um recurso do catálogo à quantidade solicitada e preserva o custo do momento do pedido. | `ItemRequisicao.java` |
| **Validação Orçamentária** | Processo automatizado que calcula o custo total da requisição e decide entre aprovação imediata ou retenção para análise manual. | `ValidarOrcamentoUseCase.java` |
| **Intervenção Manual** | Ação administrativa realizada por um Arquiteto FinOps para liberar ou rejeitar uma requisição retida, exigindo justificativa. | `IntervirRequisicaoUseCase.java` e `DecisaoManual.java` |
| **Trilha de Auditoria Imutável** | Histórico cronológico de todas as intervenções manuais e ajustes realizados, protegido contra edição ou exclusão. | `EventoIntervencao.java` e `IntervencaoAuditadaJdbc.java` |
