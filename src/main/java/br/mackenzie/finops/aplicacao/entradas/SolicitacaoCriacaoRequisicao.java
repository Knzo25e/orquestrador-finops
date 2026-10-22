package br.mackenzie.finops.aplicacao.entradas;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entrada da criação persistente.
 * O chamador preserva o UUID e o conteúdo ao repetir a solicitação.
 * Os preços serão obtidos do catálogo pelo fluxo de criação.
 */
public record SolicitacaoCriacaoRequisicao(
        UUID requisicaoId,
        UUID departamentoId,
        List<ItemSolicitado> itens
) {
    public SolicitacaoCriacaoRequisicao {
        if (requisicaoId == null) {
            throw new IllegalArgumentException(
                    "O identificador da requisição é obrigatório."
            );
        }

        if (departamentoId == null) {
            throw new IllegalArgumentException(
                    "O identificador do departamento é obrigatório."
            );
        }

        if (itens == null) {
            throw new IllegalArgumentException(
                    "A lista de itens solicitados é obrigatória."
            );
        }

        List<ItemSolicitado> copia = new ArrayList<>(itens);

        if (copia.isEmpty()) {
            throw new IllegalArgumentException(
                    "A requisição deve conter pelo menos um item solicitado."
            );
        }

        for (ItemSolicitado item : copia) {
            if (item == null) {
                throw new IllegalArgumentException(
                        "A lista não pode conter um item solicitado nulo."
                );
            }
        }

        itens = List.copyOf(copia);
    }
}