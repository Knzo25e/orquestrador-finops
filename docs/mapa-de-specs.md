# Mapa de Specs - Orquestrador FinOps

| ID | Spec | Base Principal (Rastreabilidade) | Dependências |
|---|---|---|---|
| **001** | Fundação do Domínio (Entidades) | Modelo Conceitual, Regras de Negócio | Nenhuma (Concluído) |
| **002** | Caso de Uso: Criar Requisição de Recurso | UC01, RF01, Entidades de Domínio | 001 |
| **003** | Caso de Uso: Validar Limite de Orçamento | UC02, RF02, RNF-Desempenho | 001, 002 |
| **004** | API Rest: Exposição dos Casos de Uso | ADR-001 (Padrão REST) | 002, 003 |
| **005** | Infraestrutura: Persistência de Dados | ADR-002 | 001, 002, 003 |

## Decisões Arquiteturais Resolvidas
* **DECISÃO-01 (Stack Base):** O projeto será desenvolvido em Java utilizando o ecossistema Spring Boot.
* **DECISÃO-02 (Banco de Dados):** O SGBD relacional oficial do projeto será o MySQL. Durante as fases iniciais de validação do domínio, utilizaremos persistência em memória (banco fictício isolado por interfaces) para garantir o desacoplamento.
* **DECISÃO-03 (Integração Cloud):** A conexão com provedores em nuvem (APIs da AWS) foi postergada. O orquestrador utilizará *stubs* (simuladores de resposta) de infraestrutura até que as regras de negócio de FinOps estejam totalmente consolidadas.