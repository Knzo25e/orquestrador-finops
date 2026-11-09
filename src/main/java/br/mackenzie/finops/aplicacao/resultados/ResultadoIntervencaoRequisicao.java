package br.mackenzie.finops.aplicacao.resultados;

import java.util.UUID;

public record ResultadoIntervencaoRequisicao(UUID operacaoId, String resultado,
                                            FotografiaRequisicao requisicao, int statusHttp, boolean falhaNoFechamento) {}
