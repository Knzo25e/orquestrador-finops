# UC-02 — Validação Automática de Orçamento

**Requisito principal:** RF-02.
**Regras relacionadas:** RB-02 e RB-05.
**Requisito não funcional:** RNF-01.
**Ator principal:** Desenvolvedor.
**Objetivo:** Avaliar o custo da requisição frente ao saldo disponível do departamento e definir seu encaminhamento.

## Pré-condições

- A requisição foi criada com os itens selecionados e está em INICIAL.
- A requisição pertence ao departamento do desenvolvedor, conforme RNF-02.
- Os dados necessários para calcular o custo e consultar o saldo estão disponíveis.

## Fluxo principal

1. O Desenvolvedor submete a requisição para análise.
2. O Sistema coloca a requisição em "Em Analise".
3. O Sistema calcula o custoTotalProjetado somando os subtotais dos itens.
4. O Sistema verifica o saldo disponível atual do departamento.
5. Sendo o custo menor ou igual ao saldo, o Sistema compromete o valor da requisição, atualiza o saldo e altera o estado para "Provisionamento Liberado".

A verificação do saldo e a efetivação da liberação devem ocorrer de forma consistente, conforme RB-05. Não pode existir liberação sem o correspondente comprometimento do valor nem comprometimento duplicado para a mesma requisição.

## Fluxos alternativos e exceções

### A1 — Saldo insuficiente

No passo 5, se o custo exceder o saldo disponível:

1. O Sistema altera o estado para "Revisão Pendente".
2. O Sistema mantém o saldo sem desconto referente a essa requisição.
3. A requisição fica disponível na fila de análise do Arquiteto Cloud.
4. A validação automática termina.

### A2 — Repetição de processamento de requisição já liberada

Se uma requisição já estiver em "Provisionamento Liberado" quando houver nova tentativa de processamento:

1. O Sistema preserva o estado da requisição.
2. O Sistema não compromete nem desconta novamente seu valor.
3. A tentativa termina sem nova liberação.

## Continuidade após revisão técnica

Esta seção descreve a ligação com o fluxo manual da Spec 009; ela não integra o caminho de aprovação automática da Spec 003.

- Se o Arquiteto Cloud rejeitar o pedido em revisão, a requisição passa para "Rejeitado", com justificativa e auditoria.
- Se aprovar tecnicamente, a requisição passa para AGUARDANDO_AJUSTE_ORCAMENTARIO, sem desconto de saldo ou autorização de provisionamento.
- O ajuste do orçamento é uma ação separada, executada pelo Arquiteto Cloud e registrada conforme RF-04 e RF-05.
- Após o ajuste autorizado, o sistema revalida o saldo.
- Com saldo suficiente, a requisição pode ser liberada respeitando RB-05.
- Com saldo insuficiente, permanece em AGUARDANDO_AJUSTE_ORCAMENTARIO.
- Aumentar o orçamento não aprova tecnicamente uma requisição que ainda esteja em "Revisão Pendente".

## Regras e critérios verificáveis

- **RB-02:** Pedidos acima do saldo não recebem aprovação automática.
- **RB-05:** A liberação preserva saldo não negativo e impede comprometimento duplicado, inclusive em concorrência.
- **RNF-01:** A validação automática deve ser processada em menos de 2 segundos.
- **IF** o custo exceder o saldo disponível, **THEN** o sistema **SHALL** encaminhar a requisição para "Revisão Pendente", sem descontar seu valor.
- **WHEN** houver nova tentativa de processar uma requisição já liberada, o sistema **SHALL** preservar o comprometimento existente sem realizar novo desconto.

## Pós-condições

- Com saldo suficiente: requisição em "Provisionamento Liberado" e valor comprometido uma única vez.
- Com saldo insuficiente: requisição em "Revisão Pendente", sem comprometimento referente ao pedido.
- Em repetição de processamento após liberação: estado e comprometimento existentes preservados.

"Provisionamento Liberado" representa autorização; não comprova que um recurso tenha sido criado na AWS.

## Rastreabilidade e situação

- Fluxo automático: Spec 003.
- Ajuste de teto: Spec 008.
- Revisão técnica e revalidação após ajuste: Spec 009.
- Estados: seção 3.3 de [spec.md](../spec.md).
- Decisões financeiras: Issue #31 e seção 4 de [arquitetura.md](../arquitetura.md).
- Este documento descreve comportamento esperado. A implementação e sua verificação completa permanecem pendentes.