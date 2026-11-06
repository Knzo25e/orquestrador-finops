# Revisão do recorte acadêmico e roteiro local

- Acompanhamento [Issue #93](https://github.com/Knzo25e/orquestrador-finops/issues/93).
- Base verificada `44d026f`, em 05/11/2026.
- Fonte acadêmica *Projeto Final – Modelagem de Software*, semestre 2026-2, turma 04I, seções 6 a 11. O prazo de 26/11/2026 e a meta interna proposta de 21/11/2026 são registros do projeto em [AGENTS.md](../../AGENTS.md).

## Rastreabilidade do enunciado

| Seção | Exigência | Evidência e trabalho restante no FinOps |
|---|---|---|
| 6.1 | Pelo menos dois perfis com permissões, responsabilidades ou interesses distintos | Desenvolvedor e Arquiteto Cloud na [SPEC-006](../casos-de-uso/SPEC-006-perfis-acesso.md). Testes com identidade controlada exercitam a criação restrita ao Desenvolvedor e a diferença entre consultas por autoria e departamento ou entre departamentos. Falta a composição autenticada da demonstração. |
| 6.2 | Persistência e continuidade entre sessões | [SPEC-005](../casos-de-uso/SPEC-005-persistir-requisicoes.md). O roteiro recupera o pedido em novos processos Java, incluindo estado, valores e saldo. |
| 6.3 | Um fluxo principal com regras, validações, estados ou decisões além do cadastro | RF-01 e RF-02. O pedido é criado em `INICIAL` e a validação decide entre liberação e revisão por saldo ou reserva. A criação HTTP e a validação JDBC têm evidências separadas. Falta ligar a submissão autorizada ao fluxo demonstrável. |
| 6.4 | Regras de negócio explícitas na especificação | RB-01 a RB-05 em [spec.md](../spec.md). Os testes de reserva, saldo e débito único exercitam RB-02 e RB-05. Os cenários aprovados de RF-03 a RF-05 dependem do detalhamento das Specs 007 a 009. |
| 6.5 | Uso estruturado de IA ou LLM quando integrado ao produto | O sistema atual aplica as regras no código. O uso de Codex no desenvolvimento pertence ao fluxo de trabalho da disciplina. |

O enunciado consultado deixa a tecnologia em aberto e não prescreve login institucional, portal visual completo ou AWS real. A demonstração segue a diretriz de contas próprias e sessão no servidor de OPEN-004-02. O detalhamento do contrato de sessão e da coordenação transacional está em revisão. O ADR-004 posterga a AWS real. [Arquitetura e ADRs](../arquitetura.md) · [SPEC-004](../casos-de-uso/SPEC-004-interface-aplicacao.md)

As seções 7 a 11 também pedem README com execução, requisitos EARS, modelo e casos de uso, plano e tarefas, 3 a 5 ADRs, revisão técnica multidimensional, segurança e SSDLC, estratégia de testes com evidências e rastreabilidade individual no GitHub. Para a entrega, conferir o acesso do professor como colaborador, o quadro do GitHub Project, os responsáveis e prioridades das Issues, a revisão por outro integrante antes do merge e as evidências individuais.

## Roteiro local de verificação

Em PowerShell na raiz, configure `JAVA_HOME` com JDK 25. Para a parte JDBC, use MySQL isolado com banco `finops_test`, conta `finops_test_app@localhost` com permissões de leitura e escrita e os scripts [V001 e V002](../../src/main/resources/db/mysql) aplicados nessa ordem por um administrador. Configure `FINOPS_DB_URL`, `FINOPS_DB_USER` e `FINOPS_DB_PASSWORD` na sessão. Use TLS, por exemplo `jdbc:mysql://localhost:<porta>/finops_test?sslMode=REQUIRED`, e mantenha a senha fora do histórico e do repositório.

```powershell
.\mvnw.cmd -B verify
if ($LASTEXITCODE -ne 0) { throw 'Falha no build ou nos testes JUnit.' }
.\mvnw.cmd -B '-DincludeScope=runtime' dependency:copy-dependencies
if ($LASTEXITCODE -ne 0) { throw 'Falha ao preparar o classpath JDBC.' }
```

O build executa os testes JUnit, incluindo os adaptadores HTTP em ambiente controlado. Os verificadores abaixo criam dados próprios no MySQL isolado.

```powershell
$java = Join-Path $env:JAVA_HOME 'bin\java.exe'
$classpath = 'target/classes;target/dependency/*'
$pacote = 'br.mackenzie.finops.infraestrutura.persistencia'
$ids = 1..3 | ForEach-Object { [guid]::NewGuid().ToString() }

function Executar-Verificacao($classe, $argumentos) {
    & $java -cp $classpath "$pacote.$classe" @argumentos
    if ($LASTEXITCODE -ne 0) { throw "$classe falhou (código $LASTEXITCODE)." }
}

Executar-Verificacao VerificarConexaoBanco @()
Executar-Verificacao VerificarConsultasAutorizadasJdbc @()
Executar-Verificacao VerificarValidacaoTransacionalJdbc @()

try {
    Executar-Verificacao VerificarPersistenciaCriacao (@('gravar') + $ids)
    Executar-Verificacao VerificarPersistenciaCriacao (@('consultar') + $ids)
    Executar-Verificacao VerificarPersistenciaValidacao (@('liberar') + $ids)
    Executar-Verificacao VerificarPersistenciaValidacao (@('repetir') + $ids)
} finally {
    Executar-Verificacao VerificarPersistenciaCriacao (@('limpar') + $ids)
}
```

Para incluir a integração do POST com MySQL, use `.\mvnw.cmd -B "-Dtest=*Test,CriacaoHttpMysqlIT" test`. Com as migrações já aplicadas em `finops_test`, o teste cria um departamento e um recurso próprios e os remove junto com seus pedidos ao encerrar. O cenário combina criação HTTP e validação pela entrada interna vigente.

## Resultados e revisão técnica

Em Windows, com JDK 25.0.2, Maven Wrapper 3.10.0 e MySQL 8.0.44 isolado:

- O build padrão aprovou 33 testes JUnit. A execução explícita com `CriacaoHttpMysqlIT` aprovou 35, incluindo dois de integração HTTP com MySQL.
- Os verificadores JDBC confirmaram TLS, 13 verificações de consulta autorizada e 84 de validação financeira, incluindo revisão por saldo ou reserva, rollback e concorrência.
- O cenário em cinco processos retornou `CRIADA → JA_EXISTENTE → LIBERADA → JA_LIBERADA` e confirmou a limpeza. A repetição preservou os preços históricos. A liberação debitou R$ 62,50 uma vez e as duas validações confirmaram saldo de R$ 737,50.

Na regressão complementar da Issue #93, passaram 35 testes com `CriacaoHttpMysqlIT` e os dois testes desse IT em uma execução adicional. O POST preservou R$ 1.000,00. A validação interna debitou R$ 25,00 uma vez e o POST repetido retornou `PROVISIONAMENTO_LIBERADO`, com saldo de R$ 975,00. Uma conexão independente confirmou que só permaneceram os registros preexistentes.

| Dimensão | Achado e encaminhamento |
|---|---|
| Arquitetura | Os testes compõem o servidor HTTP. O roteiro combina verificadores existentes. Falta uma execução integrada com submissão autorizada. |
| Desempenho | A medição anterior cobre memória. Medir RNF-01 com MySQL e cobrir a disputa JDBC por insuficiência de saldo, além da disputa por reserva já exercitada. |
| Segurança e SSDLC | Os testes exercitam a política com identidade controlada. Revisar ameaças de troca de vínculo, acesso por UUID e tratamento de falhas antes de expor a submissão. |
| Observabilidade | Os relatórios Surefire e a saída dos verificadores sustentam a evidência local. Definir como registrar falhas, resultados incertos e intervenções sem expor dados sensíveis. A auditoria de intervenções pertence a RF-05. |

## Próxima implementação proposta

Manter catálogo e criação HTTP na [Issue #81](https://github.com/Knzo25e/orquestrador-finops/issues/81). Preparar a composição local da aplicação e uma entrada autorizada de submissão em tarefa própria. O caso de uso `ValidarRequisicaoPersistenteUseCase` recebe apenas o UUID do pedido e deve permanecer interno até existir uma entrada com autorização.

- **Contrato a aprovar.** O Desenvolvedor ativo solicita validação de pedido próprio do departamento atual. O servidor deriva a identidade, consulta o vínculo vigente e executa a decisão automática. A entrada recebe o identificador do pedido. O domínio determina o resultado a partir de saldo e reserva. Definir o adaptador, a representação de resultados e o tratamento de inexistência, acesso negado e confirmação desconhecida.
- **Fronteira transacional a revisar com o Astra.** Preservar a ordem departamento → requisição. Avaliar a leitura protegida dos atributos de autorização na mesma transação da validação, considerando a ordem de bloqueio da identidade e os escritores concorrentes. Reutilizar a operação financeira JDBC e o domínio. Uma consulta de autorização independente da transação deixa uma janela para mudança de vínculo.
- **Aceite proposto.** Demonstrar criação sem débito, submissão válida com liberação ou revisão, negativa para outro autor ou departamento sem alteração de dados e repetição com o mesmo UUID sem novo débito. Cobrir disputa concorrente, mudança de vínculo, rollback e confirmação desconhecida.
- **Recorte aprovado.** A demonstração usa contas próprias e sessão por cookie e inclui revisão técnica, autorização de uso da reserva, ajuste de teto e consulta da auditoria. O plano distingue essas escolhas dos contratos ainda em revisão. [Plano técnico do MVP](../plan.md#recorte-acadêmico-e-execução-local--issue-93)
