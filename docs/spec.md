# Especificação do Sistema (Spec)

## 1. Personas (Perfis de Usuário)
* **Desenvolvedor (Solicitante):** Busca agilidade para subir ambientes. Acessa o catálogo, seleciona instâncias (ex: Máquinas, Bancos de Dados) e monta requisições. Possui um orçamento departamental que atua como teto de gastos.
* **Arquiteto Cloud (Aprovador/Gestor):** Responsável por FinOps e segurança. Intervém apenas quando há quebra de limites orçamentários, possuindo autonomia para aprovar exceções, alterar a infraestrutura solicitada (downgrade) ou rejeitar o pedido.

## 2. Requisitos Funcionais (EARS) e Regras de Negócio

### [RF-01] Montar ambiente via catálogo de recursos
O sistema deve permitir que o perfil Desenvolvedor acesse um catálogo de infraestrutura e adicione itens a uma requisição.
* **[RN-01]** Cada item do catálogo deve possuir um custo mensal predefinido no banco de dados.
* **Cenário 1:** Quando o Desenvolvedor acessar a tela de requisição, o sistema deve exibir o catálogo com os valores atualizados.
* **Cenário 2:** O sistema deve calcular dinamicamente e exibir o valor projetado total conforme o usuário seleciona os itens.
## 3. Modelo de Domínio (Entidades Principais)

### Entidade: RecursoCatalogo
Representa um item de infraestrutura disponível para contratação.
* **id:** Identificador único (UUID)
* **nome:** Descrição do recurso (ex: "Máquina Virtual Padrão")
* **cpu:** Quantidade de núcleos de processamento
* **ram:** Quantidade de memória em GB
* **armazenamento:** Capacidade de disco em GB
* **sistemaOperacional:** Software base (ex: Ubuntu, Windows)
* **custoMensal:** Valor financeiro projetado por mês (Double/BigDecimal)

### Entidade: Departamento
Representa o centro de custos ao qual os desenvolvedores pertencem.
* **id:** Identificador único (UUID)
* **nome:** Nome da equipe (ex: "Desenvolvimento Backend")
* **orcamentoMensal:** Teto de gastos planejado para o mês (Double/BigDecimal)
* **saldoDisponivel:** Valor restante liberado para novos provisionamentos (Double/BigDecimal)