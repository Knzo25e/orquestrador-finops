package br.mackenzie.finops.aplicacao.excecoes;

import java.util.UUID;

public final class ConfirmacaoIntervencaoDesconhecidaException extends RuntimeException {
    private final UUID operacaoId;

    public ConfirmacaoIntervencaoDesconhecidaException(UUID operacaoId) {
        super("Não foi possível confirmar a intervenção. Reconcilie explicitamente com a mesma entrada.");
        this.operacaoId = operacaoId;
    }

    public UUID getOperacaoId() {
        return operacaoId;
    }
}
