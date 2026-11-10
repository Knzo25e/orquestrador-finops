package br.mackenzie.finops.aplicacao.portas;

import br.mackenzie.finops.aplicacao.entradas.SolicitacaoIntervencaoRequisicao;
import br.mackenzie.finops.aplicacao.resultados.ResultadoIntervencaoRequisicao;
import java.util.UUID;

public interface IntervencaoRequisicaoPersistente {
    ResultadoIntervencaoRequisicao executarAutorizado(UUID usuario, UUID requisicao, SolicitacaoIntervencaoRequisicao pedido);
}
