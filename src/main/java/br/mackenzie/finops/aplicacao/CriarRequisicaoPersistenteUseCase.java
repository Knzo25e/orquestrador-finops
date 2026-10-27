package br.mackenzie.finops.aplicacao;

import br.mackenzie.finops.aplicacao.entradas.ItemSolicitado;
import br.mackenzie.finops.aplicacao.entradas.SolicitacaoCriacaoRequisicao;
import br.mackenzie.finops.aplicacao.portas.CriacaoRequisicaoPersistente;
import br.mackenzie.finops.aplicacao.resultados.ResultadoCriacaoRequisicao;

import java.util.List;
import java.util.UUID;

    /**
 * Entrada da criação persistente, conforme a SPEC-005 / Issue #65.
 *
 * Valida e copia a solicitação antes de delegar à porta responsável
 * pela operação transacional.
    */
public final class CriarRequisicaoPersistenteUseCase {

    private final CriacaoRequisicaoPersistente criacao;

    public CriarRequisicaoPersistenteUseCase(
            CriacaoRequisicaoPersistente criacao
    ) {
        if (criacao == null) {
            throw new IllegalArgumentException(
                    "A operação de criação persistente é obrigatória."
            );
        }

        this.criacao = criacao;
    }

    public ResultadoCriacaoRequisicao executar(
            UUID requisicaoId,
            UUID departamentoId,
            List<ItemSolicitado> itens
    ) {
        SolicitacaoCriacaoRequisicao solicitacao =
                new SolicitacaoCriacaoRequisicao(
                        requisicaoId,
                        departamentoId,
                        itens
                );

        return criacao.executar(solicitacao);
    }
    /**
     * Criacao com autoria obrigatoria.
     * A autoria deve ser fornecida por uma integracao confiavel.
     * Este metodo ainda nao realiza autenticacao ou autorizacao.
    */
    public ResultadoCriacaoRequisicao executar(
            UUID requisicaoId,
            UUID departamentoId,
            List<ItemSolicitado> itens,
            UUID desenvolvedorId
    ) {
        SolicitacaoCriacaoRequisicao solicitacao =
                SolicitacaoCriacaoRequisicao.comAutoria(
                        requisicaoId,
                        departamentoId,
                        itens,
                        desenvolvedorId
                );

        return criacao.executar(solicitacao);
    }
}