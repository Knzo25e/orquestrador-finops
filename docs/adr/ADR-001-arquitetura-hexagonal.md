# ADR-001: Adoção de Arquitetura Hexagonal (Ports & Adapters)

- **Status:** Aceito
- **Data:** 2026-10

## Contexto
O Orquestrador FinOps concentra regras financeiras críticas (cálculo de orçamento, validação de teto departamental e máquina de estados de requisições) que não podem depender de detalhes de banco de dados ou protocolo HTTP, além de precisarem ser testadas rapidamente sem infraestrutura externa.

## Decisão
Estruturar o sistema seguindo a **Arquitetura Hexagonal (Ports & Adapters)** combinada com princípios de **Domain-Driven Design (DDD)**, isolando o núcleo em `dominio` e `aplicacao` (com interfaces explícitas em `aplicacao/portas`) e mantendo persistência e camada web como adaptadores na `infraestrutura`.

## Consequências
- **Positivas:** O núcleo de negócio não importa classes de SQL ou HTTP; permite alternar entre repositórios em memória (`*RepositoryMemoria`) nos testes unitários e adaptadores MySQL (`*Jdbc`) em produção sem alterar regras de domínio.
- **Negativas:** Maior quantidade de interfaces e classes de mapeamento entre camadas em comparação a uma arquitetura tradicional em três camadas acopladas.
