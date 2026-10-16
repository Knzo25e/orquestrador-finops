package br.mackenzie.finops.dominio.entidades;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class ItemRequisicao {
    private final RecursoCatalogo recurso;
    private final Integer quantidade;
    private final BigDecimal precoUnitario;
    private final BigDecimal custoSubtotal;

    public ItemRequisicao(RecursoCatalogo recurso, Integer quantidade) {
        validarRecursoEQuantidade(recurso, quantidade);

        this.recurso = recurso;
        this.quantidade = quantidade;
        this.precoUnitario = recurso.getCustoMensal();
        this.custoSubtotal = precoUnitario.multiply(
                BigDecimal.valueOf(quantidade)
        );
    }

    private ItemRequisicao(
            RecursoCatalogo recurso,
            Integer quantidade,
            BigDecimal precoUnitario,
            BigDecimal custoSubtotal
    ) {
        this.recurso = recurso;
        this.quantidade = quantidade;
        this.precoUnitario = precoUnitario;
        this.custoSubtotal = custoSubtotal;
    }

    public static ItemRequisicao reconstituir(
            RecursoCatalogo recurso,
            Integer quantidade,
            BigDecimal precoUnitario,
            BigDecimal subtotalArmazenado
    ) {
        validarRecursoEQuantidade(recurso, quantidade);

        if (recurso.getId() == null) {
            throw new IllegalArgumentException(
                    "O identificador do recurso é obrigatório."
            );
        }

        BigDecimal precoValidado = validarValorMonetario(
                precoUnitario,
                "O preço unitário"
        );

        BigDecimal subtotalValidado = validarValorMonetario(
                subtotalArmazenado,
                "O subtotal armazenado"
        );

        BigDecimal subtotalCalculado = precoValidado.multiply(
                BigDecimal.valueOf(quantidade)
        );

        if (subtotalValidado.compareTo(subtotalCalculado) != 0) {
            throw new IllegalArgumentException(
                    "O subtotal armazenado deve ser igual ao preço unitário multiplicado pela quantidade."
            );
        }

        return new ItemRequisicao(
                recurso,
                quantidade,
                precoValidado,
                subtotalValidado
        );
    }

    private static void validarRecursoEQuantidade(
            RecursoCatalogo recurso,
            Integer quantidade
    ) {
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
    }

    private static BigDecimal validarValorMonetario(
            BigDecimal valor,
            String campo
    ) {
        if (valor == null) {
            throw new IllegalArgumentException(
                    campo + " é obrigatório."
            );
        }

        if (valor.signum() < 0) {
            throw new IllegalArgumentException(
                    campo + " não pode ser negativo."
            );
        }

        try {
            return valor.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException excecao) {
            throw new IllegalArgumentException(
                    campo + " não pode conter frações de centavo.",
                    excecao
            );
        }
    }

    public RecursoCatalogo getRecurso() {
        return recurso;
    }

    public Integer getQuantidade() {
        return quantidade;
    }

    public BigDecimal getPrecoUnitario() {
        return precoUnitario;
    }

    public BigDecimal getCustoSubtotal() {
        return custoSubtotal;
    }
}