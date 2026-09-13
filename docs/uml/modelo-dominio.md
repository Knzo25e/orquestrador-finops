**Modelo de Domínio: Entidades e Relacionamentos**

**1. Tabela de Multiplicidade (Relacionamentos)**

| Origem | Multiplicidade | Destino | Regra de Negócio |
| :--- | :--- | :--- | :--- |
| **Departamento** | `1` -- `0..*` | **Requisição** | Um departamento pode ter várias requisições. Cada requisição pertence a um único departamento. |
| **Requisição** | `1` -- `1..*` | **ItemRequisicao** | Uma requisição contém um ou mais itens. Cada item pertence estritamente àquela requisição. |
| **ItemRequisicao** | `0..*` -- `1` | **RecursoCatalogo** | Vários itens de requisições diferentes podem apontar para o mesmo recurso de catálogo tabelado. |

**2. Dicionário de Entidades**

**Departamento**
Representa o centro de custos que agrupa os desenvolvedores, definindo e controlando o teto financeiro mensal.

| Atributo / Método | Tipo | Descrição |
| :--- | :--- | :--- |
| `id` | UUID | Identificador único do departamento. |
| `nome` | String | Nome do setor (ex: "Marketing", "Engenharia"). |
| `orcamentoMensal` | Double | Limite total de gastos estabelecido pela empresa para o mês. |
| `saldoDisponivel` | Double | Saldo restante atualizado dinamicamente após aprovações. |
| `descontarSaldo(valor)` | void | Deduz o custo de uma requisição aprovada do saldo atual. |
| `verificarDisponibilidade()` | boolean | Retorna verdadeiro se o saldo cobrir o valor recebido como parâmetro. |

**Requisição**
O pedido central submetido pelo Desenvolvedor, que trafega pela máquina de estados e fluxo de aprovação.

| Atributo / Método | Tipo | Descrição |
| :--- | :--- | :--- |
| `id` | UUID | Identificador único da requisição. |
| `desenvolvedorId` | UUID | Referência ao usuário solicitante. |
| `status` | String | Estado atual (ex: "Em Análise", "Revisão Pendente", "Liberado"). |
| `custoTotalProjetado` | Double | Soma do custo subtotal de todos os itens vinculados. |
| `calcularTotalProjetado()`| Double | Itera sobre os itens do pedido e atualiza o custo total. |
| `aprovarAutomaticamente()`| void | Altera o status para liberado e aciona o desconto do saldo. |
| `enviarParaRevisao()` | void | Bloqueia o pedido para análise manual do Arquiteto Cloud. |

**RecursoCatalogo**
O "cardápio" fixo de infraestrutura mantido pela equipe de governança/FinOps.

| Atributo / Método | Tipo | Descrição |
| :--- | :--- | :--- |
| `id` | UUID | Identificador único do recurso. |
| `nome` | String | Descrição do recurso (ex: "Banco de Dados 16GB"). |
| `custoMensal` | Double | O preço interno fixado (Chargeback) para o consumo daquele recurso. |

**ItemRequisicao**
A entidade associativa que vincula o pedido ao catálogo, especificando o volume solicitado.

| Atributo / Método | Tipo | Descrição |
| :--- | :--- | :--- |
| `quantidade` | Integer | Quantas instâncias daquele recurso específico foram pedidas. |
| `custoSubtotal` | Double | O `custoMensal` do recurso multiplicado pela `quantidade`. |
