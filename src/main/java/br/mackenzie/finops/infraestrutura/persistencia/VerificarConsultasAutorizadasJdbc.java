package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.aplicacao.ConsultarRequisicaoAutorizadaUseCase;
import br.mackenzie.finops.aplicacao.ListarRequisicoesAutorizadasUseCase;
import br.mackenzie.finops.aplicacao.excecoes.AcessoNegadoException;
import br.mackenzie.finops.aplicacao.seguranca.ContextoIdentidade;
import br.mackenzie.finops.aplicacao.seguranca.PerfilUsuario;
import br.mackenzie.finops.dominio.entidades.Departamento;
import br.mackenzie.finops.dominio.entidades.ItemRequisicao;
import br.mackenzie.finops.dominio.entidades.RecursoCatalogo;
import br.mackenzie.finops.dominio.entidades.Requisicao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class VerificarConsultasAutorizadasJdbc {

    private static int verificacoes;

    public static void main(String[] args) throws Exception {
        FabricaConexoes fabrica = new FabricaConexoes(
                ConfiguracaoBanco.carregarDoAmbiente()
        );

        UUID autor = UUID.randomUUID();
        UUID colega = UUID.randomUUID();
        UUID departamentoA = UUID.randomUUID();
        UUID departamentoB = UUID.randomUUID();
        UUID recurso = UUID.randomUUID();

        List<UUID> pedidos = List.of(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        System.out.println("Departamento A: " + departamentoA);
        System.out.println("Departamento B: " + departamentoB);
        System.out.println("Recurso: " + recurso);
        System.out.println("Pedidos: " + pedidos);

        ConsultaRequisicaoJdbc consultas = new ConsultaRequisicaoJdbc(
                () -> abrirTeste(fabrica)
        );

        ContextoIdentidade desenvolvedor = new ContextoIdentidade(
                autor, PerfilUsuario.DESENVOLVEDOR, true, departamentoA
        );
        ContextoIdentidade arquiteto = new ContextoIdentidade(
                UUID.randomUUID(),
                PerfilUsuario.ARQUITETO_CLOUD,
                true,
                null
        );

        var buscarDev = new ConsultarRequisicaoAutorizadaUseCase(
                () -> Optional.of(desenvolvedor), consultas
        );
        var listarDev = new ListarRequisicoesAutorizadasUseCase(
                () -> Optional.of(desenvolvedor), consultas
        );
        var buscarArquiteto = new ConsultarRequisicaoAutorizadaUseCase(
                () -> Optional.of(arquiteto), consultas
        );
        var listarArquiteto = new ListarRequisicoesAutorizadasUseCase(
                () -> Optional.of(arquiteto), consultas
        );

        try {
            preparar(
                    fabrica, departamentoA, departamentoB,
                    recurso, pedidos, autor, colega
            );

            List<String> antes = fotografar(fabrica, pedidos);

            conferir(
                    buscarDev.executar(pedidos.get(0))
                            .map(p -> autor.equals(p.getDesenvolvedorId())
                                    && departamentoA.equals(
                                            p.getDepartamento().getId()
                                    ))
                            .orElse(false),
                    "Desenvolvedor recupera pedido proprio do departamento atual"
            );

            conferir(
                    buscarDev.executar(pedidos.get(1)).isEmpty(),
                    "Pedido de colega no mesmo departamento nao e devolvido"
            );

            conferir(
                    buscarDev.executar(pedidos.get(2)).isEmpty(),
                    "Pedido proprio de outro departamento nao e devolvido"
            );

            conferir(
                    buscarDev.executar(pedidos.get(3)).isEmpty(),
                    "Pedido sem autoria nao e devolvido ao desenvolvedor"
            );

            conferir(
                    buscarDev.executar(UUID.randomUUID()).isEmpty(),
                    "Pedido inexistente tambem retorna ausencia"
            );

            List<Requisicao> lista = listarDev.executar(departamentoA);

            conferir(
                    lista.size() == 1
                            && pedidos.get(0).equals(lista.get(0).getId()),
                    "Listagem JDBC do desenvolvedor retorna somente seu pedido"
            );

            exigirNegacao(
                    () -> listarDev.executar(departamentoB),
                    "Desenvolvedor nao lista outro departamento"
            );

            conferir(
                    buscarArquiteto.executar(pedidos.get(3))
                            .map(p -> p.getDesenvolvedorId() == null)
                            .orElse(false),
                    "Arquiteto recupera pedido sem autoria"
            );

            conferir(
                    buscarArquiteto.executar(pedidos.get(2))
                            .map(p -> departamentoB.equals(
                                    p.getDepartamento().getId()
                            ))
                            .orElse(false),
                    "Arquiteto recupera pedido de outro departamento"
            );

            List<UUID> idsA = listarArquiteto.executar(departamentoA)
                    .stream()
                    .map(Requisicao::getId)
                    .toList();

            conferir(
                    idsA.size() == 3
                            && idsA.containsAll(List.of(
                                    pedidos.get(0),
                                    pedidos.get(1),
                                    pedidos.get(3)
                            )),
                    "Arquiteto lista autores diferentes e autoria desconhecida"
            );

            List<Requisicao> listaB =
                    listarArquiteto.executar(departamentoB);

            conferir(
                    listaB.size() == 1
                            && pedidos.get(2).equals(listaB.get(0).getId()),
                    "Listagem do arquiteto respeita o departamento solicitado"
            );

            conferir(
                    antes.equals(fotografar(fabrica, pedidos)),
                    "Outra conexao confirma dados e valores preservados"
            );
        } catch (Exception falha) {
            try {
                limpar(
                        fabrica, pedidos,
                        recurso, departamentoA, departamentoB
                );
            } catch (Exception falhaLimpeza) {
                falha.addSuppressed(falhaLimpeza);
            }
            throw falha;
        }

        limpar(fabrica, pedidos, recurso, departamentoA, departamentoB);

        try (Connection conexao = abrirTeste(fabrica)) {
            conferir(
                    contar(conexao, "requisicao", pedidos) == 0
                            && contar(
                                    conexao, "departamento",
                                    List.of(departamentoA, departamentoB)
                            ) == 0
                            && contar(
                                    conexao, "recurso_catalogo",
                                    List.of(recurso)
                            ) == 0
                            && contarItens(conexao, pedidos) == 0,
                    "Outra conexao confirma limpeza completa do teste"
            );
        }

        System.out.println(
                "Todas as " + verificacoes + " verificacoes passaram."
        );
    }

    private static void preparar(
            FabricaConexoes fabrica,
            UUID departamentoA,
            UUID departamentoB,
            UUID recursoId,
            List<UUID> pedidos,
            UUID autor,
            UUID colega
    ) throws SQLException {
        try (Connection conexao = abrirTeste(fabrica)) {
            conexao.setAutoCommit(false);

            try {
                Departamento a = departamento(departamentoA);
                Departamento b = departamento(departamentoB);

                var departamentos = new DepartamentoRepositoryJdbc(conexao);
                departamentos.adicionar(a);
                departamentos.adicionar(b);

                RecursoCatalogo recurso = new RecursoCatalogo(
                        recursoId,
                        "Recurso consultas autorizadas / Issue 76",
                        new BigDecimal("12.50")
                );
                new RecursoCatalogoRepositoryJdbc(conexao).adicionar(recurso);

                var repositorio = new RequisicaoRepositoryJdbc(conexao);

                adicionar(repositorio, pedidos.get(0), a, recurso, autor);
                adicionar(repositorio, pedidos.get(1), a, recurso, colega);
                adicionar(repositorio, pedidos.get(2), b, recurso, autor);
                adicionar(repositorio, pedidos.get(3), a, recurso, null);

                conexao.commit();
            } finally {
                conexao.rollback();
            }
        }
    }

    private static Departamento departamento(UUID id) {
        return Departamento.reconstituir(
                id,
                "Departamento consultas autorizadas / Issue 76",
                new BigDecimal("1000.00"),
                new BigDecimal("800.00")
        );
    }

    private static void adicionar(
            RequisicaoRepositoryJdbc repositorio,
            UUID id,
            Departamento departamento,
            RecursoCatalogo recurso,
            UUID autor
    ) {
        Requisicao pedido = autor == null
                ? Requisicao.criarComId(id, departamento)
                : Requisicao.criarComId(id, departamento, autor);

        pedido.adicionarItem(new ItemRequisicao(recurso, 2));
        repositorio.adicionar(pedido);
    }

    private static List<String> fotografar(
            FabricaConexoes fabrica,
            List<UUID> pedidos
    ) throws SQLException {
        String sql = """
                SELECT q.id, q.departamento_id, q.desenvolvedor_id,
                       q.estado, q.custo_total, q.motivo_revisao,
                       d.teto_mensal, d.saldo_disponivel,
                       i.ordem, i.recurso_id, i.preco_unitario,
                       i.quantidade, i.subtotal, r.custo_mensal
                FROM requisicao q
                JOIN departamento d ON d.id = q.departamento_id
                JOIN item_requisicao i ON i.requisicao_id = q.id
                JOIN recurso_catalogo r ON r.id = i.recurso_id
                WHERE q.id = ?
                ORDER BY i.ordem
                """;

        List<String> valores = new ArrayList<>();

        try (
                Connection conexao = abrirTeste(fabrica);
                PreparedStatement comando = conexao.prepareStatement(sql)
        ) {
            for (UUID id : pedidos) {
                comando.setString(1, id.toString());

                try (ResultSet resultado = comando.executeQuery()) {
                    int linhas = 0;

                    while (resultado.next()) {
                        linhas++;
                        for (int coluna = 1; coluna <= 14; coluna++) {
                            valores.add(resultado.getString(coluna));
                        }
                    }

                    if (linhas != 1) {
                        throw new IllegalStateException(
                                "O cenario deveria conter um item por pedido."
                        );
                    }
                }
            }
        }

        return valores;
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
            String tabela,
            List<UUID> ids
    ) throws SQLException {
        // Nomes fixos, definidos exclusivamente por este verificador.
        String sql = switch (tabela) {
            case "requisicao" ->
                    "SELECT COUNT(*) FROM requisicao WHERE id = ?";
            case "departamento" ->
                    "SELECT COUNT(*) FROM departamento WHERE id = ?";
            case "recurso_catalogo" ->
                    "SELECT COUNT(*) FROM recurso_catalogo WHERE id = ?";
            default -> throw new IllegalArgumentException("Tabela invalida.");
        };

        return contarPorIds(conexao, sql, ids);
    }

    private static long contarItens(
            Connection conexao,
            List<UUID> pedidos
    ) throws SQLException {
        return contarPorIds(
                conexao,
                "SELECT COUNT(*) FROM item_requisicao WHERE requisicao_id = ?",
                pedidos
        );
    }

    private static long contarPorIds(
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

    private static void exigirNegacao(Runnable operacao, String descricao) {
        try {
            operacao.run();
        } catch (AcessoNegadoException esperada) {
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