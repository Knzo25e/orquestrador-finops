package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.aplicacao.CriarRequisicaoCoordenadaUseCase;
import br.mackenzie.finops.aplicacao.entradas.ItemSolicitado;
import br.mackenzie.finops.aplicacao.excecoes.AcessoNegadoException;
import br.mackenzie.finops.aplicacao.excecoes.ConflitoIdentificadorException;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class VerificarCriacaoAutorizadaJdbc {

    private static int verificacoes;

    public static void main(String[] args) throws Exception {
        FabricaConexoes fabrica = new FabricaConexoes(
                ConfiguracaoBanco.carregarDoAmbiente()
        );

        UUID departamentoA = UUID.randomUUID();
        UUID departamentoB = UUID.randomUUID();
        UUID recursoId = UUID.randomUUID();
        UUID pedidoId = UUID.randomUUID();
        UUID autorId = UUID.randomUUID();

        System.out.println("Departamento A: " + departamentoA);
        System.out.println("Departamento B: " + departamentoB);
        System.out.println("Recurso: " + recursoId);
        System.out.println("Pedido: " + pedidoId);

        CriacaoRequisicaoJdbc jdbc = new CriacaoRequisicaoJdbc(
                () -> abrirTeste(fabrica)
        );

        var criar = entrada(jdbc, autorId, departamentoA);
        var outroAutor = entrada(jdbc, UUID.randomUUID(), departamentoA);
        var outroVinculo = entrada(jdbc, autorId, departamentoB);

        List<ItemSolicitado> itens = List.of(
                new ItemSolicitado(recursoId, 2)
        );
        List<ItemSolicitado> itensDiferentes = List.of(
                new ItemSolicitado(recursoId, 3)
        );

        try {
            preparar(fabrica, departamentoA, departamentoB, recursoId);

            var criado = criar.executar(pedidoId, itens);

            conferir(
                    criado.situacao()
                            == ResultadoCriacaoRequisicao.Situacao.CRIADA
                            && !criado.falhaNoFechamento()
                            && autorId.equals(
                                    criado.requisicao().getDesenvolvedorId()
                            )
                            && departamentoA.equals(
                                    criado.requisicao().getDepartamento().getId()
                            ),
                    "Criacao confirma autor e departamento da identidade"
            );

            List<String> antes = fotografar(
                    fabrica, pedidoId, departamentoA, departamentoB
            );

            var repetido = criar.executar(pedidoId, itens);

            conferir(
                    repetido.situacao()
                            == ResultadoCriacaoRequisicao.Situacao.JA_EXISTENTE
                            && !repetido.falhaNoFechamento()
                            && autorId.equals(
                                    repetido.requisicao().getDesenvolvedorId()
                            ),
                    "Mesmo autor reconhece repeticao autorizada"
            );

            rejeitar(
                    ConflitoIdentificadorException.class,
                    () -> criar.executar(pedidoId, itensDiferentes),
                    "Autor autorizado recebe conflito por conteudo diferente"
            );

            rejeitar(
                    AcessoNegadoException.class,
                    () -> outroAutor.executar(pedidoId, itens),
                    "Outro autor recebe negativa de acesso"
            );

            rejeitar(
                    AcessoNegadoException.class,
                    () -> outroAutor.executar(pedidoId, itensDiferentes),
                    "Outro autor recebe negativa antes de comparar conteudo"
            );

            rejeitar(
                    AcessoNegadoException.class,
                    () -> outroVinculo.executar(pedidoId, itens),
                    "Mesmo autor com outro vinculo nao reutiliza pedido antigo"
            );

            conferir(
                    antes.equals(fotografar(
                            fabrica, pedidoId, departamentoA, departamentoB
                    )),
                    "Outra conexao confirma preservacao apos repeticoes e negativas"
            );
        } catch (Exception falha) {
            try {
                limpar(
                        fabrica, pedidoId, recursoId,
                        departamentoA, departamentoB
                );
            } catch (Exception falhaLimpeza) {
                falha.addSuppressed(falhaLimpeza);
            }
            throw falha;
        }

        limpar(fabrica, pedidoId, recursoId, departamentoA, departamentoB);

        try (Connection conexao = abrirTeste(fabrica)) {
            conferir(
                    contar(
                            conexao,
                            "SELECT COUNT(*) FROM requisicao WHERE id = ?",
                            pedidoId
                    ) == 0
                            && contar(
                                    conexao,
                                    "SELECT COUNT(*) FROM item_requisicao "
                                            + "WHERE requisicao_id = ?",
                                    pedidoId
                            ) == 0
                            && contar(
                                    conexao,
                                    "SELECT COUNT(*) FROM recurso_catalogo WHERE id = ?",
                                    recursoId
                            ) == 0
                            && contar(
                                    conexao,
                                    "SELECT COUNT(*) FROM departamento WHERE id = ?",
                                    departamentoA
                            ) == 0
                            && contar(
                                    conexao,
                                    "SELECT COUNT(*) FROM departamento WHERE id = ?",
                                    departamentoB
                            ) == 0,
                    "Outra conexao confirma limpeza completa"
            );
        }

        System.out.println(
                "Todas as " + verificacoes + " verificacoes passaram."
        );
    }

    private static CriarRequisicaoCoordenadaUseCase entrada(
            CriacaoRequisicaoJdbc jdbc,
            UUID autor,
            UUID departamento
    ) {
        ContextoIdentidade identidade = new ContextoIdentidade(
                autor, PerfilUsuario.DESENVOLVEDOR, true, departamento
        );

        IdentidadeCoordenadaTeste fonte =
                new IdentidadeCoordenadaTeste(autor, identidade);

        return new CriarRequisicaoCoordenadaUseCase(fonte, jdbc);
    }

    private static void preparar(
            FabricaConexoes fabrica,
            UUID departamentoA,
            UUID departamentoB,
            UUID recursoId
    ) throws SQLException {
        try (Connection conexao = abrirTeste(fabrica)) {
            conexao.setAutoCommit(false);

            try {
                var departamentos = new DepartamentoRepositoryJdbc(conexao);

                for (UUID id : List.of(departamentoA, departamentoB)) {
                    departamentos.adicionar(Departamento.reconstituir(
                            id,
                            "Teste criacao autorizada / Issue 76",
                            new BigDecimal("1000.00"),
                            new BigDecimal("800.00")
                    ));
                }

                new RecursoCatalogoRepositoryJdbc(conexao).adicionar(
                        new RecursoCatalogo(
                                recursoId,
                                "Recurso criacao autorizada / Issue 76",
                                new BigDecimal("12.50")
                        )
                );

                conexao.commit();
            } finally {
                conexao.rollback();
            }
        }
    }

    private static List<String> fotografar(
            FabricaConexoes fabrica,
            UUID pedidoId,
            UUID departamentoA,
            UUID departamentoB
    ) throws SQLException {
        List<String> valores = new ArrayList<>();

        String sql = """
                SELECT q.id, q.departamento_id, q.desenvolvedor_id,
                       q.estado, q.custo_total, q.motivo_revisao,
                       i.ordem, i.recurso_id, i.preco_unitario,
                       i.quantidade, i.subtotal
                FROM requisicao q
                JOIN item_requisicao i ON i.requisicao_id = q.id
                WHERE q.id = ?
                ORDER BY i.ordem
                """;

        try (Connection conexao = abrirTeste(fabrica)) {
            try (PreparedStatement comando = conexao.prepareStatement(sql)) {
                comando.setString(1, pedidoId.toString());

                try (ResultSet resultado = comando.executeQuery()) {
                    if (!resultado.next()) {
                        throw new IllegalStateException("Pedido nao encontrado.");
                    }

                    if (!"INICIAL".equals(resultado.getString("estado"))
                            || resultado.getBigDecimal("custo_total")
                                    .compareTo(new BigDecimal("25.00")) != 0) {
                        throw new IllegalStateException(
                                "Estado ou total inesperado."
                        );
                    }

                    for (int coluna = 1; coluna <= 11; coluna++) {
                        valores.add(resultado.getString(coluna));
                    }

                    if (resultado.next()) {
                        throw new IllegalStateException(
                                "Quantidade inesperada de itens."
                        );
                    }
                }
            }

            for (UUID id : List.of(departamentoA, departamentoB)) {
                try (PreparedStatement comando = conexao.prepareStatement(
                        "SELECT teto_mensal, saldo_disponivel "
                                + "FROM departamento WHERE id = ?"
                )) {
                    comando.setString(1, id.toString());

                    try (ResultSet resultado = comando.executeQuery()) {
                        if (!resultado.next()
                                || resultado.getBigDecimal("saldo_disponivel")
                                        .compareTo(new BigDecimal("800.00")) != 0
                                || resultado.getBigDecimal("teto_mensal")
                                        .compareTo(new BigDecimal("1000.00")) != 0) {
                            throw new IllegalStateException(
                                    "Valores do departamento foram alterados."
                            );
                        }

                        valores.add(resultado.getString("teto_mensal"));
                        valores.add(resultado.getString("saldo_disponivel"));
                    }
                }
            }
        }

        return valores;
    }

    private static void limpar(
            FabricaConexoes fabrica,
            UUID pedido,
            UUID recurso,
            UUID departamentoA,
            UUID departamentoB
    ) throws SQLException {
        try (Connection conexao = abrirTeste(fabrica)) {
            conexao.setAutoCommit(false);

            try {
                excluir(
                        conexao,
                        "DELETE FROM item_requisicao WHERE requisicao_id = ?",
                        pedido
                );
                excluir(conexao, "DELETE FROM requisicao WHERE id = ?", pedido);
                excluir(
                        conexao,
                        "DELETE FROM recurso_catalogo WHERE id = ?",
                        recurso
                );
                excluir(
                        conexao,
                        "DELETE FROM departamento WHERE id = ?",
                        departamentoA
                );
                excluir(
                        conexao,
                        "DELETE FROM departamento WHERE id = ?",
                        departamentoB
                );

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
            UUID id
    ) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, id.toString());

            try (ResultSet resultado = comando.executeQuery()) {
                if (!resultado.next()) {
                    throw new IllegalStateException("Contagem sem resultado.");
                }
                return resultado.getLong(1);
            }
        }
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

    private static void rejeitar(
            Class<? extends RuntimeException> tipo,
            Runnable operacao,
            String descricao
    ) {
        try {
            operacao.run();
        } catch (RuntimeException falha) {
            if (!tipo.isInstance(falha)) {
                throw new IllegalStateException(
                        "FALHOU: excecao inesperada em " + descricao,
                        falha
                );
            }

            conferir(true, descricao);
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