package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.aplicacao.entradas.ItemSolicitado;
import br.mackenzie.finops.aplicacao.entradas.SolicitacaoAjusteTeto;
import br.mackenzie.finops.aplicacao.entradas.SolicitacaoIntervencaoRequisicao;
import br.mackenzie.finops.aplicacao.excecoes.*;
import br.mackenzie.finops.aplicacao.resultados.ResultadoIntervencaoRequisicao;
import br.mackenzie.finops.dominio.entidades.DecisaoManual;
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
import static br.mackenzie.finops.dominio.entidades.DecisaoManual.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

class FluxoManualMysqlIT {
    @Test
    void aprovaNegaReservaELiberaComDebitoUnicoERecuperaFotografiasOriginais() throws Exception {
        try (var c = new CenarioIntervencoesMysql()) {
            var pedido = criarRevisao(c, 72);
            var aprovacao = entrada(APROVAR);
            var tecnica = intervir(c, pedido, aprovacao);
            assertEquals("APROVADA_TECNICAMENTE", tecnica.resultado());
            assertEquals("AGUARDANDO_AUTORIZACAO_FINANCEIRA", tecnica.requisicao().estado());
            assertNull(tecnica.requisicao().motivoRevisao());
            assertEquals("1000.00", c.saldo(c.usuarios.departamento));
            assertEquals(ResultadoValidacao.ESTADO_NAO_PERMITIDO,
                    new ValidacaoRequisicaoJdbc(c.runtime).executarAutorizado(c.usuarios.autor, pedido).resultado());
            assertEquals("RESERVA_NEGADA", intervir(c, pedido, entrada(NEGAR_RESERVA)).resultado());
            assertEquals("AGUARDA_AUTORIZACAO", intervir(c, pedido, entrada(LIBERAR_SEM_RESERVA)).resultado());
            var autorizacao = entrada(AUTORIZAR_RESERVA);
            var liberada = intervir(c, pedido, autorizacao);
            assertEquals("LIBERADA_COM_RESERVA", liberada.resultado());
            assertEquals("100.00", c.saldo(c.usuarios.departamento));
            assertEquals(liberada, intervir(c, pedido, autorizacao));
            assertEquals(tecnica, intervir(c, pedido, aprovacao));
            assertEquals(409, intervir(c, pedido, entrada(AUTORIZAR_RESERVA)).statusHttp());
            var repetida = new CriacaoRequisicaoJdbc(c.runtime).executarComIdentidade(c.usuarios.autor, pedido,
                    List.of(new ItemSolicitado(c.usuarios.recurso, 72)));
            assertEquals("Provisionamento Liberado", repetida.requisicao().getStatus());
            try (var conexao = c.usuarios.abrir()) {
                var evento = RegistroIntervencaoJdbc.buscar(conexao, aprovacao.operacaoId()).orElseThrow().evento();
                assertEquals("RESERVA", evento.dados().get("motivoOriginal"));
                assertEquals("900.00", evento.dados().get("custoHistorico"));
                assertEquals("100.00", evento.dados().get("valorReserva"));
            }
            assertEquals(5, c.quantidade());
        }
    }

    @Test
    void insuficienciaExigeAjusteEAutorizarReservaDispensadaNaoLibera() throws Exception {
        try (var c = new CenarioIntervencoesMysql()) {
            var pedido = criarRevisao(c, 88);
            assertEquals("AGUARDANDO_AJUSTE_ORCAMENTARIO", intervir(c, pedido, entrada(APROVAR)).requisicao().estado());
            assertEquals("AGUARDA_AJUSTE", intervir(c, pedido, entrada(AUTORIZAR_RESERVA)).resultado());
            try (var conexao = c.usuarios.abrir()) {
                CenarioUsuariosMysql.executar(conexao, "UPDATE recurso_catalogo SET custo_mensal = 99.99 WHERE id = ?",
                        c.usuarios.recurso.toString());
            }
            ajustar(c, "1250.00");
            var dispensada = intervir(c, pedido, entrada(AUTORIZAR_RESERVA));
            assertEquals("RESERVA_DISPENSADA", dispensada.resultado());
            assertEquals("AGUARDANDO_LIBERACAO_FINANCEIRA", dispensada.requisicao().estado());
            assertEquals("1250.00", c.saldo(c.usuarios.departamento));
            assertEquals("LIBERADA", intervir(c, pedido, entrada(LIBERAR_SEM_RESERVA)).resultado());
            assertEquals("150.00", c.saldo(c.usuarios.departamento));
            assertEquals("12.50", dispensada.requisicao().itens().getFirst().precoUnitario());
            assertEquals(5, c.quantidade());
        }
    }

    @Test
    void autorizacaoEmTentativaSemSaldoNaoPodeSerReutilizadaAposAjuste() throws Exception {
        try (var c = new CenarioIntervencoesMysql()) {
            var pedido = criarRevisao(c, 72);
            intervir(c, pedido, entrada(APROVAR));
            var outro = criar(c, 56);
            assertEquals(ResultadoValidacao.LIBERADA, new ValidacaoRequisicaoJdbc(c.runtime)
                    .executarAutorizado(c.usuarios.autor, outro).resultado());
            assertEquals("AGUARDA_AJUSTE", intervir(c, pedido, entrada(AUTORIZAR_RESERVA)).resultado());
            ajustar(c, "1600.00");
            assertEquals("AGUARDA_AUTORIZACAO", intervir(c, pedido, entrada(LIBERAR_SEM_RESERVA)).resultado());
            assertEquals("900.00", c.saldo(c.usuarios.departamento));
            assertEquals("LIBERADA_COM_RESERVA", intervir(c, pedido, entrada(AUTORIZAR_RESERVA)).resultado());
            assertEquals("0.00", c.saldo(c.usuarios.departamento));
        }
    }

    @Test
    void rejeitaLegadoComJustificativaEAutorizaAntesDeDescobrirAlvo() throws Exception {
        try (var c = new CenarioIntervencoesMysql()) {
            var pedido = criarRevisao(c, 72);
            try (var conexao = c.usuarios.abrir()) {
                CenarioUsuariosMysql.executar(conexao, "UPDATE requisicao SET desenvolvedor_id = NULL WHERE id = ?", pedido.toString());
            }
            var rejeicao = entrada(REJEITAR);
            assertEquals("REJEITADO", intervir(c, pedido, rejeicao).requisicao().estado());
            assertEquals(409, intervir(c, pedido, entrada(APROVAR)).statusHttp());
            assertEquals("1000.00", c.saldo(c.usuarios.departamento));
            try (var conexao = c.usuarios.abrir()) {
                assertNull(new RequisicaoRepositoryJdbc(conexao).buscarPorId(pedido).orElseThrow().getDesenvolvedorId());
                assertEquals(rejeicao.justificativa(), RegistroIntervencaoJdbc.buscar(conexao, rejeicao.operacaoId()).orElseThrow().evento().justificativa());
            }
            var protegida = new IntervencaoRequisicaoJdbc(() -> {
                var real = c.runtime.abrir();
                var controlada = mock(Connection.class, delegatesTo(real));
                doAnswer(chamada -> { fail("Não pode descobrir alvo antes de autorizar."); return null; })
                        .when(controlada).prepareStatement(startsWith("SELECT departamento_id FROM requisicao"));
                return controlada;
            });
            for (UUID alvo : List.of(pedido, UUID.randomUUID())) {
                assertThrows(AcessoNegadoException.class,
                        () -> protegida.executarAutorizado(c.usuarios.autor, alvo, entrada(APROVAR)));
            }
            CriacaoIdentidadeMysqlIT.executarEmOutroProcesso("desativar-desenvolvedor", c.arquiteto);
            assertThrows(AcessoNegadoException.class, () -> intervir(c, pedido, rejeicao));
            assertEquals(2, c.quantidade());
        }
    }

    @Test
    void falhaDaAuditoriaReverteRevisaoRejeicaoELiberacaoComDebito() throws Exception {
        for (var decisao : List.of(APROVAR, REJEITAR, AUTORIZAR_RESERVA)) {
            try (var c = new CenarioIntervencoesMysql()) {
                var pedido = criarRevisao(c, 72);
                if (decisao == AUTORIZAR_RESERVA) {
                    intervir(c, pedido, entrada(APROVAR));
                }
                var falha = new IntervencaoRequisicaoJdbc(() -> {
                    var real = c.runtime.abrir();
                    var controlada = mock(Connection.class, delegatesTo(real));
                    doThrow(new SQLException("Falha simulada do recibo.")).when(controlada)
                            .prepareStatement(startsWith("INSERT INTO intervencao"));
                    return controlada;
                });
                assertThrows(FalhaPersistenciaException.class,
                        () -> falha.executarAutorizado(c.arquiteto, pedido, entrada(decisao)));
                assertEquals("1000.00", c.saldo(c.usuarios.departamento));
                assertEquals(decisao == AUTORIZAR_RESERVA ? 1 : 0, c.quantidade());
                try (var conexao = c.usuarios.abrir()) {
                    var gravada = new RequisicaoRepositoryJdbc(conexao).buscarPorId(pedido).orElseThrow();
                    assertEquals(decisao == AUTORIZAR_RESERVA ? "AGUARDANDO_AUTORIZACAO_FINANCEIRA" : "Revisão Pendente", gravada.getStatus());
                }
            }
        }
    }

    @Test
    void commitIncertoExigeReconciliacaoExplicitaParaRevisaoDebitoENegativa() throws Exception {
        for (boolean confirmado : List.of(false, true)) {
            for (var decisao : List.of(APROVAR, REJEITAR, AUTORIZAR_RESERVA, LIBERAR_SEM_RESERVA)) {
                try (var c = new CenarioIntervencoesMysql()) {
                    var pedido = criarRevisao(c, 72);
                    if (decisao == AUTORIZAR_RESERVA) {
                        intervir(c, pedido, entrada(APROVAR));
                    }
                    int anteriores = c.quantidade();
                    var conexoes = new AtomicInteger();
                    var incerta = new IntervencaoRequisicaoJdbc(() -> {
                        conexoes.incrementAndGet();
                        var real = c.runtime.abrir();
                        var controlada = mock(Connection.class, delegatesTo(real));
                        doAnswer(chamada -> {
                            if (confirmado) { real.commit(); }
                            throw new SQLException("Confirmação perdida.");
                        }).when(controlada).commit();
                        return controlada;
                    });
                    var entrada = entrada(decisao);
                    var erro = assertThrows(ConfirmacaoIntervencaoDesconhecidaException.class,
                            () -> incerta.executarAutorizado(c.arquiteto, pedido, entrada));
                    assertEquals(entrada.operacaoId(), erro.getOperacaoId());
                    assertEquals(1, conexoes.get());
                    assertEquals(anteriores + (confirmado ? 1 : 0), c.quantidade());
                    var reconciliada = intervir(c, pedido, entrada);
                    assertEquals(decisao == LIBERAR_SEM_RESERVA ? 409 : 200, reconciliada.statusHttp());
                    assertEquals(reconciliada, intervir(c, pedido, entrada));
                    assertEquals(anteriores + 1, c.quantidade());
                    assertEquals(decisao == AUTORIZAR_RESERVA ? "100.00" : "1000.00", c.saldo(c.usuarios.departamento));
                }
            }
        }
    }

    @Test
    void duasLiberacoesConcorrentesComUuidIgualOuDiferenteDebitamUmaVez() throws Exception {
        for (boolean mesmoUuid : List.of(true, false)) {
            try (var c = new CenarioIntervencoesMysql(); var executor = Executors.newFixedThreadPool(2)) {
                var pedido = criarRevisao(c, 72);
                intervir(c, pedido, entrada(APROVAR));
                var comando = entrada(AUTORIZAR_RESERVA);
                var pausa = new PausaCommitTesteJdbc();
                var primeira = executor.submit(() -> new IntervencaoRequisicaoJdbc(() -> pausa.envolver(c.runtime.abrir()))
                        .executarAutorizado(c.arquiteto, pedido, comando));
                try {
                    pausa.aguardarChegadaAoCommit();
                    CriacaoIdentidadeMysqlIT.executarEmOutroProcesso("sondar", c.arquiteto, c.usuarios.departamento, pedido);
                    var aberta = new CountDownLatch(1);
                    var segunda = executor.submit(() -> new IntervencaoRequisicaoJdbc(() -> {
                        var real = c.runtime.abrir(); aberta.countDown(); return real;
                    }).executarAutorizado(c.arquiteto, pedido, mesmoUuid ? comando : entrada(AUTORIZAR_RESERVA)));
                    assertTrue(aberta.await(5, TimeUnit.SECONDS));
                    assertThrows(TimeoutException.class, () -> segunda.get(150, TimeUnit.MILLISECONDS));
                    pausa.liberarCommit();
                    var original = primeira.get(10, TimeUnit.SECONDS);
                    var repetida = segunda.get(10, TimeUnit.SECONDS);
                    assertEquals(200, original.statusHttp());
                    assertEquals(mesmoUuid ? 200 : 409, repetida.statusHttp());
                    if (mesmoUuid) { assertEquals(original, repetida); }
                } finally { pausa.liberarCommit(); }
                assertEquals("100.00", c.saldo(c.usuarios.departamento));
                assertEquals(mesmoUuid ? 2 : 3, c.quantidade());
            }
        }
    }

    @Test
    void ajusteELiberacaoConcorrentesRespeitamCompromissosNosDoisSentidos() throws Exception {
        for (boolean ajustePrimeiro : List.of(true, false)) {
            try (var c = new CenarioIntervencoesMysql(); var executor = Executors.newFixedThreadPool(2)) {
                var pedido = criarRevisao(c, 72);
                intervir(c, pedido, entrada(APROVAR));
                var pausa = new PausaCommitTesteJdbc();
                var aberta = new CountDownLatch(1);
                FornecedorConexaoJdbc primeiraConexao = () -> pausa.envolver(c.runtime.abrir());
                FornecedorConexaoJdbc segundaConexao = () -> { var real = c.runtime.abrir(); aberta.countDown(); return real; };
                var manual = new IntervencaoRequisicaoJdbc(ajustePrimeiro ? segundaConexao : primeiraConexao);
                var ajuste = new AjusteOrcamentarioJdbc(ajustePrimeiro ? primeiraConexao : segundaConexao);
                var primeira = executor.submit(() -> ajustePrimeiro
                        ? ajuste.executarAutorizado(c.arquiteto, c.usuarios.departamento, new SolicitacaoAjusteTeto(UUID.randomUUID(), "800.00", "Redução concorrente")).resultado()
                        : manual.executarAutorizado(c.arquiteto, pedido, entrada(AUTORIZAR_RESERVA)).resultado());
                try {
                    pausa.aguardarChegadaAoCommit();
                    var segunda = executor.submit(() -> ajustePrimeiro
                            ? manual.executarAutorizado(c.arquiteto, pedido, entrada(AUTORIZAR_RESERVA)).resultado()
                            : ajuste.executarAutorizado(c.arquiteto, c.usuarios.departamento, new SolicitacaoAjusteTeto(UUID.randomUUID(), "800.00", "Redução concorrente")).resultado());
                    assertTrue(aberta.await(5, TimeUnit.SECONDS));
                    assertThrows(TimeoutException.class, () -> segunda.get(150, TimeUnit.MILLISECONDS));
                    pausa.liberarCommit();
                    assertEquals(ajustePrimeiro ? "TETO_AJUSTADO" : "LIBERADA_COM_RESERVA", primeira.get(10, TimeUnit.SECONDS));
                    assertEquals(ajustePrimeiro ? "AGUARDA_AJUSTE" : "TETO_ABAIXO_DO_COMPROMETIDO", segunda.get(10, TimeUnit.SECONDS));
                } finally { pausa.liberarCommit(); }
                assertEquals(ajustePrimeiro ? "800.00" : "100.00", c.saldo(c.usuarios.departamento));
                assertEquals(3, c.quantidade());
            }
        }
    }

    @Test
    void aprovacaoERejeicaoConcorrentesRespeitamDecisaoConfirmadaPrimeiro() throws Exception {
        for (var decisao : List.of(APROVAR, REJEITAR)) {
            try (var c = new CenarioIntervencoesMysql(); var executor = Executors.newFixedThreadPool(2)) {
                var pedido = criarRevisao(c, 72);
                var pausa = new PausaCommitTesteJdbc();
                var primeira = executor.submit(() -> new IntervencaoRequisicaoJdbc(() -> pausa.envolver(c.runtime.abrir()))
                        .executarAutorizado(c.arquiteto, pedido, entrada(decisao)));
                try {
                    pausa.aguardarChegadaAoCommit();
                    var aberta = new CountDownLatch(1);
                    var segunda = executor.submit(() -> new IntervencaoRequisicaoJdbc(() -> {
                        var real = c.runtime.abrir();
                        aberta.countDown();
                        return real;
                    }).executarAutorizado(c.arquiteto, pedido, entrada(decisao == APROVAR ? REJEITAR : APROVAR)));
                    assertTrue(aberta.await(5, TimeUnit.SECONDS));
                    assertThrows(TimeoutException.class, () -> segunda.get(150, TimeUnit.MILLISECONDS));
                    pausa.liberarCommit();
                    assertEquals(200, primeira.get(10, TimeUnit.SECONDS).statusHttp());
                    assertEquals(409, segunda.get(10, TimeUnit.SECONDS).statusHttp());
                } finally {
                    pausa.liberarCommit();
                }
                assertEquals("1000.00", c.saldo(c.usuarios.departamento));
                assertEquals(2, c.quantidade());
            }
        }
    }

    @Test
    void relePerfilAlteradoEnquantoTentativaAguardaBloqueioDeIdentidade() throws Exception {
        try (var c = new CenarioIntervencoesMysql(); var executor = Executors.newSingleThreadExecutor();
             var administracao = c.usuarios.abrir()) {
            var pedido = criarRevisao(c, 72);
            administracao.setAutoCommit(false);
            CenarioUsuariosMysql.executar(administracao, "UPDATE usuario SET perfil = 'DESENVOLVEDOR' WHERE id = ?", c.arquiteto.toString());
            var aberta = new CountDownLatch(1);
            var tentativa = executor.submit(() -> new IntervencaoRequisicaoJdbc(() -> {
                var real = c.runtime.abrir();
                aberta.countDown();
                return real;
            }).executarAutorizado(c.arquiteto, pedido, entrada(APROVAR)));
            try {
                assertTrue(aberta.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> tentativa.get(150, TimeUnit.MILLISECONDS));
                administracao.commit();
                var falha = assertThrows(java.util.concurrent.ExecutionException.class, () -> tentativa.get(10, TimeUnit.SECONDS));
                assertInstanceOf(AcessoNegadoException.class, falha.getCause());
            } finally {
                administracao.rollback();
            }
            assertEquals(0, c.quantidade());
            assertEquals("1000.00", c.saldo(c.usuarios.departamento));
        }
    }

    @Test
    void reconciliaEmOutraJvmEValidaAutomaticamenteSemDuplicarDebitoManual() throws Exception {
        try (var c = new CenarioIntervencoesMysql(); var executor = Executors.newFixedThreadPool(2)) {
            var pedido = criarRevisao(c, 72);
            intervir(c, pedido, entrada(APROVAR));
            var entrada = entrada(AUTORIZAR_RESERVA);
            var pausa = new PausaCommitTesteJdbc();
            var primeira = executor.submit(() -> new IntervencaoRequisicaoJdbc(() -> pausa.envolver(c.runtime.abrir()))
                    .executarAutorizado(c.arquiteto, pedido, entrada));
            Process externa = null;
            try {
                pausa.aguardarChegadaAoCommit();
                externa = new ProcessBuilder(java.nio.file.Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                        "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                        ReconciliacaoExterna.class.getName(), c.arquiteto.toString(), pedido.toString(), entrada.operacaoId().toString())
                        .redirectErrorStream(true).start();
                var leitor = externa.inputReader();
                assertEquals("PRONTO", leitor.readLine());
                var aberta = new CountDownLatch(1);
                var automatica = executor.submit(() -> new ValidacaoRequisicaoJdbc(() -> {
                    var real = c.runtime.abrir();
                    aberta.countDown();
                    return real;
                }).executarAutorizado(c.usuarios.autor, pedido));
                assertTrue(aberta.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> automatica.get(150, TimeUnit.MILLISECONDS));
                pausa.liberarCommit();
                assertEquals("LIBERADA_COM_RESERVA", primeira.get(10, TimeUnit.SECONDS).resultado());
                assertEquals(ResultadoValidacao.JA_LIBERADA, automatica.get(10, TimeUnit.SECONDS).resultado());
                assertTrue(externa.waitFor(12, TimeUnit.SECONDS));
                assertEquals(0, externa.exitValue(), leitor.lines().collect(java.util.stream.Collectors.joining("\n")));
            } finally {
                pausa.liberarCommit();
                if (externa != null && externa.isAlive()) {
                    externa.destroyForcibly();
                }
            }
            assertEquals("100.00", c.saldo(c.usuarios.departamento));
            assertEquals(2, c.quantidade());
        }
    }

    public static class ReconciliacaoExterna {
        public static void main(String[] args) throws Exception {
            var fabrica = new FabricaConexoes(new ConfiguracaoBanco(System.getenv("FINOPS_DB_URL"),
                    System.getenv("FINOPS_RUNTIME_DB_USER"), System.getenv("FINOPS_RUNTIME_DB_PASSWORD")));
            try (var conexao = fabrica.abrir()) {
                if (!"finops_test".equals(conexao.getCatalog())) {
                    throw new IllegalStateException("Exige finops_test.");
                }
            }
            System.out.println("PRONTO");
            var resultado = new IntervencaoRequisicaoJdbc(fabrica).executarAutorizado(UUID.fromString(args[0]), UUID.fromString(args[1]),
                    new SolicitacaoIntervencaoRequisicao(UUID.fromString(args[2]), AUTORIZAR_RESERVA, "Justificativa da intervenção"));
            if (!"LIBERADA_COM_RESERVA".equals(resultado.resultado()) || !"PROVISIONAMENTO_LIBERADO".equals(resultado.requisicao().estado())) {
                throw new IllegalStateException("Recibo não recuperado corretamente.");
            }
        }
    }

    static SolicitacaoIntervencaoRequisicao entrada(DecisaoManual decisao) {
        return new SolicitacaoIntervencaoRequisicao(UUID.randomUUID(), decisao, "Justificativa da intervenção");
    }

    static UUID criar(CenarioIntervencoesMysql c, int quantidade) {
        var id = UUID.randomUUID();
        new CriacaoRequisicaoJdbc(c.runtime).executarComIdentidade(c.usuarios.autor, id,
                List.of(new ItemSolicitado(c.usuarios.recurso, quantidade)));
        return id;
    }

    static UUID criarRevisao(CenarioIntervencoesMysql c, int quantidade) {
        var id = criar(c, quantidade);
        var resultado = new ValidacaoRequisicaoJdbc(c.runtime).executarAutorizado(c.usuarios.autor, id).resultado();
        assertTrue(resultado == ResultadoValidacao.REVISAO_POR_RESERVA || resultado == ResultadoValidacao.REVISAO_POR_SALDO_INSUFICIENTE);
        return id;
    }

    static ResultadoIntervencaoRequisicao intervir(CenarioIntervencoesMysql c, UUID pedido, SolicitacaoIntervencaoRequisicao entrada) {
        return new IntervencaoRequisicaoJdbc(c.runtime).executarAutorizado(c.arquiteto, pedido, entrada);
    }

    static void ajustar(CenarioIntervencoesMysql c, String teto) {
        new AjusteOrcamentarioJdbc(c.runtime).executarAutorizado(c.arquiteto, c.usuarios.departamento,
                new SolicitacaoAjusteTeto(UUID.randomUUID(), teto, "Ajuste para o cenário manual"));
    }
}
