# UC-02 — Validação Automática de Orçamento

**Requisito principal:** RF-02.
**Regras relacionadas:** RB-02 e RB-05.
**Requisito não funcional:** RNF-01.
**Ator principal:** Desenvolvedor.
**Objetivo:** Avaliar o custo da requisição frente ao saldo disponível e à reserva do departamento, definindo sua liberação automática ou encaminhamento para revisão.

## Pré-condições

- A requisição foi criada com pelo menos um item válido e está em INICIAL.
- A requisição pertence ao departamento do desenvolvedor, conforme RNF-02.
- Os dados necessários para calcular o custo, o saldo e a reserva estão disponíveis e são válidos.
- O saldo disponível é não negativo.

A repetição de processamento de uma requisição já liberada é tratada em A2.

A verificação do vínculo do usuário depende da autorização definida na Spec 006. Este caso de uso não comprova sua implementação.

## Regra de reserva

A reserva inicial corresponde a 10% do orçamento mensal do departamento.

- Reserva = orçamento mensal × percentual de reserva.
- Saldo projetado após o pedido = saldo disponível − custo da requisição.

A liberação automática exige custo dentro do saldo e saldo projetado estritamente acima da reserva.

A reserva é calculada sobre o teto mensal, não sobre o saldo restante. Configuração do percentual e precisão monetária seguem as pendências da SPEC-003.

## Fluxo principal

1. O Desenvolvedor submete a requisição para análise.
2. O Sistema verifica o estado da requisição.
3. Estando em INICIAL, o Sistema coloca a requisição em "Em Analise".
4. O Sistema calcula o custoTotalProjetado somando os subtotais dos itens.
5. O Sistema consulta o saldo disponível, o teto mensal e o percentual de reserva do departamento vinculado à requisição.
6. O Sistema calcula a reserva e o saldo projetado após o pedido.
7. Se o custo couber no saldo e o saldo projetado ficar acima da reserva, o Sistema compromete o valor uma única vez, atualiza o saldo e altera o estado para "Provisionamento Liberado".
8. O Sistema retorna o resultado.

A verificação do estado, a leitura dos valores financeiros e a efetivação da liberação devem ocorrer de forma consistente, inclusive em concorrência, conforme RB-05.

Não pode existir liberação sem o correspondente comprometimento nem comprometimento duplicado para a mesma requisição.

## Fluxos alternativos e exceções

### A1 — Encaminhamento para revisão

No passo 7, o Sistema verifica primeiro se existe saldo suficiente.

#### A1.1 — Saldo insuficiente

Se o custo exceder o saldo disponível:

1. O Sistema altera o estado para "Revisão Pendente".
2. O resultado identifica o motivo: insuficiência de saldo.
3. O saldo permanece sem desconto referente à requisição.
4. O pedido fica elegível para consulta na fila do Arquiteto Cloud.
5. A validação automática termina.

#### A1.2 — Reserva atingida ou consumida

Se o custo couber no saldo, mas o saldo projetado após o pedido for menor ou igual à reserva:

1. O Sistema altera o estado para "Revisão Pendente".
2. O resultado identifica o motivo: utilização da reserva.
3. O saldo permanece sem desconto referente à requisição.
4. O pedido fica elegível para consulta na fila do Arquiteto Cloud.
5. A validação automática termina.

Um pedido cujo custo seja exatamente igual ao saldo também segue para revisão, sem zerar o saldo automaticamente.

### A2 — Repetição de processamento de requisição já liberada

Se, no passo 2, a requisição já estiver em "Provisionamento Liberado":

1. O Sistema preserva o estado da requisição.
2. O Sistema não compromete nem desconta novamente seu valor.
3. A tentativa termina informando que a requisição já estava liberada.

### A3 — Demais estados

O tratamento dos estados que não admitem validação automática deve seguir a alternativa A3 da SPEC-003 e a seção 3.3 de spec.md.

A definição do resultado para processamento já em andamento permanece em OPEN-003-02. Não se presume autorização para executar novamente o fluxo ou duplicar descontos.

## Continuidade após revisão técnica

Esta seção descreve a ligação com o fluxo manual da Spec 009. Não integra a aprovação automática da Spec 003.

- A rejeição técnica leva ao estado "Rejeitado", com justificativa e auditoria.
- A aprovação técnica não compromete saldo nem autoriza provisionamento.
- Se faltar saldo após aprovação técnica, o pedido segue para AGUARDANDO_AJUSTE_ORCAMENTARIO.
- Se houver saldo suficiente, mas a liberação utilizar a reserva, o pedido segue para AGUARDANDO_AUTORIZACAO_FINANCEIRA.
- O Arquiteto Cloud realiza aprovação técnica, ajuste do teto e autorização de uso da reserva como ações separadas e auditadas.
- O ajuste do teto não é obrigatório quando já existe saldo suficiente e o impedimento é apenas a utilização da reserva.
- Após um ajuste, ainda será necessária autorização de uso da reserva se a liberação atingir ou consumir a reserva vigente.
- Toda liberação manual revalida as condições financeiras e respeita RB-05.
- A autorização para utilizar a reserva nunca permite saldo negativo.
- Aumentar o orçamento não aprova tecnicamente um pedido em "Revisão Pendente".

Os casos em que a necessidade de utilizar a reserva desaparece durante a espera, os efeitos de mudanças sobre autorizações anteriores e a negativa de autorização financeira permanecem para detalhamento na Spec 009, conforme OPEN-003-05.

## Regras e critérios verificáveis

- **RB-02:** A aprovação automática exige saldo suficiente e saldo projetado estritamente acima da reserva.
- **RB-05:** A liberação preserva saldo não negativo e impede comprometimento duplicado, inclusive em concorrência.
- **RNF-01:** A validação automática deve ser processada em menos de dois segundos, segundo protocolo a definir em OPEN-003-03.
- **IF** o custo exceder o saldo, **THEN** o sistema **SHALL** encaminhar o pedido para revisão por insuficiência de saldo, sem desconto.
- **IF** o custo couber no saldo e o saldo projetado for menor ou igual à reserva, **THEN** o sistema **SHALL** encaminhar o pedido para revisão por reserva, sem desconto.
- **WHEN** houver nova tentativa de processar uma requisição já liberada, o sistema **SHALL** preservar o comprometimento existente sem realizar novo desconto.

Os cenários numéricos, de fronteira e de concorrência estão definidos na SPEC-003.

## Pós-condições

- Aprovação automática: requisição em "Provisionamento Liberado", valor comprometido uma única vez e saldo resultante acima da reserva considerada na operação.
- Revisão por insuficiência: requisição em "Revisão Pendente", motivo identificável e nenhum comprometimento referente ao pedido.
- Revisão por reserva: requisição em "Revisão Pendente", motivo identificável e nenhum comprometimento referente ao pedido.
- Repetição após liberação: estado e comprometimento existentes preservados.

"Provisionamento Liberado" representa autorização; não comprova criação de recurso na AWS.

## Rastreabilidade e situação

- Fluxo automático e critérios de aceitação: [SPEC-003](SPEC-003-validar-orcamento.md).
- Ajuste de teto: Spec 008.
- Revisão técnica, autorização financeira e revalidação manual: Spec 009.
- Autorização por perfil: Spec 006.
- Auditoria: Spec 007.
- Estados e regras: seção 3.3 de [spec.md](../spec.md).
- Decisões financeiras: Issues #31 e #35 e seção 4 de [arquitetura.md](../arquitetura.md).

Este documento descreve comportamento esperado. A implementação e sua verificação completa permanecem pendentes.