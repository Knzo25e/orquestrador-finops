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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class VerificarConcorrenciaCriacaoAutorizadaJdbc {

    private static int verificacoes;

    public static void main(String[] args) throws Exception {
        FabricaConexoes fabrica = new FabricaConexoes(
                ConfiguracaoBanco.carregarDoAmbiente()
        );

        executarCenario(fabrica, false);
        executarCenario(fabrica, true);

        System.out.println(
                "Todas as " + verificacoes + " verificacoes passaram."
        );
    }

    private static void executarCenario(
            FabricaConexoes fabrica,
            boolean autoresDiferentes
    ) throws Exception {
        String cenario = autoresDiferentes
                ? "Autores diferentes"
                : "Mesmo autor";

        UUID departamentoId = UUID.randomUUID();
        UUID recursoId = UUID.randomUUID();
        UUID pedidoId = UUID.randomUUID();
        UUID autorA = UUID.randomUUID();
        UUID autorB = autoresDiferentes ? UUID.randomUUID() : autorA;

        System.out.println("\nCenario: " + cenario);
        System.out.println("Departamento: " + departamentoId);
        System.out.println("Recurso: " + recursoId);
        System.out.println("Pedido: " + pedidoId);

        var barreira = new BarreiraCriacaoAutorizadaTesteJdbc();

        CriacaoRequisicaoJdbc jdbc = new CriacaoRequisicaoJdbc(
                () -> barreira.envolver(abrirTeste(fabrica))
        );

        IdentidadeCoordenadaTeste fonteA =
                new IdentidadeCoordenadaTeste(
                        autorA,
                        new ContextoIdentidade(
                                autorA,
                                PerfilUsuario.DESENVOLVEDOR,
                                true,
                                departamentoId
                        )
                );

        // Chamadas do mesmo usuário compartilham a mesma coordenação.
        IdentidadeCoordenadaTeste fonteB = autoresDiferentes
                ? new IdentidadeCoordenadaTeste(
                        autorB,
                        new ContextoIdentidade(
                                autorB,
                                PerfilUsuario.DESENVOLVEDOR,
                                true,
                                departamentoId
                        )
                )
                : fonteA;

        var entradaA = entrada(jdbc, fonteA);
        var entradaB = entrada(jdbc, fonteB);

        List<ItemSolicitado> itens = List.of(
                new ItemSolicitado(recursoId, 2)
        );

        try {
            preparar(fabrica, departamentoId, recursoId);

            List<Tentativa> tentativas;

            try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
                Future<Tentativa> tarefaA = executor.submit(
                        () -> tentar(entradaA, autorA, pedidoId, itens)
                );
                Future<Tentativa> tarefaB = executor.submit(
                        () -> tentar(entradaB, autorB, pedidoId, itens)
                );

                try {
                    tentativas = List.of(
                            tarefaA.get(45, TimeUnit.SECONDS),
                            tarefaB.get(45, TimeUnit.SECONDS)
                    );
                } catch (InterruptedException falha) {
                    tarefaA.cancel(true);
                    tarefaB.cancel(true);
                    Thread.currentThread().interrupt();
                    throw falha;
                } catch (Exception falha) {
                    tarefaA.cancel(true);
                    tarefaB.cancel(true);
                    throw falha;
                }
            }

            long criadas = tentativas.stream()
                    .filter(t -> situacao(
                            t, ResultadoCriacaoRequisicao.Situacao.CRIADA
                    ))
                    .count();

            long existentes = tentativas.stream()
                    .filter(t -> situacao(
                            t, ResultadoCriacaoRequisicao.Situacao.JA_EXISTENTE
                    ))
                    .count();

            long negadas = tentativas.stream()
                    .filter(Tentativa::negada)
                    .count();

            conferir(
                    criadas == 1
                            && existentes == (autoresDiferentes ? 0 : 1)
                            && negadas == (autoresDiferentes ? 1 : 0),
                    cenario + ": resultados concorrentes esperados"
            );

            conferir(
                    barreira.tentativasInsercao() == 2
                            && barreira.conexoesAbertas() == 3,
                    cenario + ": duas insercoes e nova conexao de reconciliacao"
            );

            Tentativa vencedora = tentativas.stream()
                    .filter(t -> situacao(
                            t, ResultadoCriacaoRequisicao.Situacao.CRIADA
                    ))
                    .findFirst()
                    .orElseThrow();

            boolean retornosCorretos = tentativas.stream().allMatch(t -> {
                if (t.negada()) {
                    return t.resultado() == null;
                }

                var resultado = t.resultado();
                var pedido = resultado.requisicao();

                return !resultado.falhaNoFechamento()
                        && pedidoId.equals(pedido.getId())
                        && t.autor().equals(pedido.getDesenvolvedorId())
                        && departamentoId.equals(
                                pedido.getDepartamento().getId()
                        );
            });

            conferir(
                    retornosCorretos,
                    cenario + ": resultados preservam identidade e negativa sem pedido"
            );

            conferirBanco(
                    fabrica,
                    departamentoId,
                    recursoId,
                    pedidoId,
                    vencedora.autor(),
                    cenario
            );
        } catch (Exception falha) {
            try {
                limpar(fabrica, departamentoId, recursoId, pedidoId);
            } catch (Exception falhaLimpeza) {
                falha.addSuppressed(falhaLimpeza);
            }
            throw falha;
        }

        limpar(fabrica, departamentoId, recursoId, pedidoId);

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
                                    "SELECT COUNT(*) FROM departamento WHERE id = ?",
                                    departamentoId
                            ) == 0
                            && contar(
                                    conexao,
                                    "SELECT COUNT(*) FROM recurso_catalogo WHERE id = ?",
                                    recursoId
                            ) == 0,
                    cenario + ": outra conexao confirma limpeza"
            );
        }
    }

    private record Tentativa(
            UUID autor,
            ResultadoCriacaoRequisicao resultado,
            boolean negada
    ) {
    }

    private static Tentativa tentar(
            CriarRequisicaoCoordenadaUseCase entrada,
            UUID autor,
            UUID pedidoId,
            List<ItemSolicitado> itens
    ) {
        try {
            return new Tentativa(
                    autor, entrada.executar(pedidoId, itens), false
            );
        } catch (AcessoNegadoException esperada) {
            return new Tentativa(autor, null, true);
        }
    }

    private static boolean situacao(
            Tentativa tentativa,
            ResultadoCriacaoRequisicao.Situacao esperada
    ) {
        return tentativa.resultado() != null
                && tentativa.resultado().situacao() == esperada;
    }

    private static CriarRequisicaoCoordenadaUseCase entrada(
            CriacaoRequisicaoJdbc jdbc,
            IdentidadeCoordenadaTeste fonte
    ) {
        return new CriarRequisicaoCoordenadaUseCase(fonte, jdbc);
    }

    private static void preparar(
            FabricaConexoes fabrica,
            UUID departamentoId,
            UUID recursoId
    ) throws SQLException {
        try (Connection conexao = abrirTeste(fabrica)) {
            conexao.setAutoCommit(false);

            try {
                new DepartamentoRepositoryJdbc(conexao).adicionar(
                        Departamento.reconstituir(
                                departamentoId,
                                "Concorrencia autorizada / Issue 76",
                                new BigDecimal("1000.00"),
                                new BigDecimal("800.00")
                        )
                );

                new RecursoCatalogoRepositoryJdbc(conexao).adicionar(
                        new RecursoCatalogo(
                                recursoId,
                                "Recurso concorrencia autorizada / Issue 76",
                                new BigDecimal("12.50")
                        )
                );

                conexao.commit();
            } finally {
                conexao.rollback();
            }
        }
    }

    private static void conferirBanco(
            FabricaConexoes fabrica,
            UUID departamentoId,
            UUID recursoId,
            UUID pedidoId,
            UUID autorVencedor,
            String cenario
    ) throws SQLException {
        try (Connection conexao = abrirTeste(fabrica)) {
            conexao.setTransactionIsolation(
                    Connection.TRANSACTION_READ_COMMITTED
            );

            var pedido = new RequisicaoRepositoryJdbc(conexao)
                    .buscarPorId(pedidoId)
                    .orElseThrow();

            conferir(
                    autorVencedor.equals(pedido.getDesenvolvedorId())
                            && departamentoId.equals(
                                    pedido.getDepartamento().getId()
                            )
                            && "INICIAL".equals(pedido.getStatus())
                            && pedido.getMotivoRevisao() == null
                            && igual(pedido.getCustoTotalProjetado(), "25.00")
                            && igual(
                                    pedido.getDepartamento().getSaldoDisponivel(),
                                    "800.00"
                            )
                            && igual(
                                    pedido.getDepartamento().getOrcamentoMensal(),
                                    "1000.00"
                            )
                            && pedido.getItens().size() == 1
                            && recursoId.equals(
                                    pedido.getItens().get(0).getRecurso().getId()
                            )
                            && pedido.getItens().get(0).getQuantidade() == 2
                            && igual(
                                    pedido.getItens().get(0).getPrecoUnitario(),
                                    "12.50"
                            )
                            && contar(
                                    conexao,
                                    "SELECT COUNT(*) FROM requisicao "
                                            + "WHERE departamento_id = ?",
                                    departamentoId
                            ) == 1,
                    cenario + ": banco preserva unico pedido do vencedor e saldo"
            );
        }
    }

    private static void limpar(
            FabricaConexoes fabrica,
            UUID departamentoId,
            UUID recursoId,
            UUID pedidoId
    ) throws SQLException {
        try (Connection conexao = abrirTeste(fabrica)) {
            conexao.setAutoCommit(false);

            try {
                excluir(
                        conexao,
                        "DELETE FROM item_requisicao WHERE requisicao_id = ?",
                        pedidoId
                );
                excluir(
                        conexao,
                        "DELETE FROM requisicao WHERE id = ?",
                        pedidoId
                );
                excluir(
                        conexao,
                        "DELETE FROM recurso_catalogo WHERE id = ?",
                        recursoId
                );
                excluir(
                        conexao,
                        "DELETE FROM departamento WHERE id = ?",
                        departamentoId
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

            // Limita a espera por bloqueios durante este teste.
            try (Statement comando = conexao.createStatement()) {
                comando.execute("SET SESSION innodb_lock_wait_timeout = 10");
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