package br.mackenzie.finops.aplicacao;

import br.mackenzie.finops.aplicacao.portas.ValidacaoRequisicaoPersistente;
import br.mackenzie.finops.aplicacao.resultados.ResultadoValidacaoPersistente;

import java.util.UUID;

/**
 * Entrada da validação persistente, conforme a SPEC-005 / Issue #67.
 *
 * Valida o identificador antes de delegar à operação transacional.
 */
public final class ValidarRequisicaoPersistenteUseCase {

    private final ValidacaoRequisicaoPersistente validacao;

    public ValidarRequisicaoPersistenteUseCase(
            ValidacaoRequisicaoPersistente validacao
    ) {
        if (validacao == null) {
            throw new IllegalArgumentException(
                    "A operação de validação persistente é obrigatória."
            );
        }

        this.validacao = validacao;
    }

    public ResultadoValidacaoPersistente executar(UUID requisicaoId) {
        if (requisicaoId == null) {
            throw new IllegalArgumentException(
                    "O identificador da requisição é obrigatório."
            );
        }

        return validacao.executar(requisicaoId);
    }
}