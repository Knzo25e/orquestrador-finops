package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.dominio.entidades.Departamento;
import br.mackenzie.finops.dominio.entidades.RecursoCatalogo;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

public final class VerificarPersistenciaCadastros {

    private static final String NOME_DEPARTAMENTO =
            "Teste entre processos / Departamento / Issue 61";
    private static final String NOME_RECURSO =
            "Teste entre processos / Recurso / Issue 61";

    public static void main(String[] args) {
        try {
            executar(args);
        } catch (SQLException excecao) {
            System.err.println(
                    "Falha JDBC. SQLState: " + excecao.getSQLState()
                            + "; codigo: " + excecao.getErrorCode()
            );
            System.exit(1);
        } catch (RuntimeException excecao) {
            System.err.println(excecao.getMessage());
            System.exit(1);
        }
    }

    private static void executar(String[] args) throws SQLException {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "Informe: gravar|consultar|limpar departamentoUUID recursoUUID"
            );
        }

        String operacao = args[0];

        if (!operacao.equals("gravar")
                && !operacao.equals("consultar")
                && !operacao.equals("limpar")) {
            throw new IllegalArgumentException("Operacao desconhecida.");
        }

        UUID departamentoId = UUID.fromString(args[1]);
        UUID recursoId = UUID.fromString(args[2]);

        FabricaConexoes fabrica = new FabricaConexoes(
                ConfiguracaoBanco.carregarDoAmbiente()
        );

        try (Connection conexao = fabrica.abrir()) {
            conferirAmbiente(conexao);
            conexao.setAutoCommit(false);

            try {
                DepartamentoRepositoryJdbc departamentos =
                        new DepartamentoRepositoryJdbc(conexao);
                RecursoCatalogoRepositoryJdbc recursos =
                        new RecursoCatalogoRepositoryJdbc(conexao);

                switch (operacao) {
                    case "gravar" -> {
                        if (departamentos.buscarPorId(departamentoId).isPresent()
                                || recursos.buscarPorId(recursoId).isPresent()) {
                            throw new IllegalStateException(
                                    "Os identificadores do teste ja existem."
                            );
                        }

                        departamentos.adicionar(
                                Departamento.reconstituir(
                                        departamentoId,
                                        NOME_DEPARTAMENTO,
                                        new BigDecimal("1000.00"),
                                        new BigDecimal("725.50")
                                )
                        );

                        recursos.adicionar(
                                new RecursoCatalogo(
                                        recursoId,
                                        NOME_RECURSO,
                                        new BigDecimal("125.25")
                                )
                        );

                        conexao.commit();
                        System.out.println(
                                "PASSOU: dois cadastros confirmados por commit."
                        );
                    }

                    case "consultar" -> {
                        Departamento departamento = departamentos
                                .buscarPorId(departamentoId)
                                .orElseThrow(() -> new IllegalStateException(
                                        "Departamento persistido nao encontrado."
                                ));

                        RecursoCatalogo recurso = recursos
                                .buscarPorId(recursoId)
                                .orElseThrow(() -> new IllegalStateException(
                                        "Recurso persistido nao encontrado."
                                ));

                        if (!departamentoId.equals(departamento.getId())
                                || !NOME_DEPARTAMENTO.equals(
                                        departamento.getNome()
                                )
                                || departamento.getOrcamentoMensal().compareTo(
                                        new BigDecimal("1000.00")
                                ) != 0
                                || departamento.getSaldoDisponivel().compareTo(
                                        new BigDecimal("725.50")
                                ) != 0
                                || !recursoId.equals(recurso.getId())
                                || !NOME_RECURSO.equals(recurso.getNome())
                                || recurso.getCustoMensal().compareTo(
                                        new BigDecimal("125.25")
                                ) != 0) {
                            throw new IllegalStateException(
                                    "Os dados recuperados diferem dos gravados."
                            );
                        }

                        conexao.rollback();
                        System.out.println(
                                "PASSOU: identidades, nomes e valores recuperados."
                        );
                    }

                    case "limpar" -> {
                        excluir(
                                conexao,
                                "DELETE FROM recurso_catalogo WHERE id = ? AND nome = ?",
                                recursoId,
                                NOME_RECURSO
                        );

                        excluir(
                                conexao,
                                "DELETE FROM departamento WHERE id = ? AND nome = ?",
                                departamentoId,
                                NOME_DEPARTAMENTO
                        );

                        if (departamentos.buscarPorId(departamentoId).isPresent()
                                || recursos.buscarPorId(recursoId).isPresent()) {
                            throw new IllegalStateException(
                                    "Limpeza incompleta; registros preservados."
                            );
                        }

                        conexao.commit();
                        System.out.println(
                                "PASSOU: limpeza dos registros do teste confirmada."
                        );
                    }

                    default -> throw new IllegalStateException(
                            "Operacao nao implementada."
                    );
                }
            } finally {
                // Encerra qualquer transação ainda pendente.
                // Não desfaz um commit que já tenha sido concluído.
                conexao.rollback();
            }
        }
    }

    private static void excluir(
            Connection conexao,
            String sql,
            UUID id,
            String nome
    ) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, id.toString());
            comando.setString(2, nome);
            comando.executeUpdate();
        }
    }

    private static void conferirAmbiente(Connection conexao)
            throws SQLException {
        try (
                Statement comando = conexao.createStatement();
                ResultSet resultado = comando.executeQuery(
                        "SELECT DATABASE(), CURRENT_USER()"
                )
        ) {
            if (!resultado.next()
                    || !"finops_test".equals(resultado.getString(1))
                    || !"finops_test_app@localhost".equals(
                            resultado.getString(2)
                    )) {
                throw new IllegalStateException(
                        "Execucao permitida somente em finops_test "
                                + "com finops_test_app@localhost."
                );
            }
        }
    }
}