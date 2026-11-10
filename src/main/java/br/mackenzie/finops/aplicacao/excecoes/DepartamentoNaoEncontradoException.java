package br.mackenzie.finops.aplicacao.excecoes;

public final class DepartamentoNaoEncontradoException extends RuntimeException {
    public DepartamentoNaoEncontradoException() {
        super("Departamento não encontrado.");
    }
}
