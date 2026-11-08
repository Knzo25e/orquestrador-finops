package br.mackenzie.finops.dominio.entidades;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static br.mackenzie.finops.dominio.entidades.ResultadoAjusteOrcamentario.Situacao.*;
import static org.junit.jupiter.api.Assertions.*;

class AjusteOrcamentarioTest {
    @Test
    void ajustaPreservandoComprometidoEAceitaSaldoZero() {
        var departamento = new Departamento(UUID.randomUUID(), "Engenharia", new BigDecimal("1000.00"));
        departamento.descontarSaldo(new BigDecimal("700.00"));
        var aumento = departamento.ajustarTeto(new BigDecimal("1500.00"));
        assertEquals(TETO_AJUSTADO, aumento.situacao());
        assertEquals(new BigDecimal("800.00"), aumento.saldoNovo());
        assertEquals(new BigDecimal("500.00"), aumento.variacaoTeto());
        var reducao = departamento.ajustarTeto(new BigDecimal("900.00"));
        assertEquals(new BigDecimal("200.00"), reducao.saldoNovo());
        assertEquals(new BigDecimal("-600.00"), reducao.variacaoTeto());
        var negativa = departamento.ajustarTeto(new BigDecimal("699.99"));
        assertEquals(TETO_ABAIXO_DO_COMPROMETIDO, negativa.situacao());
        assertEquals(new BigDecimal("900.00"), negativa.tetoNovo());
        assertEquals(new BigDecimal("200.00"), negativa.saldoNovo());
        var limite = departamento.ajustarTeto(new BigDecimal("700.00"));
        assertEquals(TETO_AJUSTADO, limite.situacao());
        assertEquals(new BigDecimal("0.00"), departamento.getSaldoDisponivel());
        assertEquals(new BigDecimal("700.00"), limite.comprometido());
        assertEquals(new BigDecimal("70.00"), departamento.calcularReserva());
        assertEquals(TETO_INALTERADO, departamento.ajustarTeto(new BigDecimal("700.00")).situacao());
        assertEquals(new BigDecimal("800.00"), aumento.saldoNovo());
    }

    @Test
    void entradasInvalidasNaoAlteramTetoOuSaldo() {
        var departamento = new Departamento(UUID.randomUUID(), "Engenharia", new BigDecimal("100.00"));
        for (BigDecimal valor : List.of(new BigDecimal("-1.00"), new BigDecimal("0.001"))) {
            assertThrows(IllegalArgumentException.class, () -> departamento.ajustarTeto(valor));
        }
        assertThrows(IllegalArgumentException.class, () -> departamento.ajustarTeto(null));
        assertEquals(new BigDecimal("100.00"), departamento.getOrcamentoMensal());
        assertEquals(new BigDecimal("100.00"), departamento.getSaldoDisponivel());
    }
}
