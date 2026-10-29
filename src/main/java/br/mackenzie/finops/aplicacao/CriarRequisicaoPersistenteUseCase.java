package br.mackenzie.finops.aplicacao;

import br.mackenzie.finops.aplicacao.entradas.ItemSolicitado;
import br.mackenzie.finops.aplicacao.entradas.SolicitacaoCriacaoRequisicao;
import br.mackenzie.finops.aplicacao.portas.CriacaoRequisicaoPersistente;
import br.mackenzie.finops.aplicacao.resultados.ResultadoCriacaoRequisicao;

import java.util.List;
import java.util.UUID;

/**
 * Entrada interna de compatibilidade da criação persistente.
 *
 * Preserva os contratos anteriores da SPEC-005 e os verificadores
 * de persistência e autoria.
 *
 * Não obtém identidade confiável, não verifica autorização e não
 * coordena mudanças de vínculo. Informar um autor não autentica
 * nem autoriza o solicitante.
 *
 * Adaptadores voltados ao usuário devem utilizar
 * CriarRequisicaoCoordenadaUseCase, com uma implementação confiável
 * de ExecucaoComIdentidadeCoordenada.
 *
 * Não expor esta entrada diretamente em controllers, endpoints,
 * comandos ou interfaces destinados ao usuário.
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