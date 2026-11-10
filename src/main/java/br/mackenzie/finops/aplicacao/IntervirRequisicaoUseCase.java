package br.mackenzie.finops.aplicacao;

import br.mackenzie.finops.aplicacao.entradas.SolicitacaoIntervencaoRequisicao;
import br.mackenzie.finops.aplicacao.excecoes.AcessoNegadoException;
import br.mackenzie.finops.aplicacao.excecoes.EntradaRequisicaoInvalidaException;
import br.mackenzie.finops.aplicacao.portas.IntervencaoRequisicaoPersistente;
import br.mackenzie.finops.aplicacao.resultados.ResultadoIntervencaoRequisicao;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class IntervirRequisicaoUseCase {
    private final Supplier<Optional<UUID>> usuario;
    private final IntervencaoRequisicaoPersistente intervencao;

    public IntervirRequisicaoUseCase(Supplier<Optional<UUID>> usuario, IntervencaoRequisicaoPersistente intervencao) {
        this.usuario = usuario;
        this.intervencao = intervencao;
    }

    public ResultadoIntervencaoRequisicao revisar(UUID requisicao, SolicitacaoIntervencaoRequisicao pedido) {
        return executar(requisicao, pedido, true);
    }

    public ResultadoIntervencaoRequisicao liberar(UUID requisicao, SolicitacaoIntervencaoRequisicao pedido) {
        return executar(requisicao, pedido, false);
    }

    private ResultadoIntervencaoRequisicao executar(UUID requisicao, SolicitacaoIntervencaoRequisicao pedido, boolean tecnica) {
        var autenticado = usuario.get().orElseThrow(AcessoNegadoException::new);
        if (requisicao == null || pedido == null || tecnica != pedido.decisao().tecnica()) {
            throw new EntradaRequisicaoInvalidaException();
        }
        return intervencao.executarAutorizado(autenticado, requisicao, pedido);
    }
}
