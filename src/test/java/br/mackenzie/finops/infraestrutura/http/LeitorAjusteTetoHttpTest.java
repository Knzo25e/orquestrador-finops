package br.mackenzie.finops.infraestrutura.http;

import br.mackenzie.finops.aplicacao.entradas.SolicitacaoAjusteTeto;
import br.mackenzie.finops.aplicacao.excecoes.EntradaRequisicaoInvalidaException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LeitorAjusteTetoHttpTest {
    private static final UUID ID = UUID.randomUUID();

    @Test
    void aceitaLimitesENormalizaSemArredondar() throws Exception {
        for (String teto : List.of("0.00", "0.01", "99999999999999999.99")) {
            assertEquals(teto, ler("{\"operacaoId\":\"" + ID + "\",\"novoTeto\":\"" + teto
                    + "\",\"justificativa\":\" Motivo \"}").novoTeto());
        }
        var pedido = new SolicitacaoAjusteTeto(ID, "001500.00", " Motivo ");
        assertEquals("1500.00", pedido.novoTeto());
        assertEquals("Motivo", pedido.justificativa());
    }

    @Test
    void rejeitaFormatoMonetarioInvalidoCentavosENumerosForaDoLimite() {
        for (String valor : List.of("-0.00", "-1.00", "+1.00", "1", "1.0", "1.000", "1.001", "1e2",
                "1,00", " 1.00", "1.00 ", ".00", "100000000000000000.00", "NaN", "Infinity", "")) {
            assertThrows(EntradaRequisicaoInvalidaException.class,
                    () -> new SolicitacaoAjusteTeto(ID, valor, "Motivo"), valor);
        }
        assertThrows(EntradaRequisicaoInvalidaException.class, () -> new SolicitacaoAjusteTeto(ID, null, "Motivo"));
        assertThrows(EntradaRequisicaoInvalidaException.class, () -> new SolicitacaoAjusteTeto(ID, "1.00", "  "));
        assertThrows(EntradaRequisicaoInvalidaException.class, () -> new SolicitacaoAjusteTeto(ID, "1.00", "x".repeat(501)));
    }

    @Test
    void rejeitaJsonAmbiguoCamposDeIdentidadeECorpoExcessivo() {
        String valido = "{\"operacaoId\":\"" + ID + "\",\"novoTeto\":\"1.00\",\"justificativa\":\"Motivo\"}";
        for (String corpo : List.of("", "null", "[]", "{}", valido + "{}",
                valido.replace("\"1.00\"", "1.00"), valido.replace("\"1.00\"", "null"),
                valido.replace("\"novoTeto\":", "\"novoTeto\":\"2.00\",\"novoTeto\":"),
                valido.replace("\"justificativa\":", "\"responsavelId\":\"arbitrario\",\"justificativa\":"),
                valido.replace(ID.toString(), "1-1-1-1-1"))) {
            assertThrows(LeitorPedidoCriacaoHttp.EntradaInvalidaException.class, () -> ler(corpo));
        }
        assertThrows(LeitorPedidoCriacaoHttp.CorpoMuitoGrandeException.class, () -> ler("x".repeat(65537)));
    }

    private static SolicitacaoAjusteTeto ler(String corpo) throws Exception {
        return LeitorAjusteTetoHttp.ler(new ByteArrayInputStream(corpo.getBytes(StandardCharsets.UTF_8)));
    }
}
