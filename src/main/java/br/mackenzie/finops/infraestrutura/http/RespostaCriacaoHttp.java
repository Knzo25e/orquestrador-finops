package br.mackenzie.finops.infraestrutura.http;

import br.mackenzie.finops.aplicacao.resultados.ResultadoCriacaoRequisicao;
import br.mackenzie.finops.aplicacao.resultados.FotografiaRequisicao;
import br.mackenzie.finops.dominio.entidades.EstadoRequisicao;
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
        var fotografia = FotografiaRequisicao.de(requisicao);
        List<Item> itens = fotografia.itens().stream()
                .map(item -> new Item(
                        item.recursoId(), item.quantidade(), item.precoUnitario(), item.subtotal()
                ))
                .toList();

        return new Pedido(fotografia.id(), fotografia.estado(), fotografia.motivoRevisao(), fotografia.custoTotalProjetado(), itens);
    }

    static String estadoPublico(String estado) {
        return EstadoRequisicao.deStatus(estado).name();

    }

    static String motivoPublico(ResultadoValidacao motivo) {
        return FotografiaRequisicao.motivoPublico(motivo);

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
