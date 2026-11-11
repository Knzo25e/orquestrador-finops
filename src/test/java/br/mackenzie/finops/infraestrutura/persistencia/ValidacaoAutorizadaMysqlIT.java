package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.aplicacao.entradas.ItemSolicitado;
import br.mackenzie.finops.aplicacao.excecoes.AcessoNegadoException;
import br.mackenzie.finops.aplicacao.excecoes.ConfirmacaoValidacaoDesconhecidaException;
import br.mackenzie.finops.aplicacao.excecoes.FalhaValidacaoPersistenteException;
import br.mackenzie.finops.aplicacao.excecoes.RequisicaoNaoEncontradaException;
import br.mackenzie.finops.dominio.entidades.Requisicao;
import br.mackenzie.finops.dominio.entidades.ResultadoValidacao;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static br.mackenzie.finops.infraestrutura.persistencia.CriacaoIdentidadeMysqlIT.executarEmOutroProcesso;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class ValidacaoAutorizadaMysqlIT {
    @Test
    void serializaSubmissoesEReautorizaDepoisDeAlteracoesEmOutraJvm() throws Exception {
        try (var cenario = new CenarioUsuariosMysql();
             var independente = new CenarioUsuariosMysql();
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var pedido = criar(cenario, 56);
            var concorrente = criar(cenario, 56);
            var outroDepartamento = criar(independente, 1);
            var pausa = new PausaCommitTesteJdbc();
            var pausada = new ValidacaoRequisicaoJdbc(() -> pausa.envolver(cenario.abrir()));
            var normal = new ValidacaoRequisicaoJdbc(cenario.fabrica);
            var primeira = executor.submit(() -> pausada.executarAutorizado(cenario.autor, pedido));
            try {
                pausa.aguardarChegadaAoCommit();
                executarEmOutroProcesso("sondar", cenario.autor, cenario.departamento, pedido);
                assertEquals(ResultadoValidacao.LIBERADA,
                        executor.submit(() -> normal.executarAutorizado(independente.autor, outroDepartamento))
                                .get(5, TimeUnit.SECONDS).resultado());
                var iniciadas = new CountDownLatch(2);
                var paralela = new ValidacaoRequisicaoJdbc(() -> {
                    var conexao = cenario.abrir();
                    iniciadas.countDown();
                    return conexao;
                });
                var repetida = executor.submit(() -> paralela.executarAutorizado(cenario.autor, pedido));
                var disputa = executor.submit(() -> paralela.executarAutorizado(cenario.autor, concorrente));
                assertTrue(iniciadas.await(5, TimeUnit.SECONDS));
                pausa.liberarCommit();
                assertEquals(ResultadoValidacao.LIBERADA, primeira.get(12, TimeUnit.SECONDS).resultado());
                assertEquals(ResultadoValidacao.JA_LIBERADA, repetida.get(12, TimeUnit.SECONDS).resultado());
                assertEquals(ResultadoValidacao.REVISAO_POR_SALDO_INSUFICIENTE,
                        disputa.get(12, TimeUnit.SECONDS).resultado());
            } finally {
                pausa.liberarCommit();
            }
            assertEquals("300.00", saldo(consultar(cenario, pedido)));
            executarEmOutroProcesso("transferir", cenario.autor, cenario.outroDepartamento);
            assertThrows(RequisicaoNaoEncontradaException.class,
                    () -> normal.executarAutorizado(cenario.autor, pedido));
            var novoPedido = criar(cenario, 1);
            executarEmOutroProcesso("desativar-desenvolvedor", cenario.autor);
            assertThrows(AcessoNegadoException.class, () -> normal.executarAutorizado(cenario.autor, novoPedido));
            assertEquals("INICIAL", consultar(cenario, novoPedido).getStatus());
            assertEquals("1000.00", saldo(consultar(cenario, novoPedido)));
        }
    }

    @Test
    void falhaDepoisDoDebitoProvisorioDesfazSaldoEEstado() throws Exception {
        try (var cenario = new CenarioUsuariosMysql()) {
            var pedido = criar(cenario, 2);
            var falha = new ValidacaoRequisicaoJdbc(() -> {
                var real = cenario.abrir();
                var controlada = mock(Connection.class, delegatesTo(real));
                doAnswer(chamada -> {
                    var departamento = new DepartamentoRepositoryJdbc(real).buscarPorId(cenario.departamento)
                            .orElseThrow();
                    assertEquals("975.00", departamento.getSaldoDisponivel().toPlainString());
                    throw new SQLException("Falha simulada antes de gravar o estado.");
                }).when(controlada).prepareStatement(startsWith("UPDATE requisicao"));
                return controlada;
            });
            assertThrows(FalhaValidacaoPersistenteException.class,
                    () -> falha.executarAutorizado(cenario.autor, pedido));
            var recuperado = consultar(cenario, pedido);
            assertEquals("INICIAL", recuperado.getStatus());
            assertEquals("1000.00", saldo(recuperado));
        }
    }

    @Test
    void commitIncertoReconciliaExplicitamenteSemDuplicarDebito() throws Exception {
        for (boolean commitEfetivado : List.of(false, true)) {
            try (var cenario = new CenarioUsuariosMysql()) {
                var pedido = criar(cenario, 2);
                var tentativas = new AtomicInteger();
                var incerta = new ValidacaoRequisicaoJdbc(() -> {
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
                var falha = assertThrows(ConfirmacaoValidacaoDesconhecidaException.class,
                        () -> incerta.executarAutorizado(cenario.autor, pedido));
                assertEquals(pedido, falha.getRequisicaoId());
                assertEquals(1, tentativas.get());
                var recuperado = consultar(cenario, pedido);
                assertEquals(commitEfetivado ? "975.00" : "1000.00", saldo(recuperado));

                var normal = new ValidacaoRequisicaoJdbc(cenario.fabrica);
                if (commitEfetivado) {
                    try (var conexao = cenario.abrir()) {
                        CenarioUsuariosMysql.executar(conexao, "UPDATE usuario SET ativo = FALSE WHERE id = ?",
                                cenario.autor.toString());
                        assertThrows(AcessoNegadoException.class,
                                () -> normal.executarAutorizado(cenario.autor, pedido));
                        CenarioUsuariosMysql.executar(conexao, "UPDATE usuario SET ativo = TRUE WHERE id = ?",
                                cenario.autor.toString());
                    }
                }
                var resultado = normal.executarAutorizado(cenario.autor, pedido);
                assertEquals(commitEfetivado ? ResultadoValidacao.JA_LIBERADA : ResultadoValidacao.LIBERADA,
                        resultado.resultado());
                assertEquals("975.00", saldo(consultar(cenario, pedido)));
            }
        }
    }

    @Test
    void falhaDeFechamentoPreservaLiberacaoConfirmada() throws Exception {
        try (var cenario = new CenarioUsuariosMysql()) {
            var pedido = criar(cenario, 2);
            var validacao = new ValidacaoRequisicaoJdbc(() -> {
                var real = cenario.abrir();
                var controlada = mock(Connection.class, delegatesTo(real));
                doAnswer(chamada -> {
                    real.close();
                    throw new SQLException("Falha simulada de fechamento.");
                }).when(controlada).close();
                return controlada;
            });
            var resultado = validacao.executarAutorizado(cenario.autor, pedido);
            assertEquals(ResultadoValidacao.LIBERADA, resultado.resultado());
            assertTrue(resultado.falhaNoFechamento());
            assertEquals("975.00", saldo(consultar(cenario, pedido)));
        }
    }

    private static UUID criar(CenarioUsuariosMysql cenario, int quantidade) {
        var pedido = UUID.randomUUID();
        new CriacaoRequisicaoJdbc(cenario.fabrica).executarComIdentidade(
                cenario.autor, pedido, List.of(new ItemSolicitado(cenario.recurso, quantidade))
        );
        return pedido;
    }

    private static Requisicao consultar(CenarioUsuariosMysql cenario, UUID pedido) throws SQLException {
        try (var conexao = cenario.abrir()) {
            return new RequisicaoRepositoryJdbc(conexao).buscarPorId(pedido).orElseThrow();
        }
    }

    private static String saldo(Requisicao requisicao) {
        return requisicao.getDepartamento().getSaldoDisponivel().toPlainString();
    }
}
