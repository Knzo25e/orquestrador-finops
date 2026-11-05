package br.mackenzie.finops.infraestrutura.http;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LeitorPedidoCriacaoHttpTest {

    @Test
    void aceitaCorpoComExatamente65536Bytes() throws IOException {
        byte[] corpo = new byte[65_536];
        corpo[0] = 11;
        corpo[corpo.length - 1] = 22;

        var entrada = new ByteArrayInputStream(corpo);
        byte[] lido = LeitorPedidoCriacaoHttp.lerCorpo(entrada);

        assertArrayEquals(corpo, lido);
    }

    @Test
    void rejeitaCorpoCom65537Bytes() {
        byte[] corpo = new byte[65_537];
        var entrada = new ByteArrayInputStream(corpo);

        assertThrows(
                LeitorPedidoCriacaoHttp.CorpoMuitoGrandeException.class,
                () -> LeitorPedidoCriacaoHttp.lerCorpo(entrada)
        );
    }

        private static final String JSON_VALIDO = """
            {
              "requisicaoId": "0116c1b1-c55e-4ce7-b9e2-0dad46269507",
              "itens": [
                {"recursoId": "ebcd32eb-7ac9-4938-be36-25efeb24083f", "quantidade": 2},
                {"recursoId": "ebcd32eb-7ac9-4938-be36-25efeb24083f", "quantidade": 7}
              ]
            }
            """;

    @Test
    void lePedidoValidoPreservandoAsDuasOcorrenciasDoRecurso() throws IOException {
        var pedido = LeitorPedidoCriacaoHttp.lerPedido(entradaJson(JSON_VALIDO));

        var requisicaoId = UUID.fromString("0116c1b1-c55e-4ce7-b9e2-0dad46269507");
        var recursoId = UUID.fromString("ebcd32eb-7ac9-4938-be36-25efeb24083f");

        assertEquals(requisicaoId, pedido.requisicaoId());
        assertEquals(2, pedido.itens().size());
        assertEquals(recursoId, pedido.itens().get(0).recursoId());
        assertEquals(2, pedido.itens().get(0).quantidade().intValue());
        assertEquals(recursoId, pedido.itens().get(1).recursoId());
        assertEquals(7, pedido.itens().get(1).quantidade().intValue());
    }

    @Test
    void rejeitaQuantidadeEnviadaComoTexto() {
        String json = JSON_VALIDO.replace("\"quantidade\": 2", "\"quantidade\": \"2\"");

        assertThrows(
                LeitorPedidoCriacaoHttp.EntradaInvalidaException.class,
                () -> LeitorPedidoCriacaoHttp.lerPedido(entradaJson(json))
        );
    }

    @Test
    void rejeitaPropriedadeQuantidadeDuplicadaNoMesmoItem() {
        String json = JSON_VALIDO.replace(
                "\"quantidade\": 2",
                "\"quantidade\": 2, \"quantidade\": 3"
        );

        assertThrows(
                LeitorPedidoCriacaoHttp.EntradaInvalidaException.class,
                () -> LeitorPedidoCriacaoHttp.lerPedido(entradaJson(json))
        );
    }

    private static ByteArrayInputStream entradaJson(String json) {
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }
}