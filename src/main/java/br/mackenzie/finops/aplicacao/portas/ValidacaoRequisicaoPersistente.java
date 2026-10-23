package br.mackenzie.finops.aplicacao.portas;

import br.mackenzie.finops.aplicacao.excecoes.ConfirmacaoValidacaoDesconhecidaException;
import br.mackenzie.finops.aplicacao.excecoes.FalhaValidacaoPersistenteException;
import br.mackenzie.finops.aplicacao.resultados.ResultadoValidacaoPersistente;

import java.util.UUID;

/**
 * Contrato de validação persistente, conforme a SPEC-005 / Issue #67.
 *
 * A implementação controla conexão, bloqueios e transação.
 * O contrato não expõe tipos JDBC.
 */
public interface ValidacaoRequisicaoPersistente {

    /**
     * Valida uma requisição utilizando as regras financeiras do domínio.
     *
     * Alterações de saldo, estado e motivo são confirmadas na mesma
     * transação antes do retorno.
     *
     * Pedido já liberado retorna JA_LIBERADA sem novo desconto.
     * Pedido em revisão retorna ESTADO_NAO_PERMITIDO, preservando
     * estado, motivo e saldo.
     *
     * Resultados sem alterações somente são retornados após encerrar
     * a transação de leitura e liberar os bloqueios.
     *
     * Uma falha posterior de fechamento é sinalizada no resultado,
     * sem transformar uma operação confirmada em operação abortada.
     *
     * @throws IllegalArgumentException se o UUID for nulo ou a
     *         requisição não existir na consulta inicial
     * @throws ConfirmacaoValidacaoDesconhecidaException se não for
     *         possível determinar se o commit foi efetivado;
     *         a reconciliação deve utilizar o mesmo UUID
     * @throws FalhaValidacaoPersistenteException se ocorrer falha
     *         de infraestrutura ou inconsistência nos dados armazenados
     */
    ResultadoValidacaoPersistente executar(UUID requisicaoId);
}