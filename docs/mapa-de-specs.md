# Mapa de Specs - Orquestrador FinOps

## Referências

- Requisitos e regras: [spec.md](spec.md).
- Modelo de domínio: [modelo-dominio.md](uml/modelo-dominio.md).
- Arquitetura, drivers e ADRs: [arquitetura.md](arquitetura.md).

Este mapa organiza o trabalho previsto. A existência de uma linha não comprova implementação ou aprovação da Spec individual.

Os IDs existentes foram preservados. A ordem de execução é indicada pela posição na tabela e pelas dependências, não pela numeração dos IDs.

## Mapa de Specs — atualização da Issue #35

| ID | Spec / comportamento esperado | Base principal | Dependências | Situação conhecida |
|---|---|---|---|---|
| 001 | Fundação do domínio: representar departamentos, recursos, itens e requisições | Modelo de domínio; ADR-001; DA-02 | Nenhuma | Entidades existentes; aderência completa ao modelo e invariantes ainda pendente |
| 002 | Criar requisição com itens, total calculado e status INICIAL | RF-01; modelo de domínio; ADR-001; SPEC-002 | 001 | Implementação parcial; cálculo e status verificados na Issue #26/PR #27; proteção contra departamento nulo e dez verificações registradas na Issue #33/PR #34 |
| 003 | Validar saldo e reserva, liberando automaticamente somente pedidos cujo saldo resultante fique acima da reserva | RF-02; RB-02; RB-05; RNF-01; RNF-02; UC-02; DA-01 | 001, 002 | SPEC-003 elaborada na Issue #35, em revisão; reserva, consistência concorrente e verificação completa pendentes de implementação |
| 005 | Salvar e recuperar dados do fluxo de requisições entre sessões | Exigência de persistência do projeto final, seção 6.2; ADR-002; DA-01 | 001, 002, 003 | MySQL decidido; implementação e critérios detalhados pendentes |
| 006 | Restringir ações por perfil e vincular solicitações ao departamento do desenvolvedor | Personas; RF-01; RF-03; RF-04; RNF-02 | 001 | Spec individual e implementação pendentes |
| 004 | Expor consulta do catálogo e envio de requisições pela interface de aplicação | RF-01; RF-02; RNF-02; ADR-001; ADR-003 | 002, 003, 005, 006 | Recorte inicial proposto; contrato REST e critérios de aceite ainda precisam ser especificados |
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
- **Situação:** regra de reserva aprovada; detalhamento das Specs em revisão e implementação pendente.


## Questões e verificações pendentes

- **OPEN-MAPA-02:** Confirmar e detalhar o contrato REST previsto no mapa anterior. ADR-001 trata da arquitetura em camadas e não constitui uma decisão sobre REST.
- **OPEN-MAPA-03:** Localizar ou elaborar o caso de uso UC-01 referenciado pela SPEC-002; o documento não foi encontrado no material analisado.
- Padronizar versões do Java, Spring Boot e ferramenta de build antes da integração do framework.
- Conferir a cobertura integral dos requisitos e a ordem de execução na revisão do grupo.
- **OPEN-003-01:** decisões sobre entradas e precisão financeira definidas na Issue #37, com validações e migração monetária verificadas localmente; aplicação do arredondamento ao cálculo da reserva ainda pendente.
- **OPEN-003-02 a OPEN-003-05:** detalhar concorrência, protocolo de desempenho, configuração do percentual e continuidade manual, conforme a SPEC-003. A atribuição da configuração do percentual a uma Spec de implementação ainda precisa ser definida.


## Governança e evidências

- A proposta de revisão deste mapa está vinculada à Issue #29.
- Cada Spec individual deve ser revisada antes da implementação correspondente.
- As funcionalidades ainda não especificadas nesta revisão não estão autorizadas para implementação apenas por aparecerem no mapa.
- A Issue #26 e o PR #27 registram a correção do total e cinco verificações executadas em Main.java; não comprovam atendimento integral à SPEC-002.
- A versão anterior do mapa, integrada pelo PR #30 da Issue #29, foi aprovada pela equipe conforme confirmação do responsável pelo projeto.
- A revisão financeira da Issue #31 foi integrada pelo PR #32.
- A Issue #33 e o PR #34 registram a proteção do construtor contra departamento nulo e dez verificações executadas em Main.java; não comprovam atendimento integral à SPEC-002.
- A atualização atual está vinculada à Issue #35. A aprovação da regra de reserva pela equipe não substitui a revisão integral da SPEC-003 e dos documentos relacionados no PR.