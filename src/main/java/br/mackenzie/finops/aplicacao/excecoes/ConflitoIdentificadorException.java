package br.mackenzie.finops.aplicacao.excecoes;

import java.util.UUID;

/**
 * O UUID já pertence a uma solicitação com conteúdo diferente.
 */
public final class ConflitoIdentificadorException
        extends IllegalArgumentException {

    private final UUID requisicaoId;

    public ConflitoIdentificadorException(UUID requisicaoId) {
        super(
                "O identificador da requisição já está associado "
                        + "a uma solicitação diferente."
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