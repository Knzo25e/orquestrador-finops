package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.aplicacao.entradas.SolicitacaoCriacaoRequisicao;
import br.mackenzie.finops.aplicacao.excecoes.AcessoNegadoException;
import br.mackenzie.finops.aplicacao.seguranca.ContextoIdentidade;
import br.mackenzie.finops.aplicacao.seguranca.PoliticaAutorizacao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Confere a autorização da criação e do acesso ao pedido repetido.
 *
 * Não abre conexões nem encerra transações.
 * A consulta do cabeçalho deve usar a mesma conexão e fotografia
 * transacional da recuperação posterior do pedido.
 */
final class AutorizacaoCriacaoJdbc {

    private AutorizacaoCriacaoJdbc() {
    }

    static void conferirSolicitacao(
            SolicitacaoCriacaoRequisicao solicitacao,
            ContextoIdentidade identidade
    ) {
        if (solicitacao == null) {
            throw new IllegalArgumentException(
                    "A solicitação é obrigatória."
            );
        }

        PoliticaAutorizacao politica = new PoliticaAutorizacao();

        if (!politica.podeCriarRequisicao(
                identidade,
                solicitacao.departamentoId()
        )) {
            throw new AcessoNegadoException();
        }

        if (!identidade.usuarioId().equals(
                solicitacao.desenvolvedorId()
        )) {
            throw new AcessoNegadoException();
        }
    }

    /**
     * Autoriza o acesso ao cabeçalho existente, quando houver.
     *
     * Ausência permite seguir para a tentativa de criação.
     * Uma inclusão concorrente ainda exige nova conferência
     * na transação de reconciliação.
     */
    static void conferirPedidoExistente(
            Connection conexao,
            UUID requisicaoId,
            ContextoIdentidade identidade
    ) throws SQLException {
        if (conexao == null) {
            throw new IllegalArgumentException(
                    "A conexão é obrigatória."
            );
        }

        ValidacaoCadastroJdbc.validarId(
                requisicaoId,
                "O identificador da requisição"
        );

        if (identidade == null
                || !new PoliticaAutorizacao().podeCriarRequisicao(
                        identidade,
                        identidade.departamentoId()
                )) {
            throw new AcessoNegadoException();
        }

        if (conexao.getAutoCommit()
                || conexao.getTransactionIsolation()
                        != Connection.TRANSACTION_REPEATABLE_READ) {
            throw new IllegalStateException(
                    "A conferência exige transação em REPEATABLE READ."
            );
        }

        String sql = """
                SELECT desenvolvedor_id, departamento_id
                FROM requisicao
                WHERE id = ?
                """;

        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, requisicaoId.toString());

            try (ResultSet resultado = comando.executeQuery()) {
                if (!resultado.next()) {
                    return;
                }

                String autorArmazenado =
                        resultado.getString("desenvolvedor_id");

                String departamentoArmazenado =
                        resultado.getString("departamento_id");

                // Comparação exata com os UUIDs canônicos da identidade.
                // Autor ausente ou cabeçalho fora do escopo resulta
                // na mesma negativa, sem reconstruir o pedido.
                if (!identidade.usuarioId().toString().equals(
                        autorArmazenado
                ) || !identidade.departamentoId().toString().equals(
                        departamentoArmazenado
                )) {
                    throw new AcessoNegadoException();
                }
            }
        }
    }
}