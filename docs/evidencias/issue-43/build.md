# Evidência de build — Issue #43

## Ambiente

- Data: 13/10/2026.
- Windows 10 Pro.
- Eclipse Adoptium JDK 25.0.2.
- Maven 3.10.0.
- Maven Wrapper 3.3.4.
- Branch: build/43-java25-maven-wrapper.

Este registro resume as saídas locais compartilhadas pelo responsável pela alteração; não é uma transcrição integral dos logs.

## Resultados

- O comando .\mvnw.cmd -version confirmou Maven 3.10.0 e Java 25.0.2 após configurar JAVA_HOME na sessão.
- O comando .\mvnw.cmd compile compilou 12 arquivos com release 25 e terminou com BUILD SUCCESS.
- As quatro classes de verificação foram executadas explicitamente com Java, usando target/classes.
- Main: 10 verificações aprovadas.
- VerificarValoresMonetarios: 34 verificações aprovadas.
- VerificarValidacaoOrcamentaria: 25 verificações aprovadas.
- VerificarConcorrenciaOrcamentaria: seis verificações aprovadas, distribuídas em três cenários.
- Total: 75 verificações aprovadas.

## Ocorrência na limpeza

Inicialmente, clean falhou com AccessDeniedException ao excluir uma pasta dentro de target.

A pasta apresentava os atributos ReadOnly, Directory, Archive e ReparsePoint. Após remover ReadOnly somente de target e seu conteúdo, o comando .\mvnw.cmd clean compile terminou com BUILD SUCCESS, recompilando os 12 arquivos com release 25.

As 75 verificações foram executadas antes dessa última limpeza e recompilação. Não houve alteração do código-fonte entre essas etapas.

## Limites

- As verificações são classes com método main; não foram executadas pelo Maven Surefire.
- Esta evidência não valida Spring Boot, MySQL ou persistência.
- A medição de desempenho da Issue #41 não foi repetida nesta preparação.
- O Wrapper foi executado no Windows; o script para Linux/macOS não foi exercitado.