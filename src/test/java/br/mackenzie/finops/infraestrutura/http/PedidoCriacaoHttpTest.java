package br.mackenzie.finops.infraestrutura.http;

import br.mackenzie.finops.aplicacao.entradas.ItemSolicitado;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PedidoCriacaoHttpTest {

    private static final UUID REQUISICAO_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final UUID RECURSO_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void aceitaCemOcorrenciasDoMesmoRecursoNaOrdemRecebida() {
        List<ItemSolicitado> itens = criarItens(100);

        var pedido = new PedidoCriacaoHttp(REQUISICAO_ID, itens);

        assertEquals(100, pedido.itens().size());
        assertEquals(itens, pedido.itens());
    }

    @Test
    void rejeitaCentoEUmaOcorrenciasMesmoComUmUnicoRecurso() {
        List<ItemSolicitado> itens = criarItens(101);

        assertThrows(
                IllegalArgumentException.class,
                () -> new PedidoCriacaoHttp(REQUISICAO_ID, itens)
        );
    }

    private static List<ItemSolicitado> criarItens(int total) {
        List<ItemSolicitado> itens = new ArrayList<>();
        for (int quantidade = 1; quantidade <= total; quantidade++) {
            itens.add(new ItemSolicitado(RECURSO_ID, quantidade));
        }
        return itens;
    }
}