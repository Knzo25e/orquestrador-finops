package br.mackenzie.finops.dominio.entidades;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Requisicao {
    private UUID id;
    private Departamento departamento;
    private UUID desenvolvedorId;
    private String status;
    private BigDecimal custoTotalProjetado;
    private String justificativaArquiteto;
    private List<ItemRequisicao> itens;

    public Requisicao(Departamento departamento) {
        if (departamento == null) {
            throw new IllegalArgumentException(
                    "Uma requisição não pode ser criada sem um departamento solicitante."
            );
        }

        this.id = UUID.randomUUID();
        this.departamento = departamento;
        this.status = "INICIAL";
        this.custoTotalProjetado = new BigDecimal("0.00");
        this.itens = new ArrayList<>();
    }

    public void adicionarItem(ItemRequisicao item) {
        if (item == null) {
            throw new IllegalArgumentException(
                    "O item da requisição é obrigatório."
            );
        }

        this.itens.add(item);
        calcularTotalProjetado();
    }

    public BigDecimal calcularTotalProjetado() {
        BigDecimal total = new BigDecimal("0.00");

        for (ItemRequisicao item : this.itens) {
            total = total.add(item.getCustoSubtotal());
        }

        this.custoTotalProjetado = total;
        return total;
    }

    // Roteamento existente; ainda não implementa a reserva da SPEC-003.
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

    public UUID getId() {
        return id;
    }

    public String getStatus() {
        return status;
    }

    public BigDecimal getCustoTotalProjetado() {
        return custoTotalProjetado;
    }

    public Departamento getDepartamento() {
        return departamento;
    }

    public List<ItemRequisicao> getItens() {
        return itens;
    }
}