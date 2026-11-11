package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.aplicacao.entradas.ItemSolicitado;
import br.mackenzie.finops.aplicacao.entradas.SolicitacaoAjusteTeto;
import br.mackenzie.finops.aplicacao.excecoes.AcessoNegadoException;
import br.mackenzie.finops.aplicacao.excecoes.ConfirmacaoIntervencaoDesconhecidaException;
import br.mackenzie.finops.aplicacao.excecoes.ConflitoOperacaoException;
import br.mackenzie.finops.dominio.entidades.ResultadoValidacao;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

class AjusteOrcamentarioMysqlIT {
    @Test
    void ajustaLimitesENegativasComHistoricoERepeticaoDaFotografiaOriginal() throws Exception {
        try (var c = new CenarioIntervencoesMysql()) {
            var pedido = criar(c, 56);
            validar(c, pedido);
            var ajuste = new AjusteOrcamentarioJdbc(c.runtime);
            var aumento = entrada("1500.00");
            var original = ajuste.executarAutorizado(c.arquiteto, c.usuarios.departamento, aumento);
            assertEquals("TETO_AJUSTADO", original.resultado());
            assertEquals("800.00", original.dados().get("saldoNovo"));
            assertEquals("700.00", original.dados().get("comprometido"));
            var negativa = entrada("699.99");
            var recusada = ajuste.executarAutorizado(c.arquiteto, c.usuarios.departamento, negativa);
            assertEquals(409, recusada.statusHttp());
            assertEquals("TETO_ABAIXO_DO_COMPROMETIDO", recusada.resultado());
            assertEquals("1500.00", recusada.dados().get("tetoNovo"));
            assertEquals("699.99", recusada.dados().get("tetoSolicitado"));
            assertEquals("200.00", ajuste.executarAutorizado(c.arquiteto, c.usuarios.departamento, entrada("900.00"))
                    .dados().get("saldoNovo"));
            assertEquals("0.00", ajuste.executarAutorizado(c.arquiteto, c.usuarios.departamento, entrada("700.00"))
                    .dados().get("saldoNovo"));
            assertEquals("TETO_INALTERADO", ajuste.executarAutorizado(c.arquiteto, c.usuarios.departamento, entrada("700.00"))
                    .resultado());
            assertEquals(original, ajuste.executarAutorizado(c.arquiteto, c.usuarios.departamento,
                    new SolicitacaoAjusteTeto(aumento.operacaoId(), "001500.00", " Motivo do ajuste ")));
            assertEquals(recusada, ajuste.executarAutorizado(c.arquiteto, c.usuarios.departamento, negativa));
            assertThrows(ConflitoOperacaoException.class, () -> ajuste.executarAutorizado(c.arquiteto,
                    c.usuarios.departamento, new SolicitacaoAjusteTeto(aumento.operacaoId(), "1501.00", aumento.justificativa())));
            assertEquals("0.00", c.saldo(c.usuarios.departamento));
            assertEquals(5, c.quantidade());
            try (var conexao = c.usuarios.abrir()) {
                var requisicao = new RequisicaoRepositoryJdbc(conexao).buscarPorId(pedido).orElseThrow();
                assertEquals("Provisionamento Liberado", requisicao.getStatus());
                assertEquals("700.00", requisicao.getCustoTotalProjetado().toPlainString());
                assertEquals("12.50", requisicao.getItens().getFirst().getPrecoUnitario().toPlainString());
            }
        }
    }

    @Test
    void persisteZeroELimiteDecimalSemConversaoEmPontoFlutuante() throws Exception {
        try (var c = new CenarioIntervencoesMysql()) {
            var ajuste = new AjusteOrcamentarioJdbc(c.runtime);
            ajuste.executarAutorizado(c.arquiteto, c.usuarios.departamento, entrada("0.00"));
            assertEquals("0.00", c.saldo(c.usuarios.departamento));
            ajuste.executarAutorizado(c.arquiteto, c.usuarios.departamento, entrada("99999999999999999.99"));
            assertEquals("99999999999999999.99", c.saldo(c.usuarios.departamento));
            try (var conexao = c.usuarios.abrir()) {
                assertEquals("10000000000000000.00", new DepartamentoRepositoryJdbc(conexao)
                        .buscarPorId(c.usuarios.departamento).orElseThrow().calcularReserva().toPlainString());
            }
        }
    }

    @Test
    void falhaDaAuditoriaReverteAjusteReal() throws Exception {
        try (var c = new CenarioIntervencoesMysql()) {
            var ajuste = new AjusteOrcamentarioJdbc(() -> {
                var real = c.runtime.abrir();
                var controlada = mock(Connection.class, delegatesTo(real));
                doThrow(new SQLException("Falha simulada da auditoria.")).when(controlada)
                        .prepareStatement(startsWith("INSERT INTO intervencao"));
                return controlada;
            });
            assertThrows(FalhaPersistenciaException.class,
                    () -> ajuste.executarAutorizado(c.arquiteto, c.usuarios.departamento, entrada("1500.00")));
            assertEquals("1000.00", c.saldo(c.usuarios.departamento));
            try (var conexao = c.usuarios.abrir()) {
                assertEquals("1000.00", new DepartamentoRepositoryJdbc(conexao).buscarPorId(c.usuarios.departamento)
                        .orElseThrow().getOrcamentoMensal().toPlainString());
            }
            assertEquals(0, c.quantidade());
        }
    }

    @Test
    void commitIncertoPrevaleceInclusiveSobreNegativaERepeticaoNaoDuplicaAjuste() throws Exception {
        for (boolean confirmado : List.of(false, true)) {
            for (String novoTeto : List.of("1500.00", "1000.00", "600.00")) {
                try (var c = new CenarioIntervencoesMysql()) {
                    validar(c, criar(c, 56));
                    var conexoes = new AtomicInteger();
                    var ajuste = new AjusteOrcamentarioJdbc(() -> {
                        conexoes.incrementAndGet();
                        var real = c.runtime.abrir();
                        var controlada = mock(Connection.class, delegatesTo(real));
                        doAnswer(chamada -> {
                            if (confirmado) {
                                real.commit();
                            }
                            throw new SQLException("Confirmação perdida.");
                        }).when(controlada).commit();
                        return controlada;
                    });
                    var entrada = entrada(novoTeto);
                    var incerta = assertThrows(ConfirmacaoIntervencaoDesconhecidaException.class,
                            () -> ajuste.executarAutorizado(c.arquiteto, c.usuarios.departamento, entrada));
                    assertEquals(entrada.operacaoId(), incerta.getOperacaoId());
                    assertEquals(1, conexoes.get());
                    assertEquals(confirmado ? 1 : 0, c.quantidade());
                    var normal = new AjusteOrcamentarioJdbc(c.runtime);
                    var recuperada = normal.executarAutorizado(c.arquiteto, c.usuarios.departamento, entrada);
                    assertEquals(novoTeto.equals("600.00") ? 409 : 200, recuperada.statusHttp());
                    assertEquals(recuperada, normal.executarAutorizado(c.arquiteto, c.usuarios.departamento, entrada));
                    assertEquals(novoTeto.equals("1500.00") ? "800.00" : "300.00", c.saldo(c.usuarios.departamento));
                    assertEquals(1, c.quantidade());
                }
            }
        }
    }

    @Test
    void validaAposReducaoConcorrenteERecusaReducaoAposDebitoConcorrente() throws Exception {
        for (boolean ajustePrimeiro : List.of(true, false)) {
            try (var c = new CenarioIntervencoesMysql(); var executor = Executors.newFixedThreadPool(2)) {
                var pedido = criar(c, 56);
                var entrada = entrada("500.00");
                var pausa = new PausaCommitTesteJdbc();
                var segundaAberta = new CountDownLatch(1);
                FornecedorConexaoJdbc primeiraConexao = () -> pausa.envolver(c.runtime.abrir());
                FornecedorConexaoJdbc segundaConexao = () -> {
                    var real = c.runtime.abrir();
                    segundaAberta.countDown();
                    return real;
                };
                var ajuste = new AjusteOrcamentarioJdbc(ajustePrimeiro ? primeiraConexao : segundaConexao);
                var validacao = new ValidacaoRequisicaoJdbc(ajustePrimeiro ? segundaConexao : primeiraConexao);
                var primeira = executor.submit(() -> {
                    if (ajustePrimeiro) {
                        return ajuste.executarAutorizado(c.arquiteto, c.usuarios.departamento, entrada).resultado();
                    }
                    return validacao.executarAutorizado(c.usuarios.autor, pedido).resultado().name();
                });
                try {
                    pausa.aguardarChegadaAoCommit();
                    try (var observadora = c.usuarios.abrir()) {
                        observadora.setAutoCommit(false);
                        try (var consulta = observadora.prepareStatement("SELECT id FROM departamento WHERE id = ? FOR UPDATE NOWAIT")) {
                            consulta.setString(1, c.usuarios.departamento.toString());
                            assertEquals(3572, assertThrows(SQLException.class, consulta::executeQuery).getErrorCode());
                        } finally {
                            observadora.rollback();
                        }
                    }
                    var segunda = executor.submit(() -> {
                        if (ajustePrimeiro) {
                            return validacao.executarAutorizado(c.usuarios.autor, pedido).resultado().name();
                        }
                        return ajuste.executarAutorizado(c.arquiteto, c.usuarios.departamento, entrada).resultado();
                    });
                    assertTrue(segundaAberta.await(5, TimeUnit.SECONDS));
                    assertThrows(TimeoutException.class, () -> segunda.get(150, TimeUnit.MILLISECONDS));
                    pausa.liberarCommit();
                    assertEquals(ajustePrimeiro ? "TETO_AJUSTADO" : "LIBERADA", primeira.get(10, TimeUnit.SECONDS));
                    assertEquals(ajustePrimeiro ? "REVISAO_POR_SALDO_INSUFICIENTE" : "TETO_ABAIXO_DO_COMPROMETIDO",
                            segunda.get(10, TimeUnit.SECONDS));
                } finally {
                    pausa.liberarCommit();
                }
                assertEquals(ajustePrimeiro ? "500.00" : "300.00", c.saldo(c.usuarios.departamento));
                assertEquals(1, c.quantidade());
            }
        }
    }

    @Test
    void reautorizaRepeticaoENegaDesenvolvedorSemAlterarHistorico() throws Exception {
        try (var c = new CenarioIntervencoesMysql()) {
            var ajuste = new AjusteOrcamentarioJdbc(c.runtime);
            var pedido = entrada("1500.00");
            assertThrows(AcessoNegadoException.class,
                    () -> ajuste.executarAutorizado(c.usuarios.autor, c.usuarios.departamento, pedido));
            assertEquals(0, c.quantidade());
            ajuste.executarAutorizado(c.arquiteto, c.usuarios.departamento, pedido);
            CriacaoIdentidadeMysqlIT.executarEmOutroProcesso("desativar-desenvolvedor", c.arquiteto);
            assertThrows(AcessoNegadoException.class,
                    () -> ajuste.executarAutorizado(c.arquiteto, c.usuarios.departamento, pedido));
            assertEquals(1, c.quantidade());
            assertEquals("1500.00", c.saldo(c.usuarios.departamento));
        }
    }

    @Test
    void ajustePreservaPedidoInicialEProximaValidacaoUsaReservaVigenteEPrecoHistorico() throws Exception {
        try (var c = new CenarioIntervencoesMysql()) {
            var pedido = criar(c, 9);
            try (var conexao = c.usuarios.abrir()) {
                CenarioUsuariosMysql.executar(conexao, "UPDATE recurso_catalogo SET custo_mensal = 99.99 WHERE id = ?",
                        c.usuarios.recurso.toString());
            }
            new AjusteOrcamentarioJdbc(c.runtime).executarAutorizado(c.arquiteto, c.usuarios.departamento, entrada("125.00"));
            try (var conexao = c.usuarios.abrir()) {
                assertEquals("INICIAL", new RequisicaoRepositoryJdbc(conexao).buscarPorId(pedido).orElseThrow().getStatus());
            }
            assertEquals("125.00", c.saldo(c.usuarios.departamento));
            var resultado = new ValidacaoRequisicaoJdbc(c.runtime).executarAutorizado(c.usuarios.autor, pedido);
            assertEquals(ResultadoValidacao.REVISAO_POR_RESERVA, resultado.resultado());
            assertEquals("112.50", resultado.requisicao().getCustoTotalProjetado().toPlainString());
            assertEquals("125.00", c.saldo(c.usuarios.departamento));
            assertEquals(1, c.quantidade());
        }
    }

    @Test
    void valoresEReciboSobrevivemAOperacoesEmJvmIndependente() throws Exception {
        try (var c = new CenarioIntervencoesMysql()) {
            var pedido = entrada("1500.00");
            new AjusteOrcamentarioJdbc(c.runtime).executarAutorizado(c.arquiteto, c.usuarios.departamento, pedido);
            assertEquals("TETO_AJUSTADO|900.00|900.00", executarExterno(c, entrada("900.00")));
            assertEquals("900.00", c.saldo(c.usuarios.departamento));
            assertEquals("TETO_AJUSTADO|1500.00|1500.00", executarExterno(c, pedido));
            assertEquals("900.00", c.saldo(c.usuarios.departamento));
            assertEquals(2, c.quantidade());
        }
    }

    private static String executarExterno(CenarioIntervencoesMysql c, SolicitacaoAjusteTeto pedido) throws Exception {
        var processo = new ProcessBuilder(
                java.nio.file.Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                AjusteExterno.class.getName(), c.arquiteto.toString(), c.usuarios.departamento.toString(),
                pedido.operacaoId().toString(), pedido.novoTeto(), pedido.justificativa()).redirectErrorStream(true).start();
        if (!processo.waitFor(12, TimeUnit.SECONDS)) {
            processo.destroyForcibly();
            fail("Ajuste externo excedeu o prazo.");
        }
        String saida = new String(processo.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).strip();
        assertEquals(0, processo.exitValue(), saida);
        return saida;
    }

    public static class AjusteExterno {
        public static void main(String[] args) throws Exception {
            var fabrica = new FabricaConexoes(new ConfiguracaoBanco(System.getenv("FINOPS_DB_URL"),
                    System.getenv("FINOPS_RUNTIME_DB_USER"), System.getenv("FINOPS_RUNTIME_DB_PASSWORD")));
            try (var conexao = fabrica.abrir()) {
                if (!"finops_test".equals(conexao.getCatalog())) {
                    throw new IllegalStateException("Exige finops_test.");
                }
            }
            var resultado = new AjusteOrcamentarioJdbc(fabrica).executarAutorizado(UUID.fromString(args[0]),
                    UUID.fromString(args[1]), new SolicitacaoAjusteTeto(UUID.fromString(args[2]), args[3], args[4]));
            System.out.println(resultado.resultado() + "|" + resultado.dados().get("tetoNovo") + "|" + resultado.dados().get("saldoNovo"));
        }
    }

    static SolicitacaoAjusteTeto entrada(String teto) {
        return new SolicitacaoAjusteTeto(UUID.randomUUID(), teto, "Motivo do ajuste");
    }

    private static UUID criar(CenarioIntervencoesMysql c, int quantidade) {
        var id = UUID.randomUUID();
        new CriacaoRequisicaoJdbc(c.runtime).executarComIdentidade(c.usuarios.autor, id,
                List.of(new ItemSolicitado(c.usuarios.recurso, quantidade)));
        return id;
    }

    private static void validar(CenarioIntervencoesMysql c, UUID pedido) {
        assertEquals(ResultadoValidacao.LIBERADA,
                new ValidacaoRequisicaoJdbc(c.runtime).executarAutorizado(c.usuarios.autor, pedido).resultado());
    }
}
