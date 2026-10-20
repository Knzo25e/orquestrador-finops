package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.aplicacao.portas.DepartamentoRepository;
import br.mackenzie.finops.dominio.entidades.Departamento;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * Cadastro e consulta de departamentos no MySQL.
 *
 * A conexão pertence ao chamador, que controla a transação
 * e seu fechamento. Não compartilhar entre threads.
 */
public final class DepartamentoRepositoryJdbc
        implements DepartamentoRepository {

    private final Connection conexao;

    public DepartamentoRepositoryJdbc(Connection conexao) {
        if (conexao == null) {
            throw new IllegalArgumentException(
                    "A conexão é obrigatória."
            );
        }

        this.conexao = conexao;
    }

    @Override
    public void adicionar(Departamento departamento) {
        if (departamento == null) {
            throw new IllegalArgumentException(
                    "O departamento é obrigatório."
            );
        }

        UUID id = ValidacaoCadastroJdbc.validarId(
                departamento.getId(),
                "O identificador do departamento"
        );

        String nome = ValidacaoCadastroJdbc.validarNome(
                departamento.getNome()
        );

        BigDecimal teto = ValidacaoCadastroJdbc.validarValorMonetario(
                departamento.getOrcamentoMensal(),
                "O teto mensal"
        );

        BigDecimal saldo = ValidacaoCadastroJdbc.validarValorMonetario(
                departamento.getSaldoDisponivel(),
                "O saldo disponível"
        );

        ValidacaoCadastroJdbc.validarSaldo(teto, saldo);

        String sql = """
                INSERT INTO departamento (
                    id, nome, teto_mensal, saldo_disponivel
                ) VALUES (?, ?, ?, ?)
                """;

        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, id.toString());
            comando.setString(2, nome);
            comando.setBigDecimal(3, teto);
            comando.setBigDecimal(4, saldo);

            if (comando.executeUpdate() != 1) {
                throw new FalhaPersistenciaException(
                        "O cadastro do departamento não afetou exatamente um registro."
                );
            }
        } catch (SQLException excecao) {
            if (excecao.getErrorCode() == 1062
                    && "23000".equals(excecao.getSQLState())) {
                throw new IllegalArgumentException(
                        "Já existe um departamento com esse identificador."
                );
            }

            throw new FalhaPersistenciaException(
                    "Falha ao cadastrar departamento no banco."
            );
        }
    }

    @Override
    public Optional<Departamento> buscarPorId(UUID id) {
        ValidacaoCadastroJdbc.validarId(
                id,
                "O identificador do departamento"
        );

        String sql = """
                SELECT id, nome, teto_mensal, saldo_disponivel
                FROM departamento
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
                    "Falha ao consultar departamento no banco."
            );
        }
    }

    private Departamento reconstituir(
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

            BigDecimal teto =
                    ValidacaoCadastroJdbc.validarValorMonetario(
                            resultado.getBigDecimal("teto_mensal"),
                            "O teto mensal"
                    );

            BigDecimal saldo =
                    ValidacaoCadastroJdbc.validarValorMonetario(
                            resultado.getBigDecimal("saldo_disponivel"),
                            "O saldo disponível"
                    );

            ValidacaoCadastroJdbc.validarSaldo(teto, saldo);

            return Departamento.reconstituir(id, nome, teto, saldo);
        } catch (IllegalArgumentException excecao) {
            throw new FalhaPersistenciaException(
                    "O departamento armazenado contém dados inconsistentes."
            );
        }
    }
}