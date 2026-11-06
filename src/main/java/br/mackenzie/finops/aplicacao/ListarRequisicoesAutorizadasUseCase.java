package br.mackenzie.finops.aplicacao;

import br.mackenzie.finops.aplicacao.excecoes.AcessoNegadoException;
import br.mackenzie.finops.aplicacao.excecoes.EntradaRequisicaoInvalidaException;
import br.mackenzie.finops.aplicacao.portas.ConsultaRequisicaoPersistente;
import br.mackenzie.finops.aplicacao.portas.ProvedorIdentidade;
import br.mackenzie.finops.aplicacao.seguranca.ContextoIdentidade;
import br.mackenzie.finops.aplicacao.seguranca.PerfilUsuario;
import br.mackenzie.finops.aplicacao.seguranca.PoliticaAutorizacao;
import br.mackenzie.finops.aplicacao.resultados.ResumoRequisicao;
import br.mackenzie.finops.dominio.entidades.Requisicao;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ListarRequisicoesAutorizadasUseCase {
    public static final int LIMITE_PADRAO = 25;
    private static final int LIMITE_MAXIMO = 100;

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

    public Pagina executarPagina(UUID departamentoInformado, UUID apos, int limite) {
        ContextoIdentidade identidade = obterIdentidadeAutorizada();
        boolean arquiteto = identidade.perfil() == PerfilUsuario.ARQUITETO_CLOUD;
        if (limite < 1 || limite > LIMITE_MAXIMO || arquiteto != (departamentoInformado != null)) {
            throw new EntradaRequisicaoInvalidaException();
        }

        UUID departamento = arquiteto ? departamentoInformado : identidade.departamentoId();
        UUID autor = arquiteto ? null : identidade.usuarioId();
        var resumos = consultas.listarResumos(departamento, autor, apos, limite + 1);
        if (resumos == null || resumos.size() > limite + 1) {
            throw new IllegalStateException("A consulta retornou uma página inválida.");
        }

        String anterior = apos == null ? null : apos.toString();
        for (var resumo : resumos) {
            if (resumo == null || resumo.id() == null || !departamento.equals(resumo.departamentoId())
                    || !politica.podeConsultarRequisicao(identidade, resumo.autorId(), resumo.departamentoId())
                    || (anterior != null && anterior.compareTo(resumo.id().toString()) >= 0)) {
                throw new IllegalStateException("A consulta retornou um resumo inválido ou fora do escopo.");
            }
            anterior = resumo.id().toString();
        }

        boolean temMais = resumos.size() > limite;
        var pagina = List.copyOf(resumos.subList(0, Math.min(resumos.size(), limite)));
        return new Pagina(pagina, temMais ? pagina.getLast().id() : null);
    }

    public record Pagina(List<ResumoRequisicao> requisicoes, UUID proximoCursor) {}

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
