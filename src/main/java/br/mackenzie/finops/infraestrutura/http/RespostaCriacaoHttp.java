package br.mackenzie.finops.infraestrutura.http;

import br.mackenzie.finops.aplicacao.resultados.ResultadoCriacaoRequisicao;
import br.mackenzie.finops.dominio.entidades.Requisicao;
import br.mackenzie.finops.dominio.entidades.ResultadoValidacao;

import java.util.List;
import java.util.UUID;

public record RespostaCriacaoHttp(
        String situacao,
        Pedido requisicao,
        List<Aviso> avisos
) {
    static RespostaCriacaoHttp de(ResultadoCriacaoRequisicao resultado) {
        return new RespostaCriacaoHttp(resultado.situacao().name(), pedido(resultado.requisicao()),
                avisos(resultado.falhaNoFechamento()));
    }

    static Pedido pedido(Requisicao requisicao) {
        List<Item> itens = requisicao.getItens().stream()
                .map(item -> new Item(
                        item.getRecurso().getId(),
                        item.getQuantidade(),
                        item.getPrecoUnitario().toPlainString(),
                        item.getCustoSubtotal().toPlainString()
                ))
                .toList();

        return new Pedido(requisicao.getId(), estadoPublico(requisicao.getStatus()),
                motivoPublico(requisicao.getMotivoRevisao()), requisicao.getCustoTotalProjetado().toPlainString(), itens);
    }

    static String estadoPublico(String estado) {
        return switch (estado) {
            case "INICIAL" -> "INICIAL";
            case "Provisionamento Liberado" -> "PROVISIONAMENTO_LIBERADO";
            case "Revisão Pendente" -> "REVISAO_PENDENTE";
            default -> throw new IllegalStateException(
                    "Estado sem representação no contrato HTTP."
            );
        };

    }

    static String motivoPublico(ResultadoValidacao motivo) {
        return motivo == null ? null : switch (motivo) {
            case REVISAO_POR_SALDO_INSUFICIENTE -> "SALDO_INSUFICIENTE";
            case REVISAO_POR_RESERVA -> "RESERVA";
            default -> throw new IllegalStateException(
                    "Motivo sem representação no contrato HTTP."
            );
        };

    }

    static List<Aviso> avisos(boolean falhaNoFechamento) {
        return falhaNoFechamento
                ? List.of(new Aviso(
                        "FALHA_NO_FECHAMENTO",
                        "A operação foi concluída, mas houve uma falha ao encerrar seus recursos."
                ))
                : List.of();

    }

    public record Pedido(
            UUID id,
            String estado,
            String motivoRevisao,
            String custoTotalProjetado,
            List<Item> itens
    ) {}

    public record Item(
            UUID recursoId,
            int quantidade,
            String precoUnitario,
            String subtotal
    ) {}

    public record Aviso(String codigo, String mensagem) {}
}
