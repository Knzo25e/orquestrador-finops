# ADR-002: Implementação em Java Puro sem Frameworks Web ou ORM

- **Status:** Aceito
- **Data:** 2026-10

## Contexto
Frameworks como Spring Boot e Hibernate automatizam injeção de dependência, roteamento e transações, mas ocultam o fluxo real de execução e o controle fino de transações, dificultando a rastreabilidade direta entre a modelagem da disciplina e o código executado.

## Decisão
Desenvolver o backend utilizando **Java puro** gerenciado com Maven, utilizando o servidor HTTP embutido da própria JDK (`FinopsServidor`), composição manual de dependências na classe `ComposicaoFinops` e acesso relacional via **JDBC puro**.

## Consequências
- **Positivas:** Transparência arquitetural total (zero "mágica" de anotações), startup leve, controle explícito de `commit`/`rollback` e uso nativo de `BigDecimal` para precisão monetária.
- **Negativas:** Necessidade de escrever manualmente parsers HTTP (`LeitorPedidoCriacaoHttp`, `LeitorAjusteTetoHttp`), tratamento de rotas e mapeamento de `ResultSet` no JDBC.
