# Especificação do Sistema (Spec)

## 1. Personas e Contexto de Uso

**Carlos, o Desenvolvedor Ágil (Solicitante)**
* **Contexto:** Trabalha em uma squad e precisa provisionar bancos de dados e máquinas para testar aplicações.
* **Dores:** Sofre com tickets de TI lentos e burocracia que travam suas entregas.
* **Comportamento no Sistema:** Acessa o catálogo, monta o pedido e espera aprovação instantânea para seguir trabalhando, sem interagir com gestores caso o custo da infraestrutura caiba no orçamento.

**Ana, a Arquiteta Cloud (Aprovadora/FinOps)**
* **Contexto:** Controla a governança e o orçamento de infraestrutura da empresa inteira.
* **Dores:** Lida com o desperdício gerado por máquinas superdimensionadas e orçamentos departamentais estourados.
* **Comportamento no Sistema:** Não solicita infraestrutura. Analisa pedidos em revisão, aprova tecnicamente ou rejeita solicitações e administra o orçamento dos departamentos. No MVP, acumula a responsabilidade técnica e orçamentária, mas executa aprovação técnica e ajuste de orçamento como ações separadas, com justificativa e registro de auditoria. Não pode liberar provisionamento sem saldo suficiente.

## 2. Requisitos Funcionais, Não Funcionais e Regras (EARS)

### 2.1. Requisitos Funcionais (RF)
* **RF-01:** O sistema deve permitir que o perfil Desenvolvedor acesse um catálogo de infraestrutura e adicione itens a uma requisição.
* **RF-02:** O sistema deve avaliar o custo projetado da requisição frente ao orçamento do departamento, decidindo o roteamento automático do pedido.
* **RF-03 (Revisão Técnica):** WHEN o Arquiteto Cloud analisar uma requisição em "Revisão Pendente", o sistema SHALL permitir registrar sua aprovação técnica ou rejeição com justificativa. A aprovação técnica encaminha o pedido para `AGUARDANDO_AJUSTE_ORCAMENTARIO`, sem autorizar o provisionamento.
* **RF-04 (Gestão de Teto):** WHEN o Arquiteto Cloud solicitar a configuração ou alteração do orçamento mensal de um departamento, o sistema SHALL validar o novo teto, preservar o valor já comprometido e recalcular o saldo disponível conforme a RB-04.
* **RF-05 (Auditoria Imutável):** WHEN uma intervenção manual for efetivada, o sistema SHALL registrar um histórico imutável contendo a ação, o responsável, a data, a justificativa e o departamento ou a requisição afetada. Para ajustes orçamentários, o registro SHALL incluir o teto anterior, o novo teto e o valor do ajuste.

### 2.2. Requisitos Não Funcionais (RNF)
* **RNF-01 (Desempenho):** O sistema deve processar a validação automática de orçamento em menos de 2 segundos.
* **RNF-02 (Segurança):** O sistema deve garantir o isolamento de dados, impedindo que um Desenvolvedor utilize o orçamento de um departamento ao qual não está vinculado.

### 2.3. Regras de Negócio (RB) - Formato EARS
* **RB-01 (Catálogo Fixo):** **WHILE** o sistema estiver operando, **IT HAS TO** garantir que cada item do catálogo possua um custo mensal predefinido no banco de dados.
* **RB-02 (Trava de Orçamento):** **WHILE** a requisição estiver em validação, **IF** o custo projetado ultrapassar o saldo disponível da equipe, **THEN** o sistema não pode aprovar automaticamente o pedido, bloqueando-o com o status "Revisão Pendente".
* **RB-03 (Aprovação Técnica e Liberação Orçamentária):** A aprovação técnica de uma requisição em revisão não altera o orçamento, não compromete saldo e não autoriza o provisionamento. O pedido aprovado tecnicamente permanece em `AGUARDANDO_AJUSTE_ORCAMENTARIO` até que ocorra um ajuste orçamentário autorizado pelo Arquiteto Cloud e uma revalidação confirme saldo suficiente. Se o saldo continuar insuficiente, o pedido permanece aguardando.
* **RB-04 (Alteração de Teto):** O saldo disponível corresponde ao orçamento mensal menos o valor já comprometido. Alterar o teto preserva os compromissos existentes. IF o novo teto for inferior ao valor já comprometido, THEN o sistema SHALL rejeitar a alteração, manter os valores anteriores e informar que o orçamento não pode ser reduzido abaixo dos compromissos existentes.
* **RB-05 (Proteção do Saldo na Liberação):** O sistema não deve autorizar provisionamento cujo custo estimado exceda o saldo disponível do departamento. Na liberação, a verificação do saldo e o comprometimento do valor devem ocorrer como uma única operação consistente, impedindo saldo negativo e desconto duplicado para a mesma requisição.

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
* **Responsabilidades previstas pela Issue #31:**
  * Alterar o teto mensal preservando o valor já comprometido.
  * Recalcular o saldo disponível conforme RB-04.
  * Rejeitar alterações que deixem o teto abaixo do valor comprometido, mantendo os valores anteriores.
  * Participar da liberação consistente do saldo conforme RB-05.

O valor comprometido representa os custos estimados das requisições já liberadas no período considerado; não representa uma fatura real da AWS. Sua representação técnica será definida na Spec de implementação.

**Entidade: Requisicao**
Representa o pedido submetido pelo Desenvolvedor.
* **Atributos:**
  * id: UUID
  * desenvolvedorId: UUID
  * status: String — estados e transições definidos na seção 3.3.
  * custoTotalProjetado: Double
  * justificativaArquiteto: String
* **Operações:**
  * calcularTotalProjetado(): Double
  * aprovarAutomaticamente(): void
  * enviarParaRevisao(): void
* **Responsabilidades previstas pela Issue #31:**
  * Registrar aprovação técnica ou rejeição conforme RF-03.
  * Manter o estado AGUARDANDO_AJUSTE_ORCAMENTARIO após aprovação técnica.
  * Para pedidos em AGUARDANDO_AJUSTE_ORCAMENTARIO, permitir liberação somente após o ajuste autorizado e a revalidação previstos na RB-03.
  * Preservar a liberação automática de pedidos com saldo suficiente na validação inicial, conforme RB-02 e RB-05.
  * Respeitar as transições da seção 3.3 e impedir comprometimento duplicado do valor, conforme RB-05.

Essas responsabilidades descrevem comportamento esperado. As assinaturas dos novos métodos serão definidas na Spec de implementação. A autorização do usuário e a coordenação da auditoria devem respeitar a separação de responsabilidades da arquitetura.

**Entidade: ItemRequisicao**
Vincula o recurso solicitado à quantidade desejada nesta requisição específica.
* **Atributos:**
  * id: UUID
  * quantidade: Integer
  * custoSubtotal: Double

### 3.3. Estados e Transições da Requisição

Este modelo descreve o comportamento esperado. Sua documentação não comprova que todas as transições estejam implementadas.

| Estado | Significado |
|---|---|
| INICIAL | Requisição criada, ainda não submetida à validação de orçamento. |
| Em Analise | Requisição submetida à validação automática de orçamento. |
| Revisão Pendente | Solicitação encaminhada ao Arquiteto Cloud por insuficiência de saldo. |
| AGUARDANDO_AJUSTE_ORCAMENTARIO | Requisição aprovada tecnicamente, mas ainda sem liberação orçamentária. |
| Provisionamento Liberado | Saldo validado e valor comprometido; provisionamento autorizado. Não significa que o recurso já foi provisionado. |
| Rejeitado | Solicitação rejeitada pelo Arquiteto Cloud, com justificativa. |

#### Transições previstas

| Origem | Ação ou condição | Destino | Efeito financeiro |
|---|---|---|---|
| INICIAL | Desenvolvedor submete a requisição | Em Analise | Nenhum |
| Em Analise | Custo menor ou igual ao saldo disponível | Provisionamento Liberado | Comprometer o valor uma única vez, conforme RB-05 |
| Em Analise | Custo maior que o saldo disponível | Revisão Pendente | Nenhum |
| Revisão Pendente | Arquiteto registra aprovação técnica com justificativa | AGUARDANDO_AJUSTE_ORCAMENTARIO | Nenhum |
| Revisão Pendente | Arquiteto rejeita com justificativa | Rejeitado | Nenhum |
| AGUARDANDO_AJUSTE_ORCAMENTARIO | Após ajuste autorizado, a revalidação confirma saldo suficiente | Provisionamento Liberado | Comprometer o valor uma única vez, conforme RB-05 |
| AGUARDANDO_AJUSTE_ORCAMENTARIO | Após ajuste autorizado, a revalidação encontra saldo insuficiente | AGUARDANDO_AJUSTE_ORCAMENTARIO | Nenhum comprometimento adicional |

O ajuste do teto é uma operação sobre o Departamento, separada da aprovação técnica da requisição. Seu registro segue RF-05, e o recálculo do saldo segue RB-04.

Uma requisição em Revisão Pendente não recebe aprovação técnica automaticamente em razão de um aumento do orçamento.

Os nomes desta seção são a referência documental dos estados. A padronização das representações no código e nos diagramas deverá acompanhar a implementação correspondente.