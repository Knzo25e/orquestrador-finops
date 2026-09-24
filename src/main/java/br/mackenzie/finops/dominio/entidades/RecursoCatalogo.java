package br.mackenzie.finops.dominio.entidades;

import java.util.UUID;

public class RecursoCatalogo {
    private UUID id;
    private String nome;
    private Double custoMensal;

    public RecursoCatalogo(UUID id, String nome, Double custoMensal) {
        this.id = id;
        this.nome = nome;
        this.custoMensal = custoMensal;
    }

    public UUID getId() { return id; }
    public String getNome() { return nome; }
    public Double getCustoMensal() { return custoMensal; }
}