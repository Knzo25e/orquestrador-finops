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

### [RF-02] Validação Automática de Orçamento (Máquina de Estados)
O sistema deve avaliar o custo projetado da requisição frente ao orçamento do departamento do Desenvolvedor, decidindo o roteamento automático do pedido.
* **[RN-02]** O sistema não pode aprovar automaticamente pedidos que ultrapassem o saldo disponível da equipe.
* **Cenário 1:** Se o custo projetado for menor ou igual ao orçamento, o sistema deve mudar o status da requisição para "Provisionamento Liberado".
* **Cenário 2:** Se o custo projetado estourar a verba do departamento, o sistema deve bloquear o pedido e alterar o status para "Revisão Pendente".

### [RF-03] Avaliação de Requisições Pendentes pelo Arquiteto Cloud
O sistema deve fornecer uma fila de aprovação para o Arquiteto Cloud atuar sobre os pedidos em "Revisão Pendente", permitindo intervenção manual.
* **[RN-03]** O Arquiteto Cloud possui permissão para sobrescrever a trava de orçamento mediante justificativa, ou alterar os itens originais do pedido.
* **Cenário 1:** Quando o Arquiteto escolher "Aprovar", o sistema deve registrar a aprovação manual e mudar o status para "Provisionamento Liberado".
* **Cenário 2:** Quando o Arquiteto escolher "Rejeitar", o sistema deve encerrar o fluxo e mudar o status para "Cancelado".
* **Cenário 3:** Quando o Arquiteto escolher "Alterar", o sistema deve permitir a remoção ou downgrade de instâncias para adequar o custo antes da aprovação.

## 3. Modelo de Domínio (Entidades Principais)

### 3.1. Relacionamentos e Multiplicidade
* Um **Departamento** possui de 0 a N **Requisições**. Cada **Requisição** pertence a exatamente 1 **Departamento**.
* Uma **Requisição** contém de 1 a N **Itens de Requisição**. Cada **Item de Requisição** pertence a exatamente 1 **Requisição**.
* Cada **Item de Requisição** referencia exatamente 1 **RecursoCatalogo**. Um **RecursoCatalogo** pode aparecer em 0 a N **Itens de Requisição**.

### 3.2. Entidades, Atributos e Operações

**Entidade: RecursoCatalogo**
Representa um item de infraestrutura disponível para contratação.
* **Atributos:**
  * id: UUID
  * nome: String
  * cpu: Integer
  * ram: Integer (GB)
  * armazenamento: Integer (GB)
  * custoMensal: Double
* **Operações:**
  * atualizarCusto(novoValor: Double)

**Entidade: Departamento**
Representa o centro de custos ao qual os desenvolvedores pertencem.
* **Atributos:**
  * id: UUID
  * nome: String
  * orcamentoMensal: Double
  * saldoDisponivel: Double
* **Operações:**
  * descontarSaldo(valor: Double): void
  * verificarDisponibilidade(valor: Double): boolean

**Entidade: Requisicao**
Representa o pedido submetido pelo Desenvolvedor.
* **Atributos:**
  * id: UUID
  * desenvolvedorId: UUID
  * status: String ("Em Analise", "Aprovado", "Rejeitado")
  * custoTotalProjetado: Double
  * justificativaArquiteto: String
* **Operações:**
  * calcularTotalProjetado(): Double
  * aprovarAutomaticamente(): void
  * enviarParaRevisao(): void

**Entidade: ItemRequisicao**
Vincula o recurso solicitado à quantidade desejada nesta requisição específica.
* **Atributos:**
  * id: UUID
  * quantidade: Integer
  * custoSubtotal: Double