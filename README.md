# Orquestrador FinOps (Cloud Provisioning)

Projeto acadêmico desenvolvido para a disciplina de Modelagem de Sistemas da Universidade Presbiteriana Mackenzie.

O sistema avalia requisições de infraestrutura considerando o saldo e a reserva orçamentária do departamento. A aprovação automática exige saldo suficiente e saldo resultante estritamente acima da reserva. Os demais pedidos são encaminhados para revisão, com identificação do motivo.

## Situação atual

- Domínio e casos de uso implementados em Java.
- Valores monetários representados por BigDecimal.
- Reserva fixa de 10% do orçamento mensal, com arredondamento HALF_UP para duas casas decimais.
- Coordenação concorrente em uma única instância, desde que as requisições compartilhem o mesmo objeto Departamento.
- Fluxo manual, configuração editável da reserva, persistência e integração com Spring Boot ainda pendentes.
- Não há provisionamento real na AWS.
- Repositórios em memória com cadastro e consulta (A1) e casos de uso integrados de criação e validação por identificadores (A2), verificados localmente. Persistência MySQL permanece pendente.

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
    $classes = @(
        "Main",
        "VerificarValoresMonetarios",
        "VerificarValidacaoOrcamentaria",
        "VerificarConcorrenciaOrcamentaria",
        "VerificarRepositoriosMemoria",
        "VerificarConcorrenciaRepositorios",
        "VerificarIntegracaoRepositorios",
        "VerificarConcorrenciaIntegracao"
    )

    foreach ($classe in $classes) {
        Write-Host "`n=== $classe ==="
        & "$env:JAVA_HOME\bin\java.exe" -cp "target/classes" "br.mackenzie.finops.aplicacao.$classe"

        if ($LASTEXITCODE -ne 0) {
            throw "A verificação $classe falhou."
        }
    }
}
```

São 176 verificações:
- Main: 10.
- VerificarValoresMonetarios: 34.
- VerificarValidacaoOrcamentaria: 25.
- VerificarConcorrenciaOrcamentaria: seis verificações em três cenários.
- VerificarRepositoriosMemoria: 44.
- VerificarConcorrenciaRepositorios: nove verificações em três cenários.
- VerificarIntegracaoRepositorios: 39.
- VerificarConcorrenciaIntegracao: nove verificações em três cenários.

Evidências da integração e limites: [Issue #53](docs/evidencias/issue-53/integracao-repositorios.md).

Evidências dos repositórios e limites: [Issue #49](docs/evidencias/issue-49/repositorios-memoria.md).

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