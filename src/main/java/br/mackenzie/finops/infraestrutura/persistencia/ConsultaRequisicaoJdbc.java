package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.aplicacao.portas.ConsultaRequisicaoPersistente;
import br.mackenzie.finops.dominio.entidades.Requisicao;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Executa consultas com conexão exclusiva por chamada.
 *
 * Não autentica usuários nem decide permissões.
 * Os casos de uso devem selecionar a consulta conforme a identidade.
 */
public final class ConsultaRequisicaoJdbc
        implements ConsultaRequisicaoPersistente {

    private final FornecedorConexaoJdbc fornecedor;

    public ConsultaRequisicaoJdbc(FabricaConexoes fabrica) {
        if (fabrica == null) {
            throw new IllegalArgumentException(
                    "A fábrica de conexões é obrigatória."
            );
        }

        this.fornecedor = fabrica::abrir;
    }

    // Permite fornecer conexões controladas nos testes.
    ConsultaRequisicaoJdbc(FornecedorConexaoJdbc fornecedor) {
        if (fornecedor == null) {
            throw new IllegalArgumentException(
                    "O fornecedor de conexões é obrigatório."
            );
        }

        this.fornecedor = fornecedor;
    }

    @Override
    public Optional<Requisicao> buscarPorIdEAutorEDepartamento(
            UUID requisicaoId,
            UUID autorId,
            UUID departamentoId
    ) {
        validarRequisicao(requisicaoId);
        validarAutor(autorId);
        validarDepartamento(departamentoId);

        return consultar(repositorio ->
                repositorio.buscarPorIdEAutorEDepartamento(
                        requisicaoId, autorId, departamentoId
                )
        );
    }

    @Override
    public List<Requisicao> listarPorAutorEDepartamento(
            UUID autorId,
            UUID departamentoId
    ) {
        validarAutor(autorId);
        validarDepartamento(departamentoId);

        return consultar(repositorio ->
                repositorio.listarPorAutorEDepartamento(
                        autorId, departamentoId
                )
        );
    }

    @Override
    public Optional<Requisicao> buscarPorId(UUID requisicaoId) {
        validarRequisicao(requisicaoId);

        return consultar(repositorio ->
                repositorio.buscarPorId(requisicaoId)
        );
    }

    @Override
    public List<Requisicao> listarPorDepartamento(UUID departamentoId) {
        validarDepartamento(departamentoId);

        return consultar(repositorio ->
                repositorio.listarPorDepartamento(departamentoId)
        );
    }

    private <T> T consultar(
            Function<RequisicaoRepositoryJdbc, T> operacao
    ) {
        try (Connection conexao = fornecedor.abrir()) {
            if (conexao == null) {
                throw new FalhaPersistenciaException(
                        "Não foi possível abrir a conexão de consulta."
                );
            }

            conexao.setTransactionIsolation(
                    Connection.TRANSACTION_READ_COMMITTED
            );

            // Cada consulta do leitor utiliza uma única instrução SQL.
            conexao.setAutoCommit(true);

            return operacao.apply(
                    new RequisicaoRepositoryJdbc(conexao)
            );
        } catch (SQLException excecao) {
            throw new FalhaPersistenciaException(
                    "Falha ao abrir, configurar ou fechar "
                            + "a conexão de consulta."
            );
        }
    }

    private static void validarRequisicao(UUID id) {
        ValidacaoCadastroJdbc.validarId(
                id, "O identificador da requisição"
        );
    }

    private static void validarAutor(UUID id) {
        ValidacaoCadastroJdbc.validarId(
                id, "O identificador do autor"
        );
    }

    private static void validarDepartamento(UUID id) {
        ValidacaoCadastroJdbc.validarId(
                id, "O identificador do departamento"
        );
    }
}