package br.mackenzie.finops.aplicacao;

import br.mackenzie.finops.aplicacao.entradas.ItemSolicitado;
import br.mackenzie.finops.aplicacao.excecoes.AcessoNegadoException;
import br.mackenzie.finops.aplicacao.portas.CriacaoComIdentidadeTransacional;
import br.mackenzie.finops.aplicacao.portas.CriacaoRequisicaoAutorizadaPersistente;
import br.mackenzie.finops.aplicacao.portas.ExecucaoComIdentidadeCoordenada;
import br.mackenzie.finops.aplicacao.resultados.ResultadoCriacaoRequisicao;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Entrada de criação com identidade coordenada.
 *
 * Autor e departamento não são parâmetros da entrada.
 */
public final class CriarRequisicaoCoordenadaUseCase {

    private final BiFunction<UUID, List<ItemSolicitado>, ResultadoCriacaoRequisicao> executarCriacao;

    public CriarRequisicaoCoordenadaUseCase(
            ExecucaoComIdentidadeCoordenada identidades,
            CriacaoRequisicaoAutorizadaPersistente criacao
    ) {
        if (identidades == null || criacao == null) {
            throw new IllegalArgumentException(
                    "A coordenação de identidade e a criação são obrigatórias."
            );
        }

        executarCriacao = (requisicaoId, itens) -> identidades.executar(identidade -> {
            if (identidade == null) {
                throw new IllegalStateException(
                        "A coordenação retornou uma identidade inválida."
                );
            }

            // A fotografia é utilizada somente dentro do período
            // protegido pela coordenação.
            CriarRequisicaoAutorizadaUseCase entrada =
                    new CriarRequisicaoAutorizadaUseCase(
                            () -> Optional.of(identidade),
                            criacao
                    );

            // Chamada síncrona: a coordenação permanece ativa até
            // a operação transacional retornar ou lançar uma exceção.
            return entrada.executar(requisicaoId, itens);
        });
    }

    public CriarRequisicaoCoordenadaUseCase(
            Supplier<Optional<UUID>> usuarioAutenticado,
            CriacaoComIdentidadeTransacional criacao
    ) {
        if (usuarioAutenticado == null || criacao == null) {
            throw new IllegalArgumentException("A sessão e a criação são obrigatórias.");
        }
        executarCriacao = (requisicaoId, itens) -> criacao.executarComIdentidade(
                usuarioAutenticado.get().orElseThrow(AcessoNegadoException::new),
                requisicaoId, itens
        );
    }

    public ResultadoCriacaoRequisicao executar(UUID requisicaoId, List<ItemSolicitado> itens) {
        return executarCriacao.apply(requisicaoId, itens);
    }
}
