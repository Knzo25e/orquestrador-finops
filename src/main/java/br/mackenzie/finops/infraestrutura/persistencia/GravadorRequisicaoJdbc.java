package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.dominio.entidades.Requisicao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Insere cabeçalho e itens na transação do chamador.
 * Qualquer falha exige rollback pelo chamador.
 */
final class GravadorRequisicaoJdbc {

    private final Connection conexao;

    GravadorRequisicaoJdbc(Connection conexao) {
        if (conexao == null) {
            throw new IllegalArgumentException("A conexão é obrigatória.");
        }

        this.conexao = conexao;
    }

    void adicionar(Requisicao requisicao) {
        DadosCadastroRequisicaoJdbc dados =
                DadosCadastroRequisicaoJdbc.preparar(requisicao);

        try {
            if (conexao.getAutoCommit()) {
                throw new IllegalStateException(
                        "O cadastro da requisição exige autocommit desabilitado."
                );
            }

            conferirVinculos(dados);
            inserirCabecalho(dados);
            inserirItens(dados);
        } catch (SQLException excecao) {
            if (excecao.getErrorCode() == 1452
                    && "23000".equals(excecao.getSQLState())) {
                throw new IllegalArgumentException(
                        "Um vínculo da requisição não existe no banco. "
                                + "Execute rollback da transação."
                );
            }

            throw new FalhaPersistenciaException(
                    "Falha ao cadastrar a requisição. "
                            + "Execute rollback da transação."
            );
        }
    }

    private void conferirVinculos(DadosCadastroRequisicaoJdbc dados)
            throws SQLException {
        if (!existe(
                "SELECT id FROM departamento WHERE id = ?",
                dados.departamentoId()
        )) {
            throw new IllegalArgumentException(
                    "O departamento da requisição não está cadastrado."
            );
        }

        Set<UUID> recursosConferidos = new HashSet<>();

        for (DadosCadastroRequisicaoJdbc.Item item : dados.itens()) {
            if (recursosConferidos.add(item.recursoId())
                    && !existe(
                            "SELECT id FROM recurso_catalogo WHERE id = ?",
                            item.recursoId()
                    )) {
                throw new IllegalArgumentException(
                        "Um recurso da requisição não está cadastrado."
                );
            }
        }
    }

    private boolean existe(String sql, UUID id) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, id.toString());

            try (ResultSet resultado = comando.executeQuery()) {
                return resultado.next();
            }
        }
    }

    private void inserirCabecalho(DadosCadastroRequisicaoJdbc dados)
            throws SQLException {
        String sql = """
                INSERT INTO requisicao (
                    id, departamento_id, estado, custo_total,
                    motivo_revisao, desenvolvedor_id
                ) VALUES (?, ?, 'INICIAL', ?, NULL, ?)
                """;

        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, dados.id().toString());
            comando.setString(2, dados.departamentoId().toString());
            comando.setBigDecimal(3, dados.total());

            if (dados.desenvolvedorId() == null) {
                comando.setNull(4, java.sql.Types.CHAR);
            } else {
                comando.setString(4, dados.desenvolvedorId().toString());
            }

            conferirInsercao(comando.executeUpdate());
        } catch (SQLException excecao) {
            if (excecao.getErrorCode() == 1062
                    && "23000".equals(excecao.getSQLState())) {
                throw new RequisicaoDuplicadaException();
            }

            throw excecao;
        }
    }

    private void inserirItens(DadosCadastroRequisicaoJdbc dados)
            throws SQLException {
        String sql = """
                INSERT INTO item_requisicao (
                    requisicao_id, ordem, recurso_id,
                    preco_unitario, quantidade, subtotal
                ) VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            for (int indice = 0; indice < dados.itens().size(); indice++) {
                DadosCadastroRequisicaoJdbc.Item item =
                        dados.itens().get(indice);

                comando.setString(1, dados.id().toString());
                comando.setInt(2, indice + 1);
                comando.setString(3, item.recursoId().toString());
                comando.setBigDecimal(4, item.precoUnitario());
                comando.setInt(5, item.quantidade());
                comando.setBigDecimal(6, item.subtotal());

                conferirInsercao(comando.executeUpdate());
            }
        }
    }

    private static void conferirInsercao(int quantidade) {
        if (quantidade != 1) {
            throw new FalhaPersistenciaException(
                    "A inserção não afetou exatamente um registro. "
                            + "Execute rollback da transação."
            );
        }
    }
}