# Auditoria final e roteiro de entrega do MVP

Verificação de 11/11/2026 sobre a main `6f667b4ac8e12ecb7cc3f441ac253c8f2592e115`, integrada pelo [PR #110](https://github.com/Knzo25e/orquestrador-finops/pull/110). Escopo definido no [recorte de entrega](../plan.md#recorte-aprovado-para-26112026).

## Requisitos e Specs

| Requisito | Resultado no recorte aprovado | Implementação e evidência |
|---|---|---|
| RF-01 · Specs 001, 002, 004, 005 e 006 | Atendido | Catálogo autorizado, custo calculado no servidor e criação persistente em `INICIAL` sem débito. `CatalogoController`, `RequisicaoController`, `CriacaoHttpMysqlIT` e `SessaoMysqlIT`. O ensaio do JAR consultou o catálogo nos dois perfis e confirmou `INICIAL` no detalhe dos três pedidos. |
| RF-02 · Specs 003, 005 e 006 e contratos do PR #98 | Atendido | O POST separado revalida a identidade e aplica custo histórico e reserva vigente. `ValidacaoRequisicaoJdbc`, `ValidacaoAutorizadaMysqlIT` e `SessaoMysqlIT` cobrem liberação automática com débito único, motivo `RESERVA` no limite e `SALDO_INSUFICIENTE`, sem débito em revisão. A suíte também cobre detalhe e listagem paginada. |
| RF-03 · Specs 006 e 009 | Atendido | Aprovação ou rejeição técnica justificada pelo Arquiteto ativo. `IntervencaoRequisicaoJdbc` e `FluxoManualMysqlIT` cobrem transições, aprovação sem débito e disputa entre decisões. O JAR reproduziu aprovação e rejeição e confirmou o saldo preservado. |
| RF-04 · Specs 007, 008 e 009 | Atendido | `AjusteOrcamentarioJdbc` e `IntervencaoRequisicaoJdbc` preservam compromissos e revalidam saldo, teto e reserva. `AjusteOrcamentarioMysqlIT` e `FluxoManualMysqlIT` exercitam limites, recálculo, negativa, concorrência e débito único. O JAR reproduziu os dois caminhos financeiros, com e sem reserva. |
| RF-05 · Specs 007, 008 e 009 | Atendido para os privilégios da aplicação | `IntervencaoAuditadaJdbc` grava evento e recibo na transação da decisão, com ator, instante UTC, alvo, justificativa e dados pertinentes. `AuditoriaMysqlIT` e `SessaoMysqlIT` exercitam filtragem, imutabilidade perante a conta de execução, repetição, colisão e rollback. No JAR, a repetição devolveu a fotografia original e o histórico permaneceu em dez registros. Administradores do MySQL podem alterar permissões e dados. |
| RNF-01 · Spec 003 | Parcialmente comprovado | A sondagem de 06/11 teve 192 POSTs em conexão estabelecida, com um e oito clientes, um departamento e um item por pedido. Máximo de 119,85 ms naquele ambiente. O ensaio representativo da main segue pendente. |
| RNF-02 · Specs 004, 005 e 006 | Atendido | `PoliticaAutorizacao` e os adaptadores JDBC impõem autoria e departamento atual com identidade do servidor. `SessaoMysqlIT`, `CriacaoIdentidadeMysqlIT` e `ValidacaoAutorizadaMysqlIT` exercitam isolamento, troca de vínculo, desativação, coordenação por tentativa e paginação após o filtro de permissão. O Arquiteto tem o escopo transversal previsto, incluindo registros legados. |

As evidências acima se referem ao recorte do MVP e aos [requisitos e regras](../spec.md), às [Specs individuais](../casos-de-uso) e aos [contratos aprovados de submissão e consulta](https://github.com/Knzo25e/orquestrador-finops/pull/98). O [mapa de Specs](../mapa-de-specs.md) ainda mostra a Spec 004 sem endpoints, a 006 sem autenticação real e a 009 com integração pendente. Para o estado do executável, vale a verificação da main identificada acima. A SPEC-010 (stub de infraestrutura) permanece pendente fora do recorte da demonstração. O estado `PROVISIONAMENTO_LIBERADO` comprova a autorização financeira. [ADR-004](../arquitetura.md)

## Reprodução em cópia limpa

- Worktree criado no SHA auditado, JDK 25.0.2, Maven Wrapper 3.10.0 e MySQL/InnoDB 8.0.44, com instância descartável e schemas separados de teste e demonstração. V001–V005 aplicadas em ordem. O ambiente já tinha JDK, MySQL e cache Maven. A instalação em outra máquina fica para o ensaio de entrega.
- O comando abaixo gerou o JAR e executou **97 testes, zero falhas, zero erros e zero ignorados**. `MigracaoEstadosMysqlIT` aplicou V005 sobre V004 com dados anteriores e verificou autoria, itens, preços, estados e motivos. O schema de teste voltou a zero registros nas seis tabelas verificadas. Os 164 checks JDBC, 123 checks de domínio e três cenários concorrentes dos programas avulsos têm evidência na mesma árvore integrada e são contagens da rodada anterior. [Evidência da integração](../evidencias/issue-109/integracao-main.md)
- Cadastro real dos perfis pelo comando interativo `cadastrar-demonstracao`. JAR iniciado em IPv4 local, com TLS confiável para o cliente e conta MySQL restrita. A conta tinha `SELECT/INSERT` no histórico. A regressão exercitou a recusa de alteração e exclusão com essa classe de permissões.
- Ao término do fluxo manual, a consulta SQL mostrou teto de **R$ 2.500,00**, saldo de **R$ 400,00**, dois pedidos liberados, um rejeitado e **dez eventos**. Depois de reiniciar o JAR, uma nova sessão recuperou os três estados finais e os dez eventos.
- A perda de confirmação do commit foi exercitada com falhas injetadas antes e depois da efetivação, incluindo a reconciliação explícita. O teste de corte real de transporte durante um commit em MySQL remoto segue pendente. A ausência momentânea de um resultado em consulta não demonstra rollback.

```powershell
.mvnw.cmd -B "-Dtest=*Test,CriacaoHttpMysqlIT,SessaoMysqlIT,CriacaoIdentidadeMysqlIT,ValidacaoAutorizadaMysqlIT,AuditoriaMysqlIT,AjusteOrcamentarioMysqlIT,FluxoManualMysqlIT,MigracaoEstadosMysqlIT" verify
```

## Demonstração reproduzível

Antes da apresentação, siga o [guia de execução local](../executar-mvp-local.md) para preparar V001–V005, o certificado confiável, as variáveis do banco e a conta MySQL de execução. Use um banco dedicado, com departamento `10000000-0000-4000-8000-000000000001`, teto e saldo de `1000.00`, e recurso `10000000-0000-4000-8000-000000000002`, com custo mensal unitário de `12.50`. Prepare dois perfis de navegador independentes. Faça o cadastro uma vez com a conta de preparação e troque as variáveis para a conta restrita antes de iniciar o servidor.

```powershell
.mvnw.cmd -B verify
$jar = 'target/orquestrador-finops-0.1.0-SNAPSHOT.jar'
& "$env:JAVA_HOMEinjava.exe" -jar $jar cadastrar-demonstracao 10000000-0000-4000-8000-000000000001
& "$env:JAVA_HOMEinjava.exe" -jar $jar
```

Abra [https://127.0.0.1:8443/login](https://127.0.0.1:8443/login). Entre como `desenvolvedor` em um perfil e `arquiteto` no outro. Cole o bloco a seguir no console de cada sessão, na mesma origem. Execute as chamadas pela ordem da tabela. Transfira somente os UUIDs dos pedidos entre as sessões e mantenha os objetos da aprovação e da autorização para repetir o comando com o mesmo corpo. A tabela usa as quantidades 72, 96 e 80 sobre o saldo inicial intacto.

```javascript
const dept = "10000000-0000-4000-8000-000000000001";
const recurso = "10000000-0000-4000-8000-000000000002";
const req = "/api/v1/requisicoes";
const orcamento = `/api/v1/departamentos/${dept}`;
const { cabecalho, token } = await fetch("/api/v1/csrf").then(r => r.json());
const get = async caminho => {
  const r = await fetch(caminho);
  return { status: r.status, corpo: await r.json() };
};
const post = async (caminho, corpo) => {
  const headers = { [cabecalho]: token };
  if (corpo !== undefined) headers["Content-Type"] = "application/json";
  const r = await fetch(caminho, { method: "POST", headers, body: JSON.stringify(corpo) });
  return { status: r.status, corpo: await r.json() };
};
const criar = quantidade => post(req, {
  requisicaoId: crypto.randomUUID(), itens: [{ recursoId: recurso, quantidade }]
});
const comando = (decisao, justificativa) => ({
  operacaoId: crypto.randomUUID(), decisao, justificativa
});
const revisar = (id, corpo) => post(`${req}/${id}/revisao-tecnica`, corpo);
const liberar = (id, corpo) => post(`${req}/${id}/liberacao-financeira`, corpo);
```

| Perfil e ação | Chamada e resultado a mostrar |
|---|---|
| Desenvolvedor consulta catálogo e cria | `await get("/api/v1/catalogo/recursos")`. Execute `await criar(72)`, `await criar(96)` e `await criar(80)`, cada um com HTTP 201. Copie os UUIDs para `A`, `B` e `C`. `await get(req + "/" + A)` mostra `INICIAL` e total de R$ 900,00. Os três custos históricos são R$ 900,00, R$ 1.200,00 e R$ 1.000,00. |
| Desenvolvedor submete | `await post(req + "/" + id + "/validacao")` para cada UUID. Resultado HTTP 200, com A e C em revisão por `RESERVA` e B por `SALDO_INSUFICIENTE`. Atingir exatamente R$ 100,00 de saldo após o custo de A exige revisão. O saldo permanece em R$ 1.000,00. |
| Arquiteto decide tecnicamente | Guarde `const aprovarA = comando("APROVAR", "Recurso adequado à demanda")`. Execute `await revisar(A, aprovarA)`, `await revisar(C, comando("REJEITAR", "Dimensionamento não justificado"))` e `await revisar(B, comando("APROVAR", "Demanda validada"))`. A aguarda autorização, C fica `REJEITADO` e B aguarda ajuste. O saldo continua em R$ 1.000,00. |
| Arquiteto nega e depois autoriza a reserva de A | `await liberar(A, comando("NEGAR_RESERVA", "Reserva mantida nesta tentativa"))` devolve `RESERVA_NEGADA`. `LIBERAR_SEM_RESERVA` em outro comando devolve `AGUARDA_AUTORIZACAO`. Guarde `const autorizarA = comando("AUTORIZAR_RESERVA", "Uso autorizado para esta demanda")` e execute `await liberar(A, autorizarA)`. Resultado `LIBERADA_COM_RESERVA`, saldo R$ 100,00. |
| Arquiteto repete e comprova débito único | Repita `await revisar(A, aprovarA)` e `await liberar(A, autorizarA)` com os mesmos objetos. Compare a resposta com a original. Para repetir em outra sessão, cole novamente o bloco de funções após o login e copie os corpos originais, preservando os UUIDs. A liberação com outro UUID após o estado final retorna `409 ESTADO_NAO_PERMITIDO` e gera um evento de decisão recusada. O saldo continua em R$ 100,00. |
| Arquiteto ajusta o teto e libera B | `await post(orcamento + "/ajustes-orcamentarios", {operacaoId: crypto.randomUUID(), novoTeto: "2500.00", justificativa: "Adequação à demanda"})` devolve `TETO_AJUSTADO`, preserva R$ 900,00 comprometidos e eleva o saldo para R$ 1.600,00. `await liberar(B, comando("AUTORIZAR_RESERVA", "Reavaliar após o ajuste"))` devolve `RESERVA_DISPENSADA` sem débito. `await liberar(B, comando("LIBERAR_SEM_RESERVA", "Liberação nas condições vigentes"))` devolve `LIBERADA`, saldo R$ 400,00. |
| Arquiteto consulta histórico | `await get(orcamento + "/intervencoes?limite=25")` lista dez eventos na sequência completa. Com `&requisicaoId=` mais o UUID de A, mostre ação, ator, UTC, justificativa, motivo original, valores e `operacaoId`. Os dois replays recuperam recibos existentes. |

Para conferir o saldo com uma conta autorizada de leitura, execute `SELECT teto_mensal, saldo_disponivel FROM departamento WHERE id = '10000000-0000-4000-8000-000000000001';` nos pontos indicados. Se apresentar também a liberação automática, use uma base de ensaio separada. Duas unidades custam R$ 25,00, a validação devolve `LIBERADA` e a repetição devolve `JA_LIBERADA`. Prepare uma base nova para cada repetição integral do roteiro.

## Ensaio representativo do RNF-01

1. Registrar o SHA, versões, hardware, topologia do MySQL, volume de dados e pico esperado. Confirmar com a equipe o perfil de carga representativo. Rodar no ambiente da demonstração e documentar a distância entre cliente, aplicação e banco.
2. Usar o [script e a sondagem anterior](../evidencias/rnf-01-mysql.md) como referência para a linha de base com um e oito clientes. O script atual cobre apenas um departamento e um item por pedido. Para a rodada representativa, parametrizar a geração de carga fora do código da aplicação, com sessões independentes, diversos departamentos, um departamento com maior disputa e pedidos com volumes típicos de itens. Medir 100 itens separadamente como limite de entrada. Preparar pedidos inéditos em `INICIAL` e saldo suficiente para a quantidade planejada de liberações. Repetir, após aquecimento, três rodadas sustentadas de cinco minutos no pico acordado.
3. Medir até a leitura completa da resposta por HTTPS, com conexão estabelecida, incluindo JDBC e espera por locks. Medir resolução, TCP e TLS em separado para `localhost` e IPv4 direto e registrar a família de endereços escolhida. A sondagem anterior concentrou a diferença no TCP. A tentativa IPv6 antes de IPv4 é uma hipótese compatível com a ordem de resolução e ainda sem captura que a confirme.
4. Registrar por cenário, carga e quantidade de itens o total, p50, p95, p99, máximo, amostras com duração igual ou superior a dois segundos, erros e divergências. Conferir resultados, saldo e débitos no banco. Considerar o critério demonstrado para o perfil acordado se todas as validações medidas ficarem abaixo de dois segundos e a rodada preservar os resultados e os invariantes esperados. Guardar o comando, parâmetros, dados agregados e contagem de amostras em evidência reproduzível.

## Entrega acadêmica e ordem de trabalho

As exigências de produto 6.1–6.4 estão demonstradas pelos dois perfis com permissões distintas, pela recuperação após reinício, pelo fluxo de decisão financeira e pelas regras RB-01 a RB-05 testadas. O item 6.5 é condicionado ao uso de IA no produto. A aplicação utiliza regras de negócio no código. A conferência do enunciado usa a transcrição de critérios da [revisão acadêmica registrada no repositório](recorte-mvp-academico.md).

As seções 7–11 têm evidências no README, requisitos, modelo/UML, casos de uso, plano, tarefas, quatro ADRs, testes e histórico de commits. A conferência final deve validar a cobertura do fluxo manual nos artefatos de modelagem, a consistência da redação EARS e a evidência de revisão técnica e SSDLC. A verificação do GitHub encontrou zero revisões formais registradas nos PRs #106, #108 e #110. Confirmar com a equipe se há evidência de revisão por outro integrante fora desses PRs, o vínculo do professor, o quadro do Project, responsáveis, prioridades e a rastreabilidade individual. O histórico com commits de três autores, isoladamente, não comprova todos esses critérios.

| Até quando | Prioridade e condição de saída |
|---|---|
| 15/11 | Ensaiar a instalação em outra máquina com o roteiro e verificar os itens acadêmicos com a equipe. Atualizar os indicadores de integração no mapa e nas entradas de documentação usadas na apresentação. Registrar dificuldades de reprodução e evidências acadêmicas disponíveis. |
| 20/11 | Rodar e registrar o ensaio representativo do RNF-01. Triar somente defeitos reproduzidos que ameacem a entrega e apresentar proposta antes de qualquer alteração funcional. Correção aprovada segue em branch própria, com testes e PR. |
| 21/11 | Congelar novas funcionalidades. Fixar o SHA e os dados usados na demonstração. |
| 22–25/11 | Repetir a demonstração com outro integrante, conferir os requisitos e evidências no pacote final e ensaiar a apresentação. Reexecutar regressão se houver alteração aprovada no executável. |
| 26/11 | Entregar e apresentar o MVP com o roteiro validado, o resultado de desempenho disponível e as limitações remanescentes explicitadas. |
