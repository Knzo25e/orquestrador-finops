package br.mackenzie.finops.infraestrutura.persistencia;

public final class VerificarConfiguracaoBanco {
    private static int verificacoes;

    public static void main(String[] args) {
        String url =
                "jdbc:mysql://localhost:3306/finops_test?sslMode=REQUIRED";
        String usuario = "usuario_ficticio";
        String senha = " senha_ficticia ";

        System.out.println("--- Configuracao JDBC / Issue #59 ---");

        ConfiguracaoBanco configuracao =
                new ConfiguracaoBanco(url, usuario, senha);

        conferir(
                url.equals(configuracao.getUrl())
                        && usuario.equals(configuracao.getUsuario()),
                "Preserva URL e usuario informados"
        );

        conferir(
                senha.equals(configuracao.getSenha()),
                "Preserva a senha sem remover espacos"
        );

        conferir(
                !configuracao.toString().contains(url)
                        && !configuracao.toString().contains(usuario)
                        && !configuracao.toString().contains(senha),
                "Representacao textual nao revela a configuracao"
        );

        String[] entradasInvalidas = {null, "", "   "};
        String[] descricoes = {"nula", "vazia", "em branco"};

        for (int i = 0; i < entradasInvalidas.length; i++) {
            String entrada = entradasInvalidas[i];
            String descricao = descricoes[i];

            esperarRejeicao(
                    () -> new ConfiguracaoBanco(entrada, usuario, senha),
                    "FINOPS_DB_URL",
                    "Rejeita URL " + descricao
            );

            esperarRejeicao(
                    () -> new ConfiguracaoBanco(url, entrada, senha),
                    "FINOPS_DB_USER",
                    "Rejeita usuario " + descricao
            );

            esperarRejeicao(
                    () -> new ConfiguracaoBanco(url, usuario, entrada),
                    "FINOPS_DB_PASSWORD",
                    "Rejeita senha " + descricao
            );
        }

        esperarRejeicao(
                () -> new ConfiguracaoBanco(
                        "jdbc:postgresql://localhost/finops_test",
                        usuario,
                        senha
                ),
                "FINOPS_DB_URL",
                "Rejeita protocolo diferente de MySQL"
        );

        esperarRejeicao(
                () -> new FabricaConexoes(null),
                "configuração",
                "Fabrica rejeita configuracao nula"
        );

        System.out.println(
                "Todas as " + verificacoes + " verificacoes passaram."
        );
    }

    private static void esperarRejeicao(
            Runnable operacao,
            String trechoEsperado,
            String descricao
    ) {
        try {
            operacao.run();
        } catch (IllegalArgumentException excecao) {
            conferir(
                    excecao.getMessage() != null
                            && excecao.getMessage().contains(trechoEsperado),
                    descricao
            );
            return;
        }

        throw new IllegalStateException(
                "FALHOU: " + descricao + " — entrada aceita indevidamente."
        );
    }

    private static void conferir(boolean condicao, String descricao) {
        if (!condicao) {
            throw new IllegalStateException("FALHOU: " + descricao);
        }

        verificacoes++;
        System.out.println("PASSOU: " + descricao);
    }
}