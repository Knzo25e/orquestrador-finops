package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.dominio.entidades.Departamento;
import br.mackenzie.finops.dominio.entidades.ItemRequisicao;
import br.mackenzie.finops.dominio.entidades.RecursoCatalogo;
import br.mackenzie.finops.dominio.entidades.Requisicao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.sql.PreparedStatement;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Locale;

public final class VerificarRequisicoesJdbc {
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
        UUID requisicaoId = UUID.randomUUID();

        try (Connection conexao = fabrica.abrir()) {
            conferirAmbiente(conexao);
            conexao.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            conexao.setAutoCommit(false);

            DepartamentoRepositoryJdbc departamentos =
                    new DepartamentoRepositoryJdbc(conexao);
            RecursoCatalogoRepositoryJdbc recursos =
                    new RecursoCatalogoRepositoryJdbc(conexao);
            RequisicaoRepositoryJdbc requisicoes =
                    new RequisicaoRepositoryJdbc(conexao);

            try {
                conferir(
                        requisicoes.buscarPorId(requisicaoId).isEmpty(),
                        "UUID inexistente retorna ausencia"
                );

                conferir(
                        requisicoes.listarPorDepartamento(departamentoId).isEmpty(),
                        "Departamento sem pedidos retorna lista vazia"
                );

                Departamento departamento = Departamento.reconstituir(
                        departamentoId,
                        "Departamento teste Issue 63",
                        new BigDecimal("1000.00"),
                        new BigDecimal("800.00")
                );

                RecursoCatalogo recurso = new RecursoCatalogo(
                        recursoId,
                        "Recurso teste Issue 63",
                        new BigDecimal("12.50")
                );

                departamentos.adicionar(departamento);
                recursos.adicionar(recurso);

                Requisicao pedido = Requisicao.criarComId(
                        requisicaoId, departamento
                );
                pedido.adicionarItem(new ItemRequisicao(recurso, 2));
                pedido.adicionarItem(new ItemRequisicao(recurso, 3));

                requisicoes.adicionar(pedido);

                Requisicao recuperada = requisicoes
                        .buscarPorId(requisicaoId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Requisicao cadastrada nao encontrada."
                        ));

                conferir(
                        recuperada.getId().equals(requisicaoId)
                                && recuperada.getDepartamento().getId()
                                        .equals(departamentoId)
                                && recuperada != pedido,
                        "Recuperacao preserva identidade e reconstitui instancia"
                );

                conferir(
                        "INICIAL".equals(recuperada.getStatus())
                                && recuperada.getMotivoRevisao() == null
                                && recuperada.getCustoTotalProjetado()
                                        .compareTo(new BigDecimal("62.50")) == 0,
                        "Estado, motivo e total preservados"
                );

                List<ItemRequisicao> itens = recuperada.getItens();

                conferir(
                        itens.size() == 2
                                && itens.get(0).getQuantidade() == 2
                                && itens.get(1).getQuantidade() == 3
                                && itens.get(0).getRecurso().getId().equals(recursoId)
                                && itens.get(1).getRecurso().getId().equals(recursoId),
                        "Ordem e recursos repetidos preservados"
                );

                conferir(
                        itens.get(0).getPrecoUnitario()
                                .compareTo(new BigDecimal("12.50")) == 0
                                && itens.get(1).getPrecoUnitario()
                                        .compareTo(new BigDecimal("12.50")) == 0
                                && itens.get(0).getCustoSubtotal()
                                        .compareTo(new BigDecimal("25.00")) == 0
                                && itens.get(1).getCustoSubtotal()
                                        .compareTo(new BigDecimal("37.50")) == 0,
                        "Precos e subtotais preservados"
                );

                conferir(
                        recuperada.getDepartamento().getSaldoDisponivel()
                                .compareTo(new BigDecimal("800.00")) == 0
                                && departamentos.buscarPorId(departamentoId)
                                        .orElseThrow().getSaldoDisponivel()
                                        .compareTo(new BigDecimal("800.00")) == 0
                                && departamento.getSaldoDisponivel()
                                        .compareTo(new BigDecimal("800.00")) == 0,
                        "Cadastro e recuperacao preservam saldo"
                );

                List<Requisicao> lista =
                        requisicoes.listarPorDepartamento(departamentoId);

                conferir(
                        lista.size() == 1
                                && lista.get(0).getId().equals(requisicaoId)
                                && lista.get(0).getItens().size() == 2,
                        "Listagem recupera pedido completo do departamento"
                );

                try {
                    lista.add(recuperada);
                    throw new IllegalStateException(
                            "FALHOU: lista permitiu inclusao."
                    );
                } catch (UnsupportedOperationException esperado) {
                    conferir(true, "Lista retornada nao permite inclusao");
                }

            verificarDuplicata(requisicoes, pedido, recurso);
            verificarPrecoHistorico(conexao, requisicoes, requisicaoId, recursoId);

                conferir(
                        !conexao.isClosed()
                                && !conexao.getAutoCommit()
                                && conexao.getTransactionIsolation()
                                        == Connection.TRANSACTION_READ_COMMITTED,
                        "Repositorio preserva configuracao e conexao aberta"
                );
            } finally {
                conexao.rollback();
            }

            conferir(
                    requisicoes.buscarPorId(requisicaoId).isEmpty()
                            && departamentos.buscarPorId(departamentoId).isEmpty()
                            && recursos.buscarPorId(recursoId).isEmpty(),
                    "Rollback remove os cadastros temporarios"
            );

            conexao.rollback();
        }

        try (Connection conexao = fabrica.abrir()) {
            conferirAmbiente(conexao);
            conexao.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

            conferir(
                    new RequisicaoRepositoryJdbc(conexao)
                            .buscarPorId(requisicaoId).isEmpty(),
                    "Nova conexao confirma ausencia da requisicao desfeita"
            );
        }
    verificarAutocommit(fabrica);
    verificarDadosInconsistentes(fabrica);
    verificarRollbackAposFalha(fabrica);

        System.out.println(
                "Todas as " + verificacoes + " verificacoes passaram."
        );
    }

        private static void verificarPrecoHistorico(
            Connection conexao,
            RequisicaoRepositoryJdbc requisicoes,
            UUID requisicaoId,
            UUID recursoId
    ) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement(
                "UPDATE recurso_catalogo SET custo_mensal = ? WHERE id = ?"
        )) {
            comando.setBigDecimal(1, new BigDecimal("99.00"));
            comando.setString(2, recursoId.toString());

            conferir(
                    comando.executeUpdate() == 1,
                    "Alteracao do catalogo afeta somente o recurso do teste"
            );
        }

        Requisicao recuperada = requisicoes.buscarPorId(requisicaoId)
                .orElseThrow(() -> new IllegalStateException(
                        "Requisicao nao encontrada apos alterar o catalogo."
                ));

        List<ItemRequisicao> itens = recuperada.getItens();

        conferir(
                itens.size() == 2
                        && itens.stream().allMatch(item ->
                                item.getRecurso().getCustoMensal()
                                        .compareTo(new BigDecimal("99.00")) == 0
                        ),
                "Recursos recuperados apresentam o preco atual do catalogo"
        );

        conferir(
                itens.stream().allMatch(item ->
                        item.getPrecoUnitario()
                                .compareTo(new BigDecimal("12.50")) == 0
                )
                        && itens.get(0).getCustoSubtotal()
                                .compareTo(new BigDecimal("25.00")) == 0
                        && itens.get(1).getCustoSubtotal()
                                .compareTo(new BigDecimal("37.50")) == 0
                        && recuperada.getCustoTotalProjetado()
                                .compareTo(new BigDecimal("62.50")) == 0,
                "Alteracao do catalogo preserva precos historicos e total"
        );

        conferir(
                recuperada.getDepartamento().getSaldoDisponivel()
                        .compareTo(new BigDecimal("800.00")) == 0,
                "Recuperacao apos alterar o catalogo preserva saldo"
        );
    }

        private static void verificarDuplicata(
            RequisicaoRepositoryJdbc requisicoes,
            Requisicao original,
            RecursoCatalogo recurso
    ) {
        Requisicao duplicata = Requisicao.criarComId(
                original.getId(),
                original.getDepartamento()
        );
        duplicata.adicionarItem(new ItemRequisicao(recurso, 1));

        try {
            requisicoes.adicionar(duplicata);
            throw new IllegalStateException(
                    "FALHOU: cadastro duplicado foi aceito."
            );
        } catch (IllegalArgumentException esperado) {
            conferir(true, "Requisicao com UUID duplicado e rejeitada");
        }

        Requisicao recuperada = requisicoes.buscarPorId(original.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Requisicao original desapareceu apos a duplicata."
                ));

        List<ItemRequisicao> itens = recuperada.getItens();

        conferir(
                recuperada.getDepartamento().getId()
                        .equals(original.getDepartamento().getId())
                        && "INICIAL".equals(recuperada.getStatus())
                        && recuperada.getMotivoRevisao() == null
                        && recuperada.getCustoTotalProjetado()
                                .compareTo(new BigDecimal("62.50")) == 0,
                "Duplicata preserva cabecalho e total originais"
        );

        conferir(
                itens.size() == 2
                        && itens.get(0).getQuantidade() == 2
                        && itens.get(1).getQuantidade() == 3
                        && itens.stream().allMatch(item ->
                                item.getRecurso().getId().equals(recurso.getId())
                                        && item.getPrecoUnitario()
                                                .compareTo(new BigDecimal("12.50")) == 0
                        )
                        && itens.get(0).getCustoSubtotal()
                                .compareTo(new BigDecimal("25.00")) == 0
                        && itens.get(1).getCustoSubtotal()
                                .compareTo(new BigDecimal("37.50")) == 0,
                "Duplicata preserva os dois itens originais"
        );
    }

        private static void verificarRollbackAposFalha(
            FabricaConexoes fabrica
    ) throws SQLException {
        UUID departamentoId = UUID.randomUUID();
        UUID recursoId = UUID.randomUUID();
        UUID requisicaoId = UUID.randomUUID();
        boolean[] falhaInjetada = {false};

        try (Connection conexao = fabrica.abrir()) {
            conferirAmbiente(conexao);
            conexao.setAutoCommit(false);

            try {
                Departamento departamento = Departamento.reconstituir(
                        departamentoId,
                        "Departamento rollback Issue 63",
                        new BigDecimal("1000.00"),
                        new BigDecimal("800.00")
                );

                RecursoCatalogo recurso = new RecursoCatalogo(
                        recursoId,
                        "Recurso rollback Issue 63",
                        new BigDecimal("12.50")
                );

                new DepartamentoRepositoryJdbc(conexao)
                        .adicionar(departamento);
                new RecursoCatalogoRepositoryJdbc(conexao)
                        .adicionar(recurso);

                Requisicao pedido = Requisicao.criarComId(
                        requisicaoId, departamento
                );
                pedido.adicionarItem(new ItemRequisicao(recurso, 2));
                pedido.adicionarItem(new ItemRequisicao(recurso, 3));

                Connection conexaoComFalha = simularFalhaNoSegundoItem(
                        conexao, falhaInjetada
                );

                try {
                    new RequisicaoRepositoryJdbc(conexaoComFalha)
                            .adicionar(pedido);

                    throw new IllegalStateException(
                            "FALHOU: a falha simulada nao foi propagada."
                    );
                } catch (FalhaPersistenciaException esperado) {
                    conferir(
                            falhaInjetada[0],
                            "Falha no segundo item e informada ao chamador"
                    );
                }

                conferir(
                        contar(
                                conexao,
                                "SELECT COUNT(*) FROM requisicao WHERE id = ?",
                                requisicaoId
                        ) == 1
                                && contar(
                                        conexao,
                                        "SELECT COUNT(*) FROM item_requisicao "
                                                + "WHERE requisicao_id = ? AND ordem = 1",
                                        requisicaoId
                                ) == 1
                                && contar(
                                        conexao,
                                        "SELECT COUNT(*) FROM item_requisicao "
                                                + "WHERE requisicao_id = ?",
                                        requisicaoId
                                ) == 1,
                        "Falha ocorre apos gravar cabecalho e primeiro item"
                );

                conferir(
                        !conexao.isClosed() && !conexao.getAutoCommit(),
                        "Falha preserva conexao e controle da transacao"
                );
            } finally {
                conexao.rollback();
            }

            conferir(
                    registrosAusentes(
                            conexao, requisicaoId, departamentoId, recursoId
                    ),
                    "Rollback remove cabecalho, item e cadastros auxiliares"
            );

            conexao.rollback();
        }

        try (Connection conexao = fabrica.abrir()) {
            conferirAmbiente(conexao);

            conferir(
                    registrosAusentes(
                            conexao, requisicaoId, departamentoId, recursoId
                    ),
                    "Nova conexao confirma ausencia da gravacao parcial"
            );
        }
    }

    private static Connection simularFalhaNoSegundoItem(
            Connection real,
            boolean[] falhaInjetada
    ) {
        int[] tentativas = {0};

        return (Connection) Proxy.newProxyInstance(
                VerificarRequisicoesJdbc.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, metodo, argumentos) -> {
                    Object resultado = invocar(real, metodo, argumentos);

                    if ("prepareStatement".equals(metodo.getName())
                            && argumentos != null
                            && argumentos.length > 0
                            && argumentos[0] instanceof String sql
                            && sql.stripLeading()
                                    .toUpperCase(Locale.ROOT)
                                    .startsWith("INSERT INTO ITEM_REQUISICAO")) {
                        PreparedStatement comando =
                                (PreparedStatement) resultado;

                        return Proxy.newProxyInstance(
                                VerificarRequisicoesJdbc.class.getClassLoader(),
                                new Class<?>[]{PreparedStatement.class},
                                (proxyComando, metodoComando, argumentosComando) -> {
                                    if ("executeUpdate".equals(
                                            metodoComando.getName()
                                    )) {
                                        tentativas[0]++;

                                        if (tentativas[0] == 2) {
                                            falhaInjetada[0] = true;
                                            throw new SQLException(
                                                    "Falha simulada pelo teste.",
                                                    "HY000"
                                            );
                                        }
                                    }

                                    return invocar(
                                            comando,
                                            metodoComando,
                                            argumentosComando
                                    );
                                }
                        );
                    }

                    return resultado;
                }
        );
    }

    private static Object invocar(
            Object alvo,
            Method metodo,
            Object[] argumentos
    ) throws Throwable {
        try {
            return metodo.invoke(alvo, argumentos);
        } catch (InvocationTargetException excecao) {
            throw excecao.getCause();
        }
    }

    private static long contar(
            Connection conexao,
            String sql,
            UUID id
    ) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, id.toString());

            try (ResultSet resultado = comando.executeQuery()) {
                if (!resultado.next()) {
                    throw new IllegalStateException(
                            "A contagem nao retornou resultado."
                    );
                }

                return resultado.getLong(1);
            }
        }
    }

    private static boolean registrosAusentes(
            Connection conexao,
            UUID requisicaoId,
            UUID departamentoId,
            UUID recursoId
    ) throws SQLException {
        return contar(
                conexao,
                "SELECT COUNT(*) FROM requisicao WHERE id = ?",
                requisicaoId
        ) == 0
                && contar(
                        conexao,
                        "SELECT COUNT(*) FROM item_requisicao "
                                + "WHERE requisicao_id = ?",
                        requisicaoId
                ) == 0
                && contar(
                        conexao,
                        "SELECT COUNT(*) FROM departamento WHERE id = ?",
                        departamentoId
                ) == 0
                && contar(
                        conexao,
                        "SELECT COUNT(*) FROM recurso_catalogo WHERE id = ?",
                        recursoId
                ) == 0;
    }

        private static void verificarAutocommit(
            FabricaConexoes fabrica
    ) throws SQLException {
        UUID departamentoId = UUID.randomUUID();
        UUID recursoId = UUID.randomUUID();
        UUID requisicaoId = UUID.randomUUID();

        try (Connection conexao = fabrica.abrir()) {
            conferirAmbiente(conexao);

            exigirAutocommitAtivo(conexao);

            Departamento departamento = Departamento.reconstituir(
                    departamentoId,
                    "Departamento autocommit Issue 63",
                    new BigDecimal("1000.00"),
                    new BigDecimal("800.00")
            );

            RecursoCatalogo recurso = new RecursoCatalogo(
                    recursoId,
                    "Recurso autocommit Issue 63",
                    new BigDecimal("12.50")
            );

            Requisicao pedido = Requisicao.criarComId(
                    requisicaoId, departamento
            );
            pedido.adicionarItem(new ItemRequisicao(recurso, 2));

            boolean rejeitou = false;

            try {
                new RequisicaoRepositoryJdbc(conexao).adicionar(pedido);
            } catch (IllegalStateException esperado) {
                rejeitou = true;
            }

            conferir(
                    rejeitou,
                    "Cadastro rejeita autocommit antes de conferir vinculos"
            );

            conferir(
                    registrosAusentes(
                            conexao, requisicaoId, departamentoId, recursoId
                    ),
                    "Rejeicao por autocommit nao insere registros"
            );

            conferir(
                    !conexao.isClosed() && conexao.getAutoCommit(),
                    "Rejeicao preserva conexao aberta e autocommit habilitado"
            );
        }
    }

    private static void exigirAutocommitAtivo(
            Connection conexao
    ) throws SQLException {
        if (!conexao.getAutoCommit()) {
            throw new IllegalStateException(
                    "O teste exige uma nova conexao com autocommit habilitado."
            );
        }
    }

    private static void verificarDadosInconsistentes(
            FabricaConexoes fabrica
    ) throws SQLException {
        UUID departamentoId = UUID.randomUUID();
        UUID recursoId = UUID.randomUUID();
        UUID requisicaoId = UUID.randomUUID();

        try (Connection conexao = fabrica.abrir()) {
            conferirAmbiente(conexao);
            conexao.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            conexao.setAutoCommit(false);

            try {
                Departamento departamento = Departamento.reconstituir(
                        departamentoId,
                        "Departamento inconsistencias Issue 63",
                        new BigDecimal("1000.00"),
                        new BigDecimal("800.00")
                );

                RecursoCatalogo recurso = new RecursoCatalogo(
                        recursoId,
                        "Recurso inconsistencias Issue 63",
                        new BigDecimal("12.50")
                );

                new DepartamentoRepositoryJdbc(conexao)
                        .adicionar(departamento);
                new RecursoCatalogoRepositoryJdbc(conexao)
                        .adicionar(recurso);

                Requisicao pedido = Requisicao.criarComId(
                        requisicaoId, departamento
                );
                pedido.adicionarItem(new ItemRequisicao(recurso, 2));
                pedido.adicionarItem(new ItemRequisicao(recurso, 3));

                RequisicaoRepositoryJdbc repositorio =
                        new RequisicaoRepositoryJdbc(conexao);
                repositorio.adicionar(pedido);

                // Cada cenário parte dos mesmos dados válidos.
                java.sql.Savepoint base = conexao.setSavepoint();

                try {
                    alterarRegistro(
                            conexao,
                            "UPDATE requisicao SET custo_total = 1.00 WHERE id = ?",
                            requisicaoId,
                            1
                    );

                    exigirLeituraInconsistente(
                            repositorio, requisicaoId, departamentoId,
                            "Total diferente da soma"
                    );
                } finally {
                    conexao.rollback(base);
                    conexao.releaseSavepoint(base);
                }

                base = conexao.setSavepoint();

                try {
                    alterarRegistro(
                            conexao,
                            "UPDATE item_requisicao SET ordem = 3 "
                                    + "WHERE requisicao_id = ? AND ordem = 2",
                            requisicaoId,
                            1
                    );

                    exigirLeituraInconsistente(
                            repositorio, requisicaoId, departamentoId,
                            "Lacuna na ordem dos itens"
                    );
                } finally {
                    conexao.rollback(base);
                    conexao.releaseSavepoint(base);
                }

                base = conexao.setSavepoint();

                try {
                    alterarRegistro(
                            conexao,
                            "DELETE FROM item_requisicao WHERE requisicao_id = ?",
                            requisicaoId,
                            2
                    );

                    exigirLeituraInconsistente(
                            repositorio, requisicaoId, departamentoId,
                            "Cabecalho sem itens"
                    );
                } finally {
                    conexao.rollback(base);
                    conexao.releaseSavepoint(base);
                }

                Requisicao recuperada = repositorio
                        .buscarPorId(requisicaoId).orElseThrow();

                conferir(
                        recuperada.getItens().size() == 2
                                && recuperada.getCustoTotalProjetado()
                                        .compareTo(new BigDecimal("62.50")) == 0
                                && recuperada.getDepartamento()
                                        .getSaldoDisponivel()
                                        .compareTo(new BigDecimal("800.00")) == 0,
                        "Reversao dos cenarios restaura pedido e preserva saldo"
                );
            } finally {
                conexao.rollback();
            }

            conferir(
                    registrosAusentes(
                            conexao, requisicaoId, departamentoId, recursoId
                    ),
                    "Teste de inconsistencias remove todos os registros temporarios"
            );

            conexao.rollback();
        }
    }

    private static void alterarRegistro(
            Connection conexao,
            String sql,
            UUID id,
            int quantidadeEsperada
    ) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, id.toString());

            if (comando.executeUpdate() != quantidadeEsperada) {
                throw new IllegalStateException(
                        "A preparacao do cenario afetou quantidade inesperada."
                );
            }
        }
    }

    private static void exigirLeituraInconsistente(
            RequisicaoRepositoryJdbc repositorio,
            UUID requisicaoId,
            UUID departamentoId,
            String descricao
    ) {
        boolean rejeitouBusca = false;

        try {
            repositorio.buscarPorId(requisicaoId);
        } catch (FalhaPersistenciaException esperado) {
            rejeitouBusca = true;
        }

        conferir(
                rejeitouBusca,
                descricao + ": busca informa inconsistencia"
        );

        boolean rejeitouListagem = false;

        try {
            repositorio.listarPorDepartamento(departamentoId);
        } catch (FalhaPersistenciaException esperado) {
            rejeitouListagem = true;
        }

        conferir(
                rejeitouListagem,
                descricao + ": listagem informa inconsistencia"
        );
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

    private static void conferir(boolean condicao, String descricao) {
        if (!condicao) {
            throw new IllegalStateException("FALHOU: " + descricao);
        }

        verificacoes++;
        System.out.println("PASSOU: " + descricao);
    }
}