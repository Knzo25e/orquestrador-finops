package br.mackenzie.finops.aplicacao;

import br.mackenzie.finops.aplicacao.excecoes.AcessoNegadoException;
import br.mackenzie.finops.aplicacao.portas.ValidacaoRequisicaoAutorizadaPersistente;
import br.mackenzie.finops.aplicacao.resultados.ResultadoValidacaoPersistente;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class ValidarRequisicaoAutorizadaUseCase {
    private final Supplier<Optional<UUID>> usuarioAutenticado;
    private final ValidacaoRequisicaoAutorizadaPersistente validacao;

    public ValidarRequisicaoAutorizadaUseCase(
            Supplier<Optional<UUID>> usuarioAutenticado, ValidacaoRequisicaoAutorizadaPersistente validacao
    ) {
        this.usuarioAutenticado = usuarioAutenticado;
        this.validacao = validacao;
    }

    public ResultadoValidacaoPersistente executar(UUID requisicaoId) {
        return validacao.executarAutorizado(
                usuarioAutenticado.get().orElseThrow(AcessoNegadoException::new), requisicaoId
        );
    }
}
