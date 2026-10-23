package br.mackenzie.finops.aplicacao.excecoes;

/**
 * Falha de infraestrutura ou inconsistência durante a validação persistente.
 *
 * Esta exceção, isoladamente, não comprova rollback concluído.
 * Falhas durante commit possuem um tipo específico.
 */
public final class FalhaValidacaoPersistenteException
        extends RuntimeException {

    public FalhaValidacaoPersistenteException() {
        super(
                "Não foi possível concluir a validação persistente. "
                        + "Uma nova tentativa deve utilizar "
                        + "o mesmo UUID da requisição."
        );
    }
}