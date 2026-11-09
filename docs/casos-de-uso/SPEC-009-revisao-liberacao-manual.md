# SPEC-009 — Revisão técnica e liberação manual

**OPEN-009-01, OPEN-009-02 e OPEN-009-03 aprovadas pelo responsável em 09/11/2026.** RF-03 a RF-05 e RB-02 a RB-05. Depende das Specs 003, 005, 006, [007](SPEC-007-auditoria-intervencoes.md) e [008](SPEC-008-ajustar-teto.md). A base aprovada está nas seções de transições de [spec.md](../spec.md) e em OPEN-003-05. Revisada com o Astra. Implementação na [Issue #107](https://github.com/Knzo25e/orquestrador-finops/issues/107).

## Comandos aprovados

| Método e rota | Entrada além de `operacaoId` e `justificativa` | Condição |
|---|---|---|
| `POST /api/v1/requisicoes/{id}/revisao-tecnica` | `decisao` igual a `APROVAR` ou `REJEITAR` | Pedido em `REVISAO_PENDENTE` |
| `POST /api/v1/requisicoes/{id}/liberacao-financeira` | `decisao` igual a `LIBERAR_SEM_RESERVA`, `AUTORIZAR_RESERVA` ou `NEGAR_RESERVA` | Pedido já aprovado tecnicamente em um estado de espera financeira |

Os comandos exigem sessão, CSRF e Arquiteto Cloud ativo. O `200` inclui `operacaoId`, `resultado` e a fotografia `requisicao` com o DTO do pedido. A revisão técnica retorna `APROVADA_TECNICAMENTE` ou `REJEITADA`. Estado incompatível recebe `409 ESTADO_NAO_PERMITIDO`. Repetição e falhas seguem a SPEC-007. A proposta exige que a evolução do DTO e do esquema mapeie explicitamente os novos estados. Registros legados preservam autoria, itens e valores. Ao sair de `REVISAO_PENDENTE`, o DTO tem `motivoRevisao: null` e o motivo original fica registrado no evento técnico.

## Revisão técnica

| Decisão em `REVISAO_PENDENTE` | Condição financeira vigente | Destino | Efeito |
|---|---|---|---|
| `REJEITAR` | Qualquer | `REJEITADO` | Registra rejeição e justificativa. Saldo preservado. |
| `APROVAR` | Custo acima do saldo | `AGUARDANDO_AJUSTE_ORCAMENTARIO` | Saldo preservado. |
| `APROVAR` | Custo cabe no saldo e saldo resultante atinge ou consome a reserva | `AGUARDANDO_AUTORIZACAO_FINANCEIRA` | Saldo preservado. |
| `APROVAR` | Saldo resultante acima da reserva | `AGUARDANDO_LIBERACAO_FINANCEIRA` | Saldo preservado. A liberação exige outro comando. |

## Revalidação financeira

Reavaliar custo histórico, saldo, teto e reserva na tentativa atual. Aplicar RB-05 sob os bloqueios da SPEC-007. Na liberação, confirmar saldo, estado e histórico juntos. O ajuste de teto é ação separada da SPEC-008.

| Condição vigente em estado de espera financeira | Decisão | Destino e resultado aprovados |
|---|---|---|
| Custo acima do saldo | Qualquer | `AGUARDANDO_AJUSTE_ORCAMENTARIO`, `AGUARDA_AJUSTE`. Sem débito ou autorização reaproveitável. |
| Saldo suficiente e reserva necessária | `LIBERAR_SEM_RESERVA` | `AGUARDANDO_AUTORIZACAO_FINANCEIRA`, `AGUARDA_AUTORIZACAO`. Sem débito. |
| Saldo suficiente e reserva necessária | `AUTORIZAR_RESERVA` | `PROVISIONAMENTO_LIBERADO`, `LIBERADA_COM_RESERVA`. Compromete o valor uma vez, registrando a autorização explícita e os valores vigentes no mesmo commit. |
| Saldo suficiente e reserva necessária | `NEGAR_RESERVA` | `AGUARDANDO_AUTORIZACAO_FINANCEIRA`, `RESERVA_NEGADA`. Registra decisão justificada, preserva saldo e mantém a possibilidade de nova decisão. |
| Saldo após o pedido acima da reserva | `LIBERAR_SEM_RESERVA` | `PROVISIONAMENTO_LIBERADO`, `LIBERADA`. Compromete o valor uma vez. |
| Saldo após o pedido acima da reserva | `AUTORIZAR_RESERVA` ou `NEGAR_RESERVA` | `AGUARDANDO_LIBERACAO_FINANCEIRA`, `RESERVA_DISPENSADA`. Solicitar a ação separada de liberação. |

Um novo comando sobre pedido já liberado ou rejeitado preserva o estado e saldo e recebe `409`. A repetição do mesmo UUID recupera o resultado original conforme a SPEC-007.

## Decisões e aceite

- **OPEN-009-01 — aprovada em 09/11/2026.** Novo estado de espera sem reserva e aprovação técnica sem débito, com os encaminhamentos da matriz.
- **OPEN-009-02 — aprovada em 09/11/2026.** Autorização de reserva válida somente na tentativa de liberação atual, negativa justificada que preserve a pendência e resultados da revalidação quando as condições mudarem.
- **OPEN-009-03 — aprovada em 09/11/2026.** Rotas, representação pública, compatibilidade de estados e matriz completa. Preservar autoria, preços, itens e motivos originais no histórico.

Aceite da demonstração inclui aprovação técnica sem débito, rejeição justificada, autorização explícita da reserva, ajuste após insuficiência seguido de nova revalidação e consulta do histórico. Testar desaparecimento da necessidade de reserva, nova insuficiência, negativa financeira, duas liberações concorrentes, ajuste versus liberação, falha de auditoria, commit incerto, troca de perfil e regressão da validação automática.

## Base executável

V005 amplia `requisicao.estado` de 32 para 40 caracteres e inclui os quatro novos estados nos checks. Os três códigos anteriores e seus dados são preservados. A reconstituição e o DTO compartilhado atendem detalhe, listagem resumida e repetição da criação nos novos estados.

O coordenador autoriza e protege o usuário antes de descobrir o departamento do pedido, na mesma conexão. Depois, bloqueia departamento e requisição e confere o vínculo. Um Desenvolvedor ou usuário inativo recebe `403` para alvo existente ou ausente. Arquiteto autorizado recebe o `404` público do pedido ausente.

O evento registra estado anterior/novo, motivo original, custo histórico, saldo anterior/novo, teto e reserva considerados. `motivoOriginal` é vazio quando não havia motivo na tentativa. `versaoFotografia: "1"` e `requisicaoJson` armazenam o DTO completo antes do commit. O replay reconstrói a resposta a partir desse recibo, incluindo os preços históricos. O envelope de sucesso inclui `avisos`, e erros de negócio registrados incluem `operacaoId` e os avisos de fechamento aplicáveis. Resultados incertos continuam recebendo `503 CONFIRMACAO_INTERVENCAO_DESCONHECIDA`.

[Reprodução e evidências](../evidencias/issue-107/fluxo-manual.md). A aprovação técnica, o ajuste de teto e a liberação permanecem operações independentes.
