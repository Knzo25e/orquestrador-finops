package br.mackenzie.finops.dominio.entidades;

import java.math.BigDecimal;

public class ItemRequisicao {
    private RecursoCatalogo recurso;
    private Integer quantidade;
    private BigDecimal custoSubtotal;

    public ItemRequisicao(RecursoCatalogo recurso, Integer quantidade) {
        if (recurso == null) {
            throw new IllegalArgumentException(
                    "O recurso do item é obrigatório."
            );
        }

        if (quantidade == null || quantidade <= 0) {
            throw new IllegalArgumentException(
                    "A quantidade do item deve ser um inteiro maior que zero."
            );
        }

        this.recurso = recurso;
        this.quantidade = quantidade;
        this.custoSubtotal = calcularSubtotal();
    }

    private BigDecimal calcularSubtotal() {
        return this.recurso.getCustoMensal()
                .multiply(BigDecimal.valueOf(this.quantidade));
    }

    public RecursoCatalogo getRecurso() {
        return recurso;
    }

    public Integer getQuantidade() {
        return quantidade;
    }

    public BigDecimal getCustoSubtotal() {
        return custoSubtotal;
    }
}