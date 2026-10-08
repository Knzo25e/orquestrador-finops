# Mapa de Specs - Orquestrador FinOps

## Referências

- Requisitos e regras: [spec.md](spec.md).
- Modelo de domínio: [modelo-dominio.md](uml/modelo-dominio.md).
- Arquitetura, drivers e ADRs: [arquitetura.md](arquitetura.md).

Este mapa organiza o trabalho previsto. A existência de uma linha não comprova implementação ou aprovação da Spec individual.

Os IDs existentes foram preservados. A ordem de execução é indicada pela posição na tabela e pelas dependências, não pela numeração dos IDs.

## Mapa proposto para revisão do grupo

| ID | Spec / comportamento esperado | Base principal | Dependências | Situação conhecida |
|---|---|---|---|---|
| 001 | Fundação do domínio: representar departamentos, recursos, itens e requisições | Modelo de domínio; ADR-001; DA-02 | Nenhuma | Entidades existentes; aderência completa ao modelo e invariantes ainda pendente |
| 002 | Criar requisição com itens, total calculado e status INICIAL | RF-01; modelo de domínio; ADR-001; SPEC-002 | 001 | Implementação parcial; cálculo e status verificados na Issue #26 e no PR #27 |
| 003 | Validar orçamento, liberar pedidos dentro do saldo e encaminhar excedentes para revisão | RF-02; RB-02; RNF-01; UC-02; DA-01 | 001, 002 | Roteamento parcial no domínio; Spec individual e verificação do fluxo completo pendentes |
| 004 | Expor consulta do catálogo e envio de requisições pela interface de aplicação | RF-01; RF-02; RNF-02; ADR-001; ADR-003 | 002, 003, 005, 006 | Recorte inicial proposto; contrato REST e critérios de aceite ainda precisam ser especificados |
| 005 | Salvar e recuperar dados do fluxo de requisições entre sessões | Exigência de persistência do projeto final, seção 6.2; ADR-002; DA-01 | 001, 002, 003 | MySQL decidido; implementação e critérios detalhados pendentes |
| 006 | Restringir ações por perfil e vincular solicitações ao departamento do desenvolvedor | Personas; RF-01; RF-03; RF-04; RNF-02 | 001 | Spec individual e implementação pendentes |
| 007 | Registrar e preservar histórico das intervenções manuais | RF-05; DA-04; ADR-002 | 005, 006 | Spec individual e implementação pendentes |
| 008 | Configurar e alterar o teto mensal de um departamento | RF-04; RF-05; DA-01; DA-04 | 005, 006, 007 | Spec individual pendente; definir efeito da alteração sobre o saldo disponível |
| 009 | Consultar pedidos em revisão e registrar a decisão do Arquiteto Cloud | RF-03; RB-03; RF-05; UC-02; DA-03; DA-04 | 003, 005, 006, 007 | Spec individual pendente; efeito financeiro da aprovação excepcional depende de OPEN-ARQ-01 |
| 010 | Simular a resposta da infraestrutura para requisições liberadas | DECISÃO-03; ADR-004 | 003 | Decisão documentada; contrato e implementação do stub pendentes |

A Spec 004 deverá delimitar como o desenvolvedor consulta o catálogo e envia uma requisição. As interfaces dos demais comportamentos serão detalhadas nas respectivas Specs, sem concentrar toda a aplicação em uma única Spec de API.

As dependências indicam capacidades necessárias. Uma capacidade parcial não deve ser considerada suficiente sem conferir os critérios da Spec que depende dela.

## Decisões arquiteturais registradas

- **Arquitetura em camadas:** ADR-001.
- **DECISÃO-01 — Java e Spring Boot:** formalizada no ADR-003.
- **DECISÃO-02 — MySQL e repositórios em memória nas fases iniciais:** formalizada no ADR-002. Memória não atende à persistência final entre sessões.
- **DECISÃO-03 — Integração real com AWS postergada e uso de stubs:** formalizada no ADR-004.

Essas decisões não comprovam que Spring Boot, MySQL, repositórios em memória ou stubs já estejam implementados.

## Questões e verificações pendentes

- **OPEN-ARQ-01:** definir o efeito da aprovação manual acima do saldo, conforme arquitetura.md.
- **OPEN-MAPA-01:** definir como a alteração do teto mensal afeta o saldo já utilizado, antes de implementar a Spec 008.
- **OPEN-MAPA-02:** confirmar e detalhar o contrato REST previsto no mapa anterior. ADR-001 trata da arquitetura em camadas e não constitui uma decisão sobre REST.
- **OPEN-MAPA-03:** localizar ou elaborar o caso de uso UC-01 referenciado pela SPEC-002; o documento não foi encontrado no material analisado.
- Padronizar versões do Java, Spring Boot e ferramenta de build antes da integração do framework.
- Conferir a cobertura integral dos requisitos e a ordem de execução na revisão do grupo.

## Governança e evidências

- A proposta de revisão deste mapa está vinculada à Issue #29.
- Cada Spec individual deve ser revisada antes da implementação correspondente.
- As funcionalidades ainda não especificadas nesta revisão não estão autorizadas para implementação apenas por aparecerem no mapa.
- A Issue #26 e o PR #27 registram a correção do total e cinco verificações executadas em Main.java; não comprovam atendimento integral à SPEC-002.
- A aprovação do mapa pelo grupo deverá ser registrada na revisão do PR da Issue #29.