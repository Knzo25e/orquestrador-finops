package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.aplicacao.CriarRequisicaoCoordenadaUseCase;
import br.mackenzie.finops.aplicacao.entradas.ItemSolicitado;
import br.mackenzie.finops.aplicacao.excecoes.AcessoNegadoException;
import br.mackenzie.finops.aplicacao.resultados.ResultadoCriacaoRequisicao;
import br.mackenzie.finops.aplicacao.seguranca.ContextoIdentidade;
import br.mackenzie.finops.aplicacao.seguranca.PerfilUsuario;
import br.mackenzie.finops.dominio.entidades.Departamento;
import br.mackenzie.finops.dominio.entidades.RecursoCatalogo;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class VerificarVinculoCoordenadoJdbc {

    private static int verificacoes;

    public static void main(String[] args) throws Exception {
        FabricaConexoes fabrica = new FabricaConexoes(
                ConfiguracaoBanco.carregarDoAmbiente()
        );

        UUID usuario = UUID.randomUUID();
        UUID departamentoA = UUID.randomUUID();
        UUID departamentoB = UUID.randomUUID();
        UUID recurso = UUID.randomUUID();
        UUID pedidoA = UUID.randomUUID();
        UUID pedidoB = UUID.randomUUID();

        System.out.println("Departamento A: " + departamentoA);
        System.out.println("Departamento B: " + departamentoB);
        System.out.println("Recurso: " + recurso);
        System.out.println("Pedido A: " + pedidoA);
        System.out.println("Pedido B: " + pedidoB);

        ContextoIdentidade identidadeA = identidade(usuario, departamentoA);
        ContextoIdentidade identidadeB = identidade(usuario, departamentoB);

        IdentidadeCoordenadaTeste fonte =
                new IdentidadeCoordenadaTeste(usuario, identidadeA);

        PausaCommitTesteJdbc pausa = new PausaCommitTesteJdbc();

        CriacaoRequisicaoJdbc jdbc = new CriacaoRequisicaoJdbc(
                () -> pausa.envolver(abrirTeste(fabrica))
        );

        CriarRequisicaoCoordenadaUseCase criar =
                new CriarRequisicaoCoordenadaUseCase(fonte, jdbc);

        List<ItemSolicitado> itens = List.of(
                new ItemSolicitado(recurso, 2)
        );

        try {
            preparar(fabrica, departamentoA, departamentoB, recurso);

            verificarMudancaDuranteCriacao(
                    fabrica, criar, fonte, pausa,
                    identidadeB, pedidoA, itens
            );

            conferirPedido(
                    fabrica, pedidoA, usuario, departamentoA, recurso,
                    "Pedido confirmado preserva o vinculo anterior"
            );

            var segundo = criar.executar(pedidoB, itens);

            conferir(
                    segundo.situacao()
                            == ResultadoCriacaoRequisicao.Situacao.CRIADA
                            && !segundo.falhaNoFechamento()
                            && departamentoB.equals(
                                    segundo.requisicao().getDepartamento().getId()
                            ),
                    "Criacao posterior utiliza o novo vinculo"
            );

            conferirPedido(
                    fabrica, pedidoB, usuario, departamentoB, recurso,
                    "Outra conexao confirma pedido no novo departamento"
            );

            try {
                criar.executar(pedidoA, itens);
                throw new IllegalStateException(
                        "A repeticao do pedido anterior deveria ser negada."
                );
            } catch (AcessoNegadoException esperada) {
                conferir(
                        true,
                        "Novo vinculo impede repeticao do pedido anterior"
                );
            }

            conferirPedido(
                    fabrica, pedidoA, usuario, departamentoA, recurso,
                    "Negativa preserva pedido anterior e saldo"
            );
        } catch (Exception falha) {
            pausa.liberarCommit();

            try {
                limpar(
                        fabrica, List.of(pedidoA, pedidoB),
                        recurso, departamentoA, departamentoB
                );
            } catch (Exception falhaLimpeza) {
                falha.addSuppressed(falhaLimpeza);
            }
            throw falha;
        }

        limpar(
                fabrica, List.of(pedidoA, pedidoB),
                recurso, departamentoA, departamentoB
        );

        try (Connection conexao = abrirTeste(fabrica)) {
            conferir(
                    contar(
                            conexao,
                            "SELECT COUNT(*) FROM requisicao WHERE id = ?",
                            List.of(pedidoA, pedidoB)
                    ) == 0
                            && contar(
                                    conexao,
                                    "SELECT COUNT(*) FROM item_requisicao "
                                            + "WHERE requisicao_id = ?",
                                    List.of(pedidoA, pedidoB)
                            ) == 0
                            && contar(
                                    conexao,
                                    "SELECT COUNT(*) FROM departamento WHERE id = ?",
                                    List.of(departamentoA, departamentoB)
                            ) == 0
                            && contar(
                                    conexao,
                                    "SELECT COUNT(*) FROM recurso_catalogo WHERE id = ?",
                                    List.of(recurso)
                            ) == 0,
                    "Outra conexao confirma limpeza completa"
            );
        }

        System.out.println(
                "Todas as " + verificacoes + " verificacoes passaram."
        );
    }

    private static void verificarMudancaDuranteCriacao(
            FabricaConexoes fabrica,
            CriarRequisicaoCoordenadaUseCase criar,
            IdentidadeCoordenadaTeste fonte,
            PausaCommitTesteJdbc pausa,
            ContextoIdentidade novaIdentidade,
            UUID pedidoId,
            List<ItemSolicitado> itens
    ) throws Exception {
        CountDownLatch alteracaoIniciada = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var criacao = executor.submit(
                    () -> criar.executar(pedidoId, itens)
            );

            try {
                pausa.aguardarChegadaAoCommit();

                try (Connection conexao = abrirTeste(fabrica)) {
                    conferir(
                            contar(
                                    conexao,
                                    "SELECT COUNT(*) FROM requisicao WHERE id = ?",
                                    List.of(pedidoId)
                            ) == 0,
                            "Outra conexao nao ve pedido antes do commit"
                    );
                }

                var alteracao = executor.submit(() -> {
                    alteracaoIniciada.countDown();
                    fonte.atualizar(novaIdentidade);

                    // Captura a ordem no momento em que a alteração termina.
                    return pausa.commitFoiConfirmado();
                });

                if (!alteracaoIniciada.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "A tarefa de alteracao nao iniciou."
                    );
                }

                boolean aguardou = false;

                try {
                    alteracao.get(300, TimeUnit.MILLISECONDS);
                } catch (TimeoutException esperado) {
                    aguardou = true;
                }

                conferir(
                        aguardou,
                        "Alteracao de vinculo aguarda a criacao antes do commit"
                );

                pausa.liberarCommit();

                var resultado = criacao.get(10, TimeUnit.SECONDS);

                conferir(
                        resultado.situacao()
                                == ResultadoCriacaoRequisicao.Situacao.CRIADA
                                && !resultado.falhaNoFechamento()
                                && pausa.commitFoiConfirmado(),
                        "Criacao conclui com commit confirmado"
                );

                conferir(
                        alteracao.get(10, TimeUnit.SECONDS)
                                && fonte.obterIdentidadeAtual()
                                        .orElseThrow().equals(novaIdentidade),
                        "Mudanca de vinculo conclui somente depois do commit"
                );
            } finally {
                pausa.liberarCommit();
            }
        }
    }

    private static ContextoIdentidade identidade(
            UUID usuario,
            UUID departamento
    ) {
        return new ContextoIdentidade(
                usuario, PerfilUsuario.DESENVOLVEDOR, true, departamento
        );
    }

    private static void preparar(
            FabricaConexoes fabrica,
            UUID departamentoA,
            UUID departamentoB,
            UUID recurso
    ) throws SQLException {
        try (Connection conexao = abrirTeste(fabrica)) {
            conexao.setAutoCommit(false);

            try {
                var departamentos = new DepartamentoRepositoryJdbc(conexao);

                for (UUID id : List.of(departamentoA, departamentoB)) {
                    departamentos.adicionar(Departamento.reconstituir(
                            id,
                            "Vinculo coordenado / Issue 76",
                            new BigDecimal("1000.00"),
                            new BigDecimal("800.00")
                    ));
                }

                new RecursoCatalogoRepositoryJdbc(conexao).adicionar(
                        new RecursoCatalogo(
                                recurso,
                                "Recurso vinculo coordenado / Issue 76",
                                new BigDecimal("12.50")
                        )
                );

                conexao.commit();
            } finally {
                conexao.rollback();
            }
        }
    }

    private static void conferirPedido(
            FabricaConexoes fabrica,
            UUID pedidoId,
            UUID usuario,
            UUID departamento,
            UUID recurso,
            String descricao
    ) throws SQLException {
        try (Connection conexao = abrirTeste(fabrica)) {
            conexao.setTransactionIsolation(
                    Connection.TRANSACTION_READ_COMMITTED
            );

            var pedido = new RequisicaoRepositoryJdbc(conexao)
                    .buscarPorId(pedidoId)
                    .orElseThrow();

            conferir(
                    usuario.equals(pedido.getDesenvolvedorId())
                            && departamento.equals(
                                    pedido.getDepartamento().getId()
                            )
                            && "INICIAL".equals(pedido.getStatus())
                            && pedido.getMotivoRevisao() == null
                            && igual(pedido.getCustoTotalProjetado(), "25.00")
                            && igual(
                                    pedido.getDepartamento().getOrcamentoMensal(),
                                    "1000.00"
                            )
                            && igual(
                                    pedido.getDepartamento().getSaldoDisponivel(),
                                    "800.00"
                            )
                            && pedido.getItens().size() == 1
                            && recurso.equals(
                                    pedido.getItens().get(0).getRecurso().getId()
                            )
                            && pedido.getItens().get(0).getQuantidade() == 2
                            && igual(
                                    pedido.getItens().get(0).getPrecoUnitario(),
                                    "12.50"
                            ),
                    descricao
            );
        }
    }

    private static void limpar(
            FabricaConexoes fabrica,
            List<UUID> pedidos,
            UUID recurso,
            UUID departamentoA,
            UUID departamentoB
    ) throws SQLException {
        try (Connection conexao = abrirTeste(fabrica)) {
            conexao.setAutoCommit(false);

            try {
                for (UUID id : pedidos) {
                    excluir(
                            conexao,
                            "DELETE FROM item_requisicao WHERE requisicao_id = ?",
                            id
                    );
                    excluir(
                            conexao,
                            "DELETE FROM requisicao WHERE id = ?",
                            id
                    );
                }

                excluir(
                        conexao,
                        "DELETE FROM recurso_catalogo WHERE id = ?",
                        recurso
                );

                for (UUID id : List.of(departamentoA, departamentoB)) {
                    excluir(
                            conexao,
                            "DELETE FROM departamento WHERE id = ?",
                            id
                    );
                }

                conexao.commit();
            } finally {
                conexao.rollback();
            }
        }
    }

    private static void excluir(
            Connection conexao,
            String sql,
            UUID id
    ) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, id.toString());
            comando.executeUpdate();
        }
    }

    private static long contar(
            Connection conexao,
            String sql,
            List<UUID> ids
    ) throws SQLException {
        long total = 0;

        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            for (UUID id : ids) {
                comando.setString(1, id.toString());

                try (ResultSet resultado = comando.executeQuery()) {
                    if (!resultado.next()) {
                        throw new IllegalStateException("Contagem sem resultado.");
                    }
                    total += resultado.getLong(1);
                }
            }
        }

        return total;
    }

    private static Connection abrirTeste(FabricaConexoes fabrica)
            throws SQLException {
        Connection conexao = fabrica.abrir();

        try {
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
                            "Use finops_test com finops_test_app@localhost."
                    );
                }
            }

            return conexao;
        } catch (SQLException | RuntimeException falha) {
            try {
                conexao.close();
            } catch (SQLException fechamento) {
                falha.addSuppressed(fechamento);
            }
            throw falha;
        }
    }

    private static boolean igual(BigDecimal valor, String esperado) {
        return valor != null
                && valor.compareTo(new BigDecimal(esperado)) == 0;
    }

    private static void conferir(boolean condicao, String descricao) {
        if (!condicao) {
            throw new IllegalStateException("FALHOU: " + descricao);
        }

        verificacoes++;
        System.out.println("PASSOU: " + descricao);
    }
}