package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.aplicacao.portas.RecursoCatalogoRepository;
import br.mackenzie.finops.dominio.entidades.RecursoCatalogo;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * Cadastro e consulta de recursos no MySQL.
 *
 * A conexão pertence ao chamador, que controla a transação
 * e seu fechamento. Não compartilhar entre threads.
 */
public final class RecursoCatalogoRepositoryJdbc
        implements RecursoCatalogoRepository {

    private final Connection conexao;

    public RecursoCatalogoRepositoryJdbc(Connection conexao) {
        if (conexao == null) {
            throw new IllegalArgumentException(
                    "A conexão é obrigatória."
            );
        }

        this.conexao = conexao;
    }

    @Override
    public void adicionar(RecursoCatalogo recurso) {
        if (recurso == null) {
            throw new IllegalArgumentException(
                    "O recurso é obrigatório."
            );
        }

        UUID id = ValidacaoCadastroJdbc.validarId(
                recurso.getId(),
                "O identificador do recurso"
        );

        String nome = ValidacaoCadastroJdbc.validarNome(
                recurso.getNome()
        );

        BigDecimal custo = ValidacaoCadastroJdbc.validarValorMonetario(
                recurso.getCustoMensal(),
                "O custo mensal"
        );

        String sql = """
                INSERT INTO recurso_catalogo (
                    id, nome, custo_mensal
                ) VALUES (?, ?, ?)
                """;

        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, id.toString());
            comando.setString(2, nome);
            comando.setBigDecimal(3, custo);

            if (comando.executeUpdate() != 1) {
                throw new FalhaPersistenciaException(
                        "O cadastro do recurso não afetou exatamente um registro."
                );
            }
        } catch (SQLException excecao) {
            if (excecao.getErrorCode() == 1062
                    && "23000".equals(excecao.getSQLState())) {
                throw new IllegalArgumentException(
                        "Já existe um recurso com esse identificador."
                );
            }

            throw new FalhaPersistenciaException(
                    "Falha ao cadastrar recurso no banco."
            );
        }
    }

    @Override
    public Optional<RecursoCatalogo> buscarPorId(UUID id) {
        ValidacaoCadastroJdbc.validarId(
                id,
                "O identificador do recurso"
        );

        String sql = """
                SELECT id, nome, custo_mensal
                FROM recurso_catalogo
                WHERE id = ?
                """;

        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, id.toString());

            try (ResultSet resultado = comando.executeQuery()) {
                if (!resultado.next()) {
                    return Optional.empty();
                }

                return Optional.of(reconstituir(resultado, id));
            }
        } catch (SQLException excecao) {
            throw new FalhaPersistenciaException(
                    "Falha ao consultar recurso no banco."
            );
        }
    }

    private RecursoCatalogo reconstituir(
            ResultSet resultado,
            UUID idConsultado
    ) throws SQLException {
        try {
            UUID id = ValidacaoCadastroJdbc.interpretarId(
                    resultado.getString("id")
            );

            if (!idConsultado.equals(id)) {
                throw new IllegalArgumentException(
                        "O identificador recuperado difere do solicitado."
                );
            }

            String nome = ValidacaoCadastroJdbc.validarNome(
                    resultado.getString("nome")
            );

            BigDecimal custo =
                    ValidacaoCadastroJdbc.validarValorMonetario(
                            resultado.getBigDecimal("custo_mensal"),
                            "O custo mensal"
                    );

            return new RecursoCatalogo(id, nome, custo);
        } catch (IllegalArgumentException excecao) {
            throw new FalhaPersistenciaException(
                    "O recurso armazenado contém dados inconsistentes."
            );
        }
    }
}