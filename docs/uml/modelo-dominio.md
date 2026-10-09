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
| `orcamentoMensal` | BigDecimal | Limite total de gastos estabelecido pela empresa para o mês. |
| `saldoDisponivel` | BigDecimal | Orçamento mensal menos o valor já comprometido. A aprovação técnica, isoladamente, não altera esse saldo. |
| `descontarSaldo(valor)` | void | Deduz o valor na liberação orçamentária da requisição, respeitando a consistência e a proteção contra desconto duplicado previstas na RB-05. |
| `verificarDisponibilidade(valor)` | boolean | Retorna verdadeiro se o saldo disponível cobrir o valor recebido como parâmetro. |

**Requisição**
O pedido central submetido pelo Desenvolvedor, que trafega pela máquina de estados e fluxo de aprovação.

| Atributo / Método | Tipo | Descrição |
| :--- | :--- | :--- |
| `id` | UUID | Identificador único da requisição. |
| `desenvolvedorId` | UUID | Referência ao usuário solicitante. |
| `status` | String | Estado atual da requisição, conforme os estados e as transições definidos na seção 3.3 de [spec.md](../spec.md). |
| `custoTotalProjetado` | BigDecimal | Soma do custo subtotal de todos os itens vinculados. |
| `calcularTotalProjetado()`| BigDecimal | Itera sobre os itens do pedido e atualiza o custo total. |
| `aprovarAutomaticamente()`| void | Altera o status para liberado e aciona o desconto do saldo. |
| `enviarParaRevisao()` | void | Bloqueia o pedido para análise manual do Arquiteto Cloud. |

**RecursoCatalogo**
O "cardápio" fixo de infraestrutura mantido pela equipe de governança/FinOps.

| Atributo / Método | Tipo | Descrição |
| :--- | :--- | :--- |
| `id` | UUID | Identificador único do recurso. |
| `nome` | String | Descrição do recurso (ex: "Banco de Dados 16GB"). |
| `custoMensal` | BigDecimal | O preço interno fixado (Chargeback) para o consumo daquele recurso. |

**ItemRequisicao**
A entidade associativa que vincula o pedido ao catálogo, especificando o volume solicitado.

| Atributo / Método | Tipo | Descrição |
| :--- | :--- | :--- |
| `quantidade` | Integer | Quantas instâncias daquele recurso específico foram pedidas. |
| `custoSubtotal` | BigDecimal | O `custoMensal` do recurso multiplicado pela `quantidade`. |

## 3. Responsabilidades previstas — Issues #31 e #35

Este modelo descreve o comportamento esperado do domínio. Não comprova que todas as regras e operações estejam implementadas.

### Departamento

- Preservar o valor já comprometido ao alterar o teto mensal.
- Recalcular o saldo disponível conforme RB-04.
- Rejeitar um novo teto inferior ao valor comprometido, mantendo os valores anteriores e informando o motivo.
- Participar da avaliação da reserva, calculada inicialmente como 10% do orçamento mensal.
- Participar da verificação e do comprometimento consistente do saldo na liberação, conforme RB-05.

O valor comprometido corresponde aos custos estimados das requisições já liberadas no período considerado. Não representa uma fatura real da AWS.

A reserva utiliza o teto mensal como base, não o saldo restante. Alterações do teto afetam o valor da reserva considerado nas avaliações seguintes.

A representação técnica do valor comprometido, da reserva e de seu percentual será definida nas Specs de implementação. A abrangência da configuração do percentual permanece em OPEN-003-04; este modelo não presume que ela seja um atributo de cada departamento.

### Requisição

- Utilizar exclusivamente o departamento vinculado ao pedido na validação orçamentária.
- Manter o fluxo automático descrito no UC-02 e na SPEC-003.
- Permitir aprovação automática somente quando o custo couber no saldo e o saldo após o pedido ficar estritamente acima da reserva.
- Encaminhar pedidos para Revisão Pendente, sem desconto, quando faltar saldo ou quando a liberação atingir ou consumir a reserva.
- Tornar identificável o motivo do encaminhamento: insuficiência de saldo ou utilização da reserva.
- Registrar aprovação técnica ou rejeição conforme RF-03.
- Após aprovação técnica, não comprometer saldo nem autorizar provisionamento por esse ato isolado.
- Encaminhar pedidos aprovados tecnicamente para AGUARDANDO_AJUSTE_ORCAMENTARIO quando faltar saldo.
- Encaminhar pedidos aprovados tecnicamente para AGUARDANDO_AUTORIZACAO_FINANCEIRA quando houver saldo suficiente, mas a liberação depender de autorização para utilizar a reserva.
- Exigir ajuste autorizado quando faltar saldo e autorização explícita quando a liberação atingir ou consumir a reserva vigente.
- Revalidar as condições financeiras antes da liberação, respeitando RB-05.
- Preservar o estado e o comprometimento de uma requisição já liberada quando houver repetição de processamento.

Os estados e as transições seguem a seção 3.3 de spec.md. Os casos manuais ainda não definidos permanecem em OPEN-003-05 e deverão ser detalhados na Spec 009.

### Separação de responsabilidades

A aprovação técnica, o ajuste do teto e a autorização de uso da reserva são ações separadas. No MVP, são realizadas pelo perfil Arquiteto Cloud e devem ser auditadas conforme RF-05.

A autorização para utilizar a reserva não permite saldo negativo. O aumento do teto não é obrigatório quando já existe saldo suficiente e o impedimento é apenas a utilização da reserva.

A autorização dos usuários e a coordenação da auditoria devem respeitar a arquitetura em camadas. O domínio permanece responsável pelas regras financeiras e pelas mudanças de estado.

### Limites da representação atual

As assinaturas das novas operações serão definidas nas Specs de implementação.

Os valores monetários utilizam BigDecimal, com duas casas decimais e rejeição de entradas com frações de centavo, conforme a Issue #37. A política de arredondamento da reserva foi definida como HALF_UP para duas casas decimais; sua aplicação permanece pendente na implementação do cálculo da reserva.

A verificação isolada de disponibilidade de saldo não é suficiente para aprovar automaticamente: a condição de reserva também deve ser atendida.

A consistência em concorrência depende do mecanismo e do alcance a definir em OPEN-003-02.

### Referências

- Requisitos, regras e estados: [spec.md](../spec.md).
- Decisões financeiras: seção 4 de [arquitetura.md](../arquitetura.md).
- Caso de uso: [UC-02](../casos-de-uso/UC-02-validacao-orcamento.md).
- Critérios da validação automática: [SPEC-003](../casos-de-uso/SPEC-003-validar-orcamento.md).
- Planejamento das Specs: [mapa-de-specs.md](../mapa-de-specs.md).