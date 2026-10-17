package br.mackenzie.finops.infraestrutura.persistencia;

public final class ConfiguracaoBanco {
    private final String url;
    private final String usuario;
    private final String senha;

    public ConfiguracaoBanco(String url, String usuario, String senha) {
        this.url = exigirPreenchimento(url, "FINOPS_DB_URL");
        this.usuario = exigirPreenchimento(usuario, "FINOPS_DB_USER");
        this.senha = exigirPreenchimento(senha, "FINOPS_DB_PASSWORD");

        if (!this.url.startsWith("jdbc:mysql://")) {
            throw new IllegalArgumentException(
                    "FINOPS_DB_URL deve utilizar o formato jdbc:mysql://."
            );
        }
    }

    public static ConfiguracaoBanco carregarDoAmbiente() {
        return new ConfiguracaoBanco(
                System.getenv("FINOPS_DB_URL"),
                System.getenv("FINOPS_DB_USER"),
                System.getenv("FINOPS_DB_PASSWORD")
        );
    }

    private static String exigirPreenchimento(
            String valor,
            String variavel
    ) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(
                    "A variável " + variavel + " é obrigatória."
            );
        }

        return valor;
    }

    public String getUrl() {
        return url;
    }

    public String getUsuario() {
        return usuario;
    }

    String getSenha() {
        return senha;
    }

    @Override
    public String toString() {
        return "ConfiguracaoBanco[conteudo protegido]";
    }
}