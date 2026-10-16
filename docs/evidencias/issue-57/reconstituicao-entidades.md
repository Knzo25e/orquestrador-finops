# Verificação da reconstituição das entidades — Issue #57

## Base

SPEC-005, etapa B detalhada na Issue #55 e integrada pelo PR #56,
com contratos de reconstituição registrados na Issue #57.

## Ambiente e execução

- Data: 16/10/2026.
- Windows.
- Execução das verificações com Eclipse Adoptium JDK 25.0.2.
- Compilação pelo Maven Wrapper: .\mvnw.cmd compile.
- Compilação de 26 arquivos Java com release 25 concluída com BUILD SUCCESS.
- Verificações executadas explicitamente pelas classes com método main.

Este documento resume as saídas locais compartilhadas pelo
responsável pela alteração. Não é uma transcrição integral dos logs.

A primeira tentativa de executar a nova verificação falhou porque
JAVA_HOME estava vazio no terminal. Após configurar a variável,
a classe foi executada com sucesso.

## Resultados

| Classe | Verificações aprovadas |
|---|---:|
| Main | 10 |
| VerificarValoresMonetarios | 34 |
| VerificarValidacaoOrcamentaria | 25 |
| VerificarConcorrenciaOrcamentaria | 6 |
| VerificarRepositoriosMemoria | 44 |
| VerificarConcorrenciaRepositorios | 9 |
| VerificarIntegracaoRepositorios | 39 |
| VerificarConcorrenciaIntegracao | 9 |
| VerificarReconstituicaoEntidades | 64 |
| Total | 240 |

As 64 verificações de reconstituição foram executadas primeiro.
Em seguida, foram executadas as 176 verificações anteriores.

## Comportamentos exercitados

- Recuperação de departamento com identidade, teto e saldo preservados.
- Rejeição de saldo superior ao teto e valores monetários inválidos.
- Preservação do preço histórico dos itens.
- Criação normal de itens utilizando o preço atual do catálogo.
- Rejeição de subtotal e total inconsistentes.
- Criação de requisição com UUID explícito.
- Recuperação de requisições iniciais, liberadas e em revisão.
- Preservação da ordem dos itens e proteção da coleção.
- Recuperação sem alteração do saldo.
- Repetição de validação de pedido liberado sem novo comprometimento.
- Preservação dos motivos históricos de revisão.
- Rejeição de estados transitórios, desconhecidos e motivos incompatíveis.
- Preservação dos comportamentos anteriormente verificados.

## Limites

- As verificações não utilizam MySQL.
- Não comprovam persistência entre processos ou após reinício.
- Não comprovam transações, rollback ou concorrência no banco.
- Criar uma entidade com UUID explícito não implementa, isoladamente,
  idempotência do armazenamento.
- Os limites de DECIMAL(19,2) permanecem para a camada de persistência.
- As classes com main não são executadas automaticamente por mvn test.
- A etapa B da SPEC-005 permanece parcialmente preparada,
  sem integração JDBC.