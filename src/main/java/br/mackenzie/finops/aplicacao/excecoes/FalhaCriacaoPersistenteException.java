package br.mackenzie.finops.aplicacao.excecoes;

/**
 * Falha de infraestrutura durante a criação ou consulta de repetição.
 *
 * Esta exceção, isoladamente, não comprova que rollback foi concluído.
 * Falhas durante commit possuem um tipo específico.
 */
public final class FalhaCriacaoPersistenteException
        extends RuntimeException {

    public FalhaCriacaoPersistenteException() {
        super(
                "Não foi possível concluir a operação de criação persistente. "
                        + "Uma nova tentativa deve preservar o UUID "
                        + "e o conteúdo da solicitação."
        );
    }
}