package br.mackenzie.finops.infraestrutura.persistencia;

/**
 * Conflito de chave durante a inserção do cabeçalho da requisição.
 *
 * Não indica, isoladamente, que a solicitação é uma repetição equivalente.
 * O chamador deve encerrar a transação e conferir o pedido existente
 * em uma nova transação.
 */
final class RequisicaoDuplicadaException extends IllegalArgumentException {

    RequisicaoDuplicadaException() {
        super(
                "Já existe uma requisição com esse identificador. "
                        + "Execute rollback da transação."
        );
    }
}