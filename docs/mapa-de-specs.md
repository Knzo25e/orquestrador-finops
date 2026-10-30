# Mapa de Specs - Orquestrador FinOps

## Referências

- Requisitos e regras: [spec.md](spec.md).
- Modelo de domínio: [modelo-dominio.md](uml/modelo-dominio.md).
- Arquitetura, drivers e ADRs: [arquitetura.md](arquitetura.md).

Este mapa organiza o trabalho previsto. A existência de uma linha não comprova implementação ou aprovação da Spec individual.

Os IDs existentes foram preservados. A ordem de execução é indicada pela posição na tabela e pelas dependências, não pela numeração dos IDs.

## Mapa de Specs — acompanhamento das entregas

| ID | Spec / comportamento esperado | Base principal | Dependências | Situação conhecida |
|---|---|---|---|---|
| 001 | Fundação do domínio: representar departamentos, recursos, itens e requisições | Modelo de domínio; ADR-001; DA-02 | Nenhuma | Entidades existentes; aderência completa ao modelo e invariantes ainda pendente |
| 002 | Criar requisição com itens, total calculado e status INICIAL | RF-01; modelo de domínio; ADR-001; SPEC-002 | 001 | Implementação parcial; cálculo e status verificados na Issue #26/PR #27; proteção contra departamento nulo e dez verificações registradas na Issue #33/PR #34 |
| 003 | Validar saldo e reserva, liberando automaticamente somente pedidos cujo saldo resultante fique acima da reserva | RF-02; RB-02; RB-05; RNF-01; RNF-02; UC-02; DA-01 | 001, 002 | Implementação inicial integrada pelo PR #40; verificações funcionais e concorrentes locais aprovadas; desempenho verificado na Issue #41 para o protocolo documentado; cobertura integral pendente |
| 005 | Salvar e recuperar dados do fluxo de requisições entre sessões | Exigência de persistência do projeto final, seção 6.2; ADR-002; DA-01; SPEC-005 | 001, 002, 003 | A1 e A2 em memória integrados pelos PRs #50 e #54; detalhamento MySQL pelo PR #56; reconstituição pelo PR #58; conexão e esquema pelo PR #60; cadastros JDBC pelo PR #62; requisições e itens pelo PR #64; criação transacional pelo PR #66; validação financeira transacional pelo PR #68; evidências pelo PR #69. Total de 462 verificações locais e teste adicional entre processos aprovados. Cobertura integral da Spec e desempenho com MySQL ainda sujeitos às pendências e limites documentados |
| 006 | [Perfis, identidade e controle de acesso](casos-de-uso/SPEC-006-perfis-acesso.md) | Personas; RF-01; RF-03; RF-04; RF-05; RNF-02 | 001 | Especificação integrada pelo PR #71; base de identidade e política pelo PR #73; persistência da autoria pelo PR #75; autorização dos fluxos persistentes pelo PR #77, com encerramento da Issue #76. Foram registradas 648 verificações locais aprovadas. Coordenação de vínculo demonstrada com fonte controlada no mesmo processo. Autenticação de produção e provedor real com as garantias de coordenação permanecem pendentes. Cobertura integral da SPEC não declarada |
| 004 | [Interface de consulta do catálogo e criação de requisições](casos-de-uso/SPEC-004-interface-aplicacao.md) | RF-01; RB-01; RNF-02; ADR-001; ADR-003; relação com RF-02 | 002, 003, 005, 006 | Proposta documental em elaboração na Issue #81: consulta do catálogo e criação persistente, sem validação financeira implícita. Consulta e listagem de requisições ficam fora do recorte inicial proposto. Contratos HTTP e critérios de aceite descritos para revisão; decisões OPEN-004-01 a OPEN-004-07 pendentes. Nenhum endpoint ou teste HTTP implementado nesta etapa |
| 007 | Registrar e preservar histórico das intervenções manuais, incluindo autorização de uso da reserva | RF-05; DA-04; ADR-002 | 005, 006 | Spec individual e implementação pendentes; contemplar os dados de auditoria definidos na Issue #35 |
| 008 | Configurar e alterar o teto mensal, preservando compromissos e bloqueando reduções abaixo do valor comprometido | RF-04; RF-05; RB-04; DA-01; DA-04 | 005, 006, 007 | Regra de teto definida na Issue #31; considerar o efeito do teto sobre a reserva; configuração do percentual permanece em OPEN-003-04 |
| 009 | Registrar revisão técnica, autorização de uso da reserva e revalidar condições para liberação manual | RF-03; RF-04; RF-05; RB-02; RB-03; RB-04; RB-05; DA-01; DA-03; DA-04 | 003, 005, 006, 007, 008 | Decisões das Issues #31 e #35 documentadas; Spec individual, detalhes do fluxo manual e implementação pendentes |
| 010 | Simular a resposta da infraestrutura para requisições liberadas | DECISÃO-03; ADR-004 | 003 | Decisão documentada; contrato e implementação do stub pendentes |

A Spec 004 deverá delimitar como o desenvolvedor consulta o catálogo e envia uma requisição. As interfaces dos demais comportamentos serão detalhadas nas respectivas Specs, sem concentrar toda a aplicação em uma única Spec de API.

As dependências indicam capacidades necessárias. Uma capacidade parcial não deve ser considerada suficiente sem conferir os critérios da Spec que depende dela.

## Decisões arquiteturais registradas

- **Arquitetura em camadas:** ADR-001.
- **DECISÃO-01 — Java e Spring Boot:** formalizada no ADR-003.
- **DECISÃO-02 — MySQL e repositórios em memória nas fases iniciais:** formalizada no ADR-002. Memória não atende à persistência final entre sessões.
- **DECISÃO-03 — Integração real com AWS postergada e uso de stubs:** formalizada no ADR-004.

Essas decisões não comprovam que Spring Boot, MySQL, repositórios em memória ou stubs já estejam implementados.

## Decisões financeiras — Issues #31 e #35

- **Reserva:** referência inicial de 10% do orçamento mensal, aprovada pela equipe conforme confirmação do responsável pelo projeto em 09/10/2026.
- **Aprovação automática:** exige custo dentro do saldo e saldo após o pedido estritamente acima da reserva, conforme RB-02.
- **Encaminhamento para revisão:** distingue insuficiência de saldo de utilização da reserva; nenhum dos dois casos compromete saldo automaticamente.
- **OPEN-ARQ-01 — Decisão ampliada:** aprovação técnica não libera provisionamento. Quando faltar saldo, exige ajuste autorizado; quando a liberação atingir ou consumir a reserva, exige autorização financeira explícita. Toda liberação revalida as condições vigentes e respeita RB-05.
- **OPEN-MAPA-01 — Decisão preservada:** alterações do teto preservam compromissos e recalculam o saldo. Reduções abaixo do valor comprometido são bloqueadas com explicação, conforme RB-04.
- **Perfis do MVP:** Desenvolvedor e Arquiteto Cloud. O arquiteto acumula as responsabilidades técnica e orçamentária, executando ações separadas e auditadas.
- **Referências:** seção 4 de [arquitetura.md](arquitetura.md) e [SPEC-003](casos-de-uso/SPEC-003-validar-orcamento.md).
- **Situação:** fluxo automático inicial implementado e verificado localmente na Issue #39; configuração editável do percentual e fluxo manual permanecem pendentes.


## Questões e verificações pendentes

- **OPEN-MAPA-02:** Contrato REST proposto na SPEC-004, Issue #81. A proposta ainda depende de revisão e das decisões OPEN-004-01 a OPEN-004-07; não representa API implementada.
- **OPEN-MAPA-03:** UC-01 descrito na proposta da SPEC-004, relacionando consulta do catálogo e criação com a SPEC-002. Revisão e aprovação dessa delimitação permanecem pendentes.
- Java 25 e Maven Wrapper padronizados na Issue #43, integrada pelo PR #44. A versão do Spring Boot e os detalhes de sua integração permanecem pendentes.
- **OPEN-005-01 a OPEN-005-07:** conferir a situação de cada questão na [SPEC-005](casos-de-uso/SPEC-005-persistir-requisicoes.md), considerando os recortes JDBC já integrados. Não tratar todo o grupo como pendente nem como concluído. Vínculo do solicitante e cobertura integral exigem acompanhamento específico.
- Conferir a cobertura integral dos requisitos e a ordem de execução na revisão do grupo.
- **OPEN-003-01:** entradas e precisão implementadas na Issue #37; cálculo da reserva com HALF_UP implementado e verificado na Issue #39.
- **OPEN-003-02:** coordenação em memória implementada na Issue #39; coordenação da validação persistente por bloqueios no MySQL implementada na Issue #67, com concorrência entre conexões e repetição em processos separados verificadas. Limites nas evidências da Issue #67.
- **OPEN-003-03:** protocolo executado na Issue #41, com atendimento ao limite de dois segundos no ambiente e nas cargas documentados.
- **OPEN-003-04 e OPEN-003-05:** permanecem pendentes a configuração editável do percentual e o detalhamento do fluxo manual.


## Governança e evidências

- A proposta de revisão deste mapa está vinculada à Issue #29.
- Cada Spec individual deve ser revisada antes da implementação correspondente.
- As funcionalidades ainda não especificadas nesta revisão não estão autorizadas para implementação apenas por aparecerem no mapa.
- A Issue #26 e o PR #27 registram a correção do total e cinco verificações executadas em Main.java; não comprovam atendimento integral à SPEC-002.
- A versão anterior do mapa, integrada pelo PR #30 da Issue #29, foi aprovada pela equipe conforme confirmação do responsável pelo projeto.
- A revisão financeira da Issue #31 foi integrada pelo PR #32.
- A Issue #33 e o PR #34 registram a proteção do construtor contra departamento nulo e dez verificações executadas em Main.java; não comprovam atendimento integral à SPEC-002.
- A atualização da regra de reserva foi vinculada à Issue #35. A aprovação da regra de reserva pela equipe não substitui a revisão integral da SPEC-003 e dos documentos relacionados no PR.
- A proposta da SPEC-005 está vinculada à Issue #45. A etapa em memória não atende à persistência entre sessões; a conclusão da Spec depende da implementação e verificação da etapa MySQL.
- A criação transacional JDBC foi integrada pelo PR #66.
- A validação financeira transacional foi integrada pelo PR #68,
  com evidências pelo PR #69 e encerramento da Issue #67.
- [Evidências da Issue #67](evidencias/issue-67/validacao-financeira-jdbc.md):
  462 verificações locais aprovadas e teste adicional entre processos.
- A [SPEC-006](casos-de-uso/SPEC-006-perfis-acesso.md) foi documentada
  pelo PR #71. A Issue #72 implementa a base de identidade e a política
  isolada de autorização, verificadas localmente.
- [Evidências da Issue #72](evidencias/issue-72/base-identidade-autorizacao.md):
  34 verificações novas e 282 de regressão sem banco aprovadas.
  Os testes JDBC anteriores não foram reexecutados nesta etapa.
- A base de identidade e a política isolada foram integradas pelo PR #73.
- A Issue #74 implementa a persistência da autoria e sua comparação
  nas repetições de criação, mantendo compatibilidade com autoria desconhecida.
- [Evidências da Issue #74](evidencias/issue-74/autoria-requisicoes.md):
  540 verificações locais aprovadas, sendo 339 sem banco e 201 JDBC.
  Migração com preservação de um cenário anterior verificada.
  Persistência da autoria integrada pelo PR #75.
- A Issue #76 integra autorização aos fluxos persistentes de criação,
  consulta, listagem e repetição.
- [Evidências da Issue #76](evidencias/issue-76/autorizacao-requisicoes.md):
  648 verificações distintas aprovadas, sendo 415 sem banco e 233 JDBC.
  Incluem 117 verificações novas.
- Coordenação de vínculo demonstrada com fonte controlada no mesmo
  processo; não comprova coordenação distribuída ou autenticação real.
- Dois adaptadores de entrada controlados delegam aos mesmos casos
  de uso da aplicação.
- A entrega da Issue #76 foi integrada à main pelo PR #77.
  A issue foi encerrada no recorte documentado, sem declarar
  atendimento integral à SPEC-006.
- A exposição pela API depende de identidade confiável, composição
  pelas entradas autorizadas e cumprimento do contrato de coordenação.

## Proposta de interface — Issue #81

A [SPEC-004](casos-de-uso/SPEC-004-interface-aplicacao.md) propõe:

- GET /api/v1/catalogo/recursos.
- POST /api/v1/requisicoes.
- Criação separada da validação financeira.
- Preservação do UUID e do conteúdo nas novas tentativas.
- Respostas distintas para sucesso confirmado, conflito,
  negativa de acesso e confirmação desconhecida.
- Consulta e listagem de requisições em uma extensão posterior.

A proposta registra critérios de aceite e as decisões pendentes
OPEN-004-01 a OPEN-004-07. Entre elas estão autenticação,
coordenação diante de commit desconhecido, tratamento da existência
de UUIDs, limites operacionais e integração HTTP.

A listagem persistente do catálogo exige capacidades adicionais
na aplicação e na infraestrutura.

Esta etapa é documental. Não adiciona Spring Boot, endpoints
ou testes HTTP, nem autoriza exposição de operações protegidas
sem resolver as dependências aplicáveis.