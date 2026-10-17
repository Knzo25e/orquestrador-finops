package br.mackenzie.finops.infraestrutura.persistencia;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public final class FabricaConexoes {
    private final ConfiguracaoBanco configuracao;

    public FabricaConexoes(ConfiguracaoBanco configuracao) {
        if (configuracao == null) {
            throw new IllegalArgumentException(
                    "A configuração do banco é obrigatória."
            );
        }

        this.configuracao = configuracao;
    }

    /**
     * Abre uma nova conexão.
     * O chamador é responsável por fechá-la.
     * Não inicia uma transação nem executa comandos SQL.
     */
    public Connection abrir() throws SQLException {
        Properties propriedades = new Properties();
        propriedades.setProperty("user", configuracao.getUsuario());
        propriedades.setProperty("password", configuracao.getSenha());
        propriedades.setProperty("paranoid", "true");

        return DriverManager.getConnection(
                configuracao.getUrl(),
                propriedades
        );
    }
}