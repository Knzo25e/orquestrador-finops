# Especificação do Sistema (Spec)

## 1. Personas e Contexto de Uso

**Carlos, o Desenvolvedor Ágil (Solicitante)**
* **Contexto:** Trabalha em uma squad e precisa provisionar bancos de dados e máquinas para testar aplicações.
* **Dores:** Sofre com tickets de TI lentos e burocracia que travam suas entregas.
* **Comportamento no Sistema:** Acessa o catálogo e monta uma requisição. Espera aprovação automática quando o pedido atende às condições de saldo e reserva orçamentária. Quando essas condições não são atendidas, acompanha o encaminhamento para revisão.

**Ana, a Arquiteta Cloud (Aprovadora/FinOps)**
* **Contexto:** Controla a governança e o orçamento de infraestrutura da empresa inteira.
* **Dores:** Lida com o desperdício gerado por máquinas superdimensionadas e orçamentos departamentais estourados.
* **Comportamento no Sistema:** Não solicita infraestrutura. Analisa pedidos em revisão, aprova tecnicamente ou rejeita solicitações e administra o orçamento dos departamentos. No MVP, acumula as responsabilidades técnica e orçamentária, executando aprovação técnica, autorização para utilização da reserva e ajuste de orçamento como ações separadas, com justificativa e auditoria. Nenhuma autorização permite provisionamento sem saldo suficiente.

## 2. Requisitos Funcionais, Não Funcionais e Regras (EARS)

### 2.1. Requisitos Funcionais (RF)
* **RF-01:** O sistema deve permitir que o perfil Desenvolvedor acesse um catálogo de infraestrutura e adicione itens a uma requisição.
* **RF-02 (Validação Orçamentária):** WHEN uma requisição for submetida à validação automática, o sistema SHALL avaliar seu custo frente ao saldo disponível e à reserva calculada sobre o orçamento mensal do departamento vinculado, decidindo o roteamento conforme RB-02 e identificando o motivo de eventual revisão.
* **RF-03 (Revisão Técnica):** WHEN o Arquiteto Cloud analisar uma requisição em "Revisão Pendente", o sistema SHALL permitir registrar sua aprovação técnica ou rejeição com justificativa. A aprovação técnica não compromete saldo nem autoriza provisionamento; o pedido segue para o tratamento financeiro previsto na RB-03.
* **RF-04 (Gestão Orçamentária):** WHEN o Arquiteto Cloud solicitar alteração do teto mensal, o sistema SHALL preservar os compromissos existentes e recalcular o saldo conforme RB-04. WHEN autorizar a utilização da reserva para uma requisição aprovada tecnicamente, o sistema SHALL registrar essa autorização como ação separada da aprovação técnica e revalidar as condições financeiras antes da liberação. A autorização de uso da reserva não exige aumento do teto quando houver saldo suficiente e não permite saldo negativo.
* **RF-05 (Auditoria Imutável):** WHEN uma intervenção manual for efetivada, o sistema SHALL registrar histórico imutável contendo ação, responsável, data, justificativa e departamento ou requisição afetada. Para ajustes orçamentários, o registro SHALL incluir teto anterior, novo teto e valor do ajuste. Para autorização de utilização da reserva, o registro SHALL identificar a requisição autorizada e os valores de custo, saldo, teto e percentual de reserva considerados na autorização.

### 2.2. Requisitos Não Funcionais (RNF)
* **RNF-01 (Desempenho):** O sistema deve processar a validação automática de orçamento em menos de 2 segundos.
* **RNF-02 (Segurança):** O sistema deve garantir o isolamento de dados, impedindo que um Desenvolvedor utilize o orçamento de um departamento ao qual não está vinculado.

### 2.3. Regras de Negócio (RB) - Formato EARS
* **RB-01 (Catálogo Fixo):** **WHILE** o sistema estiver operando, **IT HAS TO** garantir que cada item do catálogo possua um custo mensal predefinido no banco de dados.
* **RB-02 (Saldo e Reserva na Aprovação Automática):** A reserva orçamentária corresponde inicialmente a 10% do orçamento mensal do departamento. WHEN uma requisição for validada automaticamente, o sistema SHALL liberá-la somente se o custo couber no saldo disponível e o saldo após o pedido ficar estritamente acima da reserva. IF o custo exceder o saldo, THEN o sistema SHALL encaminhar o pedido para "Revisão Pendente" por insuficiência de saldo, sem desconto. IF o custo couber no saldo, mas o saldo após o pedido for menor ou igual à reserva, THEN o sistema SHALL encaminhá-lo para "Revisão Pendente" por reserva, também sem desconto.
* **RB-03 (Aprovação Técnica e Autorização Financeira):** A aprovação técnica não altera o orçamento, não compromete saldo e não autoriza provisionamento. Para um pedido aprovado tecnicamente cuja liberação atinja ou consuma a reserva vigente, o sistema SHALL exigir autorização financeira explícita para utilização da reserva. Quando faltar saldo, SHALL exigir ajuste orçamentário autorizado antes de eventual liberação. Se, após o ajuste, a liberação ainda atingir ou consumir a reserva, a autorização de uso da reserva também será necessária. Toda liberação manual SHALL revalidar o saldo e as condições financeiras vigentes e respeitar a RB-05. No MVP, o Arquiteto Cloud executa as ações técnicas e financeiras separadamente, com auditoria.
* **RB-04 (Alteração de Teto):** O saldo disponível corresponde ao orçamento mensal menos o valor já comprometido. Alterar o teto preserva os compromissos existentes. IF o novo teto for inferior ao valor já comprometido, THEN o sistema SHALL rejeitar a alteração, manter os valores anteriores e informar que o orçamento não pode ser reduzido abaixo dos compromissos existentes.
* **RB-05 (Proteção do Saldo na Liberação):** O sistema não deve autorizar provisionamento cujo custo estimado exceda o saldo disponível do departamento. Na liberação, a verificação do saldo e o comprometimento do valor devem ocorrer como uma única operação consistente, impedindo saldo negativo e desconto duplicado para a mesma requisição.

### Decisão sobre Reserva — Issue #35

A reserva inicial de 10% do orçamento mensal foi aprovada pela equipe, conforme confirmação do responsável pelo projeto em 09/10/2026.

A reserva é uma referência para encaminhamento à revisão, não uma proibição absoluta de utilização do saldo. Seu cálculo utiliza o teto mensal, não o saldo restante.

As permissões, os limites, a abrangência e a auditoria da configuração do percentual ainda serão detalhados em OPEN-003-04 da SPEC-003. A política de precisão e arredondamento permanece em OPEN-003-01.

A regra está documentada; sua implementação e seus testes permanecem pendentes.

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
* **Responsabilidades previstas pelas Issues #31 e #35:**
  * Registrar aprovação técnica ou rejeição conforme RF-03.
  * Distinguir encaminhamento para revisão por insuficiência de saldo e por atingir ou consumir a reserva.
  * Preservar a aprovação automática somente quando o custo couber no saldo e o saldo após o pedido ficar estritamente acima da reserva, conforme RB-02.
  * Após aprovação técnica, aguardar o atendimento das condições financeiras previstas na RB-03, sem comprometer saldo antecipadamente.
  * Exigir ajuste orçamentário autorizado quando faltar saldo.
  * Exigir autorização financeira explícita quando a liberação atingir ou consumir a reserva vigente.
  * Revalidar as condições financeiras antes da liberação, pois o saldo pode mudar durante a espera.
  * Impedir comprometimento duplicado e preservar a consistência da liberação conforme RB-05.

Essas responsabilidades descrevem comportamento esperado. As assinaturas dos novos métodos serão definidas na Spec de implementação. A autorização do usuário e a coordenação da auditoria devem respeitar a separação de responsabilidades da arquitetura.

**Entidade: ItemRequisicao**
Vincula o recurso solicitado à quantidade desejada nesta requisição específica.
* **Atributos:**
  * id: UUID
  * quantidade: Integer
  * custoSubtotal: Double

### 3.3. Estados e Transições da Requisição

Este modelo descreve o comportamento esperado, atualizado pela decisão sobre reserva da Issue #35. Não comprova implementação. A representação dos estados e transições será revisada no PR correspondente.

| Estado | Significado |
|---|---|
| INICIAL | Requisição criada, ainda não submetida à validação automática. |
| Em Analise | Requisição em validação automática de saldo e reserva. |
| Revisão Pendente | Requisição encaminhada para análise técnica por insuficiência de saldo ou por atingir ou consumir a reserva. |
| AGUARDANDO_AJUSTE_ORCAMENTARIO | Requisição aprovada tecnicamente, cuja liberação depende de ajuste autorizado para obter saldo suficiente. |
| AGUARDANDO_AUTORIZACAO_FINANCEIRA | Requisição aprovada tecnicamente, com saldo suficiente na última avaliação, mas cuja liberação depende de autorização explícita para utilizar a reserva. |
| Provisionamento Liberado | Condições financeiras atendidas e valor comprometido uma única vez. Representa autorização, não provisionamento efetivo na AWS. |
| Rejeitado | Requisição rejeitada na revisão técnica pelo Arquiteto Cloud, com justificativa. |
O motivo da revisão deve distinguir insuficiência de saldo de utilização da reserva. O estado Revisão Pendente, isoladamente, não identifica essa diferença.

#### Transições automáticas

| Origem | Ação ou condição | Destino | Efeito financeiro |
|---|---|---|---|
| INICIAL | Desenvolvedor submete a requisição | Em Analise | Nenhum |
| Em Analise | Custo cabe no saldo e saldo após o pedido fica acima da reserva | Provisionamento Liberado | Comprometer o valor uma única vez, conforme RB-05 |
| Em Analise | Custo excede o saldo disponível | Revisão Pendente | Nenhum; motivo: insuficiência de saldo |
| Em Analise | Custo cabe no saldo, mas saldo após o pedido é menor ou igual à reserva | Revisão Pendente | Nenhum; motivo: reserva |
| Provisionamento Liberado | Nova tentativa de processamento | Provisionamento Liberado | Nenhum desconto adicional |

#### Revisão técnica e encaminhamento financeiro

| Origem | Ação ou condição | Destino | Efeito financeiro |
|---|---|---|---|
| Revisão Pendente | Arquiteto rejeita tecnicamente com justificativa | Rejeitado | Nenhum |
| Revisão Pendente | Arquiteto aprova tecnicamente e a avaliação atual encontra saldo insuficiente | AGUARDANDO_AJUSTE_ORCAMENTARIO | Nenhum |
| Revisão Pendente | Arquiteto aprova tecnicamente, há saldo suficiente e a liberação atinge ou consome a reserva | AGUARDANDO_AUTORIZACAO_FINANCEIRA | Nenhum |

A aprovação técnica nunca compromete saldo nem libera provisionamento por si só.

Se, no momento da aprovação técnica, uma mudança orçamentária já tiver tornado possível a liberação sem utilização da reserva, o encaminhamento deverá ser detalhado na Spec 009. Esse caso não autoriza liberação como efeito implícito da aprovação técnica.

#### Revalidação financeira após aprovação técnica

| Origem | Ação ou condição | Destino | Efeito financeiro |
|---|---|---|---|
| AGUARDANDO_AJUSTE_ORCAMENTARIO | Após ajuste autorizado, saldo continua insuficiente | AGUARDANDO_AJUSTE_ORCAMENTARIO | Nenhum comprometimento do pedido |
| AGUARDANDO_AJUSTE_ORCAMENTARIO | Após ajuste autorizado, há saldo suficiente, mas a liberação utiliza a reserva e falta autorização para isso | AGUARDANDO_AUTORIZACAO_FINANCEIRA | Nenhum comprometimento do pedido |
| AGUARDANDO_AJUSTE_ORCAMENTARIO | Após ajuste autorizado, saldo é suficiente e a reserva é preservada ou seu uso está explicitamente autorizado | Provisionamento Liberado | Comprometer o valor uma única vez, conforme RB-05 |
| AGUARDANDO_AUTORIZACAO_FINANCEIRA | Revalidação encontra saldo insuficiente | AGUARDANDO_AJUSTE_ORCAMENTARIO | Nenhum |
| AGUARDANDO_AUTORIZACAO_FINANCEIRA | Saldo é suficiente, mas a liberação ainda utiliza a reserva sem autorização | AGUARDANDO_AUTORIZACAO_FINANCEIRA | Nenhum |
| AGUARDANDO_AUTORIZACAO_FINANCEIRA | Revalidação confirma saldo suficiente e autorização explícita para utilizar a reserva | Provisionamento Liberado | Comprometer o valor uma única vez, conforme RB-05 |

O ajuste do teto altera o Departamento e segue RB-04 e RF-05. A liberação da requisição é uma operação distinta, mesmo quando executada na continuidade do ajuste.

Toda liberação deve considerar saldo, teto e reserva vigentes, de forma consistente com o comprometimento e a mudança de estado.

Aumentar o orçamento não aprova tecnicamente uma requisição em Revisão Pendente. Autorizar uso da reserva não permite saldo negativo.

#### Detalhamento pendente do fluxo manual

A Spec 009 deverá definir:

- O encaminhamento quando a necessidade de utilizar a reserva desaparecer durante a espera.
- O efeito de alterações do custo ou das condições financeiras sobre uma autorização já registrada.
- O tratamento de uma autorização financeira negada.

Essas situações ainda não constituem transições aprovadas neste documento. Devem ser acompanhadas em OPEN-003-05 da SPEC-003.

Os nomes desta seção são referências documentais. A padronização no código e nos diagramas deverá acompanhar as alterações correspondentes.