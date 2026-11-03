package br.mackenzie.finops.infraestrutura.persistencia;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class VerificarRecursosConsultaCatalogoJdbc {

    private static int verificacoes;

    public static void main(String[] args) {
        Cenario vazio = new Cenario(null);

        conferir(
                new ConsultaCatalogoJdbc(vazio::abrir).listar().isEmpty(),
                "Resultado vazio é devolvido após consulta bem-sucedida"
        );

        conferir(
                vazio.fechados.equals(
                        List.of("resultado", "comando", "conexao")
                ),
                "Sucesso fecha resultado, comando e conexão nessa ordem"
        );

        verificarFalha(
                "configurar",
                List.of("conexao")
        );

        verificarFalha(
                "preparar",
                List.of("conexao")
        );

        verificarFalha(
                "executar",
                List.of("comando", "conexao")
        );

        verificarFalha(
                "ler",
                List.of("resultado", "comando", "conexao")
        );

        verificarFalha(
                "fecharResultado",
                List.of("resultado", "comando", "conexao")
        );

        verificarFalha(
                "fecharComando",
                List.of("resultado", "comando", "conexao")
        );

        verificarFalha(
                "fecharConexao",
                List.of("resultado", "comando", "conexao")
        );

        esperarFalha(
                () -> new ConsultaCatalogoJdbc(
                        (FornecedorConexaoJdbc) () -> null
                ).listar(),
                "Conexão nula é rejeitada"
        );

        esperarFalha(
                () -> new ConsultaCatalogoJdbc(
                        (FornecedorConexaoJdbc) () -> {
                            throw new SQLException("Falha controlada.");
                        }
                ).listar(),
                "Falha ao abrir conexão não vira catálogo vazio"
        );

        System.out.println(
                "Todas as " + verificacoes + " verificacoes passaram."
        );
    }

    private static void verificarFalha(
            String etapa,
            List<String> fechamentoEsperado
    ) {
        Cenario cenario = new Cenario(etapa);

        esperarFalha(
                () -> new ConsultaCatalogoJdbc(cenario::abrir).listar(),
                etapa + ": falha informada ao chamador"
        );

        conferir(
                cenario.fechados.equals(fechamentoEsperado),
                etapa + ": todos os recursos adquiridos recebem fechamento"
        );
    }

    private static final class Cenario {

        private final String etapaFalha;
        private final List<String> fechados = new ArrayList<>();

        private Cenario(String etapaFalha) {
            this.etapaFalha = etapaFalha;
        }

        private Connection abrir() {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, metodo, argumentos) -> {
                        switch (metodo.getName()) {
                            case "setTransactionIsolation":
                            case "setAutoCommit":
                                falharEm("configurar");
                                return null;

                            case "prepareStatement":
                                falharEm("preparar");
                                return comando();

                            case "close":
                                fechados.add("conexao");
                                falharEm("fecharConexao");
                                return null;

                            default:
                                throw inesperado(metodo.getName());
                        }
                    }
            );
        }

        private PreparedStatement comando() {
            return (PreparedStatement) Proxy.newProxyInstance(
                    PreparedStatement.class.getClassLoader(),
                    new Class<?>[]{PreparedStatement.class},
                    (proxy, metodo, argumentos) -> {
                        switch (metodo.getName()) {
                            case "executeQuery":
                                falharEm("executar");
                                return resultado();

                            case "close":
                                fechados.add("comando");
                                falharEm("fecharComando");
                                return null;

                            default:
                                throw inesperado(metodo.getName());
                        }
                    }
            );
        }

        private ResultSet resultado() {
            return (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(),
                    new Class<?>[]{ResultSet.class},
                    (proxy, metodo, argumentos) -> {
                        switch (metodo.getName()) {
                            case "next":
                                falharEm("ler");
                                return false;

                            case "close":
                                fechados.add("resultado");
                                falharEm("fecharResultado");
                                return null;

                            default:
                                throw inesperado(metodo.getName());
                        }
                    }
            );
        }

        private void falharEm(String etapa) throws SQLException {
            if (etapa.equals(etapaFalha)) {
                throw new SQLException("Falha controlada: " + etapa);
            }
        }

        private AssertionError inesperado(String metodo) {
            return new AssertionError(
                    "Método JDBC inesperado no cenário: " + metodo
            );
        }
    }

    private static void esperarFalha(
            Runnable operacao,
            String descricao
    ) {
        try {
            operacao.run();
        } catch (FalhaPersistenciaException esperada) {
            conferir(true, descricao);
            return;
        }

        throw new IllegalStateException(
                "FALHOU: " + descricao + " — exceção não lançada."
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