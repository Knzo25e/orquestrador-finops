package br.mackenzie.finops.infraestrutura.http;

import br.mackenzie.finops.aplicacao.entradas.SolicitacaoAjusteTeto;
import java.io.IOException;
import java.io.InputStream;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

final class LeitorAjusteTetoHttp {
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    private LeitorAjusteTetoHttp() {}

    static SolicitacaoAjusteTeto ler(InputStream entrada) throws IOException {
        var corpo = LeitorPedidoCriacaoHttp.lerCorpo(entrada);
        try {
            var pedido = JSON.readTree(corpo);
            if (pedido == null || !pedido.isObject() || pedido.size() != 3
                    || !pedido.has("operacaoId") || !pedido.has("novoTeto") || !pedido.has("justificativa")
                    || !pedido.get("novoTeto").isString() || !pedido.get("justificativa").isString()) {
                throw new LeitorPedidoCriacaoHttp.EntradaInvalidaException();
            }
            return new SolicitacaoAjusteTeto(LeitorPedidoCriacaoHttp.lerUuid(pedido.get("operacaoId")),
                    pedido.get("novoTeto").stringValue(), pedido.get("justificativa").stringValue());
        } catch (JacksonException falha) {
            throw new LeitorPedidoCriacaoHttp.EntradaInvalidaException(falha);
        }
    }
}
