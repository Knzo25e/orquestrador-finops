package br.mackenzie.finops.aplicacao;

import br.mackenzie.finops.aplicacao.excecoes.AcessoNegadoException;
import br.mackenzie.finops.aplicacao.portas.ConsultaRequisicaoPersistente;
import br.mackenzie.finops.aplicacao.portas.ProvedorIdentidade;
import br.mackenzie.finops.aplicacao.seguranca.ContextoIdentidade;
import br.mackenzie.finops.aplicacao.seguranca.PerfilUsuario;
import br.mackenzie.finops.aplicacao.seguranca.PoliticaAutorizacao;
import br.mackenzie.finops.dominio.entidades.Requisicao;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ListarRequisicoesAutorizadasUseCase {

    private final ProvedorIdentidade identidades;
    private final ConsultaRequisicaoPersistente consultas;
    private final PoliticaAutorizacao politica;

    public ListarRequisicoesAutorizadasUseCase(
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

    public List<Requisicao> executar(UUID departamentoId) {
        ContextoIdentidade identidade = obterIdentidadeAutorizada();

        if (departamentoId == null) {
            throw new IllegalArgumentException(
                    "O identificador do departamento é obrigatório."
            );
        }

        List<Requisicao> encontradas;

        if (identidade.perfil() == PerfilUsuario.ARQUITETO_CLOUD) {
            encontradas = consultas.listarPorDepartamento(departamentoId);
        } else {
            if (!departamentoId.equals(identidade.departamentoId())) {
                throw new AcessoNegadoException();
            }

            encontradas = consultas.listarPorAutorEDepartamento(
                    identidade.usuarioId(),
                    identidade.departamentoId()
            );
        }

        if (encontradas == null) {
            throw new IllegalStateException(
                    "A consulta retornou um resultado inválido."
            );
        }

        // Confere o contrato antes de devolver qualquer elemento.
        // Esta defesa não substitui os filtros no armazenamento.
        for (Requisicao pedido : encontradas) {
            if (pedido == null
                    || pedido.getDepartamento() == null
                    || !departamentoId.equals(
                            pedido.getDepartamento().getId()
                    )) {
                throw new IllegalStateException(
                        "A consulta retornou um resultado inconsistente."
                );
            }

            if (!politica.podeConsultarRequisicao(
                    identidade,
                    pedido.getDesenvolvedorId(),
                    pedido.getDepartamento().getId()
            )) {
                throw new IllegalStateException(
                        "A consulta retornou um resultado fora do escopo."
                );
            }
        }

        return List.copyOf(encontradas);
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
                // O arquiteto pode consultar qualquer departamento.
            }

            default -> throw new AcessoNegadoException();
        }

        return identidade;
    }
}