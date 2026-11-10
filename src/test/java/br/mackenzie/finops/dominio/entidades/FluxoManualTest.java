package br.mackenzie.finops.dominio.entidades;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FluxoManualTest {
    @Test
    void revisaoTecnicaEncaminhaSemDebitoELimpaMotivo() {
        for (int condicao = 0; condicao < 3; condicao++) {
            var pedido = pedido(EstadoRequisicao.REVISAO_PENDENTE, condicao);
            var saldo = pedido.getDepartamento().getSaldoDisponivel();
            assertEquals(ResultadoIntervencaoManual.APROVADA_TECNICAMENTE, pedido.revisarTecnicamente(DecisaoManual.APROVAR));
            assertEquals(List.of(EstadoRequisicao.AGUARDANDO_AJUSTE_ORCAMENTARIO,
                    EstadoRequisicao.AGUARDANDO_AUTORIZACAO_FINANCEIRA, EstadoRequisicao.AGUARDANDO_LIBERACAO_FINANCEIRA)
                    .get(condicao).statusDominio(), pedido.getStatus());
            assertEquals(saldo, pedido.getDepartamento().getSaldoDisponivel());
            assertNull(pedido.getMotivoRevisao());
        }
        var rejeitado = pedido(EstadoRequisicao.REVISAO_PENDENTE, 1);
        assertEquals(ResultadoIntervencaoManual.REJEITADA, rejeitado.revisarTecnicamente(DecisaoManual.REJEITAR));
        assertEquals("REJEITADO", rejeitado.getStatus());
        assertEquals(new BigDecimal("1000.00"), rejeitado.getDepartamento().getSaldoDisponivel());
    }

    @Test
    void cobreMatrizFinanceiraEmTodosOsEstadosDeEspera() {
        for (var estado : List.of(EstadoRequisicao.AGUARDANDO_AJUSTE_ORCAMENTARIO,
                EstadoRequisicao.AGUARDANDO_AUTORIZACAO_FINANCEIRA, EstadoRequisicao.AGUARDANDO_LIBERACAO_FINANCEIRA)) {
            for (int condicao = 0; condicao < 3; condicao++) {
                for (var decisao : List.of(DecisaoManual.LIBERAR_SEM_RESERVA, DecisaoManual.AUTORIZAR_RESERVA, DecisaoManual.NEGAR_RESERVA)) {
                    var pedido = pedido(estado, condicao);
                    var saldo = pedido.getDepartamento().getSaldoDisponivel();
                    var resultado = pedido.liberarFinanceiramente(decisao);
                    if (condicao == 0) {
                        assertEquals(ResultadoIntervencaoManual.AGUARDA_AJUSTE, resultado);
                        assertEquals("AGUARDANDO_AJUSTE_ORCAMENTARIO", pedido.getStatus());
                    } else if (condicao == 1) {
                        assertEquals(switch (decisao) {
                            case AUTORIZAR_RESERVA -> ResultadoIntervencaoManual.LIBERADA_COM_RESERVA;
                            case NEGAR_RESERVA -> ResultadoIntervencaoManual.RESERVA_NEGADA;
                            default -> ResultadoIntervencaoManual.AGUARDA_AUTORIZACAO;
                        }, resultado);
                    } else {
                        assertEquals(decisao == DecisaoManual.LIBERAR_SEM_RESERVA
                                ? ResultadoIntervencaoManual.LIBERADA : ResultadoIntervencaoManual.RESERVA_DISPENSADA, resultado);
                    }
                    boolean liberada = resultado == ResultadoIntervencaoManual.LIBERADA
                            || resultado == ResultadoIntervencaoManual.LIBERADA_COM_RESERVA;
                    assertEquals(liberada ? saldo.subtract(new BigDecimal("900.00")) : saldo,
                            pedido.getDepartamento().getSaldoDisponivel());
                    if (liberada) {
                        assertEquals(ResultadoIntervencaoManual.ESTADO_NAO_PERMITIDO, pedido.liberarFinanceiramente(decisao));
                    }
                }
            }
        }
    }

    @Test
    void reconstituiEstadosEImpedeRetornoIndevidoAoFluxoAutomatico() {
        for (var estado : EstadoRequisicao.values()) {
            var pedido = pedido(estado, 1);
            assertEquals(estado.statusDominio(), pedido.getStatus());
            if (estado != EstadoRequisicao.INICIAL && estado != EstadoRequisicao.PROVISIONAMENTO_LIBERADO) {
                assertEquals(ResultadoValidacao.ESTADO_NAO_PERMITIDO, pedido.processarRoteamento());
            }
            if (estado != EstadoRequisicao.REVISAO_PENDENTE) {
                assertEquals(ResultadoIntervencaoManual.ESTADO_NAO_PERMITIDO, pedido.revisarTecnicamente(DecisaoManual.APROVAR));
            }
        }
    }

    private static Requisicao pedido(EstadoRequisicao estado, int condicao) {
        var teto = new BigDecimal(condicao == 2 ? "2000.00" : "1000.00");
        var saldo = condicao == 0 ? new BigDecimal("800.00") : teto;
        var departamento = Departamento.reconstituir(UUID.randomUUID(), "Engenharia", teto, saldo);
        var recurso = new RecursoCatalogo(UUID.randomUUID(), "Recurso", new BigDecimal("12.50"));
        return Requisicao.reconstituir(UUID.randomUUID(), departamento, List.of(new ItemRequisicao(recurso, 72)),
                estado.statusDominio(), new BigDecimal("900.00"),
                estado == EstadoRequisicao.REVISAO_PENDENTE ? ResultadoValidacao.REVISAO_POR_RESERVA : null);
    }
}
