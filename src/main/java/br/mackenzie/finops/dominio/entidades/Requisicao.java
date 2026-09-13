package main.java.br.mackenzie.finops.dominio.entidades;

import java.util.UUID;

public class Requisicao {
    private UUID id;
    private UUID desenvolvedorId;
    private String status;
    private Double custoTotalProjetado;
    private String justificativaArquiteto;

    public Requisicao(UUID id, UUID desenvolvedorId, Double custoTotalProjetado) {
        this.id = id;
        this.desenvolvedorId = desenvolvedorId;
        this.custoTotalProjetado = custoTotalProjetado;
        this.status = "Em Analise";
    }

    // Interação direta com a classe Departamento
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

    public UUID getId() { return id; }
    public String getStatus() { return status; }
    public Double getCustoTotalProjetado() { return custoTotalProjetado; }
}