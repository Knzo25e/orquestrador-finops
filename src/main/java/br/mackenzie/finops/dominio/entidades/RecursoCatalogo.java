package br.mackenzie.finops.dominio.entidades;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

public class RecursoCatalogo {
    private UUID id;
    private String nome;
    private BigDecimal custoMensal;

    public RecursoCatalogo(UUID id, String nome, BigDecimal custoMensal) {
        if (custoMensal == null) {
            throw new IllegalArgumentException(
                    "O custo mensal do recurso é obrigatório."
            );
        }

        if (custoMensal.signum() < 0) {
            throw new IllegalArgumentException(
                    "O custo mensal do recurso não pode ser negativo."
            );
        }

        BigDecimal custoValidado;

        try {
            custoValidado = custoMensal.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException excecao) {
            throw new IllegalArgumentException(
                    "O custo mensal do recurso não pode conter frações de centavo.",
                    excecao
            );
        }

        this.id = id;
        this.nome = nome;
        this.custoMensal = custoValidado;
    }

    public UUID getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public BigDecimal getCustoMensal() {
        return custoMensal;
    }
}