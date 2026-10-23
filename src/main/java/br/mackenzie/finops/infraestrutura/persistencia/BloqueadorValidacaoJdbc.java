package br.mackenzie.finops.infraestrutura.persistencia;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Adquire os bloqueios necessários à validação financeira.
 *
 * A ordem é sempre departamento antes de requisição.
 * A conexão e o encerramento da transação pertencem ao chamador.
 */
final class BloqueadorValidacaoJdbc {

    private final Connection conexao;

    BloqueadorValidacaoJdbc(Connection conexao) {
        if (conexao == null) {
            throw new IllegalArgumentException(
                    "A conexão é obrigatória."
            );
        }

        this.conexao = conexao;
    }

    /**
     * Retorna o UUID do departamento após bloquear os dois registros
     * e conferir novamente seu vínculo.
     *
     * Os bloqueios permanecem até o encerramento da transação.
     */
    UUID bloquear(UUID requisicaoId) {
        ValidacaoCadastroJdbc.validarId(
                requisicaoId,
                "O identificador da requisição"
        );

        try {
            conferirConfiguracao();

            UUID departamentoId = localizarDepartamento(requisicaoId);

            bloquearDepartamento(departamentoId);
            bloquearRequisicao(requisicaoId, departamentoId);

            return departamentoId;
        } catch (SQLException excecao) {
            throw new FalhaPersistenciaException(
                    "Falha ao adquirir os bloqueios da validação."
            );
        }
    }

    private void conferirConfiguracao() throws SQLException {
        if (conexao.getAutoCommit()) {
            throw new IllegalStateException(
                    "A validação exige autocommit desabilitado."
            );
        }

        if (conexao.getTransactionIsolation()
                != Connection.TRANSACTION_READ_COMMITTED) {
            throw new IllegalStateException(
                    "A validação exige READ COMMITTED."
            );
        }
    }

    private UUID localizarDepartamento(UUID requisicaoId)
            throws SQLException {
        String sql = """
                SELECT departamento_id
                FROM requisicao
                WHERE id = ?
                """;

        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, requisicaoId.toString());

            try (ResultSet resultado = comando.executeQuery()) {
                if (!resultado.next()) {
                    throw new IllegalArgumentException(
                            "A requisição solicitada não está cadastrada."
                    );
                }

                return interpretarId(
                        resultado.getString("departamento_id")
                );
            }
        }
    }

    private void bloquearDepartamento(UUID departamentoId)
            throws SQLException {
        String sql = """
                SELECT id
                FROM departamento
                WHERE id = ?
                FOR UPDATE
                """;

        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, departamentoId.toString());

            try (ResultSet resultado = comando.executeQuery()) {
                if (!resultado.next()) {
                    throw new FalhaPersistenciaException(
                            "O departamento vinculado não foi encontrado."
                    );
                }

                UUID recuperado = interpretarId(
                        resultado.getString("id")
                );

                if (!departamentoId.equals(recuperado)) {
                    throw new FalhaPersistenciaException(
                            "O departamento bloqueado difere do solicitado."
                    );
                }
            }
        }
    }

    private void bloquearRequisicao(
            UUID requisicaoId,
            UUID departamentoId
    ) throws SQLException {
        String sql = """
                SELECT id, departamento_id
                FROM requisicao
                WHERE id = ?
                FOR UPDATE
                """;

        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, requisicaoId.toString());

            try (ResultSet resultado = comando.executeQuery()) {
                if (!resultado.next()) {
                    throw new FalhaPersistenciaException(
                            "A requisição não foi encontrada após sua localização."
                    );
                }

                UUID recuperado = interpretarId(
                        resultado.getString("id")
                );

                UUID vinculoAtual = interpretarId(
                        resultado.getString("departamento_id")
                );

                if (!requisicaoId.equals(recuperado)) {
                    throw new FalhaPersistenciaException(
                            "A requisição bloqueada difere da solicitada."
                    );
                }

                if (!departamentoId.equals(vinculoAtual)) {
                    throw new FalhaPersistenciaException(
                            "O vínculo da requisição mudou durante a operação."
                    );
                }
            }
        }
    }

    private static UUID interpretarId(String texto) {
        try {
            return ValidacaoCadastroJdbc.interpretarId(texto);
        } catch (IllegalArgumentException excecao) {
            throw new FalhaPersistenciaException(
                    "O registro armazenado contém identificador inconsistente."
            );
        }
    }
}