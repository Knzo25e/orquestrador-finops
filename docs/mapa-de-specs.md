# Mapa de Specs - Orquestrador FinOps

| ID | Spec | Base Principal (Rastreabilidade) | Dependências |
|---|---|---|---|
| **001** | Fundação do Domínio (Entidades) | Modelo Conceitual, Regras de Negócio | Nenhuma (Concluído) |
| **002** | Caso de Uso: Criar Requisição de Recurso | UC01, RF01, Entidades de Domínio | 001 |
| **003** | Caso de Uso: Validar Limite de Orçamento | UC02, RF02, RNF-Desempenho | 001, 002 |
| **004** | API Rest: Exposição dos Casos de Uso | ADR-001 (Padrão REST) | 002, 003 |
| **005** | Infraestrutura: Persistência de Dados | ADR-002 | 001, 002, 003 |

## Questões em Aberto (Aguardando Decisão Humana)
* **OPEN-01:** SGBD ainda não definido (Utilizaremos PostgreSQL, MySQL ou NoSQL para as requisições?).
* **OPEN-02:** Mecanismo de mensageria para integração com os provedores de cloud (AWS/Azure) ainda não escolhido.