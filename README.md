# Orquestrador FinOps (Cloud Provisioning)

Projeto acadêmico desenvolvido para a disciplina de Modelagem de Sistemas da Universidade Presbiteriana Mackenzie.

O sistema avalia requisições de infraestrutura considerando o saldo e a reserva orçamentária do departamento. A aprovação automática exige saldo suficiente e saldo resultante estritamente acima da reserva. Os demais pedidos são encaminhados para revisão, com identificação do motivo.

## Situação atual

- Domínio e casos de uso implementados em Java.
- Valores monetários representados por BigDecimal.
- Reserva fixa de 10% do orçamento mensal, com arredondamento HALF_UP para duas casas decimais.
- Coordenação concorrente em memória, desde que as requisições compartilhem o mesmo objeto Departamento.
- Repositórios em memória (A1) e casos de uso integrados de criação e validação por identificadores (A2), verificados localmente.
- Reconstituição das entidades integrada pelo PR #58.
- Conexão JDBC e esquema MySQL integrados pelo PR #60.
- Cadastro e consulta JDBC de departamentos e recursos integrados pelo PR #62.
- Armazenamento JDBC de requisições e itens implementado e verificado localmente na Issue #63, incluindo recuperação em outro processo Java e preservação dos preços históricos; revisão e integração pendentes.
- Os casos de uso A2 continuam utilizando armazenamento em memória.
- Validação financeira transacional com MySQL, criação idempotente e reconciliação de commit permanecem pendentes.
- Fluxo manual, configuração editável da reserva e integração com Spring Boot permanecem pendentes.
- Não há provisionamento real na AWS.

## Requisitos para desenvolvimento

- JDK 25.
- Acesso à internet na primeira execução para baixar Maven e plugins.
- Maven Wrapper incluído no repositório; não é necessário instalar Maven globalmente.

Versões verificadas na Issue #43:
- Eclipse Adoptium JDK 25.0.2.
- Maven 3.10.0.
- Maven Wrapper 3.3.4.

As versões dos plugins estão fixadas no pom.xml. A compilação utiliza release 25 e UTF-8.

## Compilar no Windows

Abra o PowerShell na raiz do projeto, onde ficam pom.xml e mvnw.cmd.

Configure JAVA_HOME para a pasta do JDK 25 instalado no computador. O caminho abaixo é um exemplo e deve ser adaptado:

```powershell
$env:JAVA_HOME = "C:\caminho\para\jdk-25"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

.\mvnw.cmd -version
.\mvnw.cmd clean compile
```

Confirme que a saída do Wrapper informa Java 25.

Essas variáveis valem para a sessão atual do terminal. A pasta target contém os arquivos gerados e é ignorada pelo Git.

## Executar as verificações

Após compilar, execute na raiz do projeto e no mesmo terminal:

```powershell
& {
    $ErrorActionPreference = "Stop"

    if ([string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        throw "Configure JAVA_HOME com o caminho do JDK 25 neste terminal."
    }

    $javaExe = Join-Path $env:JAVA_HOME "bin\java.exe"

    if (-not (Test-Path -LiteralPath $javaExe)) {
        throw "Java nao encontrado em: $javaExe"
    }

    $classes = @(
        "aplicacao.Main",
        "aplicacao.VerificarValoresMonetarios",
        "aplicacao.VerificarValidacaoOrcamentaria",
        "aplicacao.VerificarConcorrenciaOrcamentaria",
        "aplicacao.VerificarRepositoriosMemoria",
        "aplicacao.VerificarConcorrenciaRepositorios",
        "aplicacao.VerificarIntegracaoRepositorios",
        "aplicacao.VerificarConcorrenciaIntegracao",
        "aplicacao.VerificarReconstituicaoEntidades",
        "infraestrutura.persistencia.VerificarConfiguracaoBanco",
        "infraestrutura.persistencia.VerificarValidacaoCadastroJdbc"
    )

    foreach ($classe in $classes) {
        Write-Host "`n=== $classe ==="

        & $javaExe -cp "target/classes" "br.mackenzie.finops.$classe"

        if ($LASTEXITCODE -ne 0) {
            throw "A verificacao $classe falhou."
        }
    }
}
```

São 282 verificações que não dependem de conexão com MySQL:
- Main: 10.
- VerificarValoresMonetarios: 34.
- VerificarValidacaoOrcamentaria: 25.
- VerificarConcorrenciaOrcamentaria: seis verificações em três cenários.
- VerificarRepositoriosMemoria: 44.
- VerificarConcorrenciaRepositorios: nove verificações em três cenários.
- VerificarIntegracaoRepositorios: 39.
- VerificarConcorrenciaIntegracao: nove verificações em três cenários.
- VerificarReconstituicaoEntidades: 64.
- VerificarConfiguracaoBanco: 14.
- VerificarValidacaoCadastroJdbc: 28.

Evidências da integração e limites: [Issue #53](docs/evidencias/issue-53/integracao-repositorios.md).

Evidências dos repositórios e limites: [Issue #49](docs/evidencias/issue-49/repositorios-memoria.md).

Evidências da reconstituição e limites: [Issue #57](docs/evidencias/issue-57/reconstituicao-entidades.md).

Evidências da conexão JDBC, esquema e limites: [Issue #59](docs/evidencias/issue-59/jdbc-esquema-mysql.md).

Evidências dos cadastros JDBC: [Issue #61](docs/evidencias/issue-61/cadastros-jdbc.md).

Na Issue #63, foram aprovadas 282 verificações sem banco e 49 com MySQL,
totalizando 331:

- VerificarCadastrosJdbc: 14 verificações com banco.
- VerificarRequisicoesJdbc: 35 verificações com banco.

Um cenário adicional com VerificarPersistenciaRequisicoes confirmou
gravação por commit, recuperação em outro processo Java e limpeza
dos registros, preservando preços históricos e saldo.

O cenário de cadastros entre processos da Issue #61 permanece
documentado em suas próprias evidências.

Os testes JDBC utilizam exclusivamente finops_test e exigem as
dependências de execução no classpath, além das variáveis
FINOPS_DB_URL, FINOPS_DB_USER e FINOPS_DB_PASSWORD.

Evidências das requisições e itens:
[Issue #63](docs/evidencias/issue-63/requisicoes-itens-jdbc.md).

A verificação de conexão e os testes manuais de SQL não estão
incluídos nas 331 verificações.

Essas classes possuem método main e não são executadas automaticamente por mvn test.

## Medição de desempenho

O programa de medição pode ser executado após a compilação:

```powershell
& "$env:JAVA_HOME\bin\java.exe" -cp "target/classes" "br.mackenzie.finops.aplicacao.MedirDesempenhoOrcamentario"
```

O protocolo e os limites da medição estão na SPEC-003. As três execuções da Issue #41 estão registradas em docs/evidencias/issue-41.

Essas evidências se aplicam ao ambiente e às cargas documentados; não comprovam desempenho com banco de dados, API, rede ou múltiplas instâncias.

## Problema de limpeza no Windows

Na preparação da Issue #43, clean falhou com AccessDeniedException em uma pasta de target marcada como ReadOnly.

Após remover esse atributo apenas de target e de seu conteúdo, clean compile terminou com sucesso:

```powershell
attrib -R "target"
attrib -R "target\*" /S /D
.\mvnw.cmd clean compile
```

Esse procedimento se aplica ao problema observado. Outras falhas de acesso exigem diagnóstico próprio.

## Documentação

- [Especificação do sistema](docs/spec.md)
- [Plano técnico](docs/plan.md)
- [Tarefas](docs/tasks.md)
- [Arquitetura e ADRs](docs/arquitetura.md)
- [Mapa de Specs](docs/mapa-de-specs.md)
- [SPEC-003 — Validação orçamentária](docs/casos-de-uso/SPEC-003-validar-orcamento.md)