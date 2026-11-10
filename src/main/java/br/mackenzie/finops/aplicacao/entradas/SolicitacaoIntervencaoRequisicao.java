package br.mackenzie.finops.aplicacao.entradas;

import br.mackenzie.finops.aplicacao.excecoes.EntradaRequisicaoInvalidaException;
import br.mackenzie.finops.dominio.entidades.DecisaoManual;
import java.util.UUID;

public record SolicitacaoIntervencaoRequisicao(UUID operacaoId, DecisaoManual decisao, String justificativa) {
    public SolicitacaoIntervencaoRequisicao {
        if (operacaoId == null || decisao == null || justificativa == null) {
            throw new EntradaRequisicaoInvalidaException();
        }
        justificativa = justificativa.strip();
        if (justificativa.isEmpty() || justificativa.codePointCount(0, justificativa.length()) > 500) {
            throw new EntradaRequisicaoInvalidaException();
        }
    }
}
