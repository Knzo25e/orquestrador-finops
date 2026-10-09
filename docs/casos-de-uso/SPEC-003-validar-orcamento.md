# SPEC-003 — Validar Orçamento da Requisição

## Identificação

**Objetivo:** Avaliar o custo de uma requisição frente ao saldo disponível e à reserva orçamentária do departamento, decidindo sua liberação automática ou encaminhamento para revisão.

**Issue:** #35.
**Status:** Em revisão; decisão sobre reserva aprovada pela equipe conforme confirmação do responsável pelo projeto em 09/10/2026. Implementação completa pendente.

## Rastreabilidade

- RF-02: avaliação e roteamento automático.
- RB-02: condições de aprovação automática e encaminhamento para revisão.
- RB-05: liberação consistente, sem saldo negativo ou comprometimento duplicado.
- RNF-01: processamento da validação automática em menos de dois segundos.
- RNF-02: isolamento entre departamentos.
- UC-02: [Validação Automática de Orçamento](UC-02-validacao-orcamento.md).
- Estados e regras: [spec.md](../spec.md), seção 3.3.
- Arquitetura: [arquitetura.md](../arquitetura.md), ADR-001 e DA-01.
- Dependências: Specs 001 e 002.

A RB-02, o UC-02 e os demais documentos relacionados devem ser alinhados à decisão sobre reserva durante esta revisão. A alteração desta Spec, isoladamente, não conclui esse alinhamento.

## Decisão sobre Reserva Orçamentária

A equipe aprovou uma reserva inicial de 10% do orçamento mensal do departamento como referência para encaminhamento de pedidos à revisão.

A reserva é calculada sobre o teto mensal, não sobre o saldo disponível.

Definições:

- T: orçamento mensal do departamento.
- S: saldo disponível antes do pedido.
- C: custo total projetado da requisição.
- p: percentual de reserva; referência inicial de 10%, equivalente a 0,10.
- R: reserva orçamentária, calculada por T × p.
- S_apos: saldo projetado após o pedido, calculado por S − C.

### Condições de roteamento

| Condição | Resultado | Efeito financeiro |
|---|---|---|
| C <= S e S_apos > R | Provisionamento Liberado | Comprometer C uma única vez |
| C <= S e S_apos <= R | Revisão Pendente por atingir ou consumir a reserva | Nenhum desconto |
| C > S | Revisão Pendente por insuficiência de saldo | Nenhum desconto |

A insuficiência de saldo é verificada antes da condição de reserva, para distinguir o motivo do encaminhamento.

A reserva é um limite para aprovação automática. Seu uso pode ser autorizado no fluxo manual, sem permitir saldo negativo.

O percentual inicial foi aprovado. As permissões, os limites e o mecanismo para configurá-lo permanecem pendentes em OPEN-003-04.

## Escopo

### Incluído

- Validar uma requisição criada com seus itens e departamento.
- Calcular o total a partir dos subtotais dos itens.
- Utilizar exclusivamente o departamento vinculado à requisição.
- Avaliar saldo, teto e percentual de reserva.
- Liberar automaticamente somente quando o saldo após o pedido ficar acima da reserva.
- Encaminhar pedidos à revisão por reserva ou por insuficiência de saldo.
- Tornar o motivo do encaminhamento identificável no resultado do fluxo.
- Evitar comprometimento duplicado em tentativas repetidas.
- Preservar a consistência do saldo e do estado em concorrência.

### Fora do escopo

- Aprovação técnica e rejeição pelo Arquiteto Cloud.
- Autorização financeira para utilização da reserva.
- Alteração do teto e liberação manual posterior.
- Interface de configuração do percentual de reserva.
- Autenticação e implementação completa da autorização por perfil.
- Interface gráfica e contrato REST.
- Persistência entre sessões.
- Provisionamento ou integração real com AWS.

Os fluxos manuais dependem das Specs 008 e 009, com autorização por perfil na Spec 006 e auditoria na Spec 007.

## Pré-condições e Dependências

- A requisição está vinculada a um departamento.
- Para uma nova validação automática, seu estado é INICIAL.
- A requisição contém pelo menos um item.
- Os dados necessários ao cálculo são válidos.
- O saldo disponível é não negativo.
- O teto e o percentual de reserva utilizados na decisão são válidos.
- O vínculo entre solicitante e departamento deve ser garantido antes da entrada no fluxo.

Uma nova tentativa sobre uma requisição já liberada segue o tratamento de repetição, sem executar novamente a liberação.

A ausência de autenticação nesta etapa não comprova atendimento integral ao RNF-02. O fluxo deve utilizar o departamento da requisição e não aceitar outro departamento para substituí-lo.

As validações das entradas financeiras e a política de precisão estão pendentes em OPEN-003-01.

## Fluxo Principal

1. O Desenvolvedor submete a requisição.
2. A aplicação solicita ao domínio a validação orçamentária.
3. O domínio verifica o estado atual da requisição.
4. Estando em INICIAL, a requisição passa para Em Analise.
5. O domínio calcula o total dos itens.
6. O domínio obtém o saldo disponível, o teto mensal e o percentual de reserva do departamento vinculado.
7. O domínio calcula a reserva e o saldo projetado após o pedido.
8. Se o custo couber no saldo e o saldo projetado ficar estritamente acima da reserva, o valor é comprometido uma única vez.
9. A requisição passa para Provisionamento Liberado.
10. A aplicação retorna o resultado.

A verificação do estado, a leitura dos valores usados na decisão, o comprometimento e a liberação devem ser coordenados de forma consistente, inclusive em concorrência.

Provisionamento Liberado representa autorização, não criação de um recurso na AWS.

## Alternativas e Exceções

### A1 — Encaminhamento para revisão

#### A1.1 — Insuficiência de saldo

Se o custo exceder o saldo disponível:

- A requisição passa para Revisão Pendente.
- O resultado identifica insuficiência de saldo.
- Não ocorre desconto referente a esse pedido.
- O fluxo automático termina.

#### A1.2 — Reserva atingida ou consumida

Se o custo couber no saldo, mas o saldo projetado após o pedido for menor ou igual à reserva:

- A requisição passa para Revisão Pendente.
- O resultado identifica que o pedido atinge ou consome a reserva.
- Não ocorre desconto referente a esse pedido.
- O fluxo automático termina.

Nos dois casos, o pedido fica elegível para consulta na fila do Arquiteto Cloud. A representação técnica do motivo será definida na implementação.

### A2 — Requisição já liberada

Se uma nova tentativa encontrar a requisição em Provisionamento Liberado:

- O estado permanece inalterado.
- O comprometimento existente é preservado.
- Não ocorre novo desconto.
- O resultado informa que a requisição já estava liberada.

### A3 — Estado fora do fluxo automático

Proposta de comportamento para revisão:

Se a requisição estiver em Revisão Pendente, AGUARDANDO_AJUSTE_ORCAMENTARIO, AGUARDANDO_AUTORIZACAO_FINANCEIRA ou Rejeitado:

- A operação informa que o estado não admite validação automática.
- O estado e o saldo permanecem inalterados.
- Não ocorre aprovação técnica nem liberação manual.

Outros estados que venham a ser definidos para espera por autorização financeira deverão ser contemplados nesse tratamento.

O tratamento de uma tentativa que encontre Em Analise permanece pendente em OPEN-003-02.

## Continuidade Manual e Responsabilidades

O MVP mantém dois perfis: Desenvolvedor e Arquiteto Cloud.

O Arquiteto Cloud acumula as responsabilidades técnica e orçamentária, executadas como ações separadas e auditadas.

A aprovação técnica, isoladamente, não compromete saldo nem autoriza provisionamento.

### Pedido com saldo suficiente, retido por reserva

A liberação depende de aprovação técnica, autorização financeira explícita para utilização da reserva e revalidação do saldo.

Não é obrigatório aumentar o teto quando já existe saldo suficiente.

### Pedido com saldo insuficiente

A liberação depende de aprovação técnica, ajuste orçamentário autorizado e revalidação que confirme saldo suficiente.

Se a liberação também atingir ou consumir a reserva vigente, deverá haver autorização explícita para utilizá-la.

### Mudança de saldo durante a espera

O motivo original da revisão não substitui a revalidação.

Se o saldo se tornar insuficiente, uma autorização de uso da reserva não permite liberar o pedido: será necessário o tratamento de ajuste orçamentário.

Os estados, as transições e os registros dessas ações deverão ser alinhados na RB-03, na arquitetura e na Spec 009, conforme OPEN-003-05.

## Invariantes

- Toda validação usa o departamento vinculado à requisição.
- A reserva é calculada sobre o orçamento mensal, não sobre o saldo restante.
- A aprovação automática exige saldo projetado estritamente superior à reserva.
- O saldo não pode ficar negativo em razão da liberação.
- Uma mesma requisição não pode comprometer seu valor mais de uma vez.
- Pedidos encaminhados para revisão não consomem saldo.
- Saldo, teto, percentual, comprometimento e estado devem participar de uma decisão consistente em concorrência.
- Somente o domínio altera o estado da requisição, conforme ADR-001.

## Critérios de Aceitação

Todos os cenários abaixo utilizam percentual de reserva de 10%.

### C1 — Aprovação automática acima da reserva

**Dado** teto mensal de R$ 10.000,00, saldo de R$ 5.000,00 e requisição INICIAL de R$ 3.000,00.

**Quando** a validação automática for executada.

**Então** a requisição fica em Provisionamento Liberado e o saldo passa a R$ 2.000,00, acima da reserva de R$ 1.000,00.

### C2 — Custo igual ao saldo

**Dado** teto mensal de R$ 10.000,00, saldo de R$ 3.000,00 e requisição INICIAL de R$ 3.000,00.

**Quando** a validação for executada.

**Então** a requisição fica em Revisão Pendente por reserva e o saldo permanece R$ 3.000,00.

### C3 — Saldo insuficiente

**Dado** teto mensal de R$ 10.000,00, saldo de R$ 2.000,00 e requisição INICIAL de R$ 3.000,00.

**Quando** a validação for executada.

**Então** a requisição fica em Revisão Pendente por insuficiência de saldo e o saldo permanece R$ 2.000,00.

### C4 — Repetição após liberação

**Dado** que uma requisição já foi liberada e seu valor comprometido.

**Quando** houver nova tentativa de validação automática.

**Então** o estado e o saldo permanecem iguais aos valores anteriores à nova tentativa.

### C5 — Departamento vinculado

**Dado** uma requisição vinculada ao departamento A e outro departamento B.

**Quando** a requisição for validada.

**Então** somente os valores de A participam da decisão e de eventual comprometimento; o saldo de B permanece inalterado.

### C6 — Concorrência entre requisições com insuficiência posterior

**Dado** teto mensal de R$ 10.000,00, saldo de R$ 5.000,00 e duas requisições INICIAL de R$ 3.000,00 para o mesmo departamento.

**Quando** ambas forem processadas concorrentemente.

**Então** somente uma é liberada, a outra fica em Revisão Pendente por insuficiência de saldo e o saldo final é R$ 2.000,00.

Não se exige qual das duas requisições será liberada primeiro.

### C7 — Concorrência sobre a mesma requisição

**Dado** teto mensal de R$ 10.000,00, saldo de R$ 5.000,00 e uma requisição INICIAL de R$ 3.000,00.

**Quando** duas tentativas concorrentes processarem essa mesma requisição.

**Então** ela é liberada uma única vez e o saldo final é R$ 2.000,00.

O resultado apresentado pela tentativa adicional depende da resolução de OPEN-003-02.

### C8 — Estados fora do fluxo

Proposta vinculada à alternativa A3:

**Dado** uma requisição em Revisão Pendente, AGUARDANDO_AJUSTE_ORCAMENTARIO, AGUARDANDO_AUTORIZACAO_FINANCEIRA ou Rejeitado.

**Quando** houver tentativa de validação automática.

**Então** a operação informa que o estado não admite esse fluxo, sem alterar estado ou saldo.

### C9 — Tempo de processamento

A validação automática deve terminar em menos de dois segundos, conforme RNF-01.

O protocolo de medição será definido em OPEN-003-03.

### C10 — Pedido imediatamente abaixo do limite de revisão

**Dado** teto e saldo de R$ 10.000,00, com reserva de R$ 1.000,00.

**Quando** uma requisição INICIAL de R$ 8.999,99 for validada.

**Então** ela é liberada automaticamente e o saldo passa a R$ 1.000,01.

### C11 — Pedido exatamente no limite de revisão

**Dado** teto e saldo de R$ 10.000,00, com reserva de R$ 1.000,00.

**Quando** uma requisição INICIAL de R$ 9.000,00 for validada.

**Então** ela fica em Revisão Pendente por reserva e o saldo permanece R$ 10.000,00.

### C12 — Pedido acima do limite, mas dentro do saldo

**Dado** teto e saldo de R$ 10.000,00, com reserva de R$ 1.000,00.

**Quando** uma requisição INICIAL de R$ 9.000,01 for validada.

**Então** ela fica em Revisão Pendente por reserva e o saldo permanece R$ 10.000,00.

### C13 — Reserva calculada sobre o teto

**Dado** teto de R$ 10.000,00 e saldo de R$ 2.000,00.

**Quando** uma requisição INICIAL de R$ 1.500,00 for validada.

**Então** ela fica em Revisão Pendente por reserva, pois deixaria R$ 500,00, abaixo da reserva de R$ 1.000,00. O saldo permanece R$ 2.000,00.

### C14 — Concorrência com reserva atingida pelo segundo pedido

**Dado** teto de R$ 10.000,00, saldo de R$ 5.000,00 e duas requisições INICIAL de R$ 2.000,00 para o mesmo departamento.

**Quando** ambas forem processadas concorrentemente.

**Então** somente uma é liberada, a outra fica em Revisão Pendente por reserva e o saldo final é R$ 3.000,00.

Não se exige qual das duas requisições será liberada primeiro.

## Estratégia de Verificação

- Verificações determinísticas para cálculo, roteamento, saldo, reserva e repetição.
- Verificações dos limites imediatamente abaixo, exatamente no limite e acima dele.
- Verificações concorrentes com coordenação explícita das tentativas.
- Conferência conjunta do estado final, motivo do encaminhamento e saldo.
- Medição de desempenho separada das verificações funcionais.
- Registro do ambiente, comando utilizado, resultados e limitações.
- Preservação das dez verificações existentes da SPEC-002.

A ferramenta e a organização dos testes serão definidas na preparação da implementação. Este documento não exige um framework específico.

## Questões em Aberto

### OPEN-003-01 — Entradas e precisão financeira

Definir limites aceitos para quantidades, custos, subtotais e teto, incluindo zero, valores negativos, nulos e não finitos.

Definir a representação monetária e a regra de arredondamento da reserva quando o percentual produzir frações de centavo.

O código atual utiliza Double e Departamento não rejeita desconto negativo. A implementação deverá tratar essas limitações antes de considerar o fluxo seguro para entradas externas.

### OPEN-003-02 — Concorrência e estado Em Analise

Definir o mecanismo de coordenação e o resultado de uma tentativa que encontre processamento em andamento.

A solução deve impedir saldo negativo, aprovação automática indevida por reserva e desconto duplicado.

A garantia da primeira implementação deve declarar seu alcance, especialmente quanto a uma ou várias instâncias da aplicação.

### OPEN-003-03 — Protocolo de desempenho

Definir ambiente, quantidade de itens, carga concorrente, número de execuções e pontos de início e fim da medição.

Uma execução isolada abaixo de dois segundos não comprova desempenho para qualquer carga.

### OPEN-003-04 — Configuração do percentual

O percentual inicial de 10% foi aprovado.

Ainda é necessário definir se a configuração será global ou por departamento, os limites permitidos, quem pode alterá-la, sua auditoria e seu efeito sobre pedidos pendentes.

A implementação da configuração não deve ser presumida como concluída nem incluída silenciosamente nesta Spec.

### OPEN-003-05 — Detalhamento e revisão do fluxo manual

As regras, a arquitetura, o UC-02, o mapa e o modelo de domínio foram atualizados nesta branch para contemplar a reserva e a separação entre aprovação técnica e autorização financeira.

O estado AGUARDANDO_AUTORIZACAO_FINANCEIRA foi incluído na proposta documental da seção 3.3 de spec.md. O histórico da OPEN-ARQ-01 foi preservado na arquitetura.

A atualização dos diagramas foi confirmada pelo responsável pelo projeto. A consistência do conjunto permanece sujeita à revisão do PR.

Continuam pendentes para a Spec 009:

- O encaminhamento quando a necessidade de utilizar a reserva desaparecer durante a espera.
- O efeito de alterações do custo ou das condições financeiras sobre uma autorização já registrada.
- O tratamento de uma autorização financeira negada.
- Os critérios detalhados de autorização, auditoria e revalidação manual, em conjunto com as Specs relacionadas.

Esta questão permanece aberta para esses detalhes. A documentação atualizada não comprova implementação do fluxo manual.

## Situação de Implementação

Há comparação de saldo e roteamento parcial nas entidades atuais.

A reserva, a distinção dos motivos de revisão, a proteção completa contra processamento duplicado, a consistência concorrente e o desempenho exigido ainda não foram implementados ou comprovados neste recorte.

A aprovação da decisão pela equipe não equivale à aprovação integral desta Spec nem à conclusão de sua implementação.