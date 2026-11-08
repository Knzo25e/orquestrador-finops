package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.aplicacao.seguranca.ContextoIdentidade;
import br.mackenzie.finops.aplicacao.seguranca.PerfilUsuario;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public final class UsuariosJdbc {
    private final FabricaConexoes fabrica;

    public UsuariosJdbc(FabricaConexoes fabrica) {
        this.fabrica = fabrica;
    }

    public Optional<Credencial> buscarCredencial(String login) {
        String sql = "SELECT id, senha_hash, ativo FROM usuario WHERE login = ?";
        try (var conexao = fabrica.abrir(); var consulta = conexao.prepareStatement(sql)) {
            consulta.setString(1, login);
            try (var linha = consulta.executeQuery()) {
                if (!linha.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Credencial(
                        UUID.fromString(linha.getString("id")),
                        linha.getString("senha_hash"), linha.getBoolean("ativo")
                ));
            }
        } catch (SQLException falha) {
            throw new FalhaPersistenciaException("Falha ao consultar credencial.");
        }
    }

    public Optional<ContextoIdentidade> identidadeAtual(UUID usuarioId) {
        try (var conexao = fabrica.abrir()) {
            return consultarIdentidade(conexao, usuarioId, false);
        } catch (SQLException falha) {
            throw new FalhaPersistenciaException("Falha ao consultar identidade.");
        }
    }

    static Optional<ContextoIdentidade> identidadeBloqueada(Connection conexao, UUID usuarioId)
            throws SQLException {
        if (conexao.getAutoCommit()) {
            throw new IllegalStateException("O bloqueio de identidade exige uma transação.");
        }
        return consultarIdentidade(conexao, usuarioId, true);
    }

    private static Optional<ContextoIdentidade> consultarIdentidade(
            Connection conexao, UUID usuarioId, boolean bloquear
    ) throws SQLException {
        String sql = "SELECT perfil, ativo, departamento_id FROM usuario WHERE id = ?";
        if (bloquear) {
            sql += " FOR SHARE";
        }
        try (var consulta = conexao.prepareStatement(sql)) {
            consulta.setString(1, usuarioId.toString());
            try (var linha = consulta.executeQuery()) {
                return linha.next() ? Optional.of(identidade(usuarioId, linha)) : Optional.empty();
            }
        }
    }

    private static ContextoIdentidade identidade(UUID id, ResultSet linha) throws SQLException {
        String departamento = linha.getString("departamento_id");
        return new ContextoIdentidade(id, PerfilUsuario.valueOf(linha.getString("perfil")),
                linha.getBoolean("ativo"), departamento == null ? null : UUID.fromString(departamento));
    }

    public static void cadastrar(
            Connection conexao, String login, String hash, PerfilUsuario perfil, UUID departamento
    ) throws SQLException {
        String sql = """
                INSERT INTO usuario (id, login, senha_hash, perfil, ativo, departamento_id)
                VALUES (?, ?, ?, ?, TRUE, ?)
                """;
        try (var insercao = conexao.prepareStatement(sql)) {
            insercao.setString(1, UUID.randomUUID().toString());
            insercao.setString(2, login);
            insercao.setString(3, hash);
            insercao.setString(4, perfil.name());
            insercao.setString(5, departamento == null ? null : departamento.toString());
            insercao.executeUpdate();
        }
    }

    public record Credencial(UUID id, String hash, boolean ativo) {
        @Override
        public String toString() {
            return "Credencial[conteúdo protegido]";
        }
    }
}
