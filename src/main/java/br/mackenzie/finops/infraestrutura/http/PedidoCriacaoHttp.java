package br.mackenzie.finops.infraestrutura.http;

import br.mackenzie.finops.aplicacao.entradas.ItemSolicitado;

import java.util.List;
import java.util.UUID;

record PedidoCriacaoHttp(UUID requisicaoId, List<ItemSolicitado> itens) {

    static final int MAXIMO_ITENS = 100;

    PedidoCriacaoHttp {
        if (requisicaoId == null) {
            throw new IllegalArgumentException("O identificador da requisição é obrigatório.");
        }

        if (itens == null || itens.isEmpty() || itens.size() > MAXIMO_ITENS) {
            throw new IllegalArgumentException(
                    "A requisição deve conter entre 1 e " + MAXIMO_ITENS + " itens."
            );
        }

        itens = List.copyOf(itens);
    }
}