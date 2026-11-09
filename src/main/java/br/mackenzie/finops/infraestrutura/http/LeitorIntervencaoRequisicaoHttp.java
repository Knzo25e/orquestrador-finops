package br.mackenzie.finops.infraestrutura.http;

import br.mackenzie.finops.aplicacao.entradas.SolicitacaoIntervencaoRequisicao;
import br.mackenzie.finops.dominio.entidades.DecisaoManual;
import java.io.IOException;
import java.io.InputStream;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

final class LeitorIntervencaoRequisicaoHttp {
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    private LeitorIntervencaoRequisicaoHttp() {}

    static SolicitacaoIntervencaoRequisicao ler(InputStream entrada) throws IOException {
        var corpo = LeitorPedidoCriacaoHttp.lerCorpo(entrada);
        try {
            var pedido = JSON.readTree(corpo);
            if (pedido == null || !pedido.isObject() || pedido.size() != 3
                    || !pedido.has("operacaoId") || !pedido.has("decisao") || !pedido.has("justificativa")
                    || !pedido.get("decisao").isString() || !pedido.get("justificativa").isString()) {
                throw new LeitorPedidoCriacaoHttp.EntradaInvalidaException();
            }
            return new SolicitacaoIntervencaoRequisicao(LeitorPedidoCriacaoHttp.lerUuid(pedido.get("operacaoId")),
                    DecisaoManual.valueOf(pedido.get("decisao").stringValue()), pedido.get("justificativa").stringValue());
        } catch (JacksonException | IllegalArgumentException falha) {
            throw new LeitorPedidoCriacaoHttp.EntradaInvalidaException(falha);
        }
    }
}
