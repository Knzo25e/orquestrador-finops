package br.mackenzie.finops.aplicacao;

import br.mackenzie.finops.aplicacao.excecoes.AcessoNegadoException;
import br.mackenzie.finops.aplicacao.portas.ConsultaRequisicaoPersistente;
import br.mackenzie.finops.aplicacao.portas.ProvedorIdentidade;
import br.mackenzie.finops.aplicacao.seguranca.ContextoIdentidade;
import br.mackenzie.finops.aplicacao.seguranca.PerfilUsuario;
import br.mackenzie.finops.aplicacao.seguranca.PoliticaAutorizacao;
import br.mackenzie.finops.dominio.entidades.Requisicao;

import java.util.Optional;
import java.util.UUID;

public final class ConsultarRequisicaoAutorizadaUseCase {

    private final ProvedorIdentidade identidades;
    private final ConsultaRequisicaoPersistente consultas;
    private final PoliticaAutorizacao politica;

    public ConsultarRequisicaoAutorizadaUseCase(
            ProvedorIdentidade identidades,
            ConsultaRequisicaoPersistente consultas
    ) {
        if (identidades == null || consultas == null) {
            throw new IllegalArgumentException(
                    "O provedor de identidade e a consulta são obrigatórios."
            );
        }

        this.identidades = identidades;
        this.consultas = consultas;
        this.politica = new PoliticaAutorizacao();
    }

    public Optional<Requisicao> executar(UUID requisicaoId) {
        ContextoIdentidade identidade = obterIdentidadeAutorizada();

        if (requisicaoId == null) {
            throw new IllegalArgumentException(
                    "O identificador da requisição é obrigatório."
            );
        }

        Optional<Requisicao> encontrada;

        if (identidade.perfil() == PerfilUsuario.ARQUITETO_CLOUD) {
            encontrada = consultas.buscarPorId(requisicaoId);
        } else {
            encontrada = consultas.buscarPorIdEAutorEDepartamento(
                    requisicaoId,
                    identidade.usuarioId(),
                    identidade.departamentoId()
            );
        }

        if (encontrada == null) {
            throw new IllegalStateException(
                    "A consulta retornou um resultado inválido."
            );
        }

        if (encontrada.isEmpty()) {
            return Optional.empty();
        }

        Requisicao pedido = encontrada.get();

        if (!requisicaoId.equals(pedido.getId())
                || pedido.getDepartamento() == null) {
            throw new IllegalStateException(
                    "A consulta retornou um resultado inconsistente."
            );
        }

        // Defesa adicional; não substitui o filtro no armazenamento.
        if (!politica.podeConsultarRequisicao(
                identidade,
                pedido.getDesenvolvedorId(),
                pedido.getDepartamento().getId()
        )) {
            return Optional.empty();
        }

        return Optional.of(pedido);
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

        if (!identidade.ativo()) {
            throw new AcessoNegadoException();
        }

        switch (identidade.perfil()) {
            case DESENVOLVEDOR -> {
                if (identidade.departamentoId() == null) {
                    throw new AcessoNegadoException();
                }
            }

            case ARQUITETO_CLOUD -> {
                // A consulta do arquiteto não exige vínculo departamental.
            }

            default -> throw new AcessoNegadoException();
        }

        return identidade;
    }
}