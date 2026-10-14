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

Os repositórios de cadastro e consulta em memória do recorte A1 foram implementados e verificados localmente na Issue #49. A integração A2 e a persistência MySQL permanecem pendentes.

O recorte A1 foi aprovado conforme confirmação do responsável e possui evidências locais registradas na Issue #49. A SPEC-005 integral permanece incompleta; as decisões abertas de A2 e MySQL precisam ser resolvidas antes das implementações correspondentes.

## Detalhamento da etapa A — Issue #47

**Status:** recorte A1 aprovado conforme confirmação do responsável pelo projeto; implementação e verificações locais realizadas na Issue #49, ainda sujeitas à revisão do PR. A2 permanece pendente.

Este detalhamento define o primeiro recorte dos repositórios em memória. Não altera os requisitos de persistência MySQL nem representa atendimento integral à SPEC-005.

### Divisão da etapa

#### A1 — Cadastro e consulta em memória

Inclui:
- Interfaces de repositório.
- Implementações em memória.
- Cadastro e consulta de departamentos, recursos e requisições.
- Validação dos vínculos no cadastro das requisições.
- Preservação da identidade compartilhada dos objetos.
- Verificações dos contratos.

Não inclui alteração dos casos de uso existentes nem gravação transacional da validação financeira.

#### A2 — Integração com os casos de uso

Deverá integrar os repositórios à criação e à validação de requisições.

Antes dessa implementação, deverão ser definidos:
- Entradas dos casos de uso baseadas em identificadores.
- Coordenação entre consulta, alteração do domínio e confirmação da operação.
- Tratamento de falhas após alterações em objetos compartilhados.
- Estratégia de compatibilidade ou adaptação das verificações existentes.

A conclusão de A1 não comprova conclusão de A2.

### Localização nas camadas

- Interfaces: br.mackenzie.finops.aplicacao.portas.
- Implementações: br.mackenzie.finops.infraestrutura.repositorios.memoria.
- Entidades: permanecem em br.mackenzie.finops.dominio.entidades.

A aplicação depende das interfaces. A infraestrutura implementa essas interfaces e pode utilizar as entidades.

As entidades não dependem dos repositórios.

A montagem dos repositórios e seu fornecimento aos futuros casos de uso ocorrerão fora das entidades.

### Contratos propostos para A1

| Interface | Operação | Resultado |
|---|---|---|
| DepartamentoRepository | adicionar(Departamento departamento) | void |
| DepartamentoRepository | buscarPorId(UUID id) | Optional<Departamento> |
| RecursoCatalogoRepository | adicionar(RecursoCatalogo recurso) | void |
| RecursoCatalogoRepository | buscarPorId(UUID id) | Optional<RecursoCatalogo> |
| RequisicaoRepository | adicionar(Requisicao requisicao) | void |
| RequisicaoRepository | buscarPorId(UUID id) | Optional<Requisicao> |
| RequisicaoRepository | listarPorDepartamento(UUID departamentoId) | List<Requisicao> |

Os nomes e assinaturas acima são uma proposta documental, não código implementado.

Não há operação genérica salvar, atualizar ou excluir neste recorte.

Adicionar significa cadastrar um identificador ainda inexistente. Não significa substituir o registro anterior.

### Entradas e ausência

- Entidade nula: IllegalArgumentException.
- Identificador nulo, na entidade cadastrada ou na consulta: IllegalArgumentException.
- Identificador inexistente em buscarPorId: Optional.empty().
- Cadastro com identificador já existente: IllegalArgumentException, inclusive quando for fornecida a mesma instância.
- Departamento inexistente em listarPorDepartamento: lista vazia.
- As exceções devem possuir mensagem explicativa.

Um cadastro rejeitado não altera os registros existentes.

Nomes iguais com identificadores diferentes não são tratados como duplicidade neste recorte.

### Identidade dos objetos

Cada conjunto de repositórios representa um contexto de armazenamento em memória de uma execução.

Dentro desse contexto:
- Existe no máximo uma instância cadastrada de Departamento por UUID.
- Existe no máximo uma instância cadastrada de RecursoCatalogo por UUID.
- Existe no máximo uma instância cadastrada de Requisicao por UUID.
- Consultas retornam a instância cadastrada.
- O cadastro não substitui uma instância existente.

Repositórios diferentes, criados independentemente, não representam automaticamente o mesmo contexto.

Não utilizar estado estático global para compartilhar dados entre testes ou execuções.

### Vínculos da requisição

O RequisicaoRepository em memória deverá receber acesso aos repositórios de departamentos e recursos do mesmo contexto.

Antes do cadastro, deverá verificar:
- Requisição com identificador válido.
- Estado INICIAL.
- Existência de pelo menos um item válido.
- Departamento previamente cadastrado.
- Mesma instância de Departamento retornada pelo repositório.
- Recursos dos itens previamente cadastrados.
- Mesmas instâncias de RecursoCatalogo retornadas pelo repositório.

Uma entidade diferente com o mesmo UUID não satisfaz a exigência de identidade desta implementação em memória.

Essa comparação de referências é uma condição da implementação em memória. Não é um requisito de identidade entre objetos recuperados por conexões MySQL diferentes.

Todas as verificações devem ocorrer antes da inclusão da requisição.

O cadastro não altera estado, itens, total ou saldo e não executa validação orçamentária.

### Referências mutáveis e coleções

A lista devolvida por listarPorDepartamento:
- Não permite inclusão, remoção ou substituição de elementos pelo chamador.
- Representa os elementos encontrados no momento da consulta.
- Não exige uma ordem específica.
- Contém referências às requisições cadastradas.

A proteção da lista não torna as entidades imutáveis.

Alterações permitidas pelos métodos de domínio ficam visíveis nas consultas seguintes, pois os repositórios mantêm referências compartilhadas.

Por esse motivo, A1 não oferece uma operação separada de confirmação nem isolamento entre uma entidade consultada e a entidade armazenada.

A verificação dos vínculos no cadastro não garante que alterações posteriores preservem esses vínculos.

Em A1, quem adicionar itens a uma requisição já cadastrada deverá utilizar recursos do mesmo contexto. O repositório não intercepta chamadas diretas aos métodos da entidade.

A integração A2 deverá revalidar os vínculos antes da validação financeira e definir como impedir alterações que contornem essa verificação.

### Coordenação em memória

As operações dos repositórios devem proteger suas estruturas internas contra acesso concorrente.

Dois cadastros concorrentes com o mesmo UUID devem produzir exatamente um cadastro bem-sucedido. O outro deve ser rejeitado sem substituição.

A validação dos vínculos e do estado no cadastro da requisição deverá respeitar a coordenação pelo objeto Departamento utilizada no domínio.

A implementação deverá adotar uma ordem consistente de aquisição de bloqueios, evitando que operações de consulta mantenham um bloqueio de repositório enquanto aguardam o bloqueio de Departamento.

Proteger o mapa de registros não substitui a coordenação financeira da SPEC-003.

### Limites de falhas

Em A1, uma rejeição de cadastro deve preservar os registros existentes.

Isso não representa rollback de alterações realizadas anteriormente por quem possui uma referência à entidade.

A1 não atende, isoladamente, aos critérios C6 e C7 de falha nas operações completas de liberação e criação.

A integração A2 deverá definir como preservar ou recuperar estado e saldo quando uma operação falhar após modificar objetos compartilhados.

Essa decisão permanece em OPEN-005-03 e bloqueia a declaração de consistência da integração completa.

### Preparação dos dados

Os testes cadastrarão explicitamente departamentos e recursos antes das requisições.

Cada cenário utilizará um novo contexto de repositórios para evitar interferência entre testes.

Não haverá carga automática de dados, conexão com MySQL ou reinicialização automática do saldo.

O recorte não implementa renovação de períodos orçamentários.

### Critérios específicos de A1

- MEM-01: cadastrar e recuperar cada tipo de entidade preserva sua referência e seu identificador.
- MEM-02: consultas por UUID inexistente retornam ausência.
- MEM-03: entradas e identificadores nulos são rejeitados.
- MEM-04: identificadores duplicados são rejeitados e o objeto anterior é preservado.
- MEM-05: listar por departamento retorna apenas as requisições vinculadas ao identificador consultado.
- MEM-06: a lista retornada não permite modificar a coleção interna do repositório.
- MEM-07: requisições com departamento ou recurso não cadastrado são rejeitadas.
- MEM-08: objetos diferentes com o mesmo UUID dos registros existentes são rejeitados como vínculos.
- MEM-09: requisições vazias ou fora de INICIAL são rejeitadas no cadastro.
- MEM-10: cadastrar e consultar não altera saldo nem executa roteamento.
- MEM-11: dois cadastros concorrentes do mesmo UUID resultam em uma inclusão e uma rejeição.
- MEM-12: contextos independentes não compartilham registros.
- MEM-13: as 75 verificações existentes continuam aprovadas.
- MEM-14: alterações permitidas pelo domínio em uma entidade cadastrada ficam visíveis em consultas posteriores, evidenciando a política de referências compartilhadas.

Os critérios MEM não substituem os critérios C1 a C14 da SPEC-005.

### Relação com as questões em aberto

- OPEN-005-01: contratos e identidade de A1 detalhados nesta proposta; integração A2 ainda pendente.
- OPEN-005-02: integração MySQL permanece pendente.
- OPEN-005-03: rejeição de cadastro delimitada; tratamento de falhas da integração financeira permanece pendente.
- OPEN-005-04: A1 reutiliza as entidades atuais; reconstrução, representação no banco e valores históricos permanecem pendentes.
- OPEN-005-05: cadastro de usuários e autorização não são implementados neste recorte.
- OPEN-005-06: preparação explícita dos dados de teste definida; inicialização persistente e período orçamentário permanecem pendentes.
- OPEN-005-07: critérios de memória definidos; protocolo MySQL permanece pendente.

A aprovação deste detalhamento autoriza somente o recorte explicitamente aprovado. Não implica aprovação das decisões ainda abertas.

## Evidências de implementação — Issue #49

O recorte A1 recebeu três interfaces e três implementações de repositórios em memória.

Foram aprovadas 44 verificações funcionais dos repositórios, nove verificações concorrentes e as 75 verificações anteriores, totalizando 128.

A compilação de 20 arquivos Java com release 25 foi concluída pelo Maven Wrapper.

Os resultados e seus limites estão registrados em [Evidência da Issue #49](../evidencias/issue-49/repositorios-memoria.md).

Essas evidências se referem ao cadastro e à consulta em memória. A integração A2, o tratamento de falhas financeiras e a persistência MySQL continuam pendentes.