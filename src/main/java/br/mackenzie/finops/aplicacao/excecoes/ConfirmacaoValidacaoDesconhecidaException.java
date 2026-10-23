package br.mackenzie.finops.aplicacao.excecoes;

import java.util.UUID;

/**
 * A chamada de commit falhou e sua efetivação não pôde ser determinada.
 *
 * Uma tentativa posterior de rollback não elimina essa incerteza.
 * A reconciliação deve utilizar o mesmo UUID da requisição.
 */
public final class ConfirmacaoValidacaoDesconhecidaException
        extends RuntimeException {

    private final UUID requisicaoId;

    public ConfirmacaoValidacaoDesconhecidaException(UUID requisicaoId) {
        super(
                "Não foi possível determinar se a validação foi confirmada. "
                        + "Execute novamente a validação com o mesmo UUID "
                        + "da requisição para reconciliar o resultado."
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