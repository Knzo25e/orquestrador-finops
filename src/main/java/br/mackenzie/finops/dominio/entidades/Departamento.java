package br.mackenzie.finops.dominio.entidades;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

public class Departamento {
    private UUID id;
    private String nome;
    private BigDecimal orcamentoMensal;
    private BigDecimal saldoDisponivel;

    public Departamento(UUID id, String nome, BigDecimal orcamentoMensal) {
        BigDecimal orcamentoValidado = validarValorMonetario(
                orcamentoMensal,
                "O orçamento mensal"
        );

        this.id = id;
        this.nome = nome;
        this.orcamentoMensal = orcamentoValidado;
        this.saldoDisponivel = orcamentoValidado;
    }

    public boolean verificarDisponibilidade(BigDecimal valor) {
        BigDecimal valorValidado = validarValorMonetario(
                valor,
                "O valor da consulta"
        );

        return this.saldoDisponivel.compareTo(valorValidado) >= 0;
    }

    public void descontarSaldo(BigDecimal valor) {
        BigDecimal valorValidado = validarValorMonetario(
                valor,
                "O valor do desconto"
        );

        if (this.saldoDisponivel.compareTo(valorValidado) < 0) {
            throw new IllegalArgumentException(
                    "Saldo insuficiente para o desconto."
            );
        }

        this.saldoDisponivel = this.saldoDisponivel.subtract(valorValidado);
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

    public UUID getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public BigDecimal getOrcamentoMensal() {
        return orcamentoMensal;
    }

    public BigDecimal getSaldoDisponivel() {
        return saldoDisponivel;
    }
}