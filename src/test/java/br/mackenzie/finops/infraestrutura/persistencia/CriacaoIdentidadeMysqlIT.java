package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.aplicacao.entradas.ItemSolicitado;
import br.mackenzie.finops.aplicacao.excecoes.AcessoNegadoException;
import br.mackenzie.finops.aplicacao.excecoes.ConfirmacaoCriacaoDesconhecidaException;
import br.mackenzie.finops.aplicacao.resultados.ResultadoCriacaoRequisicao;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class CriacaoIdentidadeMysqlIT {
    @Test
    void protegeUsuarioDepartamentoEPedidoEntreProcessos() throws Exception {
        try (var cenario = new CenarioUsuariosMysql();
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var pausa = new PausaCommitTesteJdbc();
            var criacao = new CriacaoRequisicaoJdbc(() -> pausa.envolver(cenario.abrir()));
            var pedido = UUID.randomUUID();
            var itens = List.of(new ItemSolicitado(cenario.recurso, 2));
            var futuro = executor.submit(() -> criacao.executarComIdentidade(cenario.autor, pedido, itens));
            try {
                pausa.aguardarChegadaAoCommit();
                executarEmOutroProcesso("sondar", cenario.autor, cenario.departamento, pedido);
            } finally {
                pausa.liberarCommit();
            }
            assertEquals(cenario.departamento,
                    futuro.get(15, TimeUnit.SECONDS).requisicao().getDepartamento().getId());

            executarEmOutroProcesso("transferir", cenario.autor, cenario.outroDepartamento);
            assertThrows(AcessoNegadoException.class,
                    () -> criacao.executarComIdentidade(cenario.autor, pedido, itens));
            assertEquals(cenario.outroDepartamento,
                    criacao.executarComIdentidade(cenario.autor, UUID.randomUUID(), itens)
                            .requisicao().getDepartamento().getId());

            executarEmOutroProcesso("alterar-perfil", cenario.autor);
            assertThrows(AcessoNegadoException.class,
                    () -> criacao.executarComIdentidade(cenario.autor, UUID.randomUUID(), itens));
            executarEmOutroProcesso("desativar-desenvolvedor", cenario.autor);
            assertThrows(AcessoNegadoException.class,
                    () -> criacao.executarComIdentidade(cenario.autor, UUID.randomUUID(), itens));
        }
    }

    @Test
    void releIdentidadeNaReconciliacaoDeDuplicidadeReal() throws Exception {
        try (var cenario = new CenarioUsuariosMysql();
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var barreira = new BarreiraCriacaoAutorizadaTesteJdbc();
            var conexoes = new AtomicInteger();
            var criacao = new CriacaoRequisicaoJdbc(() -> {
                if (conexoes.incrementAndGet() == 3) {
                    try (var writer = cenario.abrir()) {
                        CenarioUsuariosMysql.executar(writer, "UPDATE usuario SET departamento_id = ? WHERE id = ?",
                                cenario.outroDepartamento.toString(), cenario.autor.toString());
                    }
                }
                return barreira.envolver(cenario.abrir());
            });
            var pedido = UUID.randomUUID();
            var itens = List.of(new ItemSolicitado(cenario.recurso, 2));
            var primeira = executor.submit(() -> tentar(criacao, cenario.autor, pedido, itens));
            var segunda = executor.submit(() -> tentar(criacao, cenario.autor, pedido, itens));
            var resultados = List.of(primeira.get(20, TimeUnit.SECONDS), segunda.get(20, TimeUnit.SECONDS));

            assertTrue(resultados.contains(ResultadoCriacaoRequisicao.Situacao.CRIADA));
            assertTrue(resultados.contains(AcessoNegadoException.class));
            assertEquals(2, barreira.tentativasInsercao());
            assertEquals(3, conexoes.get());
            try (var conexao = cenario.abrir()) {
                var existente = new RequisicaoRepositoryJdbc(conexao).buscarPorId(pedido).orElseThrow();
                assertEquals(cenario.departamento, existente.getDepartamento().getId());
                assertEquals(cenario.autor, existente.getDesenvolvedorId());
            }
        }
    }

    @Test
    void commitIncertoExigeRepeticaoExplicitaComIdentidadeAtual() throws Exception {
        try (var cenario = new CenarioUsuariosMysql()) {
            var itens = List.of(new ItemSolicitado(cenario.recurso, 2));
            var normal = new CriacaoRequisicaoJdbc(cenario.fabrica);
            for (boolean commitEfetivado : List.of(false, true)) {
                var pedido = UUID.randomUUID();
                var tentativas = new AtomicInteger();
                var incerta = new CriacaoRequisicaoJdbc(() -> {
                    tentativas.incrementAndGet();
                    var real = cenario.abrir();
                    var controlada = mock(Connection.class, delegatesTo(real));
                    doAnswer(chamada -> {
                        if (commitEfetivado) {
                            real.commit();
                        }
                        throw new SQLException("Perda simulada de confirmação.");
                    }).when(controlada).commit();
                    return controlada;
                });
                assertThrows(ConfirmacaoCriacaoDesconhecidaException.class,
                        () -> incerta.executarComIdentidade(cenario.autor, pedido, itens));
                assertEquals(1, tentativas.get());
                try (var conexao = cenario.abrir()) {
                    assertEquals(commitEfetivado,
                            new RequisicaoRepositoryJdbc(conexao).buscarPorId(pedido).isPresent());
                    if (commitEfetivado) {
                        CenarioUsuariosMysql.executar(conexao, "UPDATE usuario SET ativo = FALSE WHERE id = ?",
                                cenario.autor.toString());
                        assertThrows(AcessoNegadoException.class,
                                () -> normal.executarComIdentidade(cenario.autor, pedido, itens));
                        CenarioUsuariosMysql.executar(conexao, "UPDATE usuario SET ativo = TRUE WHERE id = ?",
                                cenario.autor.toString());
                    }
                }
                var esperada = commitEfetivado ? ResultadoCriacaoRequisicao.Situacao.JA_EXISTENTE
                        : ResultadoCriacaoRequisicao.Situacao.CRIADA;
                assertEquals(esperada, normal.executarComIdentidade(cenario.autor, pedido, itens).situacao());
            }
        }
    }

    private static Object tentar(
            CriacaoRequisicaoJdbc criacao, UUID autor, UUID pedido, List<ItemSolicitado> itens
    ) {
        try {
            return criacao.executarComIdentidade(autor, pedido, itens).situacao();
        } catch (AcessoNegadoException negada) {
            return negada.getClass();
        }
    }

    static void executarEmOutroProcesso(String acao, UUID... ids) throws Exception {
        var comando = new java.util.ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                OperacaoExterna.class.getName(), acao
        ));
        for (var id : ids) {
            comando.add(id.toString());
        }
        var processo = new ProcessBuilder(comando).redirectErrorStream(true).start();
        if (!processo.waitFor(12, TimeUnit.SECONDS)) {
            processo.destroyForcibly();
            throw new IllegalStateException("A operação no processo externo excedeu o prazo.");
        }
        assertEquals(0, processo.exitValue(), new String(processo.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }

    public static class OperacaoExterna {
        public static void main(String[] args) throws Exception {
            var fabrica = new FabricaConexoes(ConfiguracaoBanco.carregarDoAmbiente());
            try (var conexao = fabrica.abrir()) {
                assertEquals("finops_test", conexao.getCatalog());
                switch (args[0]) {
                    case "sondar" -> {
                        conexao.setAutoCommit(false);
                        conferirBloqueio(conexao, "usuario", args[1]);
                        conferirBloqueio(conexao, "departamento", args[2]);
                        conferirBloqueio(conexao, "requisicao", args[3]);
                    }
                    case "transferir" -> CenarioUsuariosMysql.executar(conexao,
                            "UPDATE usuario SET departamento_id = ? WHERE id = ?", args[2], args[1]);
                    case "alterar-perfil" -> CenarioUsuariosMysql.executar(conexao,
                            "UPDATE usuario SET perfil = 'ARQUITETO_CLOUD' WHERE id = ?", args[1]);
                    case "desativar-desenvolvedor" -> CenarioUsuariosMysql.executar(conexao,
                            "UPDATE usuario SET ativo = FALSE, perfil = 'DESENVOLVEDOR' WHERE id = ?", args[1]);
                    default -> throw new IllegalArgumentException("Operação de teste desconhecida.");
                }
            }
        }

        private static void conferirBloqueio(Connection conexao, String tabela, String id) throws SQLException {
            try (var consulta = conexao.prepareStatement(
                    "SELECT id FROM " + tabela + " WHERE id = ? FOR UPDATE NOWAIT"
            )) {
                consulta.setString(1, id);
                var falha = assertThrows(SQLException.class, consulta::executeQuery);
                assertEquals(3572, falha.getErrorCode());
            } finally {
                conexao.rollback();
            }
        }
    }
}
