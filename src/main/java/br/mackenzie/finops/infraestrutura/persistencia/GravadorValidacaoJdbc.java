package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.dominio.entidades.Departamento;
import br.mackenzie.finops.dominio.entidades.Requisicao;
import br.mackenzie.finops.dominio.entidades.ResultadoValidacao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Persiste o resultado financeiro produzido pelo domínio.
 *
 * Exige departamento e requisição previamente bloqueados,
 * na mesma conexão e transação.
 *
 * Não executa commit, rollback ou fechamento da conexão.
 */
final class GravadorValidacaoJdbc {

    private final Connection conexao;

    GravadorValidacaoJdbc(Connection conexao) {
        if (conexao == null) {
            throw new IllegalArgumentException(
                    "A conexão é obrigatória."
            );
        }

        this.conexao = conexao;
    }

    void gravar(
            Requisicao requisicao,
            ResultadoValidacao resultado,
            BigDecimal saldoAnterior
    ) {
        if (requisicao == null || resultado == null) {
            throw inconsistencia();
        }

        Departamento departamento = requisicao.getDepartamento();

        if (requisicao.getId() == null
                || departamento == null
                || departamento.getId() == null) {
            throw inconsistencia();
        }

        BigDecimal anterior = validarMoeda(saldoAnterior);
        BigDecimal atual = validarMoeda(
                departamento.getSaldoDisponivel()
        );
        BigDecimal custo = validarMoeda(
                requisicao.getCustoTotalProjetado()
        );

        String estado;
        String motivo;

        switch (resultado) {
            case LIBERADA -> {
                if (!"Provisionamento Liberado".equals(
                        requisicao.getStatus()
                ) || requisicao.getMotivoRevisao() != null
                        || atual.compareTo(anterior.subtract(custo)) != 0) {
                    throw inconsistencia();
                }

                estado = "PROVISIONAMENTO_LIBERADO";
                motivo = null;
            }

            case REVISAO_POR_SALDO_INSUFICIENTE,
                    REVISAO_POR_RESERVA -> {
                if (!"Revisão Pendente".equals(requisicao.getStatus())
                        || requisicao.getMotivoRevisao() != resultado
                        || atual.compareTo(anterior) != 0) {
                    throw inconsistencia();
                }

                estado = "REVISAO_PENDENTE";
                motivo = resultado
                        == ResultadoValidacao.REVISAO_POR_SALDO_INSUFICIENTE
                        ? "SALDO_INSUFICIENTE"
                        : "RESERVA";
            }

            default -> throw new IllegalStateException(
                    "O resultado informado não exige gravação financeira."
            );
        }

        try {
            conferirConfiguracao();

            if (resultado == ResultadoValidacao.LIBERADA
                    && custo.signum() > 0) {
                atualizarSaldo(departamento, anterior, atual);
            }

            atualizarRequisicao(requisicao, estado, motivo);
        } catch (SQLException excecao) {
            throw new FalhaPersistenciaException(
                    "Falha ao gravar o resultado da validação. "
                            + "Execute rollback da transação."
            );
        }
    }

    private void conferirConfiguracao() throws SQLException {
        if (conexao.getAutoCommit()) {
            throw new IllegalStateException(
                    "A gravação financeira exige autocommit desabilitado."
            );
        }

        if (conexao.getTransactionIsolation()
                != Connection.TRANSACTION_READ_COMMITTED) {
            throw new IllegalStateException(
                    "A gravação financeira exige READ COMMITTED."
            );
        }
    }

    private void atualizarSaldo(
            Departamento departamento,
            BigDecimal saldoAnterior,
            BigDecimal saldoAtual
    ) throws SQLException {
        String sql = """
                UPDATE departamento
                SET saldo_disponivel = ?
                WHERE id = ?
                  AND saldo_disponivel = ?
                """;

        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setBigDecimal(1, saldoAtual);
            comando.setString(2, departamento.getId().toString());
            comando.setBigDecimal(3, saldoAnterior);

            conferirAtualizacao(comando.executeUpdate());
        }
    }

    private void atualizarRequisicao(
            Requisicao requisicao,
            String estado,
            String motivo
    ) throws SQLException {
        String sql = """
                UPDATE requisicao
                SET estado = ?, motivo_revisao = ?
                WHERE id = ?
                  AND departamento_id = ?
                  AND estado = 'INICIAL'
                  AND motivo_revisao IS NULL
                """;

        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, estado);

            if (motivo == null) {
                comando.setNull(2, Types.VARCHAR);
            } else {
                comando.setString(2, motivo);
            }

            comando.setString(3, requisicao.getId().toString());
            comando.setString(
                    4,
                    requisicao.getDepartamento().getId().toString()
            );

            conferirAtualizacao(comando.executeUpdate());
        }
    }

    private static BigDecimal validarMoeda(BigDecimal valor) {
        try {
            return ValidacaoCadastroJdbc.validarValorMonetario(
                    valor,
                    "O valor da operação financeira"
            );
        } catch (IllegalArgumentException excecao) {
            throw inconsistencia();
        }
    }

    private static void conferirAtualizacao(int quantidade) {
        if (quantidade != 1) {
            throw new FalhaPersistenciaException(
                    "A atualização financeira não afetou exatamente "
                            + "um registro. Execute rollback da transação."
            );
        }
    }

    private static FalhaPersistenciaException inconsistencia() {
        return new FalhaPersistenciaException(
                "O resultado financeiro contém dados inconsistentes."
        );
    }
}