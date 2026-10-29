package br.mackenzie.finops.aplicacao.portas;

import br.mackenzie.finops.aplicacao.entradas.SolicitacaoCriacaoRequisicao;
import br.mackenzie.finops.aplicacao.excecoes.ConfirmacaoCriacaoDesconhecidaException;
import br.mackenzie.finops.aplicacao.excecoes.ConflitoIdentificadorException;
import br.mackenzie.finops.aplicacao.excecoes.FalhaCriacaoPersistenteException;
import br.mackenzie.finops.aplicacao.resultados.ResultadoCriacaoRequisicao;

/**
 * Contrato de criação persistente, conforme a SPEC-005 / Issue #65.
 *
 * A implementação controla os recursos e a transação.
 * O contrato não expõe tipos JDBC.
 */
public interface CriacaoRequisicaoPersistente {

    /**
     * Cria uma requisição ou reconhece uma repetição equivalente.
     *
     * Uma criação somente retorna CRIADA após commit confirmado.
     * Uma repetição equivalente retorna JA_EXISTENTE e preserva
     * os preços históricos, o estado e o motivo armazenados.
     *
     * A equivalência considera a autoria, o departamento e a sequência
     * dos itens, incluindo identificadores dos recursos e quantidades.
     * Duas autorias ausentes preservam a compatibilidade anterior.
     * Autores diferentes, inclusive quando somente um estiver ausente,
     * produzem conflito.
     *
     * Este contrato não autentica nem autoriza o solicitante.
     *
     * O chamador deve preservar o UUID e o conteúdo da solicitação
     * nas novas tentativas, inclusive após confirmação desconhecida.
     *
     * A operação não desconta saldo nem executa roteamento.
     * Uma falha de fechamento posterior ao sucesso é indicada
     * no resultado, sem transformar o sucesso em falha de criação.
     *
     * @throws ConflitoIdentificadorException se o UUID já estiver
     *         associado a uma solicitação com conteúdo diferente
     * @throws IllegalArgumentException se a solicitação for nula
     *         ou possuir dados ou vínculos inválidos
     * @throws ConfirmacaoCriacaoDesconhecidaException se não for
     *         possível determinar se o commit foi efetivado;
     *         uma tentativa de rollback não elimina essa incerteza
     * @throws FalhaCriacaoPersistenteException se ocorrer outra
     *         falha na operação ou no encerramento necessário
     *         para reconhecer uma repetição
     */
    ResultadoCriacaoRequisicao executar(
            SolicitacaoCriacaoRequisicao solicitacao
    );
}