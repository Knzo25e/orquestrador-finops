package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.dominio.entidades.Departamento;
import br.mackenzie.finops.dominio.entidades.ItemRequisicao;
import br.mackenzie.finops.dominio.entidades.RecursoCatalogo;
import br.mackenzie.finops.dominio.entidades.Requisicao;
import br.mackenzie.finops.aplicacao.CriarRequisicaoPersistenteUseCase;
import br.mackenzie.finops.aplicacao.entradas.ItemSolicitado;
import br.mackenzie.finops.aplicacao.resultados.ResultadoCriacaoRequisicao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

public final class VerificarPersistenciaCriacao {

    private static final String NOME_DEPARTAMENTO =
            "Teste entre processos / Departamento / Issue 65";
    private static final String NOME_RECURSO =
            "Teste entre processos / Recurso / Issue 65";

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
        if (args.length != 4) {
            throw new IllegalArgumentException(
                    "Informe: gravar|consultar|limpar "
                            + "departamentoUUID recursoUUID requisicaoUUID"
            );
        }

        String operacao = args[0];

        if (!List.of("gravar", "consultar", "limpar").contains(operacao)) {
            throw new IllegalArgumentException("Operacao desconhecida.");
        }

        UUID departamentoId = UUID.fromString(args[1]);
        UUID recursoId = UUID.fromString(args[2]);
        UUID requisicaoId = UUID.fromString(args[3]);

        FabricaConexoes fabrica = new FabricaConexoes(
                ConfiguracaoBanco.carregarDoAmbiente()
        );

        try (Connection conexao = fabrica.abrir()) {
            conferirAmbiente(conexao);
            conexao.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            conexao.setAutoCommit(false);

            try {
                switch (operacao) {
                    case "gravar" -> gravar(
                            fabrica, conexao,
                            departamentoId, recursoId, requisicaoId
                    );
                    case "consultar" -> {
                        verificarRepeticao(
                                fabrica, departamentoId, recursoId, requisicaoId
                        );

                         consultar(
                                conexao, departamentoId, recursoId, requisicaoId
                        );
                    }
                    case "limpar" -> limpar(
                            conexao, departamentoId, recursoId, requisicaoId
                    );
                    default -> throw new IllegalStateException(
                            "Operacao nao implementada."
                    );
                }
            } finally {
                conexao.rollback();
            }
        }
    }

    private static void gravar(
            FabricaConexoes fabrica,
            Connection conexao,
            UUID departamentoId,
            UUID recursoId,
            UUID requisicaoId
    ) throws SQLException {
        DepartamentoRepositoryJdbc departamentos =
                new DepartamentoRepositoryJdbc(conexao);
        RecursoCatalogoRepositoryJdbc recursos =
                new RecursoCatalogoRepositoryJdbc(conexao);
        RequisicaoRepositoryJdbc requisicoes =
                new RequisicaoRepositoryJdbc(conexao);

        exigir(
                departamentos.buscarPorId(departamentoId).isEmpty()
                        && recursos.buscarPorId(recursoId).isEmpty()
                        && requisicoes.buscarPorId(requisicaoId).isEmpty(),
                "Os identificadores do teste ja existem."
        );

        departamentos.adicionar(
                Departamento.reconstituir(
                        departamentoId,
                        NOME_DEPARTAMENTO,
                        new BigDecimal("1000.00"),
                        new BigDecimal("800.00")
                )
        );

        recursos.adicionar(
                new RecursoCatalogo(
                        recursoId,
                        NOME_RECURSO,
                        new BigDecimal("12.50")
                )
        );

        // Os cadastros precisam estar confirmados para que a conexão
        // exclusiva do caso de uso possa consultá-los.
        conexao.commit();

        ResultadoCriacaoRequisicao resultado = criarCasoDeUso(fabrica)
                .executar(
                        requisicaoId,
                        departamentoId,
                        List.of(
                                new ItemSolicitado(recursoId, 2),
                                new ItemSolicitado(recursoId, 3)
                        )
                );

        exigir(
                resultado.situacao()
                        == ResultadoCriacaoRequisicao.Situacao.CRIADA
                        && !resultado.falhaNoFechamento()
                        && resultado.requisicao().getId().equals(requisicaoId)
                        && igual(
                                resultado.requisicao().getCustoTotalProjetado(),
                                "62.50"
                        ),
                "O caso de uso nao confirmou a criacao esperada."
        );

        // Altera o catálogo depois do commit do pedido.
        try (PreparedStatement comando = conexao.prepareStatement(
                "UPDATE recurso_catalogo SET custo_mensal = ? WHERE id = ?"
        )) {
            comando.setBigDecimal(1, new BigDecimal("99.00"));
            comando.setString(2, recursoId.toString());

            exigir(
                    comando.executeUpdate() == 1,
                    "A alteracao do catalogo nao afetou exatamente um registro."
            );
        }

        conexao.commit();

        System.out.println(
                "PASSOU: caso de uso confirmou CRIADA; "
                        + "catalogo alterado apos a criacao."
        );
    }

        private static void verificarRepeticao(
            FabricaConexoes fabrica,
            UUID departamentoId,
            UUID recursoId,
            UUID requisicaoId
    ) {
        ResultadoCriacaoRequisicao resultado = criarCasoDeUso(fabrica)
                .executar(
                        requisicaoId,
                        departamentoId,
                        List.of(
                                new ItemSolicitado(recursoId, 2),
                                new ItemSolicitado(recursoId, 3)
                        )
                );

        Requisicao pedido = resultado.requisicao();

        exigir(
                resultado.situacao()
                        == ResultadoCriacaoRequisicao.Situacao.JA_EXISTENTE
                        && !resultado.falhaNoFechamento()
                        && pedido.getId().equals(requisicaoId)
                        && pedido.getDepartamento().getId()
                                .equals(departamentoId)
                        && "INICIAL".equals(pedido.getStatus())
                        && pedido.getMotivoRevisao() == null
                        && pedido.getItens().size() == 2
                        && pedido.getItens().get(0).getQuantidade() == 2
                        && pedido.getItens().get(1).getQuantidade() == 3
                        && igual(pedido.getCustoTotalProjetado(), "62.50")
                        && igual(
                                pedido.getDepartamento().getSaldoDisponivel(),
                                "800.00"
                        ),
                "A repeticao em outro processo nao preservou o pedido."
        );

        for (ItemRequisicao item : pedido.getItens()) {
            exigir(
                    item.getRecurso().getId().equals(recursoId)
                            && igual(item.getPrecoUnitario(), "12.50")
                            && igual(item.getRecurso().getCustoMensal(), "99.00"),
                    "A repeticao nao preservou os precos historicos."
            );
        }

        System.out.println(
                "PASSOU: outro processo retorna JA_EXISTENTE "
                        + "com precos historicos e saldo preservados."
        );
    }

    private static CriarRequisicaoPersistenteUseCase criarCasoDeUso(
            FabricaConexoes fabrica
    ) {
        return new CriarRequisicaoPersistenteUseCase(
                new CriacaoRequisicaoJdbc(() -> {
                    Connection nova = fabrica.abrir();

                    try {
                        conferirAmbiente(nova);
                        return nova;
                    } catch (SQLException | RuntimeException excecao) {
                        try {
                            nova.close();
                        } catch (SQLException fechamento) {
                            excecao.addSuppressed(new IllegalStateException(
                                    "Falha ao fechar conexao de teste."
                            ));
                        }

                        throw excecao;
                    }
                })
        );
    }

    private static void consultar(
            Connection conexao,
            UUID departamentoId,
            UUID recursoId,
            UUID requisicaoId
    ) throws SQLException {
        RequisicaoRepositoryJdbc requisicoes =
                new RequisicaoRepositoryJdbc(conexao);

        Requisicao pedido = requisicoes.buscarPorId(requisicaoId)
                .orElseThrow(() -> new IllegalStateException(
                        "Requisicao persistida nao encontrada."
                ));

        Departamento departamento = pedido.getDepartamento();

        exigir(
                pedido.getId().equals(requisicaoId)
                        && departamento.getId().equals(departamentoId)
                        && NOME_DEPARTAMENTO.equals(departamento.getNome())
                        && igual(departamento.getOrcamentoMensal(), "1000.00")
                        && igual(departamento.getSaldoDisponivel(), "800.00")
                        && "INICIAL".equals(pedido.getStatus())
                        && pedido.getMotivoRevisao() == null
                        && igual(pedido.getCustoTotalProjetado(), "62.50"),
                "Cabecalho ou departamento recuperado difere do esperado."
        );

        List<ItemRequisicao> itens = pedido.getItens();

        exigir(
                itens.size() == 2
                        && itens.get(0).getQuantidade() == 2
                        && itens.get(1).getQuantidade() == 3
                        && igual(itens.get(0).getCustoSubtotal(), "25.00")
                        && igual(itens.get(1).getCustoSubtotal(), "37.50"),
                "Ordem, quantidade ou subtotal dos itens difere do esperado."
        );

        for (ItemRequisicao item : itens) {
            exigir(
                    item.getRecurso().getId().equals(recursoId)
                            && NOME_RECURSO.equals(item.getRecurso().getNome())
                            && igual(item.getRecurso().getCustoMensal(), "99.00")
                            && igual(item.getPrecoUnitario(), "12.50"),
                    "Recurso ou preco historico difere do esperado."
            );
        }

        List<Requisicao> lista =
                requisicoes.listarPorDepartamento(departamentoId);

        exigir(
                lista.size() == 1
                        && lista.get(0).getId().equals(requisicaoId)
                        && lista.get(0).getItens().size() == 2
                        && igual(
                                lista.get(0).getCustoTotalProjetado(), "62.50"
                        ),
                "Listagem do departamento difere do esperado."
        );

        conexao.rollback();
        System.out.println(
                "PASSOU: outro processo recupera pedido, itens, "
                        + "precos historicos e saldo preservado."
        );
    }

    private static void limpar(
            Connection conexao,
            UUID departamentoId,
            UUID recursoId,
            UUID requisicaoId
    ) throws SQLException {
        // Confere os marcadores antes de qualquer exclusão.
        conferirMarcador(
                conexao,
                "SELECT nome FROM departamento WHERE id = ?",
                departamentoId,
                NOME_DEPARTAMENTO
        );
        conferirMarcador(
                conexao,
                "SELECT nome FROM recurso_catalogo WHERE id = ?",
                recursoId,
                NOME_RECURSO
        );

        try (PreparedStatement comando = conexao.prepareStatement(
                "SELECT departamento_id FROM requisicao WHERE id = ?"
        )) {
            comando.setString(1, requisicaoId.toString());

            try (ResultSet resultado = comando.executeQuery()) {
                if (resultado.next()) {
                    exigir(
                            departamentoId.toString().equals(resultado.getString(1)),
                            "A requisicao pertence a outro departamento; limpeza cancelada."
                    );

                    exigir(
                            contar(
                                    conexao,
                                    "SELECT COUNT(*) FROM departamento "
                                            + "WHERE id = ? AND nome = ?",
                                    departamentoId.toString(),
                                    NOME_DEPARTAMENTO
                            ) == 1,
                            "Departamento sem marcador do teste; limpeza cancelada."
                    );
                }
            }
        }

        exigir(
                contar(
                        conexao,
                        "SELECT COUNT(*) FROM item_requisicao "
                                + "WHERE requisicao_id = ? AND recurso_id <> ?",
                        requisicaoId.toString(),
                        recursoId.toString()
                ) == 0,
                "A requisicao possui outro recurso; limpeza cancelada."
        );

        executarExclusao(
                conexao,
                "DELETE FROM item_requisicao WHERE requisicao_id = ?",
                requisicaoId.toString()
        );
        executarExclusao(
                conexao,
                "DELETE FROM requisicao WHERE id = ? AND departamento_id = ?",
                requisicaoId.toString(),
                departamentoId.toString()
        );
        executarExclusao(
                conexao,
                "DELETE FROM recurso_catalogo WHERE id = ? AND nome = ?",
                recursoId.toString(),
                NOME_RECURSO
        );
        executarExclusao(
                conexao,
                "DELETE FROM departamento WHERE id = ? AND nome = ?",
                departamentoId.toString(),
                NOME_DEPARTAMENTO
        );

        exigir(
                contar(conexao,
                        "SELECT COUNT(*) FROM item_requisicao WHERE requisicao_id = ?",
                        requisicaoId.toString()) == 0
                        && contar(conexao,
                                "SELECT COUNT(*) FROM requisicao WHERE id = ?",
                                requisicaoId.toString()) == 0
                        && contar(conexao,
                                "SELECT COUNT(*) FROM recurso_catalogo WHERE id = ?",
                                recursoId.toString()) == 0
                        && contar(conexao,
                                "SELECT COUNT(*) FROM departamento WHERE id = ?",
                                departamentoId.toString()) == 0,
                "Limpeza incompleta."
        );

        conexao.commit();
        System.out.println(
                "PASSOU: limpeza dos registros do teste confirmada."
        );
    }

    private static void conferirMarcador(
            Connection conexao,
            String sql,
            UUID id,
            String nomeEsperado
    ) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, id.toString());

            try (ResultSet resultado = comando.executeQuery()) {
                if (resultado.next()) {
                    exigir(
                            nomeEsperado.equals(resultado.getString(1)),
                            "Registro sem marcador do teste; limpeza cancelada."
                    );
                }
            }
        }
    }

    private static void executarExclusao(
            Connection conexao,
            String sql,
            String... parametros
    ) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            preencher(comando, parametros);
            comando.executeUpdate();
        }
    }

    private static long contar(
            Connection conexao,
            String sql,
            String... parametros
    ) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            preencher(comando, parametros);

            try (ResultSet resultado = comando.executeQuery()) {
                exigir(resultado.next(), "Contagem sem resultado.");
                return resultado.getLong(1);
            }
        }
    }

    private static void preencher(
            PreparedStatement comando,
            String[] parametros
    ) throws SQLException {
        for (int indice = 0; indice < parametros.length; indice++) {
            comando.setString(indice + 1, parametros[indice]);
        }
    }

    private static boolean igual(BigDecimal valor, String esperado) {
        return valor != null && valor.compareTo(new BigDecimal(esperado)) == 0;
    }

    private static void exigir(boolean condicao, String mensagem) {
        if (!condicao) {
            throw new IllegalStateException(mensagem);
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
            exigir(
                    resultado.next()
                            && "finops_test".equals(resultado.getString(1))
                            && "finops_test_app@localhost".equals(
                                    resultado.getString(2)
                            ),
                    "Execucao permitida somente em finops_test "
                            + "com finops_test_app@localhost."
            );
        }
    }
}