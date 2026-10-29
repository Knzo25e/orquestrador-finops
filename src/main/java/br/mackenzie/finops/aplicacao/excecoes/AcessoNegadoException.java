package br.mackenzie.finops.aplicacao.excecoes;

/**
 * Indica que a operação não foi autorizada.
 *
 * A mensagem não expõe identificadores, perfis, vínculos
 * ou a existência de uma requisição.
 */
public final class AcessoNegadoException extends RuntimeException {

    public AcessoNegadoException() {
        super("Operação não autorizada.");
    }
}