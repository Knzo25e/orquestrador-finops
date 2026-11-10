package br.mackenzie.finops.aplicacao.excecoes;

public final class ConflitoOperacaoException extends RuntimeException {
    public ConflitoOperacaoException() {
        super("OPERACAO_ID_EM_CONFLITO");
    }
}
