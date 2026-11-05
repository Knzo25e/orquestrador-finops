package br.mackenzie.finops.aplicacao.excecoes;

public final class SolicitacaoInvalidaException
        extends IllegalArgumentException {

    public SolicitacaoInvalidaException(String mensagem) {
        super(mensagem);
    }
}