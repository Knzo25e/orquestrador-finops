package br.mackenzie.finops.dominio.entidades;

import java.math.BigDecimal;

/** Fotografia exata dos valores efetivos antes e depois da decisão. */
public record ResultadoAjusteOrcamentario(
        Situacao situacao, BigDecimal tetoAnterior, BigDecimal tetoNovo,
        BigDecimal saldoAnterior, BigDecimal saldoNovo, BigDecimal comprometido
) {
    public enum Situacao {
        TETO_AJUSTADO, TETO_INALTERADO, TETO_ABAIXO_DO_COMPROMETIDO
    }

    public BigDecimal variacaoTeto() {
        return tetoNovo.subtract(tetoAnterior);
    }
}
