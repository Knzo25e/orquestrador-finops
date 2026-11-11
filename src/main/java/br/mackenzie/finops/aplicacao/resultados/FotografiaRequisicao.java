package br.mackenzie.finops.aplicacao.resultados;

import br.mackenzie.finops.dominio.entidades.EstadoRequisicao;
import br.mackenzie.finops.dominio.entidades.Requisicao;
import br.mackenzie.finops.dominio.entidades.ResultadoValidacao;
import java.util.List;
import java.util.UUID;

/** Contrato imutável compartilhado por consulta HTTP e recibo da intervenção. */
public record FotografiaRequisicao(UUID id, String estado, String motivoRevisao,
                                   String custoTotalProjetado, List<Item> itens) {
    public FotografiaRequisicao {
        itens = List.copyOf(itens);
    }

    public static FotografiaRequisicao de(Requisicao requisicao) {
        var itens = requisicao.getItens().stream().map(item -> new Item(item.getRecurso().getId(),
                item.getQuantidade(), item.getPrecoUnitario().toPlainString(), item.getCustoSubtotal().toPlainString())).toList();
        return new FotografiaRequisicao(requisicao.getId(), EstadoRequisicao.deStatus(requisicao.getStatus()).name(),
                motivoPublico(requisicao.getMotivoRevisao()), requisicao.getCustoTotalProjetado().toPlainString(), itens);
    }

    public static String motivoPublico(ResultadoValidacao motivo) {
        return motivo == null ? null : switch (motivo) {
            case REVISAO_POR_SALDO_INSUFICIENTE -> "SALDO_INSUFICIENTE";
            case REVISAO_POR_RESERVA -> "RESERVA";
            default -> throw new IllegalStateException("Motivo sem representação pública.");
        };
    }

    public record Item(UUID recursoId, int quantidade, String precoUnitario, String subtotal) {}
}
