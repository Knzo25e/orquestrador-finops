# SPEC-008 — Ajustar teto mensal

**Proposta para aprovação.** RF-04, RF-05, RB-04 e DA-01/04. Depende da identidade, da persistência e da [SPEC-007](SPEC-007-auditoria-intervencoes.md). Revisada com o Astra.

## Contrato proposto

`POST /api/v1/departamentos/{id}/ajustes-orcamentarios`, somente Arquiteto Cloud ativo com sessão e CSRF.

```json
{"operacaoId":"0116c1b1-c55e-4ce7-b9e2-0dad46269507","novoTeto":"1500.00","justificativa":"Ajuste para a demanda aprovada do departamento"}
```

`novoTeto` é decimal não negativo em texto, com duas casas e dentro de `DECIMAL(19,2)`. A resposta concluída tem `operacaoId`, `resultado` e `ajuste` com `departamentoId`, `tetoAnterior`, `tetoNovo`, `variacaoTeto`, `saldoAnterior`, `saldoNovo` e `comprometido`. Valores monetários mantêm duas casas. A resposta de teto recusado usa o envelope de erro e inclui o UUID da operação para consulta do histórico autorizado.

## Regra e transação

Bloquear usuário e departamento, reautorizar e verificar a repetição segundo a SPEC-007. Ler teto e saldo vigentes. Calcular `comprometido = tetoAnterior − saldoAnterior`. Se o novo teto for menor que o comprometido, retornar `409 TETO_ABAIXO_DO_COMPROMETIDO` e preservar os valores. Caso contrário, calcular `saldoNovo = novoTeto − comprometido` e retornar `200 TETO_AJUSTADO` após commit do ajuste e do histórico. Teto igual ao comprometido é válido e resulta em saldo zero.

A ação altera o departamento. A reserva será calculada sobre o novo teto na próxima avaliação. Nenhum pedido é reavaliado ou liberado como efeito do ajuste. A renovação mensal e o percentual editável estão fora deste recorte.

## Decisão e aceite

**OPEN-008-01.** Para teto igual ao atual, recomendo `200 TETO_INALTERADO`, sem escrita financeira, com recibo sem efeito conforme OPEN-007-01. Aprovar também a forma decimal da entrada e a resposta proposta.

Testar aumento, redução válida, teto igual ao comprometido, teto menor, teto inalterado, fração de centavo e estouro. Em corrida com validação ou liberação manual, comprovar que a segunda ação vê os valores confirmados da primeira. Exercitar rollback por falha na auditoria e repetição após perda da confirmação.
