# Verificação dos repositórios em memória — Issue #49

## Base

SPEC-005, recorte A1 detalhado na Issue #47 e integrado pelo PR #48.

A aprovação do recorte foi confirmada pelo responsável pelo projeto antes da implementação.

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
| VerificarRepositoriosMemoria | 44 |
| VerificarConcorrenciaRepositorios | 9, em três cenários |
| Main | 10 |
| VerificarValoresMonetarios | 34 |
| VerificarValidacaoOrcamentaria | 25 |
| VerificarConcorrenciaOrcamentaria | 6, em três cenários |
| Total | 128 |

As 44 verificações funcionais dos repositórios foram executadas primeiro. Após adicionar a classe de concorrência, foram executadas suas nove verificações e as 75 anteriores.

A última compilação processou 20 arquivos Java com release 25 e terminou com BUILD SUCCESS.

## Comportamentos exercitados

- Cadastro e recuperação preservando identidade.
- Ausência explícita nas consultas.
- Rejeição de entradas nulas e identificadores duplicados.
- Preservação dos registros após rejeição de duplicatas.
- Listagem por departamento.
- Proteção contra alteração da coleção retornada.
- Preservação dos elementos de uma lista obtida antes de novos cadastros.
- Rejeição de vínculos inexistentes ou representados por outras instâncias.
- Rejeição de requisições vazias, liberadas ou em revisão no cadastro.
- Ausência de roteamento e desconto durante cadastro e consulta.
- Visibilidade das alterações permitidas pelo domínio.
- Independência entre contextos.
- Uma inclusão e uma rejeição em cadastros concorrentes do mesmo UUID.
- Preservação das verificações existentes.

## Limites

- Os testes utilizam armazenamento em memória, dentro de um processo.
- As entidades são compartilhadas por referência.
- Não há rollback automático ou persistência após reinício.
- Os casos de uso existentes ainda não foram integrados aos repositórios.
- A verificação dos vínculos no cadastro não impede alterações posteriores diretamente nas entidades.
- Os limites de espera dos testes concorrentes detectam travamentos; não constituem medição de desempenho.
- As classes não são executadas automaticamente por mvn test.
- Estes resultados não comprovam conclusão de A2, da etapa MySQL ou da SPEC-005 integral.