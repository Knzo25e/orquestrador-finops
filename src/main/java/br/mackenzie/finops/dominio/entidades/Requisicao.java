package br.mackenzie.finops.dominio.entidades;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Requisicao {
    private final UUID id;
    private final Departamento departamento;
    private UUID desenvolvedorId;
    private String status;
    private BigDecimal custoTotalProjetado;
    private String justificativaArquiteto;
    private final List<ItemRequisicao> itens;

    private ResultadoValidacao motivoRevisao;

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
        synchronized (departamento) {
            if (item == null) {
                throw new IllegalArgumentException(
                        "O item da requisição é obrigatório."
                );
            }

            if (!"INICIAL".equals(status)) {
                throw new IllegalStateException(
                        "Só é permitido adicionar itens a uma requisição em INICIAL."
                );
            }

            itens.add(item);
            calcularTotalProjetado();
        }
    }

    public BigDecimal calcularTotalProjetado() {
        synchronized (departamento) {
            BigDecimal total = new BigDecimal("0.00");

            for (ItemRequisicao item : itens) {
                total = total.add(item.getCustoSubtotal());
            }

            custoTotalProjetado = total;
            return total;
        }
    }

    public ResultadoValidacao processarRoteamento() {
        synchronized (departamento) {
            if ("Provisionamento Liberado".equals(status)) {
                return ResultadoValidacao.JA_LIBERADA;
            }

            if (!"INICIAL".equals(status)) {
                return ResultadoValidacao.ESTADO_NAO_PERMITIDO;
            }

            if (itens.isEmpty()) {
                throw new IllegalArgumentException(
                        "A requisição deve conter pelo menos um item."
                );
            }

            BigDecimal custo = calcularTotalProjetado();
            BigDecimal saldo = departamento.getSaldoDisponivel();
            BigDecimal reserva = departamento.calcularReserva();
            BigDecimal saldoProjetado = saldo.subtract(custo);

            status = "Em Analise";

            if (custo.compareTo(saldo) > 0) {
                return enviarParaRevisao(
                        ResultadoValidacao.REVISAO_POR_SALDO_INSUFICIENTE
                );
            }

            if (saldoProjetado.compareTo(reserva) <= 0) {
                return enviarParaRevisao(
                        ResultadoValidacao.REVISAO_POR_RESERVA
                );
            }

            departamento.descontarSaldo(custo);
            status = "Provisionamento Liberado";
            return ResultadoValidacao.LIBERADA;
        }
    }

    // Chamado somente dentro da operação coordenada.
    private ResultadoValidacao enviarParaRevisao(
            ResultadoValidacao motivo
    ) {
        motivoRevisao = motivo;
        status = "Revisão Pendente";
        return motivo;
    }

    public UUID getId() {
        return id;
    }

    public Departamento getDepartamento() {
        return departamento;
    }

    public String getStatus() {
        synchronized (departamento) {
            return status;
        }
    }

    public BigDecimal getCustoTotalProjetado() {
        synchronized (departamento) {
            return custoTotalProjetado;
        }
    }

    public ResultadoValidacao getMotivoRevisao() {
        synchronized (departamento) {
            return motivoRevisao;
        }
    }

    public List<ItemRequisicao> getItens() {
        synchronized (departamento) {
            return List.copyOf(itens);
        }
    }
}