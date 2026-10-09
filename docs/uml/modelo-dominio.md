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
| `saldoDisponivel` | Double | Orçamento mensal menos o valor já comprometido. A aprovação técnica, isoladamente, não altera esse saldo. |
| `descontarSaldo(valor)` | void | Deduz o valor na liberação orçamentária da requisição, respeitando a consistência e a proteção contra desconto duplicado previstas na RB-05. |
| `verificarDisponibilidade(valor)` | boolean | Retorna verdadeiro se o saldo disponível cobrir o valor recebido como parâmetro. |

**Requisição**
O pedido central submetido pelo Desenvolvedor, que trafega pela máquina de estados e fluxo de aprovação.

| Atributo / Método | Tipo | Descrição |
| :--- | :--- | :--- |
| `id` | UUID | Identificador único da requisição. |
| `desenvolvedorId` | UUID | Referência ao usuário solicitante. |
| `status` | String | Estado atual da requisição, conforme os estados e as transições definidos na seção 3.3 de [spec.md](../spec.md). |
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

## 3. Responsabilidades previstas — Issue #31

Este modelo descreve o comportamento esperado do domínio. Não comprova que todas as regras e operações estejam implementadas.

### Departamento

- Preservar o valor já comprometido ao alterar o teto mensal.
- Recalcular o saldo disponível conforme RB-04.
- Rejeitar um novo teto inferior ao valor comprometido, mantendo os valores anteriores e informando o motivo.
- Participar da verificação e do comprometimento consistente do saldo na liberação, conforme RB-05.

O valor comprometido corresponde aos custos estimados das requisições já liberadas no período considerado. Não representa uma fatura real da AWS. Sua representação técnica será definida na Spec de implementação.

### Requisição

- Manter o fluxo automático de validação descrito no UC-02.
- Encaminhar pedidos sem saldo suficiente para "Revisão Pendente", sem desconto.
- Após aprovação técnica justificada, permanecer em AGUARDANDO_AJUSTE_ORCAMENTARIO, sem comprometer saldo ou autorizar provisionamento.
- Para pedidos nesse estado, permitir liberação somente após ajuste orçamentário autorizado e revalidação com saldo suficiente.
- Permanecer aguardando se a revalidação encontrar saldo insuficiente.
- Registrar a rejeição conforme RF-03.
- Preservar o estado e o comprometimento de uma requisição já liberada quando houver repetição de processamento.

A aprovação técnica e o ajuste do teto são ações separadas. No MVP, ambas são realizadas pelo perfil Arquiteto Cloud e devem ser auditadas conforme RF-05.

As assinaturas das novas operações serão definidas nas Specs de implementação. A autorização dos usuários e a coordenação da auditoria devem respeitar a arquitetura em camadas.

### Referências

- Requisitos, regras e estados: [spec.md](../spec.md).
- Decisões financeiras: seção 4 de [arquitetura.md](../arquitetura.md).
- Validação automática: [UC-02](../casos-de-uso/UC-02-validacao-orcamento.md).
- Planejamento das Specs 003, 008 e 009: [mapa-de-specs.md](../mapa-de-specs.md).
