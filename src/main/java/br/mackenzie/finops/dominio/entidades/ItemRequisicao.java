package br.mackenzie.finops.dominio.entidades;

public class ItemRequisicao {
    private RecursoCatalogo recurso;
    private Integer quantidade;
    private Double custoSubtotal;

    public ItemRequisicao(RecursoCatalogo recurso, Integer quantidade) {
        this.recurso = recurso;
        this.quantidade = quantidade;
        this.custoSubtotal = calcularSubtotal();
    }

    private Double calcularSubtotal() {
        return this.recurso.getCustoMensal() * this.quantidade;
    }

    public RecursoCatalogo getRecurso() { return recurso; }
    public Integer getQuantidade() { return quantidade; }
    public Double getCustoSubtotal() { return custoSubtotal; }
}