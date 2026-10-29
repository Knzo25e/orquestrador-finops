package br.mackenzie.finops.aplicacao;

import br.mackenzie.finops.aplicacao.entradas.ItemSolicitado;
import br.mackenzie.finops.aplicacao.portas.CriacaoRequisicaoAutorizadaPersistente;
import br.mackenzie.finops.aplicacao.portas.ExecucaoComIdentidadeCoordenada;
import br.mackenzie.finops.aplicacao.resultados.ResultadoCriacaoRequisicao;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Entrada de criação com identidade coordenada.
 *
 * A coordenação abrange a autorização, a execução transacional
 * e a eventual reconciliação após disputa pelo UUID.
 *
 * Autor e departamento não são parâmetros da entrada.
 */
public final class CriarRequisicaoCoordenadaUseCase {

    private final ExecucaoComIdentidadeCoordenada identidades;
    private final CriacaoRequisicaoAutorizadaPersistente criacao;

    public CriarRequisicaoCoordenadaUseCase(
            ExecucaoComIdentidadeCoordenada identidades,
            CriacaoRequisicaoAutorizadaPersistente criacao
    ) {
        if (identidades == null || criacao == null) {
            throw new IllegalArgumentException(
                    "A coordenação de identidade e a criação são obrigatórias."
            );
        }

        this.identidades = identidades;
        this.criacao = criacao;
    }

    public ResultadoCriacaoRequisicao executar(
            UUID requisicaoId,
            List<ItemSolicitado> itens
    ) {
        return identidades.executar(identidade -> {
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
}