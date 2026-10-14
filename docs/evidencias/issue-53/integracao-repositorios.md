# Verificação da integração em memória — Issue #53

## Base

SPEC-005, recorte A2 detalhado na Issue #51 e integrado pelo PR #52.

O responsável pelo projeto confirmou a aprovação do recorte antes da implementação.

## Ambiente e execução

- Data: 14/10/2026.
- Windows 10.
- Eclipse Adoptium JDK 25.0.2.
- Maven Wrapper com Maven 3.10.0.
- Compilação: .\mvnw.cmd compile.
- Execução: classes com método main, utilizando target/classes.

Este documento resume as saídas locais compartilhadas pelo responsável pela alteração. Não é uma transcrição integral dos logs.

## Resultados

| Classe | Verificações aprovadas |
|---|---:|
| Main | 10 |
| VerificarValoresMonetarios | 34 |
| VerificarValidacaoOrcamentaria | 25 |
| VerificarConcorrenciaOrcamentaria | 6, em três cenários |
| VerificarRepositoriosMemoria | 44 |
| VerificarConcorrenciaRepositorios | 9, em três cenários |
| VerificarIntegracaoRepositorios | 39 |
| VerificarConcorrenciaIntegracao | 9, em três cenários |
| Total | 176 |

As 39 verificações funcionais de A2 foram executadas primeiro. Depois de adicionar a classe de concorrência da integração, foram executadas suas nove verificações e as 128 anteriores.

A última compilação processou 25 arquivos Java com release 25 e terminou com BUILD SUCCESS.

## Comportamentos exercitados

- Criação por identificadores com departamento e recursos cadastrados.
- Preservação das instâncias do mesmo contexto.
- Total calculado pelo domínio, sem valores financeiros fornecidos pelo chamador.
- Recursos repetidos mantidos em itens separados.
- Chamadas distintas de criação produzindo pedidos distintos.
- Rejeição de entradas inválidas e dependências nulas.
- Recurso inexistente após item válido impedindo o cadastro da requisição.
- Liberação automática e consulta do estado resultante.
- Revisão por reserva e por insuficiência, sem comprometimento.
- Repetição da validação sem novo desconto.
- Preservação de pedidos em revisão.
- Rejeição de recursos não cadastrados acrescentados após o cadastro.
- Rejeição de outra instância de recurso com o mesmo UUID.
- Propagação de falha controlada de consulta antes da mutação.
- Preservação de estado e saldo após essa falha.
- Validação normal bem-sucedida após a falha anterior à mutação.
- Concorrência sobre a mesma requisição, com comprometimento único.
- Concorrência entre pedidos do mesmo departamento, respeitando saldo e reserva.
- Preservação das verificações anteriores.

## Limites

- Armazenamento em memória, em uma única instância da aplicação.
- Entidades compartilhadas por referência no mesmo contexto.
- A falha controlada ocorre antes da mutação; não comprova rollback.
- Não há recuperação dos dados após encerrar o processo.
- Não há idempotência da criação mediante chave do cliente.
- Não há integração MySQL.
- Os limites de espera dos testes concorrentes detectam travamentos; não constituem medição de desempenho.
- As classes com main não são executadas automaticamente por mvn test.
- O critério C6 e a estratégia transacional completa permanecem pendentes em OPEN-005-03.
- Estes resultados não comprovam atendimento integral à SPEC-005.