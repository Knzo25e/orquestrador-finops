# SPEC-003 — Validar Orçamento da Requisição

## Identificação

**Objetivo:** Avaliar o custo de uma requisição frente ao saldo disponível e à reserva orçamentária do departamento, decidindo sua liberação automática ou encaminhamento para revisão.

**Issue:** #35.
**Status:** implementação inicial da Issue #39 em revisão, com verificações funcionais e concorrentes executadas localmente. Desempenho e cobertura integral dos critérios permanecem pendentes.

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

As regras de validação das entradas financeiras e a política de precisão foram definidas e implementadas na Issue #37, conforme OPEN-003-01. O cálculo da reserva com HALF_UP foi implementado e verificado na Issue #39.

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

O tratamento de tentativas simultâneas e do estado Em Analise segue a decisão registrada em OPEN-003-02.

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

A tentativa adicional aguarda a operação em andamento e retorna JA_LIBERADA, sem novo comprometimento, conforme OPEN-003-02.

### C8 — Estados fora do fluxo

Proposta vinculada à alternativa A3:

**Dado** uma requisição em Revisão Pendente, AGUARDANDO_AJUSTE_ORCAMENTARIO, AGUARDANDO_AUTORIZACAO_FINANCEIRA ou Rejeitado.

**Quando** houver tentativa de validação automática.

**Então** a operação informa que o estado não admite esse fluxo, sem alterar estado ou saldo.

### C9 — Tempo de processamento

A validação automática deve terminar em menos de dois segundos, conforme RNF-01.

O protocolo de medição está definido em OPEN-003-03. Os resultados das três execuções estão no Registro de Desempenho — Issue #41.

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

**Status:** decisões de entradas e precisão implementadas na Issue #37; cálculo e arredondamento da reserva implementados e verificados na Issue #39.

#### Decisões

- O recurso de um item é obrigatório.
- A quantidade é obrigatória e deve ser um inteiro maior que zero.
- O custo mensal e o teto mensal são obrigatórios e não negativos.
- Recursos gratuitos e departamentos com teto zero são permitidos.
- Valores monetários utilizam BigDecimal, normalizados para duas casas decimais.
- Entradas com frações de centavo são rejeitadas, sem arredondamento silencioso. Zeros adicionais são aceitos: 1.230 equivale a 1.23.
- A reserva calculada deverá ser arredondada para duas casas decimais com HALF_UP.
- Entradas inválidas devem produzir IllegalArgumentException com mensagem explicativa.
- Consultas e descontos rejeitam valores nulos, negativos ou com frações de centavo.
- Descontos acima do saldo são rejeitados sem alterar o saldo.
- Pedidos gratuitos continuam sujeitos à condição de reserva da RB-02.

#### Verificação — Issue #37

**Data:** 09/10/2026.
**Execução:** Run do VS Code.
**Ambiente observado:** Eclipse Adoptium JDK 25.0.2.
**Evidência:** saída de execução local compartilhada pelo responsável pela alteração.

- Main: dez verificações anteriores adaptadas para BigDecimal, todas aprovadas.
- VerificarValoresMonetarios: 34 verificações aprovadas, abrangendo entradas inválidas, valores zero, precisão decimal e preservação dos dados após rejeições.

#### Limites

As verificações foram executadas por classes Java com método main, sem framework de testes.

Não comprovam implementação da reserva ou de seu arredondamento, consistência concorrente, proteção completa contra comprometimento duplicado, persistência ou atendimento integral à SPEC-003.

Na conclusão da Issue #37, getItens() ainda expunha a lista interna. Essa limitação foi tratada na Issue #39 com uma cópia não modificável e bloqueio de adições após a saída de INICIAL.

### OPEN-003-02 — Concorrência e estado Em Analise

**Status:** mecanismo inicial implementado na Issue #39 com synchronized sobre o objeto Departamento; cenários C6, C7 e C14 verificados localmente. O alcance permanece restrito a uma instância da aplicação com o mesmo objeto Departamento compartilhado.

A primeira implementação coordenará as operações por departamento, em uma única instância da aplicação. As requisições do mesmo departamento deverão compartilhar o mesmo objeto Departamento.

A coordenação deverá abranger a verificação do estado da requisição, a leitura dos valores financeiros, a decisão de roteamento, o comprometimento do valor e a mudança de estado. Operações que alterem o saldo deverão participar da mesma coordenação.

Uma tentativa simultânea aguardará a operação em andamento e, ao obter acesso, verificará o estado resultante:

- Se a requisição já estiver liberada, retornará esse resultado sem novo desconto.
- Se estiver em estado que não admite validação automática, informará essa condição sem alterar estado ou saldo.
- Se estiver em INICIAL, poderá executar a validação.

Em Analise será um estado transitório dentro da operação coordenada. Uma tentativa que encontre esse estado após obter acesso deverá informar que ele não admite uma nova validação automática, sem realizar desconto ou reiniciar o fluxo.

A solução deverá impedir saldo negativo, aprovação automática indevida por reserva e comprometimento duplicado.

Essa garantia não abrange múltiplas instâncias da aplicação nem objetos Departamento distintos que representem o mesmo departamento. A etapa de persistência deverá definir e verificar a coordenação correspondente no banco de dados.

### OPEN-003-03 — Protocolo de desempenho

**Status:** protocolo definido e executado na Issue #41. As três execuções atenderam ao limite do RNF-01/C9 no ambiente e nas cargas documentados, com resultados funcionais corretos.

#### Ambiente

- Windows 10 Pro, versão 10.0.19045.
- AMD Ryzen 7 5700X, com oito núcleos e 16 processadores lógicos.
- Memória RAM informada pelo sistema: 15,9 GB.
- Registrar a versão efetiva do Java em cada execução.
- Executar sem depurador e registrar interferências relevantes, como outras cargas intensivas no computador.

#### Cargas

Combinar:
- Requisições com 1, 10 e 100 itens.
- Liberação, revisão por reserva e revisão por insuficiência de saldo.
- Execução sequencial e execução com oito tarefas concorrentes compartilhando o mesmo objeto Departamento.

Cada combinação terá 100 validações de aquecimento, excluídas das estatísticas, e 1.000 validações medidas. Essas quantidades são totais por combinação, não por tarefa.

O programa completo será executado três vezes, em processos Java separados.

Cada validação utilizará uma requisição nova. Os dados serão preparados para manter o resultado esperado durante todo o lote. Aquecimento e medição utilizarão lotes independentes.

#### Intervalo medido

A duração será calculada com System.nanoTime(), imediatamente antes da chamada a ValidarOrcamentoUseCase.executar() e imediatamente após seu retorno.

O intervalo inclui a espera pelo bloqueio do departamento e a execução da validação.

Criação das requisições, preparação das tarefas, verificação dos resultados e impressão ficam fora do intervalo medido. A espera na fila do executor, anterior à chamada ao caso de uso, também não integra esta medição.

#### Resultados registrados

Para cada combinação e execução:
- Quantidade de validações medidas.
- Mediana.
- Percentil 95.
- Duração máxima.
- Quantidade de validações com duração maior ou igual a dois segundos.
- Confirmação dos resultados funcionais, estados, motivos de revisão e saldo final.

O percentil 95 utilizará o método do posto mais próximo: ordenar as durações e selecionar a posição correspondente ao teto de 0,95 multiplicado pelo número de amostras.

A mediana, para quantidade par de amostras, será a média dos dois valores centrais.

#### Critério de aceitação

Todas as validações medidas devem terminar em menos de dois segundos e produzir o resultado funcional esperado.

Uma falha funcional invalida a aprovação daquela execução, mesmo que as durações atendam ao limite.

#### Alcance

Os resultados serão evidência do RNF-01/C9 exclusivamente para o ambiente, as cargas e o intervalo medido neste protocolo.

Não comprovam desempenho com banco de dados, API, rede, múltiplas instâncias ou cargas diferentes das exercitadas.

### OPEN-003-04 — Configuração do percentual

**Status:** percentual da primeira implementação definido; configuração editável permanece pendente.

Na Issue #39, a validação utilizará percentual fixo de 10% do teto mensal para todos os departamentos, sem interface ou operação para alteração desse percentual.

A reserva será calculada com BigDecimal e arredondada para duas casas decimais com HALF_UP, conforme a decisão da Issue #37.

A configuração futura ainda deverá definir abrangência global ou por departamento, limites permitidos, responsável autorizado, auditoria e efeito sobre pedidos pendentes.

A utilização do percentual fixo não comprova implementação da configuração editável.

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

O fluxo automático inicial foi implementado na Issue #39, incluindo reserva, motivos de revisão, proteção dos itens e prevenção de comprometimento duplicado na operação coordenada.

As verificações funcionais e concorrentes estão registradas abaixo. A garantia concorrente considera uma única instância da aplicação com o mesmo objeto Departamento compartilhado pelas requisições.

O RNF-01/C9 foi verificado na Issue #41 para o ambiente e as cargas do protocolo documentado. O C8 foi exercitado apenas para Revisão Pendente. A configuração editável do percentual e o fluxo manual permanecem pendentes.

Esses resultados não representam atendimento integral à SPEC-003.

## Registro de Verificação — Issue #39

**Data:** 13/10/2026.
**Execução:** Run do VS Code.
**Ambiente observado:** Eclipse Adoptium JDK 25.0.2.
**Evidência:** saídas de execução local compartilhadas pelo responsável pela alteração.

### Comportamentos implementados

- Reserva fixa de 10% do teto mensal, calculada com BigDecimal e HALF_UP para duas casas decimais.
- Aprovação automática somente com saldo suficiente e saldo resultante estritamente acima da reserva.
- Revisão por insuficiência de saldo ou por reserva, com motivo identificável e sem desconto.
- Utilização exclusiva do departamento vinculado à requisição.
- Repetição após liberação sem comprometimento adicional.
- Rejeição de validação de requisição sem itens, preservando seu estado inicial.
- Lista de itens retornada como cópia não modificável.
- Adição de itens permitida somente em INICIAL.
- Coordenação pelo mesmo objeto Departamento, abrangendo decisão, desconto e mudança de estado.
- Caso de uso ValidarOrcamentoUseCase delegando as regras ao domínio.

### Resultados

| Classe executada | Resultado |
|---|---|
| Main | Dez verificações anteriores aprovadas |
| VerificarValoresMonetarios | 34 verificações anteriores aprovadas |
| VerificarValidacaoOrcamentaria | 25 verificações funcionais aprovadas |
| VerificarConcorrenciaOrcamentaria | Três cenários concorrentes, com seis verificações aprovadas |

Total: 75 verificações aprovadas.

Os cenários concorrentes cobriram:
- C6: dois pedidos disputando saldo; somente um liberado.
- C7: duas tentativas sobre a mesma requisição; comprometimento único.
- C14: dois pedidos cuja aprovação conjunta atingiria a reserva; somente um liberado.

As tarefas aguardaram um sinal comum de início. A ordem de execução não foi presumida.

### Limites da evidência

- Concorrência verificada em uma única instância da aplicação, compartilhando o mesmo objeto Departamento.
- Os resultados não comprovam todas as possíveis intercalações de execução.
- O C8 foi exercitado para Revisão Pendente; os estados exclusivos do fluxo manual não foram exercitados.
- Na conclusão da Issue #39, o RNF-01/C9 ainda não havia sido medido. A medição posterior está registrada na Issue #41.
- Os limites de tempo dos testes concorrentes detectam travamentos; não constituem medição de desempenho.
- Configuração editável do percentual, fluxo manual, persistência, autenticação e interface permanecem fora deste recorte.
- As verificações utilizam classes com método main, sem framework de testes.

## Registro de Desempenho — Issue #41

**Data:** 13/10/2026.
**Programa:** MedirDesempenhoOrcamentario.
**Execução:** três processos Java separados, iniciados pelo Run do VS Code.
**Java observado:** Eclipse Adoptium 25.0.2.
**Ambiente:** Windows 10 Pro 10.0.19045; AMD Ryzen 7 5700X, oito núcleos e 16 processadores lógicos; 15,9 GB de RAM.
**Evidência:** saídas de execução local compartilhadas pelo responsável pela alteração.
**Interferências externas:** não informadas até este registro.

Cada execução contemplou 18 combinações: três tamanhos de pedido, três resultados de roteamento e dois modos de execução.

Foram realizadas 100 validações de aquecimento e 1.000 validações medidas por combinação, conforme OPEN-003-03.

| Execução | Início UTC | Validações medidas | Máximo observado (ms) | Durações >= 2 s | Resultado funcional |
|---|---|---:|---:|---:|---|
| 1 | 2026-10-13T15:59:08.642625800Z | 18000 | 2,4087 | 0 | Todas corretas |
| 2 | 2026-10-13T16:00:31.120426900Z | 18000 | 3,6951 | 0 | Todas corretas |
| 3 | 2026-10-13T16:00:55.923233Z | 18000 | 4,2438 | 0 | Todas corretas |

Total: 54.000 validações medidas, além de 5.400 validações de aquecimento.

O maior tempo observado foi 4,2438 ms, na terceira execução, para revisão por insuficiência de saldo com cem itens e oito tarefas.

### Conclusão e alcance

As três execuções atenderam ao critério de duração estritamente inferior a dois segundos por validação, com resultados funcionais corretos.

Esta evidência atende ao RNF-01/C9 dentro do protocolo documentado. Não comprova desempenho para outras cargas, banco de dados, API, rede ou múltiplas instâncias.

A medição inclui a espera pelo bloqueio do departamento, mas exclui criação dos dados, espera anterior à chamada no executor, conferência dos resultados e impressão.

As 54.000 amostras de desempenho são registradas separadamente das 75 verificações anteriores; não representam 54.000 casos de teste distintos.

**Interferências externas:** a carga de outros programas durante as execuções não foi monitorada.

**Saídas completas:**
- [Execução 1](../evidencias/issue-41/execucao-1.txt)
- [Execução 2](../evidencias/issue-41/execucao-2.txt)
- [Execução 3](../evidencias/issue-41/execucao-3.txt)