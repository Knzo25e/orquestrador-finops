package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.aplicacao.ListarIntervencoesUseCase;
import br.mackenzie.finops.aplicacao.excecoes.*;
import br.mackenzie.finops.aplicacao.entradas.ItemSolicitado;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

class AuditoriaMysqlIT {
    @Test
    void registraFotografiaEReproduzRespostaOriginalMesmoAposMudarSaldo() throws Exception {
        try (var c = new CenarioIntervencoesMysql()) {
            var comando = c.comando(UUID.randomUUID(), c.usuarios.departamento);
            var resultado = c.executar(comando);
            assertEquals("999.00", c.saldo(comando.departamentoId()));
            assertEquals(200, resultado.statusHttp());
            assertEquals("Justificativa do cenário", resultado.evento().justificativa());
            assertEquals("1000.00", resultado.evento().dados().get("saldoAnterior"));
            assertTrue(resultado.evento().instante().isAfter(Instant.now().minusSeconds(60)));
            assertThrows(UnsupportedOperationException.class, () -> resultado.evento().dados().put("saldo", "0"));
            try (var conexao = c.usuarios.abrir()) {
                CenarioUsuariosMysql.executar(conexao,
                        "UPDATE departamento SET saldo_disponivel = 800 WHERE id = ?", comando.departamentoId().toString());
            }
            var repetida = new IntervencaoAuditadaJdbc(c.runtime::abrir).executar(comando, conexao -> {
                fail("Repetição não pode reavaliar a decisão.");
                return null;
            });
            assertEquals(resultado, repetida);
            assertEquals("800.00", c.saldo(comando.departamentoId()));
            assertEquals(1, c.quantidade());
            var diferente = new ComandoIntervencao(comando.operacaoId(), c.arquiteto, comando.acao(),
                    comando.departamentoId(), null, comando.justificativa(), Map.of("valor", "2.00"));
            assertThrows(ConflitoOperacaoException.class, () -> c.executar(diferente));
            try (var conexao = c.usuarios.abrir()) {
                CenarioUsuariosMysql.executar(conexao, "UPDATE usuario SET ativo = FALSE WHERE id = ?", c.arquiteto.toString());
            }
            assertThrows(AcessoNegadoException.class, () -> c.executar(comando));
            assertEquals(1, c.quantidade());
        }
    }

    @Test
    void normalizaJustificativaEComparaParametrosPorConteudoSemDependerDaOrdem() throws Exception {
        try (var c = new CenarioIntervencoesMysql()) {
            var comando = new ComandoIntervencao(UUID.randomUUID(), c.arquiteto, "SEM_MUDANCA_TESTE",
                    c.usuarios.departamento, null, " Motivo ", Map.of("a", "1.00", "b", "2.00"));
            var gravado = new IntervencaoAuditadaJdbc(c.runtime::abrir).executar(comando,
                    conexao -> new IntervencaoAuditadaJdbc.Decisao(200, "SEM_MUDANCA", Map.of("saldo", "1000.00")));
            var parametros = new java.util.LinkedHashMap<String, String>();
            parametros.put("b", "2.00");
            parametros.put("a", "1.00");
            var repetida = new ComandoIntervencao(comando.operacaoId(), c.arquiteto, comando.acao(),
                    comando.departamentoId(), null, "Motivo", parametros);
            assertEquals(gravado, c.executar(repetida));
            for (var alterada : List.of(
                    new ComandoIntervencao(comando.operacaoId(), c.arquiteto, "OUTRA_ACAO", comando.departamentoId(), null, "Motivo", parametros),
                    new ComandoIntervencao(comando.operacaoId(), c.arquiteto, comando.acao(), comando.departamentoId(), null, "Outro motivo", parametros),
                    new ComandoIntervencao(comando.operacaoId(), c.arquiteto, comando.acao(), c.usuarios.outroDepartamento, null, "Motivo", parametros))) {
                assertThrows(ConflitoOperacaoException.class, () -> c.executar(alterada));
            }
            try (var conexao = c.usuarios.abrir()) {
                CenarioUsuariosMysql.executar(conexao, "UPDATE usuario SET perfil = 'ARQUITETO_CLOUD' WHERE id = ?",
                        c.usuarios.autor.toString());
            }
            var outroResponsavel = new ComandoIntervencao(comando.operacaoId(), c.usuarios.autor, comando.acao(),
                    comando.departamentoId(), null, "Motivo", parametros);
            assertThrows(ConflitoOperacaoException.class, () -> c.executar(outroResponsavel));
            assertEquals(1, c.quantidade());
        }
    }

    @Test
    void falhaDeInsercaoDesfazMutacaoFinanceiraEDeEstado() throws Exception {
        try (var c = new CenarioIntervencoesMysql()) {
            var pedido = UUID.randomUUID();
            new CriacaoRequisicaoJdbc(c.usuarios.fabrica).executarComIdentidade(c.usuarios.autor, pedido,
                    List.of(new ItemSolicitado(c.usuarios.recurso, 1)));
            var comando = new ComandoIntervencao(UUID.randomUUID(), c.arquiteto, "MUTACAO_TESTE",
                    c.usuarios.departamento, pedido, "Teste de rollback", Map.of());
            var coordenador = new IntervencaoAuditadaJdbc(() -> {
                var real = c.runtime.abrir();
                var controlada = mock(Connection.class, delegatesTo(real));
                doThrow(new SQLException("Falha simulada do registro.")).when(controlada)
                        .prepareStatement(startsWith("INSERT INTO intervencao"));
                return controlada;
            });
            assertThrows(FalhaPersistenciaException.class, () -> coordenador.executar(comando, conexao -> {
                var decisao = CenarioIntervencoesMysql.mutacao(conexao, comando.departamentoId());
                CenarioUsuariosMysql.executar(conexao,
                        "UPDATE requisicao SET estado = 'REVISAO_PENDENTE', motivo_revisao = 'RESERVA' WHERE id = ?",
                        pedido.toString());
                return decisao;
            }));
            assertEquals("1000.00", c.saldo(comando.departamentoId()));
            assertEquals(0, c.quantidade());
            try (var conexao = c.usuarios.abrir()) {
                assertEquals("INICIAL", new RequisicaoRepositoryJdbc(conexao).buscarPorId(pedido).orElseThrow().getStatus());
            }
        }
    }

    @Test
    void resultadoIncertoExigeReconciliacaoExplicitaInclusiveNegativaESemMudanca() throws Exception {
        for (boolean confirmado : List.of(false, true)) {
            for (int tipo = 0; tipo < 3; tipo++) {
                try (var c = new CenarioIntervencoesMysql()) {
                    var comando = c.comando(UUID.randomUUID(), c.usuarios.departamento);
                    var conexoes = new AtomicInteger();
                    var acoes = new AtomicInteger();
                    int decisao = tipo;
                    IntervencaoAuditadaJdbc.Acao acao = conexao -> {
                        acoes.incrementAndGet();
                        return switch (decisao) {
                            case 0 -> CenarioIntervencoesMysql.mutacao(conexao, comando.departamentoId());
                            case 1 -> new IntervencaoAuditadaJdbc.Decisao(409, "NEGADA_TESTE", Map.of("saldo", "1000.00"));
                            default -> new IntervencaoAuditadaJdbc.Decisao(200, "SEM_MUDANCA", Map.of("saldo", "1000.00"));
                        };
                    };
                    var incerto = new IntervencaoAuditadaJdbc(() -> {
                        conexoes.incrementAndGet();
                        var real = c.runtime.abrir();
                        var controlada = mock(Connection.class, delegatesTo(real));
                        doAnswer(chamada -> {
                            if (confirmado) {
                                real.commit();
                            }
                            throw new SQLException("Perda simulada de confirmação.");
                        }).when(controlada).commit();
                        return controlada;
                    });
                    var falha = assertThrows(ConfirmacaoIntervencaoDesconhecidaException.class,
                            () -> incerto.executar(comando, acao));
                    assertEquals(comando.operacaoId(), falha.getOperacaoId());
                    assertEquals(1, conexoes.get());
                    assertEquals(confirmado ? 1 : 0, c.quantidade());
                    var recuperado = new IntervencaoAuditadaJdbc(c.runtime::abrir).executar(comando, acao);
                    assertEquals(tipo == 1 ? 409 : 200, recuperado.statusHttp());
                    assertEquals(confirmado ? 1 : 2, acoes.get());
                    assertEquals(tipo == 0 ? "999.00" : "1000.00", c.saldo(comando.departamentoId()));
                    assertEquals(1, c.quantidade());
                }
            }
        }
    }

    @Test
    void colisaoEmDepartamentosDiferentesRevertePerdedorEReautoriza() throws Exception {
        try (var c = new CenarioIntervencoesMysql(); var executor = Executors.newFixedThreadPool(2)) {
            var id = UUID.randomUUID();
            var barreira = new CyclicBarrier(2);
            var conexoes = new AtomicInteger();
            var coordenador = new IntervencaoAuditadaJdbc(() -> {
                conexoes.incrementAndGet();
                return c.runtime.abrir();
            });
            var resultados = new ArrayList<java.util.concurrent.Future<Object>>();
            for (UUID departamento : List.of(c.usuarios.departamento, c.usuarios.outroDepartamento)) {
                resultados.add(executor.submit(() -> {
                    try {
                        return coordenador.executar(c.comando(id, departamento), conexao -> {
                            var decisao = CenarioIntervencoesMysql.mutacao(conexao, departamento);
                            try {
                                barreira.await(5, TimeUnit.SECONDS);
                            } catch (Exception falha) {
                                throw new IllegalStateException(falha);
                            }
                            return decisao;
                        });
                    } catch (ConflitoOperacaoException conflito) {
                        return conflito;
                    }
                }));
            }
            var primeiro = resultados.get(0).get(15, TimeUnit.SECONDS);
            var segundo = resultados.get(1).get(15, TimeUnit.SECONDS);
            assertTrue(primeiro instanceof ConflitoOperacaoException ^ segundo instanceof ConflitoOperacaoException);
            assertEquals(3, conexoes.get());
            assertEquals(1, c.quantidade());
            assertEquals(List.of("1000.00", "999.00"),
                    java.util.stream.Stream.of(c.saldo(c.usuarios.departamento), c.saldo(c.usuarios.outroDepartamento)).sorted().toList());
        }
    }

    @Test
    void usuarioInativoOuDesenvolvedorEAlvosAusentesNaoGeramHistorico() throws Exception {
        try (var c = new CenarioIntervencoesMysql()) {
            var base = c.comando(UUID.randomUUID(), c.usuarios.departamento);
            var dev = new ComandoIntervencao(base.operacaoId(), c.usuarios.autor, base.acao(),
                    base.departamentoId(), null, base.justificativa(), base.parametros());
            assertThrows(AcessoNegadoException.class, () -> c.executar(dev));
            assertThrows(DepartamentoNaoEncontradoException.class,
                    () -> c.executar(c.comando(UUID.randomUUID(), UUID.randomUUID())));
            var ausente = new ComandoIntervencao(base.operacaoId(), c.arquiteto, base.acao(),
                    base.departamentoId(), UUID.randomUUID(), base.justificativa(), base.parametros());
            assertThrows(RequisicaoNaoEncontradaException.class, () -> c.executar(ausente));
            assertEquals(0, c.quantidade());
        }
    }

    @Test
    void runtimeNaoPodeAlterarExcluirTruncarOuRemoverHistorico() throws Exception {
        try (var c = new CenarioIntervencoesMysql()) {
            var comando = c.comando(UUID.randomUUID(), c.usuarios.departamento);
            var original = c.executar(comando);
            try (var conexao = c.runtime.abrir()) {
                for (String sql : List.of("UPDATE intervencao SET resultado = 'ADULTERADO'",
                        "DELETE FROM intervencao", "TRUNCATE TABLE intervencao", "DROP TABLE intervencao",
                        "ALTER TABLE intervencao ADD adulterado INT")) {
                    var erro = assertThrows(SQLException.class, () -> {
                        try (var comandoSql = conexao.createStatement()) {
                            comandoSql.execute(sql);
                        }
                    });
                    assertEquals(1142, erro.getErrorCode());
                }
            }
            assertEquals(original, c.executar(comando));
            assertEquals(1, c.quantidade());
        }
    }

    @Test
    void fechamentoFalhoPreservaResultadoConfirmado() throws Exception {
        try (var c = new CenarioIntervencoesMysql()) {
            var comando = c.comando(UUID.randomUUID(), c.usuarios.departamento);
            var coordenador = new IntervencaoAuditadaJdbc(() -> {
                var real = c.runtime.abrir();
                var controlada = mock(Connection.class, delegatesTo(real));
                doAnswer(chamada -> {
                    real.close();
                    throw new SQLException("Falha simulada de fechamento.");
                }).when(controlada).close();
                return controlada;
            });
            var resultado = coordenador.executar(comando, conexao -> CenarioIntervencoesMysql.mutacao(conexao, comando.departamentoId()));
            assertTrue(resultado.falhaNoFechamento());
            assertEquals(resultado.evento(), c.executar(comando).evento());
            assertEquals("999.00", c.saldo(comando.departamentoId()));
        }
    }

    @Test
    void colisaoComRollbackOuFechamentoFalhoNaoAbreNovaTentativa() throws Exception {
        for (boolean falhaRollback : List.of(true, false)) {
            try (var c = new CenarioIntervencoesMysql()) {
                var conexoes = new AtomicInteger();
                var coordenador = new IntervencaoAuditadaJdbc(() -> {
                    conexoes.incrementAndGet();
                    var real = c.runtime.abrir();
                    var controlada = mock(Connection.class, delegatesTo(real));
                    doThrow(new SQLException("Colisão simulada.", "23000", 1062)).when(controlada)
                            .prepareStatement(startsWith("INSERT INTO intervencao"));
                    if (falhaRollback) {
                        doThrow(new SQLException("Rollback simulado.")).when(controlada).rollback();
                    } else {
                        doAnswer(chamada -> {
                            real.close();
                            throw new SQLException("Fechamento simulado.");
                        }).when(controlada).close();
                    }
                    return controlada;
                });
                var comando = c.comando(UUID.randomUUID(), c.usuarios.departamento);
                assertThrows(FalhaPersistenciaException.class, () -> coordenador.executar(comando,
                        conexao -> CenarioIntervencoesMysql.mutacao(conexao, comando.departamentoId())));
                assertEquals(1, conexoes.get());
                assertEquals(0, c.quantidade());
                assertEquals("1000.00", c.saldo(comando.departamentoId()));
            }
        }
    }

    @Test
    void revogacaoEntreColisaoEReconciliacaoImpedeRecuperarRecibo() throws Exception {
        try (var c = new CenarioIntervencoesMysql()) {
            var conexoes = new AtomicInteger();
            var coordenador = new IntervencaoAuditadaJdbc(() -> {
                if (conexoes.incrementAndGet() > 1) {
                    return c.runtime.abrir();
                }
                var real = c.runtime.abrir();
                var controlada = mock(Connection.class, delegatesTo(real));
                doThrow(new SQLException("Colisão simulada.", "23000", 1062)).when(controlada)
                        .prepareStatement(startsWith("INSERT INTO intervencao"));
                doAnswer(chamada -> {
                    real.rollback();
                    CriacaoIdentidadeMysqlIT.executarEmOutroProcesso("desativar-desenvolvedor", c.arquiteto);
                    return null;
                }).when(controlada).rollback();
                return controlada;
            });
            var comando = c.comando(UUID.randomUUID(), c.usuarios.departamento);
            assertThrows(AcessoNegadoException.class, () -> coordenador.executar(comando,
                    conexao -> CenarioIntervencoesMysql.mutacao(conexao, comando.departamentoId())));
            assertEquals(2, conexoes.get());
            assertEquals(0, c.quantidade());
            assertEquals("1000.00", c.saldo(comando.departamentoId()));
        }
    }

    @Test
    void falhaDeRollbackDaConsultaPreservaNegativaDeAutorizacao() throws Exception {
        try (var c = new CenarioIntervencoesMysql()) {
            var consulta = new ConsultaIntervencoesJdbc(() -> {
                var real = c.runtime.abrir();
                var controlada = mock(Connection.class, delegatesTo(real));
                doThrow(new SQLException("Rollback simulado.")).when(controlada).rollback();
                return controlada;
            });
            var negativa = assertThrows(AcessoNegadoException.class,
                    () -> consulta.listarAutorizado(c.usuarios.autor, c.usuarios.departamento, null, null, 25));
            assertEquals(1, negativa.getSuppressed().length);
        }
    }

    @Test
    void paginaPorInstanteEUuidEAplicaFiltroDeRequisicaoAntesDoLimite() throws Exception {
        try (var c = new CenarioIntervencoesMysql()) {
            var pedido = UUID.randomUUID();
            new CriacaoRequisicaoJdbc(c.usuarios.fabrica).executarComIdentidade(c.usuarios.autor, pedido,
                    List.of(new ItemSolicitado(c.usuarios.recurso, 1)));
            var ids = new ArrayList<UUID>();
            for (int i = 1; i <= 6; i++) {
                UUID id = UUID.fromString("00000000-0000-4000-8000-%012d".formatted(i));
                ids.add(id);
                var comando = new ComandoIntervencao(id, c.arquiteto, "SEM_MUDANCA_TESTE",
                        i == 6 ? c.usuarios.outroDepartamento : c.usuarios.departamento,
                        i % 2 == 0 && i < 6 ? pedido : null, "Cenário paginado", Map.of());
                new IntervencaoAuditadaJdbc(c.runtime::abrir).executar(comando,
                        conexao -> new IntervencaoAuditadaJdbc.Decisao(200, "SEM_MUDANCA", Map.of()));
            }
            // A conta administrativa prepara empates temporais determinísticos na base de teste.
            try (var conexao = c.usuarios.abrir()) {
                CenarioUsuariosMysql.executar(conexao,
                        "UPDATE intervencao SET instante = '2026-11-07 12:34:56.123456' WHERE departamento_id IN (?, ?)",
                        c.usuarios.departamento.toString(), c.usuarios.outroDepartamento.toString());
            }
            var useCase = new ListarIntervencoesUseCase(() -> Optional.of(c.arquiteto), new ConsultaIntervencoesJdbc(c.runtime));
            var pagina = useCase.executar(c.usuarios.departamento, null, null, 2);
            assertEquals(List.of(ids.get(4), ids.get(3)), pagina.intervencoes().stream().map(e -> e.operacaoId()).toList());
            assertEquals(Instant.parse("2026-11-07T12:34:56.123456Z"), pagina.intervencoes().getFirst().instante());
            var segunda = useCase.executar(c.usuarios.departamento, null, pagina.proximoCursor(), 2);
            assertEquals(List.of(ids.get(2), ids.get(1)), segunda.intervencoes().stream().map(e -> e.operacaoId()).toList());
            var ultima = useCase.executar(c.usuarios.departamento, null, segunda.proximoCursor(), 2);
            assertEquals(List.of(ids.get(0)), ultima.intervencoes().stream().map(e -> e.operacaoId()).toList());
            assertNull(ultima.proximoCursor());
            var filtrada = useCase.executar(c.usuarios.departamento, pedido, null, 1);
            assertEquals(ids.get(3), filtrada.intervencoes().getFirst().operacaoId());
            var fim = useCase.executar(c.usuarios.departamento, pedido, filtrada.proximoCursor(), 1);
            assertEquals(ids.get(1), fim.intervencoes().getFirst().operacaoId());
            assertNull(fim.proximoCursor());
            assertTrue(useCase.executar(c.usuarios.departamento, UUID.randomUUID(), null, 25).intervencoes().isEmpty());
            assertThrows(EntradaRequisicaoInvalidaException.class, () -> useCase.executar(c.usuarios.departamento, null, "invalido", 25));
            assertThrows(EntradaRequisicaoInvalidaException.class, () -> useCase.executar(c.usuarios.departamento, null, null, 101));
            try (var conexao = c.usuarios.abrir()) {
                CenarioUsuariosMysql.executar(conexao, "UPDATE usuario SET perfil = 'DESENVOLVEDOR' WHERE id = ?", c.arquiteto.toString());
            }
            assertThrows(AcessoNegadoException.class, () -> useCase.executar(c.usuarios.departamento, null, pagina.proximoCursor(), 2));
        }
    }

    @Test
    void bloqueiaIdentidadeDepartamentoERequisicaoERepeteEmOutraJvmSemNovoEfeito() throws Exception {
        try (var c = new CenarioIntervencoesMysql(); var executor = Executors.newSingleThreadExecutor()) {
            var pedido = UUID.randomUUID();
            new CriacaoRequisicaoJdbc(c.usuarios.fabrica).executarComIdentidade(c.usuarios.autor, pedido,
                    List.of(new ItemSolicitado(c.usuarios.recurso, 1)));
            var comando = new ComandoIntervencao(UUID.randomUUID(), c.arquiteto, "MUTACAO_TESTE",
                    c.usuarios.departamento, pedido, "Teste multiprocesso", Map.of());
            var pausa = new PausaCommitTesteJdbc();
            var coordenador = new IntervencaoAuditadaJdbc(() -> pausa.envolver(c.runtime.abrir()));
            var primeira = executor.submit(() -> coordenador.executar(comando,
                    conexao -> CenarioIntervencoesMysql.mutacao(conexao, comando.departamentoId())));
            Process processo = null;
            try {
                pausa.aguardarChegadaAoCommit();
                CriacaoIdentidadeMysqlIT.executarEmOutroProcesso("sondar", c.arquiteto, comando.departamentoId(), pedido);
                processo = new ProcessBuilder(
                        java.nio.file.Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                        "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                        RepeticaoExterna.class.getName(), comando.operacaoId().toString(), c.arquiteto.toString(),
                        comando.departamentoId().toString(), pedido.toString()).redirectErrorStream(true).start();
                var leitor = processo.inputReader();
                assertEquals("PRONTO", leitor.readLine());
                assertTrue(processo.isAlive());
                pausa.liberarCommit();
                assertEquals(200, primeira.get(10, TimeUnit.SECONDS).statusHttp());
                assertTrue(processo.waitFor(10, TimeUnit.SECONDS));
                assertEquals(0, processo.exitValue(), leitor.lines().collect(java.util.stream.Collectors.joining("\n")));
            } finally {
                pausa.liberarCommit();
                if (processo != null && processo.isAlive()) {
                    processo.destroyForcibly();
                }
            }
            assertEquals(1, c.quantidade());
            assertEquals("999.00", c.saldo(comando.departamentoId()));
            CriacaoIdentidadeMysqlIT.executarEmOutroProcesso("desativar-desenvolvedor", c.arquiteto);
            assertThrows(AcessoNegadoException.class, () -> c.executar(comando));
        }
    }

    public static class RepeticaoExterna {
        public static void main(String[] args) throws Exception {
            var fabrica = new FabricaConexoes(new ConfiguracaoBanco(System.getenv("FINOPS_DB_URL"),
                    System.getenv("FINOPS_RUNTIME_DB_USER"), System.getenv("FINOPS_RUNTIME_DB_PASSWORD")));
            try (var conexao = fabrica.abrir()) {
                if (!"finops_test".equals(conexao.getCatalog())) {
                    throw new IllegalStateException("Exige finops_test.");
                }
            }
            System.out.println("PRONTO");
            var comando = new ComandoIntervencao(UUID.fromString(args[0]), UUID.fromString(args[1]), "MUTACAO_TESTE",
                    UUID.fromString(args[2]), UUID.fromString(args[3]), "Teste multiprocesso", Map.of());
            var resultado = new IntervencaoAuditadaJdbc(fabrica::abrir).executar(comando, conexao -> {
                throw new IllegalStateException("A repetição não pode executar a mutação.");
            });
            if (resultado.statusHttp() != 200 || !"999.00".equals(resultado.evento().dados().get("saldoPosterior"))) {
                throw new IllegalStateException("Resposta original não recuperada.");
            }
        }
    }
}
