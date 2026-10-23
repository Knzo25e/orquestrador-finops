# Evidências — Validação financeira transacional JDBC — Issue #67

## Implementação

- PR: https://github.com/Knzo25e/orquestrador-finops/pull/68
- Commit da implementação: 366bf41.
- Merge na main: 67af9e7.
- Data das execuções: 23/10/2026.

Este documento consolida os resultados das execuções locais.
Não é uma transcrição integral do terminal.

## Ambiente

- Windows com PowerShell.
- JDK Eclipse Adoptium 25.0.2.
- JAVA_HOME: C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot.
- Maven executado pelo wrapper do projeto: .\mvnw.cmd.
- MySQL local, serviço MYSQL80.
- Banco exclusivo de testes: finops_test.
- Usuário: finops_test_app.
- URL JDBC: jdbc:mysql://localhost:3306/finops_test?sslMode=REQUIRED.
- Classpath: target/classes;target/dependency/*.

A senha foi solicitada por Read-Host -AsSecureString e disponibilizada
temporariamente em FINOPS_DB_PASSWORD. O bloco PowerShell removeu essa
variável no finally. Nenhuma senha é registrada neste documento.

## Compilação e formatação

Comandos utilizados:

```powershell
.\mvnw.cmd compile
git diff --check
```

Resultados: BUILD SUCCESS e ausência de erros de formatação.
Após preparar os arquivos, git diff --cached --check também passou.

## Regressão anterior

Cada classe foi executada em um processo Java, com conferência
do código de saída antes de prosseguir.

Formato do comando:

```powershell
& $javaExe -cp "target/classes;target/dependency/*" "br.mackenzie.finops.$classe"
```

| Classe, relativa a br.mackenzie.finops | Verificações aprovadas |
|---|---:|
| aplicacao.Main | 10 |
| aplicacao.VerificarValoresMonetarios | 34 |
| aplicacao.VerificarValidacaoOrcamentaria | 25 |
| aplicacao.VerificarConcorrenciaOrcamentaria | 6 |
| aplicacao.VerificarRepositoriosMemoria | 44 |
| aplicacao.VerificarConcorrenciaRepositorios | 9 |
| aplicacao.VerificarIntegracaoRepositorios | 39 |
| aplicacao.VerificarConcorrenciaIntegracao | 9 |
| aplicacao.VerificarReconstituicaoEntidades | 64 |
| infraestrutura.persistencia.VerificarConfiguracaoBanco | 14 |
| infraestrutura.persistencia.VerificarValidacaoCadastroJdbc | 28 |
| infraestrutura.persistencia.VerificarCadastrosJdbc | 14 |
| infraestrutura.persistencia.VerificarRequisicoesJdbc | 35 |
| infraestrutura.persistencia.VerificarCriacaoTransacionalJdbc | 47 |
| **Total da regressão** | **378** |

Nos verificadores de concorrência orçamentária e de repositórios,
a contagem considera as verificações PASSOU, não apenas os três
cenários informados no resumo de cada programa.

## Validação transacional JDBC

Comando utilizado, com o ambiente de teste configurado:

```powershell
& $javaExe -cp "target/classes;target/dependency/*" br.mackenzie.finops.infraestrutura.persistencia.VerificarValidacaoTransacionalJdbc
```

Resultado: todas as 84 verificações passaram.

Cenários observados:

- Liberação e recuperação do saldo e estado por outra conexão.
- Repetição de pedido liberado sem novo desconto.
- Revisão por saldo insuficiente, preservando saldo.
- Revisão por saldo projetado abaixo ou igual à reserva.
- Repetição de pedidos em revisão sem reiniciar o fluxo.
- Rejeição de UUID nulo e de requisição inexistente.
- Falha entre o desconto e a atualização do pedido, com rollback.
- Falha simulada antes de efetivar commit.
- Commit efetivado com resposta simuladamente perdida.
- Reconciliação pelo mesmo UUID sem desconto duplicado.
- Falha de fechamento após commit, preservando o sucesso com aviso.
- Validações concorrentes do mesmo pedido.
- Pedidos distintos do mesmo departamento: uma liberação e uma
  revisão por reserva.
- Preservação dos preços históricos após alteração do catálogo.
- Liberação de pedido gratuito sem alteração do saldo.
- Rejeição de total armazenado inconsistente, sem correção silenciosa.
- Departamento B validado enquanto A permaneceu bloqueado,
  preservando o pedido e o saldo de A.
- Verificações de limpeza dos registros temporários.

Total dos conjuntos de verificações: 378 + 84 = 462 aprovações.

## Teste adicional entre processos Java

Foram gerados três UUIDs novos, reutilizados nas quatro chamadas:

```powershell
$departamentoId = [guid]::NewGuid().ToString()
$recursoId = [guid]::NewGuid().ToString()
$requisicaoId = [guid]::NewGuid().ToString()

$classpath = "target/classes;target/dependency/*"
$classeCriacao = "br.mackenzie.finops.infraestrutura.persistencia.VerificarPersistenciaCriacao"
$classeValidacao = "br.mackenzie.finops.infraestrutura.persistencia.VerificarPersistenciaValidacao"
```

Sequência executada, com verificação de código de saída em cada etapa:

```powershell
& $javaExe -cp $classpath $classeCriacao gravar $departamentoId $recursoId $requisicaoId
& $javaExe -cp $classpath $classeValidacao liberar $departamentoId $recursoId $requisicaoId
& $javaExe -cp $classpath $classeValidacao repetir $departamentoId $recursoId $requisicaoId
& $javaExe -cp $classpath $classeCriacao limpar $departamentoId $recursoId $requisicaoId
```

No bloco de execução, a chamada limpar ficou em um finally para
ser tentada também em caso de falha nas etapas anteriores.

Resultados da execução bem-sucedida:

1. CRIADA: pedido persistido e catálogo alterado após a criação.
2. LIBERADA: saldo de R$ 737,50 e preços históricos preservados.
3. JA_LIBERADA: saldo mantido em R$ 737,50, sem novo desconto.
4. Limpeza dos registros confirmada.

Conexões independentes confirmaram um pedido, dois itens e um único
desconto de R$ 62,50 sobre o saldo inicial de R$ 800,00.

Este teste é adicional às 462 verificações contabilizadas.

Durante a preparação do verificador, a expectativa de getStatus()
foi corrigida de LIBERADA para Provisionamento Liberado.
LIBERADA e JA_LIBERADA permanecem como resultados da operação.
A correção foi no verificador, sem alteração da regra financeira.

## Limites das evidências

- As falhas de commit e fechamento foram simuladas; não foram
  realizados ensaios de quedas reais de rede.
- A concorrência JDBC entre pedidos distintos demonstrou revisão
  por reserva. Um cenário JDBC separado de disputa por saldo
  insuficiente não foi demonstrado nessas execuções.
- O limite de dez segundos do teste de isolamento é um limite
  de espera do teste, não uma medição do requisito de desempenho.
- Estas verificações não comprovam o RNF-01 com MySQL.
- Os resultados são de execuções locais; não constituem evidência
  de execução desses verificadores no GitHub Actions.
- Aprovação manual, alteração de orçamento, API, autenticação,
  portal e provisionamento real permanecem fora deste recorte.