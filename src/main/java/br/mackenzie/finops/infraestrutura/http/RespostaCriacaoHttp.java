package br.mackenzie.finops.infraestrutura.http;

import br.mackenzie.finops.aplicacao.resultados.ResultadoCriacaoRequisicao;

import java.util.List;
import java.util.UUID;

public record RespostaCriacaoHttp(
        String situacao,
        Pedido requisicao,
        List<Aviso> avisos
) {
    static RespostaCriacaoHttp de(ResultadoCriacaoRequisicao resultado) {
        var requisicao = resultado.requisicao();

        List<Item> itens = requisicao.getItens().stream()
                .map(item -> new Item(
                        item.getRecurso().getId(),
                        item.getQuantidade(),
                        item.getPrecoUnitario().toPlainString(),
                        item.getCustoSubtotal().toPlainString()
                ))
                .toList();

        var motivo = requisicao.getMotivoRevisao();
        var pedido = new Pedido(
                requisicao.getId(),
                requisicao.getStatus(),
                motivo == null ? null : motivo.name(),
                requisicao.getCustoTotalProjetado().toPlainString(),
                itens
        );

        List<Aviso> avisos = resultado.falhaNoFechamento()
                ? List.of(new Aviso(
                        "FALHA_NO_FECHAMENTO",
                        "A operação foi concluída, mas houve uma falha ao encerrar seus recursos."
                ))
                : List.of();

        return new RespostaCriacaoHttp(resultado.situacao().name(), pedido, avisos);
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