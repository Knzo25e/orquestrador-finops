# Especificação do Sistema (Spec)

## 1. Personas e Contexto de Uso

**Carlos, o Desenvolvedor Ágil (Solicitante)**
* **Contexto:** Trabalha em uma squad e precisa provisionar bancos de dados e máquinas para testar aplicações.
* **Dores:** Sofre com tickets de TI lentos e burocracia que travam suas entregas.
* **Comportamento no Sistema:** Acessa o catálogo, monta o pedido e espera aprovação instantânea para seguir trabalhando, sem interagir com gestores caso o custo da infraestrutura caiba no orçamento.

**Ana, a Arquiteta Cloud (Aprovadora/FinOps)**
* **Contexto:** Controla a governança e o orçamento de infraestrutura da empresa inteira.
* **Dores:** Lida com o desperdício gerado por máquinas superdimensionadas e orçamentos departamentais estourados.
* **Comportamento no Sistema:** Não solicita infraestrutura. Acessa uma fila central de requisições retidas, analisa as justificativas técnicas e detém a permissão exclusiva para sobrescrever a trava financeira, fazer downgrade de instâncias ou rejeitar compras.

## 2. Requisitos Funcionais, Não Funcionais e Regras (EARS)

### 2.1. Requisitos Funcionais (RF)
* **RF-01:** O sistema deve permitir que o perfil Desenvolvedor acesse um catálogo de infraestrutura e adicione itens a uma requisição.
* **RF-02:** O sistema deve avaliar o custo projetado da requisição frente ao orçamento do departamento, decidindo o roteamento automático do pedido.
* **RF-03:** O sistema deve fornecer uma fila de aprovação para o Arquiteto Cloud atuar sobre os pedidos em "Revisão Pendente".
* **RF-04 (Gestão de Teto):** O sistema deve permitir que o Arquiteto Cloud configure e altere o orçamento mensal (teto de gastos) de cada departamento.
* **RF-05 (Auditoria Imutável):** O sistema deve registrar um histórico imutável (log de auditoria) de todas as intervenções manuais, armazenando o ID de quem aprovou/rejeitou, a data, a requisição e a justificativa.

### 2.2. Requisitos Não Funcionais (RNF)
* **RNF-01 (Desempenho):** O sistema deve processar a validação automática de orçamento em menos de 2 segundos.
* **RNF-02 (Segurança):** O sistema deve garantir o isolamento de dados, impedindo que um Desenvolvedor utilize o orçamento de um departamento ao qual não está vinculado.

### 2.3. Regras de Negócio (RB) - Formato EARS
* **RB-01 (Catálogo Fixo):** **WHILE** o sistema estiver operando, **IT HAS TO** garantir que cada item do catálogo possua um custo mensal predefinido no banco de dados.
* **RB-02 (Trava de Orçamento):** **WHILE** a requisição estiver em validação, **IF** o custo projetado ultrapassar o saldo disponível da equipe, **THEN** o sistema não pode aprovar automaticamente o pedido, bloqueando-o com o status "Revisão Pendente".
* **RB-03 (Intervenção Manual):** **WHILE** o pedido estiver bloqueado em "Revisão Pendente", **IF** o Arquiteto Cloud fornecer uma justificativa, **THEN** o sistema deve permitir a aprovação manual sobrescrevendo a trava de orçamento.

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