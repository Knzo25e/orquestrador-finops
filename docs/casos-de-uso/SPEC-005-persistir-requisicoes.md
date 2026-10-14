# SPEC-005 — Persistir o Fluxo de Requisições

## Identificação

**Objetivo:** Salvar e recuperar os dados do fluxo de requisições, preservando identidade, vínculos, valores financeiros e estados, inclusive após reiniciar a aplicação na etapa MySQL.

**Issue:** #45.
**Status:** Proposta para revisão; implementação pendente.

## Rastreabilidade

- Exigência de persistência do projeto final, referenciada no mapa de Specs.
- RF-01: criação de requisições com itens do catálogo.
- RF-02: validação e roteamento orçamentário.
- RB-01: custo mensal predefinido dos recursos.
- RB-02: avaliação de saldo e reserva.
- RB-05: liberação consistente, sem saldo negativo ou comprometimento duplicado.
- RNF-02: preservação do vínculo entre requisição e departamento; autorização completa depende da SPEC-006.
- ADR-001: domínio independente de frameworks e infraestrutura.
- ADR-002: MySQL e repositórios iniciais em memória.
- ADR-003: Java 25 e Maven Wrapper.
- Dependências: SPEC-001, SPEC-002 e SPEC-003.

Referências:
- [Especificação do sistema](../spec.md).
- [Arquitetura](../arquitetura.md).
- [Mapa de Specs](../mapa-de-specs.md).
- [SPEC-002](SPEC-002-criar-requisicao.md).
- [SPEC-003](SPEC-003-validar-orcamento.md).

## Etapas

### Etapa A — Repositórios em memória

Definir contratos de armazenamento e consulta e implementá-los em memória, permitindo verificar sua integração com os casos de uso.

Os dados permanecem disponíveis durante a execução da aplicação e são perdidos ao encerrar o processo.

Esta etapa não atende à persistência entre sessões e não conclui a SPEC-005.

### Etapa B — Persistência MySQL

Implementar os contratos utilizando MySQL e verificar recuperação após reinício, integridade dos vínculos e consistência das operações financeiras.

O atendimento integral desta Spec depende da conclusão e verificação desta etapa.

Cada etapa deverá ter seu recorte de implementação registrado e revisado antes da geração de código.

## Escopo

### Incluído

- Armazenar e consultar departamentos e recursos do catálogo necessários ao fluxo.
- Armazenar uma requisição e seus itens.
- Recuperar requisições por identificador.
- Consultar requisições vinculadas a um departamento.
- Preservar identificadores, vínculos, quantidades, valores, estados e motivos de revisão.
- Recuperar requisições sem executar novamente sua criação ou liberação.
- Integrar armazenamento aos casos de uso de criação e validação.
- Garantir gravação consistente do resultado da validação e de eventual comprometimento.
- Tratar falhas de armazenamento sem informar sucesso indevidamente.
- Verificar recuperação após reinício na etapa MySQL.

### Fora do escopo

- Autenticação e autorização completa por perfil.
- Aprovação técnica e liberação manual.
- Alteração de teto orçamentário.
- Configuração editável do percentual de reserva.
- Auditoria imutável das intervenções manuais.
- Interface gráfica e contrato REST.
- Provisionamento real na AWS.
- Exclusão de registros e política de retenção.
- Fechamento ou renovação automática de períodos orçamentários.

A persistência do fluxo automático não comprova implementação dos comportamentos previstos nas Specs 006 a 010.

## Dados a preservar

| Conjunto | Informações necessárias |
|---|---|
| Departamento | Identificador, nome, teto e saldo disponível |
| Recurso do catálogo | Identificador, nome e custo mensal |
| Requisição | Identificador, departamento vinculado, estado, custo total e motivo de revisão, quando houver |
| Item da requisição | Vínculo com a requisição, recurso referenciado, quantidade e subtotal |
| Solicitante | Identificador, quando informado pelo fluxo; seu preenchimento e validação dependem do alinhamento com a SPEC-006 |

Os dados acima descrevem necessidades de informação, não um esquema de tabelas aprovado.

A representação dos itens e dos compromissos financeiros será definida antes da implementação correspondente.

O valor comprometido representa custos estimados já liberados, não uma fatura real da AWS.

## Contratos e responsabilidades

A aplicação deverá depender de interfaces para acessar o armazenamento.

As implementações em memória e MySQL deverão respeitar o comportamento definido pelos contratos.

O domínio permanecerá responsável pelas regras financeiras e pelas transições de estado.

A infraestrutura realizará leitura e gravação sem decidir aprovação, revisão ou desconto por conta própria.

A reconstrução de entidades deverá preservar os dados armazenados, sem gerar novos identificadores nem executar efeitos financeiros.

As assinaturas, os pacotes e a coordenação das operações que envolvam vários registros serão definidos em OPEN-005-01 e OPEN-005-03.

## Pré-condições

- Os dados recebidos respeitam as validações de domínio.
- A requisição possui pelo menos um item válido.
- O departamento e os recursos referenciados podem ser identificados no armazenamento.
- A aplicação utiliza o departamento vinculado à requisição.
- Na etapa MySQL, o esquema necessário está disponível.

A preparação dos dados iniciais deverá ser definida em OPEN-005-06. Ela não deve recriar ou sobrescrever dados existentes a cada inicialização.

## Fluxo A — Criar e armazenar uma requisição

1. A aplicação obtém o departamento e os recursos necessários.
2. A criação segue as regras da SPEC-002.
3. A requisição recebe um identificador e permanece em INICIAL.
4. A aplicação solicita a gravação da requisição com seus itens e vínculos.
5. O armazenamento confirma a operação.
6. A aplicação informa sucesso e o identificador da requisição.

A gravação deve preservar a requisição completa. Uma falha não pode deixar uma requisição parcialmente armazenada, sem parte de seus itens.

Armazenar uma requisição INICIAL não compromete saldo.

## Fluxo B — Recuperar uma requisição

1. A aplicação solicita a consulta por identificador.
2. O armazenamento localiza os dados e seus vínculos.
3. A requisição é reconstruída com a mesma identidade, estado e valores.
4. A aplicação recebe o resultado.

A consulta não altera o saldo, não recalcula preços históricos com base no catálogo atual e não executa novamente a validação orçamentária.

Se o identificador não existir, a consulta informa ausência de forma explícita, conforme o contrato a definir.

## Fluxo C — Validar e gravar o resultado

1. A aplicação inicia a operação coordenada de validação.
2. São obtidos a requisição e os dados financeiros vigentes do departamento.
3. O domínio avalia o estado e aplica as regras da SPEC-003.
4. Em caso de liberação, o comprometimento e o novo estado são gravados como uma única operação consistente.
5. Em caso de revisão, o estado e o motivo são gravados sem desconto referente ao pedido.
6. A aplicação informa sucesso após a confirmação da gravação.

Duas gravações independentes, uma para o saldo e outra para a requisição, não são suficientes para comprovar a RB-05.

A implementação deverá tratar também os objetos em memória após falhas, impedindo que dados não confirmados sejam reutilizados como se estivessem persistidos.

## Alternativas e falhas

### A1 — Registro inexistente

A consulta informa ausência. Não cria automaticamente departamento, recurso ou requisição.

### A2 — Vínculo inexistente ou inválido

A gravação é rejeitada sem deixar registros parciais ou alterar o saldo.

### A3 — Requisição já liberada

Uma nova tentativa de validação preserva o estado e o comprometimento existentes, sem novo desconto.

Essa proteção deve continuar válida após reiniciar a aplicação na etapa MySQL.

### A4 — Estado fora do fluxo automático

A operação respeita os estados permitidos pela SPEC-003, preservando estado e saldo quando a validação automática não for admitida.

### A5 — Falha antes da confirmação da gravação

A aplicação não informa sucesso.

Quando a falha ocorrer antes de efetivar a operação, os dados anteriormente confirmados devem ser preservados.

O tratamento de falhas na comunicação durante a confirmação, quando o resultado puder ser desconhecido pelo cliente, será definido em OPEN-005-03.

Uma repetição não pode provocar comprometimento duplicado.

### A6 — Dados armazenados inconsistentes

A recuperação informa a inconsistência e impede a liberação baseada nesses dados.

O sistema não deve corrigir silenciosamente valores financeiros nem descartar itens para conseguir reconstruir a requisição.

## Invariantes

- Recuperar uma entidade preserva seu identificador.
- Cada requisição permanece vinculada ao mesmo departamento.
- Cada item mantém seus vínculos com requisição e recurso.
- A recuperação não provoca desconto ou transição de estado.
- O custo total recuperado deve ser coerente com os subtotais preservados.
- Valores monetários preservam a precisão estabelecida na Issue #37.
- Uma requisição liberada compromete seu valor uma única vez.
- Uma liberação confirmada não pode existir sem seu comprometimento correspondente.
- Uma falha não pode confirmar somente parte da operação financeira.
- Requisições em revisão não comprometem saldo.
- Consultas por departamento não retornam requisições de outro departamento.
- Armazenar dados não substitui a autorização de acesso prevista na SPEC-006.

## Concorrência

Na etapa em memória, a garantia atual depende do compartilhamento do mesmo objeto Departamento e da participação das operações na mesma coordenação.

Os repositórios não podem introduzir cópias independentes que invalidem essa garantia sem definir outra solução.

Na etapa MySQL, a coordenação deve considerar acessos por conexões e instâncias diferentes ao mesmo departamento.

O mecanismo deverá ser definido e verificado antes de declarar atendimento à RB-05 nessa etapa. A existência do banco, isoladamente, não constitui essa garantia.

## Critérios de aceitação

### C1 — Gravação e recuperação de uma requisição

Dada uma requisição INICIAL válida com dois itens, quando for armazenada e consultada, serão preservados identificador, departamento, itens, quantidades, subtotais, total e estado.

O saldo permanecerá inalterado.

### C2 — Consulta inexistente

Uma consulta por identificador inexistente informa ausência e não cria registros.

### C3 — Consulta por departamento

Dados pedidos dos departamentos A e B, a consulta por A retorna somente os pedidos vinculados a A.

### C4 — Recuperação após reinício

Na etapa MySQL, após salvar os dados, encerrar o processo e iniciar outro, a consulta recupera os mesmos valores e vínculos sem depender dos objetos da execução anterior.

### C5 — Repetição após reinício

Uma requisição liberada é recuperada após reinício e submetida novamente à validação.

O resultado informa que ela já foi liberada, preservando o saldo e o comprometimento.

### C6 — Falha durante a liberação

Uma falha controlada é provocada antes da confirmação da operação, após uma das alterações financeiras ter sido iniciada.

Após consultar novamente o armazenamento, não existe liberação parcial nem desconto isolado.

A aplicação não informa sucesso.

### C7 — Falha na gravação dos itens

Uma falha controlada durante a criação impede que apenas parte da requisição fique confirmada no armazenamento.

### C8 — Concorrência com insuficiência de saldo

Dados teto de R$ 10.000,00, saldo de R$ 5.000,00 e duas requisições de R$ 3.000,00, somente uma é liberada.

A outra fica em revisão por insuficiência, e o saldo final é R$ 2.000,00.

Na etapa MySQL, o cenário deve utilizar conexões independentes.

### C9 — Concorrência com reserva

Dados teto de R$ 10.000,00, saldo de R$ 5.000,00 e duas requisições de R$ 2.000,00, somente uma é liberada.

A outra fica em revisão por reserva, e o saldo final é R$ 3.000,00.

Na etapa MySQL, o cenário deve utilizar conexões independentes e consultar o estado, o motivo e o saldo confirmados ao final.

### C10 — Concorrência sobre a mesma requisição

Duas tentativas concorrentes de liberar a mesma requisição produzem somente um comprometimento.

Na etapa MySQL, a verificação utiliza conexões independentes e consulta os dados confirmados ao final.

### C11 — Precisão monetária

Custos, subtotais, totais, teto e saldo são recuperados sem perda de centavos ou conversão intermediária para ponto flutuante binário.

### C12 — Estado e motivo de revisão

Pedidos encaminhados por reserva e por insuficiência são recuperados com o estado e o motivo correspondentes, sem desconto.

### C13 — Vínculos inválidos

Tentativas de gravar referências inexistentes são rejeitadas, sem registros parciais.

### C14 — Distinção entre as etapas

As verificações em memória são identificadas como tal.

Elas não são apresentadas como evidência de recuperação após reinício ou de coordenação pelo MySQL.

## Estratégia de verificação

- Preservar as verificações existentes das Specs 002 e 003.
- Exercitar os contratos nas implementações em memória e MySQL, conforme o alcance de cada etapa.
- Utilizar dados isolados de teste.
- Consultar novamente os dados após cada gravação relevante.
- Verificar recuperação em um novo processo Java.
- Introduzir falhas controladas para verificar ausência de gravações parciais.
- Executar cenários concorrentes com coordenação explícita.
- Registrar ambiente, comandos, resultados e limitações.

As evidências de desempenho da Issue #41 não comprovam o tempo de operações que incluam MySQL. O protocolo correspondente deverá ser definido antes de afirmar atendimento ao RNF-01 com persistência.

## Questões em aberto

### OPEN-005-01 — Contratos e identidade em memória

Definir interfaces, operações, tratamento de ausência, inserção e atualização.

Definir a política para identificadores repetidos e como impedir sobrescrita indevida.

Determinar a responsabilidade por preservar a identidade compartilhada do Departamento na etapa em memória.

### OPEN-005-02 — Integração MySQL

Definir versão do MySQL, biblioteca de acesso, uso ou não de Spring Boot nesta etapa, configuração de conexão e criação ou evolução do esquema.

As escolhas devem respeitar os ADRs existentes e ser registradas antes da implementação.

### OPEN-005-03 — Operação financeira e falhas

Definir a fronteira da transação, o mecanismo de concorrência e a proteção contra atualizações perdidas.

Definir a recuperação dos objetos em memória após falhas e o tratamento de resultado desconhecido durante a confirmação.

Detalhar a proteção contra repetição da criação de pedidos e da validação financeira.

### OPEN-005-04 — Representação dos dados

Definir precisão e limites das colunas monetárias, representação de UUIDs, estados, motivos e identidade dos itens.

Definir como armazenar os valores históricos dos itens e a representação do comprometimento, preservando a coerência com o saldo.

Detalhar como detectar dados inconsistentes durante a recuperação.

### OPEN-005-05 — Solicitante e autorização

Alinhar a representação do solicitante com a SPEC-006.

A persistência não deve inventar usuários nem declarar isolamento completo apenas por armazenar o departamento.

### OPEN-005-06 — Inicialização e período orçamentário

Definir como cadastrar os dados iniciais sem sobrescrever informações existentes.

Delimitar o período considerado pelo orçamento no primeiro recorte. A ausência de renovação automática não autoriza reiniciar o saldo ao iniciar a aplicação.

### OPEN-005-07 — Verificação com persistência

Definir preparação e isolamento do banco de testes, execução entre processos, injeção de falhas e avaliação do desempenho com MySQL.

## Situação de implementação

O build foi padronizado na Issue #43.

Os repositórios e a integração MySQL ainda não foram demonstrados no material analisado.

Este documento é uma proposta para revisão. Não comprova implementação nem autoriza preencher silenciosamente as decisões em aberto.