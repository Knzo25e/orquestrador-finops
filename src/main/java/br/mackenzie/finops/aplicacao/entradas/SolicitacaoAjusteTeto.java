package br.mackenzie.finops.aplicacao.entradas;

import br.mackenzie.finops.aplicacao.excecoes.EntradaRequisicaoInvalidaException;
import java.math.BigDecimal;
import java.util.UUID;

public record SolicitacaoAjusteTeto(UUID operacaoId, String novoTeto, String justificativa) {
    public SolicitacaoAjusteTeto {
        if (operacaoId == null || novoTeto == null || !novoTeto.matches("[0-9]{1,17}\\.[0-9]{2}")
                || justificativa == null) {
            throw new EntradaRequisicaoInvalidaException();
        }
        justificativa = justificativa.strip();
        if (justificativa.isEmpty() || justificativa.codePointCount(0, justificativa.length()) > 500) {
            throw new EntradaRequisicaoInvalidaException();
        }
        novoTeto = new BigDecimal(novoTeto).toPlainString();
    }

    public BigDecimal valorNovoTeto() {
        return new BigDecimal(novoTeto);
    }
}
