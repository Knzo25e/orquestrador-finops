package br.mackenzie.finops.aplicacao.excecoes;

import java.util.UUID;

/**
 * A chamada de commit falhou e sua efetivação não pôde ser determinada.
 *
 * Uma tentativa posterior de rollback não elimina essa incerteza.
 * A reconciliação deve preservar o UUID e o conteúdo originais.
 */
public final class ConfirmacaoCriacaoDesconhecidaException
        extends RuntimeException {

    private final UUID requisicaoId;

    public ConfirmacaoCriacaoDesconhecidaException(UUID requisicaoId) {
        super(
                "Não foi possível determinar se a criação foi confirmada. "
                        + "Repita a solicitação com o mesmo UUID "
                        + "e o mesmo conteúdo para reconciliar o resultado."
        );

        if (requisicaoId == null) {
            throw new IllegalArgumentException(
                    "O identificador da requisição é obrigatório."
            );
        }

        this.requisicaoId = requisicaoId;
    }

    public UUID getRequisicaoId() {
        return requisicaoId;
    }
}