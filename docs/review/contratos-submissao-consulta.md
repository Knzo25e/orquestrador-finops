# Proposta de contratos HTTP para submissão e consulta

Proposta para aprovação do responsável pelo projeto, revisada pelo Astra em 06/11/2026. A [Issue #93](https://github.com/Knzo25e/orquestrador-finops/issues/93) acompanha o planejamento. A implementação depende da sessão e da identidade transacional do [PR #97](https://github.com/Knzo25e/orquestrador-finops/pull/97), que se apoia no [PR #95](https://github.com/Knzo25e/orquestrador-finops/pull/95).

O recorte liga criação, validação automática e acompanhamento do resultado no MVP. Aplica RF-01, RF-02, RB-02, RB-05 e RNF-02. A criação continua em `INICIAL`. As transições manuais e a exposição dos respectivos estados pertencem aos contratos das Specs 007 a 009.

## Endpoints e permissões

| Método e rota | Entrada | Desenvolvedor ativo | Arquiteto Cloud ativo |
|---|---|---|---|
| `POST /api/v1/requisicoes/{id}/validacao` | UUID na rota, cookie de sessão e token CSRF. Corpo vazio. | Submete pedido próprio do departamento atual. | `403 ACESSO_NEGADO` para qualquer UUID. |
| `GET /api/v1/requisicoes/{id}` | UUID na rota e cookie de sessão. Corpo vazio. | Consulta pedido próprio do departamento atual. | Consulta pedido de qualquer departamento, inclusive com autoria legada desconhecida. |
| `GET /api/v1/requisicoes` | Cookie e parâmetros opcionais `apos` e `limite`. `departamentoId` é exigido para o Arquiteto. | O servidor deriva o departamento e filtra por autoria. Enviar `departamentoId` resulta em `400`. | Seleciona o departamento consultado. Uma consulta por departamento pode retornar pedidos de vários autores. |

UUIDs usam a forma textual canônica com letras minúsculas. `limite` aceita inteiros de 1 a 100 e tem padrão 25. Com `apos`, a página começa depois do UUID informado. Parâmetro ausente quando obrigatório, inválido, repetido ou fora do contrato produz `400 ENTRADA_INVALIDA`. Perfil, autoria e vínculo vêm do servidor. O parâmetro do Arquiteto seleciona o departamento da consulta.

## Respostas JSON

O detalhe retorna `200` com `{"requisicao": Pedido}`. O POST concluído retorna `200` com `resultado`, a requisição da mesma tentativa e `avisos`. `Pedido` conserva a forma pública da criação. Exemplo de primeira submissão que encaminha para revisão:

```json
{
  "resultado": "REVISAO_POR_RESERVA",
  "requisicao": {
    "id": "0116c1b1-c55e-4ce7-b9e2-0dad46269507",
    "estado": "REVISAO_PENDENTE",
    "motivoRevisao": "RESERVA",
    "custoTotalProjetado": "75.00",
    "itens": [
      {
        "recursoId": "ebcd32eb-7ac9-4938-be36-25efeb24083f",
        "quantidade": 6,
        "precoUnitario": "12.50",
        "subtotal": "75.00"
      }
    ]
  },
  "avisos": []
}
```

| Resultado da tentativa | HTTP | Estado e motivo |
|---|---|---|
| `LIBERADA` | `200` | `PROVISIONAMENTO_LIBERADO`, motivo `null`. Débito único. |
| `JA_LIBERADA` | `200` | `PROVISIONAMENTO_LIBERADO`, motivo `null`. Saldo preservado. |
| `REVISAO_POR_SALDO_INSUFICIENTE` | `200` | `REVISAO_PENDENTE`, motivo `SALDO_INSUFICIENTE`. Saldo preservado. |
| `REVISAO_POR_RESERVA` | `200` | `REVISAO_PENDENTE`, motivo `RESERVA`. Saldo preservado. |
| `ESTADO_NAO_PERMITIDO` | `409` | Estado, motivo e saldo preservados. O GET mostra o estado autorizado. |

Na consulta, `INICIAL` também é público e tem motivo `null`. O estado transitório em análise é interno. Valores monetários são textos decimais com duas casas. O detalhe e a submissão preservam a ordem dos itens e os preços históricos. Saldo, teto, autoria e atributos da sessão ficam fora desses DTOs.

A listagem retorna `200` com `requisicoes` e `proximoCursor`. Cada resumo contém `id`, `estado`, `motivoRevisao` e `custoTotalProjetado`. Exemplo de página vazia:

```json
{"requisicoes": [], "proximoCursor": null}
```

Ordenar por UUID textual crescente. `apos` é apenas a fronteira `id > apos`, sem buscar o pedido do cursor. Ler até `limite + 1` resumos já filtrados e retornar até `limite`, sem contagem global. Quando houver mais registros, `proximoCursor` é o UUID do último resumo retornado. Caso contrário é `null`. Departamento sem pedidos e UUID válido de departamento inexistente produzem coleção vazia na consulta do Arquiteto. Cada página revalida a identidade e o escopo e tem sua própria fotografia. Uma inserção concorrente com UUID anterior ao cursor pode aparecer somente ao reiniciar a listagem.

## Erros e precedência

Os erros preservam o envelope `{"erro":{"codigo":"...","mensagem":"..."}}`. Use `Cache-Control: no-store` nas respostas destas rotas, inclusive negativas. A equivalência de resposta pública para ausência e ocultação de recurso segue a semântica de `404`. [RFC 9110](https://www.rfc-editor.org/rfc/rfc9110.html#section-15.5.5)

| HTTP | Código | Condição |
|---|---|---|
| `400` | `ENTRADA_INVALIDA` | UUID ou parâmetros inválidos, ou corpo com qualquer conteúdo no POST, inclusive `{}` e espaços. |
| `403` | `SESSAO_NECESSARIA` | Sessão ausente ou expirada, conforme contrato provisório aprovado. |
| `403` | `CSRF_INVALIDO` | Sessão presente com token ausente ou inválido no POST. |
| `403` | `ACESSO_NEGADO` | Perfil proibido para a operação, usuário inativo ou Desenvolvedor sem vínculo válido. A aplicação verifica essa permissão antes de buscar o pedido. |
| `404` | `REQUISICAO_NAO_ENCONTRADA` | Pedido ausente ou fora do escopo do perfil habilitado. Usar o mesmo corpo público para ambos, inclusive para pedidos de autoria legada quando o solicitante é Desenvolvedor. |
| `409` | `ESTADO_NAO_PERMITIDO` | Pedido autorizado em estado que não permite validação automática. |
| `503` | `CONFIRMACAO_VALIDACAO_DESCONHECIDA` | A confirmação do commit se perdeu. Incluir `requisicaoId` em `erro` para permitir reconciliação. |
| `503` | `SERVICO_INDISPONIVEL` | Indisponibilidade transitória identificada de forma tipada e sem ambiguidade sobre o commit. |
| `500` | `FALHA_INTERNA` | Outra falha de persistência, leitura inconsistente ou erro inesperado. |

Sessão e CSRF vêm antes do acesso aos dados. Após validar a forma da entrada, verificar identidade e permissão geral, depois autoria e departamento, e só então revelar estado ou resultado. Falhas de consulta produzem erro. Depois de sucesso confirmado, falha no fechamento conserva o `200` e inclui `FALHA_NO_FECHAMENTO` em `avisos`. Respostas públicas omitem mensagens do driver e detalhes internos.

## Transação e repetição

1. **Submissão.** Manter uma conexão InnoDB em `READ COMMITTED` durante a tentativa. Obter o identificador autenticado da sessão e reler usuário, perfil, atividade e vínculo com `FOR SHARE`. Localizar o cabeçalho por UUID, autor e departamento da identidade antes de pedir o bloqueio financeiro. Para pedido encontrado no escopo, bloquear departamento e requisição com `FOR UPDATE` e reconferir o escopo antes de ler itens ou avaliar estado. A ordem dos bloqueios é usuário → departamento → requisição. Alterações administrativas da identidade escrevem na mesma linha protegida. [Leituras com bloqueio no InnoDB](https://dev.mysql.com/doc/refman/8.0/en/innodb-locking-reads.html)
2. **Regra financeira.** Reutilizar a rotina de validação de `ValidacaoRequisicaoJdbc` e o domínio após a autorização. Ler o saldo vigente sob bloqueio. Atualizar saldo, estado e motivo em uma transação, retornando a fotografia da própria tentativa. Compartilhar o motor com a entrada interna existente. A nova entrada protegida concentra a autorização e o controller traduz HTTP.
3. **Consultas.** Reusar `ConsultarRequisicaoAutorizadaUseCase` e a política de `ListarRequisicoesAutorizadasUseCase`, estendendo a leitura JDBC para paginação de resumos. A identidade é relida no início de cada GET. O pedido e os itens, ou os resumos de uma página, são recuperados por uma única instrução SQL. A consulta segue a fotografia de autorização do início da chamada, sem os bloqueios financeiros. Uma alteração posterior afeta a próxima chamada.
4. **Repetição.** O UUID do pedido é a referência. Pedido liberado retorna `JA_LIBERADA`; pedido em revisão retorna `409` sem reavaliar, mesmo após mudança no saldo. Após `CONFIRMACAO_VALIDACAO_DESCONHECIDA`, consulte o pedido ou repita explicitamente a submissão com o mesmo UUID. Um GET que ainda mostra `INICIAL` pode anteceder a conclusão de uma transação remota. Cada repetição reautoriza e adquire os bloqueios. O servidor evita reexecução automática de tentativa incerta.

## Aceite e estratégia de testes

| Cenário | Evidência requerida |
|---|---|
| Fluxo HTTPS | Desenvolvedor entra, consulta catálogo, cria em `INICIAL` sem débito, submete e consulta o resultado. Arquiteto consulta dois departamentos, inclusive pedido legado, e recebe `403` no POST de validação. |
| Regra financeira | Teto de R$ 1.000,00 e reserva de R$ 100,00. Uma liberação de R$ 700,00 deixa R$ 300,00. Outro pedido de R$ 200,00 atinge a reserva e vai a revisão. Um de R$ 400,00 vai a revisão por insuficiência. Os dois pedidos em revisão preservam o saldo. |
| Isolamento e sessão | Dois autores no mesmo departamento, outro departamento, pedido legado, vínculo alterado e conta inativa. Confirmar `404` público idêntico para ausente e fora do escopo. Verificar sessão expirada, CSRF inválido e falha de consulta separadamente. |
| Concorrência | Duas submissões do mesmo pedido com um único débito. Dois pedidos disputando saldo no mesmo departamento sem saldo negativo. Requisições de outro departamento seguem independentes. Transferência e desativação antes ou durante a tentativa exercitam a releitura e os bloqueios em conexões independentes, incluindo JVMs separadas. |
| Repetição e falhas | Em revisão, o POST repetido conserva estado e motivo mesmo se o saldo mudar. Simular perda da resposta antes e depois do commit, falha de gravação e de fechamento. Confirmar rollback quando aplicável, resposta de incerteza, reconciliação explícita e ausência de débito duplicado. |
| Representação e paginação | DTOs públicos preservam centavos e preços históricos. Páginas são ordenadas e limitadas depois do filtro, sem itens nem totais de outros autores na listagem. Cursor, página vazia, erro e mudança de vínculo entre páginas exercitados. |

Usar testes HTTP com sessão real sobre o ambiente local e testes JDBC com MySQL isolado. Preservar a suíte de criação, consulta e validação. Medir RNF-01 separadamente com MySQL e registrar os resultados no roteiro de demonstração.

## Decisões abertas para aprovação

- **OPEN-SUB-01 — Permissão e semântica da submissão.** Recomendo o POST exclusivo do Desenvolvedor autor, `404` indistinguível no escopo e `409` na repetição de revisão. Uma alternativa é retornar sucesso para pedido já em revisão, mas ela exigiria ampliar o contrato vigente de repetição. [SPEC-003](../casos-de-uso/SPEC-003-validar-orcamento.md) · [SPEC-005](../casos-de-uso/SPEC-005-persistir-requisicoes.md)
- **OPEN-SUB-02 — Autorização e incerteza.** Recomendo os bloqueios na mesma transação e o `503` específico com reconciliação explícita pelo mesmo UUID. Autorizar numa consulta anterior deixaria uma janela de troca de vínculo. [SPEC-004](../casos-de-uso/SPEC-004-interface-aplicacao.md)
- **OPEN-CONS-01 — Listagem e fotografia de autorização.** Recomendo departamento obrigatório para o Arquiteto, escopo derivado para o Desenvolvedor e páginas de resumos. Uma coleção integral reutilizaria mais do leitor atual, ao custo de resposta sem limite. Para as leituras, manter a identidade obtida no início de cada chamada conforme a SPEC-006. [SPEC-006](../casos-de-uso/SPEC-006-perfis-acesso.md)

Após aprovação, os arquivos envolvidos concentram-se nos casos de uso e portas de validação e consulta, em `ValidacaoRequisicaoJdbc`, no leitor JDBC, nos adaptadores HTTP e nos testes. Recomendo entregar detalhe e submissão integrados primeiro, depois a listagem paginada e a regressão conjunta, mantendo a meta de congelamento em 21/11/2026.
