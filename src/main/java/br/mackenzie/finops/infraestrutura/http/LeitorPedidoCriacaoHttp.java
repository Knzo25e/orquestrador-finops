package br.mackenzie.finops.infraestrutura.http;

import br.mackenzie.finops.aplicacao.entradas.ItemSolicitado;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class LeitorPedidoCriacaoHttp {

    private static final int MAXIMO_BYTES = 64 * 1024;

    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private LeitorPedidoCriacaoHttp() {
    }

    static byte[] lerCorpo(InputStream entrada) throws IOException {
        byte[] corpo = entrada.readNBytes(MAXIMO_BYTES + 1);

        if (corpo.length > MAXIMO_BYTES) {
            throw new CorpoMuitoGrandeException();
        }

        return corpo;
    }

    static PedidoCriacaoHttp lerPedido(InputStream entrada) throws IOException {
        byte[] corpo = lerCorpo(entrada);

        JsonNode pedido;
        try {
            pedido = JSON.readTree(corpo);
        } catch (JacksonException causa) {
            throw new EntradaInvalidaException(causa);
        }

        exigirDoisCampos(pedido, "requisicaoId", "itens");
        UUID requisicaoId = lerUuid(pedido.get("requisicaoId"));
        JsonNode itensJson = pedido.get("itens");

        if (!itensJson.isArray() || itensJson.isEmpty()
                || itensJson.size() > PedidoCriacaoHttp.MAXIMO_ITENS) {
            throw new EntradaInvalidaException();
        }

        List<ItemSolicitado> itens = new ArrayList<>(itensJson.size());
        for (JsonNode item : itensJson) {
            exigirDoisCampos(item, "recursoId", "quantidade");
            UUID recursoId = lerUuid(item.get("recursoId"));
            JsonNode quantidade = item.get("quantidade");

            if (!quantidade.isIntegralNumber() || !quantidade.canConvertToInt()
                    || quantidade.intValue() < 1) {
                throw new EntradaInvalidaException();
            }

            itens.add(new ItemSolicitado(recursoId, quantidade.intValue()));
        }

        return new PedidoCriacaoHttp(requisicaoId, itens);
    }

    private static void exigirDoisCampos(JsonNode objeto, String primeiro, String segundo) {
        if (objeto == null || !objeto.isObject() || objeto.size() != 2
                || !objeto.has(primeiro) || !objeto.has(segundo)) {
            throw new EntradaInvalidaException();
        }
    }

    static UUID lerUuid(JsonNode valor) {
        if (!valor.isString()) {
            throw new EntradaInvalidaException();
        }

        return lerUuid(valor.stringValue());
    }

    static UUID lerUuid(String texto) {
        UUID uuid;
        try {
            uuid = UUID.fromString(texto);
        } catch (IllegalArgumentException causa) {
            throw new EntradaInvalidaException(causa);
        }

        if (!uuid.toString().equals(texto)) {
            throw new EntradaInvalidaException();
        }

        return uuid;
    }

    static final class CorpoMuitoGrandeException extends RuntimeException {
    }

    static final class EntradaInvalidaException extends RuntimeException {

        EntradaInvalidaException() {
        }

        EntradaInvalidaException(Throwable causa) {
            super(causa);
        }
    }
}
