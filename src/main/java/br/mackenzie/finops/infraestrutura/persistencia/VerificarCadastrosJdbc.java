package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.dominio.entidades.Departamento;
import br.mackenzie.finops.dominio.entidades.RecursoCatalogo;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

public final class VerificarCadastrosJdbc {
    private static int verificacoes;

    public static void main(String[] args) {
        try {
            executar();
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

    private static void executar() throws SQLException {
        FabricaConexoes fabrica = new FabricaConexoes(
                ConfiguracaoBanco.carregarDoAmbiente()
        );

        UUID departamentoId = UUID.randomUUID();
        UUID recursoId = UUID.randomUUID();

        try (Connection conexao = fabrica.abrir()) {
            conferirAmbiente(conexao);
            conexao.setAutoCommit(false);

            DepartamentoRepositoryJdbc departamentos =
                    new DepartamentoRepositoryJdbc(conexao);

            RecursoCatalogoRepositoryJdbc recursos =
                    new RecursoCatalogoRepositoryJdbc(conexao);

            try {
                conferir(
                        departamentos.buscarPorId(departamentoId).isEmpty()
                                && recursos.buscarPorId(recursoId).isEmpty(),
                        "Identificadores novos nao possuem cadastro"
                );

                Departamento departamento = Departamento.reconstituir(
                        departamentoId,
                        "Engenharia 'Teste'",
                        new BigDecimal("1000.00"),
                        new BigDecimal("725.50")
                );

                RecursoCatalogo recurso = new RecursoCatalogo(
                        recursoId,
                        "Servidor 'Teste'",
                        new BigDecimal("125.25")
                );

                departamentos.adicionar(departamento);
                recursos.adicionar(recurso);

                Departamento recuperado = departamentos
                        .buscarPorId(departamentoId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Departamento cadastrado nao foi encontrado."
                        ));

                RecursoCatalogo recursoRecuperado = recursos
                        .buscarPorId(recursoId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Recurso cadastrado nao foi encontrado."
                        ));

                conferir(
                        recuperado.getId().equals(departamentoId)
                                && recuperado.getNome().equals(
                                        departamento.getNome()
                                )
                                && recuperado.getOrcamentoMensal()
                                        .compareTo(new BigDecimal("1000.00")) == 0
                                && recuperado.getSaldoDisponivel()
                                        .compareTo(new BigDecimal("725.50")) == 0,
                        "Departamento preserva identidade, nome, teto e saldo"
                );

                conferir(
                        recursoRecuperado.getId().equals(recursoId)
                                && recursoRecuperado.getNome().equals(
                                        recurso.getNome()
                                )
                                && recursoRecuperado.getCustoMensal()
                                        .compareTo(new BigDecimal("125.25")) == 0,
                        "Recurso preserva identidade, nome e custo"
                );

                conferir(
                        recuperado != departamento
                                && recursoRecuperado != recurso,
                        "Consultas reconstituem novas instancias"
                );

                esperarRejeicao(
                        () -> departamentos.adicionar(
                                new Departamento(
                                        departamentoId,
                                        "Nome divergente",
                                        new BigDecimal("999.00")
                                )
                        ),
                        "Departamento duplicado e rejeitado"
                );

                esperarRejeicao(
                        () -> recursos.adicionar(
                                new RecursoCatalogo(
                                        recursoId,
                                        "Nome divergente",
                                        new BigDecimal("999.00")
                                )
                        ),
                        "Recurso duplicado e rejeitado"
                );

                Departamento aposDuplicata = departamentos
                        .buscarPorId(departamentoId).orElseThrow();

                RecursoCatalogo recursoAposDuplicata = recursos
                        .buscarPorId(recursoId).orElseThrow();

                conferir(
                        aposDuplicata.getNome().equals(departamento.getNome())
                                && aposDuplicata.getOrcamentoMensal()
                                        .compareTo(new BigDecimal("1000.00")) == 0
                                && aposDuplicata.getSaldoDisponivel()
                                        .compareTo(new BigDecimal("725.50")) == 0
                                && recursoAposDuplicata.getNome()
                                        .equals(recurso.getNome())
                                && recursoAposDuplicata.getCustoMensal()
                                        .compareTo(new BigDecimal("125.25")) == 0,
                        "Duplicatas preservam os dados originais"
                );

                conferir(
                        !conexao.isClosed() && !conexao.getAutoCommit(),
                        "Repositorios preservam conexao aberta e autocommit"
                );
            } finally {
                conexao.rollback();
            }

            conferir(
                    departamentos.buscarPorId(departamentoId).isEmpty()
                            && recursos.buscarPorId(recursoId).isEmpty(),
                    "Rollback do chamador desfaz os dois cadastros"
            );

            conexao.rollback();
        }

        try (Connection conexao = fabrica.abrir()) {
            conferirAmbiente(conexao);

            conferir(
                    new DepartamentoRepositoryJdbc(conexao)
                            .buscarPorId(departamentoId).isEmpty()
                            && new RecursoCatalogoRepositoryJdbc(conexao)
                            .buscarPorId(recursoId).isEmpty(),
                    "Nova conexao confirma ausencia dos cadastros desfeitos"
            );
        }

        verificarConexaoFechada(fabrica);

        System.out.println(
                "Todas as " + verificacoes + " verificacoes passaram."
        );
    }

        private static void verificarConexaoFechada(
            FabricaConexoes fabrica
    ) throws SQLException {
        Connection conexaoFechada;

        try (Connection conexao = fabrica.abrir()) {
            conferirAmbiente(conexao);
            conexaoFechada = conexao;
        }

        DepartamentoRepositoryJdbc departamentos =
                new DepartamentoRepositoryJdbc(conexaoFechada);

        RecursoCatalogoRepositoryJdbc recursos =
                new RecursoCatalogoRepositoryJdbc(conexaoFechada);

        esperarFalhaPersistencia(
                () -> departamentos.buscarPorId(UUID.randomUUID()),
                "Consulta de departamento com conexao fechada informa falha"
        );

        esperarFalhaPersistencia(
                () -> recursos.buscarPorId(UUID.randomUUID()),
                "Consulta de recurso com conexao fechada informa falha"
        );

        esperarFalhaPersistencia(
                () -> departamentos.adicionar(
                        new Departamento(
                                UUID.randomUUID(),
                                "Departamento sem conexao",
                                new BigDecimal("100.00")
                        )
                ),
                "Cadastro de departamento com conexao fechada informa falha"
        );

        esperarFalhaPersistencia(
                () -> recursos.adicionar(
                        new RecursoCatalogo(
                                UUID.randomUUID(),
                                "Recurso sem conexao",
                                new BigDecimal("10.00")
                        )
                ),
                "Cadastro de recurso com conexao fechada informa falha"
        );
    }

    private static void esperarFalhaPersistencia(
            Runnable operacao,
            String descricao
    ) {
        try {
            operacao.run();
        } catch (FalhaPersistenciaException excecao) {
            conferir(
                    excecao.getMessage() != null
                            && !excecao.getMessage().isBlank(),
                    descricao
            );
            return;
        }

        throw new IllegalStateException("FALHOU: " + descricao);
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

    private static void esperarRejeicao(
            Runnable operacao,
            String descricao
    ) {
        try {
            operacao.run();
        } catch (IllegalArgumentException excecao) {
            conferir(
                    excecao.getMessage() != null
                            && !excecao.getMessage().isBlank(),
                    descricao
            );
            return;
        }

        throw new IllegalStateException("FALHOU: " + descricao);
    }

    private static void conferir(boolean condicao, String descricao) {
        if (!condicao) {
            throw new IllegalStateException("FALHOU: " + descricao);
        }

        verificacoes++;
        System.out.println("PASSOU: " + descricao);
    }
}