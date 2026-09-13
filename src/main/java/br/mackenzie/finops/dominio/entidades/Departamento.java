package br.mackenzie.finops.dominio.entidades;

import java.util.UUID;

public class Departamento {
    private UUID id;
    private String nome;
    private Double orcamentoMensal;
    private Double saldoDisponivel;

    public Departamento(UUID id, String nome, Double orcamentoMensal) {
        this.id = id;
        this.nome = nome;
        this.orcamentoMensal = orcamentoMensal;
        this.saldoDisponivel = orcamentoMensal;
    }

    public boolean verificarDisponibilidade(Double valor) {
        return this.saldoDisponivel >= valor;
    }

    public void descontarSaldo(Double valor) {
        if (verificarDisponibilidade(valor)) {
            this.saldoDisponivel -= valor;
        } else {
            throw new IllegalArgumentException("Saldo insuficiente para aprovação automática.");
        }
    }

    public UUID getId() { return id; }
    public String getNome() { return nome; }
    public Double getOrcamentoMensal() { return orcamentoMensal; }
    public Double getSaldoDisponivel() { return saldoDisponivel; }
}