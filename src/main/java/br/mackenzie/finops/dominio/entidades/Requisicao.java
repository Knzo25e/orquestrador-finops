package br.mackenzie.finops.dominio.entidades;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Requisicao {
    
    private UUID id;
    private Departamento departamento; // Exigência da Spec 002
    private UUID desenvolvedorId;
    private String status;
    private Double custoTotalProjetado;
    private String justificativaArquiteto;
    private List<ItemRequisicao> itens; // Exigência para o Caso de Uso

    // Novo construtor adaptado para obedecer à Spec 002
    public Requisicao(Departamento departamento) {
        this.id = UUID.randomUUID();
        this.departamento = departamento;
        this.status = "INICIAL"; // Status padronizado pela documentação
        this.custoTotalProjetado = 0.0;
        this.itens = new ArrayList<>();
    }

    // Método exigido pela orquestração do nosso Caso de Uso
    public void adicionarItem(ItemRequisicao item) {
        this.itens.add(item);
    }

    // Mantendo a sua lógica original de FinOps!
    public void processarRoteamento(Departamento departamento) {
        if (departamento.verificarDisponibilidade(this.custoTotalProjetado)) {
            aprovarAutomaticamente(departamento);
        } else {
            enviarParaRevisao();
        }
    }

    private void aprovarAutomaticamente(Departamento departamento) {
        departamento.descontarSaldo(this.custoTotalProjetado);
        this.status = "Provisionamento Liberado";
    }

    private void enviarParaRevisao() {
        this.status = "Revisao Pendente";
    }

    // Getters
    public UUID getId() { return id; }
    public String getStatus() { return status; }
    public Double getCustoTotalProjetado() { return custoTotalProjetado; }
    public Departamento getDepartamento() { return departamento; }
    public List<ItemRequisicao> getItens() { return itens; }
}