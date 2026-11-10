package br.mackenzie.finops.infraestrutura.http;

import br.mackenzie.finops.aplicacao.excecoes.EntradaRequisicaoInvalidaException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LeitorIntervencaoRequisicaoHttpTest {
    @Test
    void exigeEntradaEstritaComJustificativaENormalizaRepeticao() throws Exception {
        String corpo = "{\"operacaoId\":\"" + UUID.randomUUID() + "\",\"decisao\":\"APROVAR\",\"justificativa\":\" Motivo \"}";
        assertEquals("Motivo", ler(corpo).justificativa());
        for (String invalido : List.of("null", "{}", "[]", corpo + "{}",
                corpo.replace("APROVAR", "DESCONHECIDA"), corpo.replace("APROVAR", "aprovar"),
                corpo.replace("\"APROVAR\"", "1"), corpo.replace("\"decisao\":", "\"perfil\":\"ARQUITETO_CLOUD\",\"decisao\":"),
                corpo.replace("\"decisao\":", "\"decisao\":\"REJEITAR\",\"decisao\":"))) {
            assertThrows(LeitorPedidoCriacaoHttp.EntradaInvalidaException.class, () -> ler(invalido));
        }
        assertThrows(EntradaRequisicaoInvalidaException.class, () -> ler(corpo.replace(" Motivo ", "  ")));
        assertThrows(EntradaRequisicaoInvalidaException.class, () -> ler(corpo.replace(" Motivo ", "x".repeat(501))));
    }

    private static br.mackenzie.finops.aplicacao.entradas.SolicitacaoIntervencaoRequisicao ler(String corpo) throws Exception {
        return LeitorIntervencaoRequisicaoHttp.ler(new ByteArrayInputStream(corpo.getBytes(StandardCharsets.UTF_8)));
    }
}
