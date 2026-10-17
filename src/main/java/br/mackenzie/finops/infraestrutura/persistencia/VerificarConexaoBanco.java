package br.mackenzie.finops.infraestrutura.persistencia;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class VerificarConexaoBanco {

    public static void main(String[] args) {
        try {
            executar();
        } catch (SQLException excecao) {
            System.err.println(
                    "Falha JDBC. SQLState: " + excecao.getSQLState()
                            + "; codigo: " + excecao.getErrorCode()
            );
            System.exit(1);
        } catch (IllegalArgumentException | IllegalStateException excecao) {
            System.err.println(excecao.getMessage());
            System.exit(1);
        }
    }

    private static void executar() throws SQLException {
        ConfiguracaoBanco configuracao =
                ConfiguracaoBanco.carregarDoAmbiente();

        FabricaConexoes fabrica = new FabricaConexoes(configuracao);

        try (Connection conexao = fabrica.abrir()) {
            try (
                    Statement comando = conexao.createStatement();
                    ResultSet resultado = comando.executeQuery(
                            "SELECT DATABASE(), VERSION(), CURRENT_USER()"
                    )
            ) {
                if (!resultado.next()) {
                    throw new IllegalStateException(
                            "A consulta de identificação não retornou resultado."
                    );
                }

                if (!"finops_test".equals(resultado.getString(1))) {
                    throw new IllegalStateException(
                            "Esta verificação exige o banco finops_test."
                    );
                }

                if (!"finops_test_app@localhost".equals(resultado.getString(3))) {
                    throw new IllegalStateException(
                            "Esta verificação exige a conta finops_test_app@localhost."
                    );
                }

                System.out.println("PASSOU: banco e conta de testes confirmados.");
                System.out.println("Servidor MySQL: " + resultado.getString(2));
            }

            try (
                    Statement comando = conexao.createStatement();
                    ResultSet resultado = comando.executeQuery(
                            "SHOW SESSION STATUS LIKE 'Ssl_cipher'"
                    )
            ) {
                if (!resultado.next()
                        || resultado.getString(2) == null
                        || resultado.getString(2).isBlank()) {
                    throw new IllegalStateException(
                            "A conexão não confirmou o uso de TLS."
                    );
                }

                System.out.println("PASSOU: conexão utiliza TLS.");
            }

            System.out.println(
                    "Driver JDBC: " + conexao.getMetaData().getDriverVersion()
            );
        }

        System.out.println("Conexão encerrada com sucesso.");
    }
}