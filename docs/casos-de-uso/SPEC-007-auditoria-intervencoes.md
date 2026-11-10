# SPEC-007 — Auditoria de intervenções manuais

**Proposta para aprovação.** RF-05 e DA-04 exigem histórico imutável das intervenções efetivadas. Esta Spec depende da persistência e da identidade confiável das Specs 005 e 006 e sustenta as propostas 008 e 009. Revisada com o Astra. [Issue #101](https://github.com/Knzo25e/orquestrador-finops/issues/101)

## Contrato proposto

- Cada comando das Specs 008 e 009 recebe `operacaoId` (UUID canônico) e `justificativa` (1 a 500 caracteres após remover espaços nas bordas). A sessão fornece o identificador do usuário. O MySQL fornece perfil e atividade atuais. Somente Arquiteto Cloud ativo executa ou consulta intervenções.
- Um registro append-only contém `operacaoId`, `acao`, `responsavelId`, `instante` UTC do servidor, `justificativa` normalizada, `departamentoId`, `requisicaoId` quando aplicável, `resultado` e `dados` com a fotografia financeira ou de estado pertinente. A chave única é `operacaoId`. Guardar também os parâmetros originais normalizados, como `novoTeto` ou `decisao`, e o resultado e código HTTP originais para reconciliação.
- Para teto, registrar teto anterior e novo, diferença, saldo anterior e novo e comprometido preservado. Para reserva, registrar custo histórico, saldo anterior e posterior, teto, percentual e valor de reserva considerados. Para revisão, registrar estado anterior, motivo original e destino. Valores monetários são textos com duas casas.
- `GET /api/v1/departamentos/{id}/intervencoes` retorna `200 {"intervencoes":[...],"proximoCursor":...}` com filtro opcional `requisicaoId` e `limite` padrão 25 e máximo 100. Ordem cronológica descendente por instante e UUID para desempate. `apos` é o cursor opaco de instante UTC e UUID do último evento. Aplicar filtros antes de limitar. Departamento sem eventos retorna coleção vazia. Usar `no-store`.
- Conservar os códigos de sessão, CSRF e autorização vigentes. Entrada inválida recebe `400 ENTRADA_INVALIDA`. Alvo ausente recebe `404` com erro público do recurso. Falha transitória identificada recebe `503 SERVICO_INDISPONIVEL`; demais falhas técnicas recebem `500 FALHA_INTERNA`.

## Atomicidade e repetição

Em `READ COMMITTED`, reler o usuário com `FOR SHARE` e bloquear os agregados na ordem usuário → departamento → requisição, quando aplicável. Em seguida, conferir `operacaoId` antes de avaliar o estado mutável. Aplicar a decisão, inserir o registro e confirmar na mesma conexão e transação. O usuário da aplicação tem apenas `SELECT` e `INSERT` no histórico. A garantia cobre os caminhos da aplicação e seus privilégios de execução. A administração do banco é uma fronteira distinta.

Resultados registrados, inclusive negativas de negócio e operações sem mudança, só retornam após commit confirmado do recibo. Se esse commit ficar incerto, prevalece a resposta de confirmação desconhecida, inclusive quando o resultado calculado era `409`.

A repetição com mesmo autor, ação, alvo e entrada normalizada recupera o resultado gravado e seu código HTTP, após reautorizar. UUID reutilizado com qualquer desses elementos diferente recebe `409 OPERACAO_ID_EM_CONFLITO`, sem expor dados do registro anterior. Uma colisão concorrente de chave única exige rollback integral e nova transação autorizada para reconciliação. Se o commit ficar incerto, retornar `503 CONFIRMACAO_INTERVENCAO_DESCONHECIDA` com `operacaoId`. A repetição explícita usa a mesma entrada. Ausência momentânea na consulta do histórico pode anteceder a conclusão de uma transação remota. Falha de fechamento após confirmação preserva o resultado e acrescenta aviso.

## Decisão e aceite

**OPEN-007-01.** Aprovar registro e recibo em uma única linha imutável por comando autorizado que chegou a uma decisão de negócio sobre um alvo existente. Recomendo registrar também negativas e operações sem mudança com resultado próprio e valores anteriores/posteriores. Isso torna recuperável a resposta original após commit incerto. Uma falha técnica com rollback ou uma tentativa sem autorização fica fora do histórico de intervenções.

Aceite mínimo inclui falha de inserção revertendo a intervenção, tentativa SQL de alterar/excluir com usuário da aplicação, repetição após commit incerto sem novo evento, colisão concorrente do UUID, revogação durante operação e listagem paginada restrita ao Arquiteto. Usar testes MySQL com conexões e processos independentes.
