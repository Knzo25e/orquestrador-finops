# ADR-003: Persistência Relacional com MySQL e Controle Transacional Explícito

- **Status:** Aceito
- **Data:** 2026-10

## Contexto
A validação orçamentária exige que a criação da requisição, a gravação dos seus itens e o abatimento do saldo do departamento ocorram de forma atômica, evitando inconsistências parciais e condições de corrida (*race conditions*) quando múltiplos usuários submetem pedidos simultaneamente.

## Decisão
Adotar o banco relacional **MySQL** com evolução de esquema versionada por scripts SQL (`V001` a `V005` em `src/main/resources/db/mysql/`) e transações JDBC explícitas (`setAutoCommit(false)`) com sincronização na validação orçamentária.

## Consequências
- **Positivas:** Garantia de propriedades ACID, integridade referencial por chaves estrangeiras entre requisições, itens, departamentos e intervenções, além de desempenho validado no `RNF-01`.
- **Negativas:** Exige instância MySQL ativa para execução da aplicação completa e dos testes de integração (`*MysqlIT`).
