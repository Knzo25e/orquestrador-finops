package br.mackenzie.finops.aplicacao;

import br.mackenzie.finops.aplicacao.entradas.ItemSolicitado;
import br.mackenzie.finops.aplicacao.entradas.SolicitacaoCriacaoRequisicao;
import br.mackenzie.finops.aplicacao.excecoes.AcessoNegadoException;
import br.mackenzie.finops.aplicacao.portas.CriacaoRequisicaoAutorizadaPersistente;
import br.mackenzie.finops.aplicacao.portas.ProvedorIdentidade;
import br.mackenzie.finops.aplicacao.resultados.ResultadoCriacaoRequisicao;
import br.mackenzie.finops.aplicacao.seguranca.ContextoIdentidade;
import br.mackenzie.finops.aplicacao.seguranca.PoliticaAutorizacao;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Entrada protegida da criação de requisições.
 *
 * Autor e departamento são derivados da identidade da execução.
 * O cliente informa somente o UUID da requisição e os itens.
 *
 * A autorização da repetição sobre o pedido armazenado pertence
 * à implementação da porta transacional.
 */
final class CriarRequisicaoAutorizadaUseCase {

    private final ProvedorIdentidade identidades;
    private final CriacaoRequisicaoAutorizadaPersistente criacao;
    private final PoliticaAutorizacao politica;

    CriarRequisicaoAutorizadaUseCase(
            ProvedorIdentidade identidades,
            CriacaoRequisicaoAutorizadaPersistente criacao
    ) {
        if (identidades == null || criacao == null) {
            throw new IllegalArgumentException(
                    "O provedor de identidade e a criação são obrigatórios."
            );
        }

        this.identidades = identidades;
        this.criacao = criacao;
        this.politica = new PoliticaAutorizacao();
    }

    public ResultadoCriacaoRequisicao executar(
            UUID requisicaoId,
            List<ItemSolicitado> itens
    ) {
        ContextoIdentidade identidade = obterIdentidadeAutorizada();

        SolicitacaoCriacaoRequisicao solicitacao =
                SolicitacaoCriacaoRequisicao.comAutoria(
                        requisicaoId,
                        identidade.departamentoId(),
                        itens,
                        identidade.usuarioId()
                );

        return criacao.executar(solicitacao, identidade);
    }

    private ContextoIdentidade obterIdentidadeAutorizada() {
        Optional<ContextoIdentidade> resultado =
                identidades.obterIdentidadeAtual();

        if (resultado == null) {
            throw new IllegalStateException(
                    "O provedor de identidade retornou um resultado inválido."
            );
        }

        ContextoIdentidade identidade = resultado.orElseThrow(
                AcessoNegadoException::new
        );

        if (!politica.podeCriarRequisicao(
                identidade,
                identidade.departamentoId()
        )) {
            throw new AcessoNegadoException();
        }

        return identidade;
    }
}