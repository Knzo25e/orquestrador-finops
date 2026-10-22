package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.aplicacao.CriarRequisicaoPersistenteUseCase;
import br.mackenzie.finops.aplicacao.entradas.ItemSolicitado;
import br.mackenzie.finops.aplicacao.excecoes.ConflitoIdentificadorException;
import br.mackenzie.finops.aplicacao.resultados.ResultadoCriacaoRequisicao;
import br.mackenzie.finops.dominio.entidades.Departamento;
import br.mackenzie.finops.dominio.entidades.RecursoCatalogo;
import br.mackenzie.finops.dominio.entidades.Requisicao;
import br.mackenzie.finops.aplicacao.excecoes.ConfirmacaoCriacaoDesconhecidaException;
import br.mackenzie.finops.aplicacao.excecoes.FalhaCriacaoPersistenteException;
import br.mackenzie.finops.dominio.entidades.ResultadoValidacao;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class VerificarCriacaoTransacionalJdbc {

    private static int verificacoes;

    public static void main(String[] args) {
        try {
            executar();
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

    private static void executar() throws SQLException {
        FabricaConexoes fabrica = new FabricaConexoes(
                ConfiguracaoBanco.carregarDoAmbiente()
        );

        UUID departamentoId = UUID.randomUUID();
        UUID recursoId = UUID.randomUUID();
        UUID requisicaoId = UUID.randomUUID();

        System.out.println("Departamento do teste: " + departamentoId);
        System.out.println("Recurso do teste: " + recursoId);
        System.out.println("Requisicao do teste: " + requisicaoId);

        // Toda conexão utilizada pelo caso de uso é conferida.
        CriacaoRequisicaoJdbc criacao = new CriacaoRequisicaoJdbc(
                () -> abrirConexaoTeste(fabrica)
        );

        CriarRequisicaoPersistenteUseCase casoDeUso =
                new CriarRequisicaoPersistenteUseCase(criacao);

        List<ItemSolicitado> itens = List.of(
                new ItemSolicitado(recursoId, 2),
                new ItemSolicitado(recursoId, 3)
        );

        try {
            prepararCadastros(fabrica, departamentoId, recursoId);

            ResultadoCriacaoRequisicao primeira = casoDeUso.executar(
                    requisicaoId, departamentoId, itens
            );

            conferir(
                    primeira.situacao()
                            == ResultadoCriacaoRequisicao.Situacao.CRIADA
                            && !primeira.falhaNoFechamento(),
                    "Primeira execucao retorna CRIADA sem falha de fechamento"
            );

            conferir(
                    primeira.requisicao().getId().equals(requisicaoId)
                            && "INICIAL".equals(
                                    primeira.requisicao().getStatus()
                            )
                            && primeira.requisicao().getMotivoRevisao() == null
                            && igual(
                                    primeira.requisicao().getCustoTotalProjetado(),
                                    "62.50"
                            ),
                    "Criacao preserva UUID, estado inicial e total"
            );

            try (Connection conexao = abrirConexaoTeste(fabrica)) {
                conexao.setTransactionIsolation(
                        Connection.TRANSACTION_READ_COMMITTED
                );

                Requisicao armazenada =
                        new RequisicaoRepositoryJdbc(conexao)
                                .buscarPorId(requisicaoId).orElseThrow();

                conferir(
                        armazenada.getItens().size() == 2
                                && armazenada.getItens().get(0)
                                        .getQuantidade() == 2
                                && armazenada.getItens().get(1)
                                        .getQuantidade() == 3
                                && igual(
                                        armazenada.getCustoTotalProjetado(),
                                        "62.50"
                                ),
                        "Outra conexao recupera pedido completo apos commit"
                );

                conferir(
                        igual(
                                armazenada.getDepartamento()
                                        .getSaldoDisponivel(),
                                "800.00"
                        ),
                        "Criacao preserva saldo do departamento"
                );
            }

            ResultadoCriacaoRequisicao repeticao = casoDeUso.executar(
                    requisicaoId, departamentoId, itens
            );

            conferir(
                    repeticao.situacao()
                            == ResultadoCriacaoRequisicao.Situacao.JA_EXISTENTE
                            && !repeticao.falhaNoFechamento()
                            && repeticao.requisicao().getId()
                                    .equals(requisicaoId),
                    "Repeticao equivalente retorna JA_EXISTENTE"
            );

            alterarPreco(fabrica, recursoId);

            ResultadoCriacaoRequisicao aposAlteracao =
                    casoDeUso.executar(
                            requisicaoId, departamentoId, itens
                    );

            conferir(
                    aposAlteracao.situacao()
                            == ResultadoCriacaoRequisicao.Situacao.JA_EXISTENTE
                            && aposAlteracao.requisicao().getItens().stream()
                                    .allMatch(item ->
                                            igual(item.getPrecoUnitario(), "12.50")
                                                    && igual(
                                                            item.getRecurso()
                                                                    .getCustoMensal(),
                                                            "99.00"
                                                    )
                                    )
                            && igual(
                                    aposAlteracao.requisicao()
                                            .getCustoTotalProjetado(),
                                    "62.50"
                            ),
                    "Repeticao preserva precos historicos apos mudar catalogo"
            );

            boolean conflito = false;

            try {
                casoDeUso.executar(
                        requisicaoId,
                        departamentoId,
                        List.of(new ItemSolicitado(recursoId, 1))
                );
            } catch (ConflitoIdentificadorException esperado) {
                conflito = requisicaoId.equals(
                        esperado.getRequisicaoId()
                );
            }

            conferir(
                    conflito,
                    "Mesmo UUID com conteudo diferente informa conflito"
            );

            try (Connection conexao = abrirConexaoTeste(fabrica)) {
                conexao.setTransactionIsolation(
                        Connection.TRANSACTION_READ_COMMITTED
                );

                List<Requisicao> pedidos =
                        new RequisicaoRepositoryJdbc(conexao)
                                .listarPorDepartamento(departamentoId);

                conferir(
                        pedidos.size() == 1
                                && pedidos.get(0).getId().equals(requisicaoId)
                                && pedidos.get(0).getItens().size() == 2
                                && igual(
                                        pedidos.get(0).getCustoTotalProjetado(),
                                        "62.50"
                                )
                                && igual(
                                        pedidos.get(0).getDepartamento()
                                                .getSaldoDisponivel(),
                                        "800.00"
                                ),
                        "Repeticoes e conflito preservam unico pedido e saldo"
                );
            }
        } finally {
            limpar(fabrica, departamentoId, recursoId, requisicaoId);
        }

        try (Connection conexao = abrirConexaoTeste(fabrica)) {
            conexao.setTransactionIsolation(
                    Connection.TRANSACTION_READ_COMMITTED
            );

            conferir(
                    new RequisicaoRepositoryJdbc(conexao)
                            .buscarPorId(requisicaoId).isEmpty()
                            && new DepartamentoRepositoryJdbc(conexao)
                                    .buscarPorId(departamentoId).isEmpty()
                            && new RecursoCatalogoRepositoryJdbc(conexao)
                                    .buscarPorId(recursoId).isEmpty(),
                    "Outra conexao confirma limpeza dos registros do teste"
            );
        }

    verificarFalhaNaConfirmacao(fabrica, false);
    verificarFalhaNaConfirmacao(fabrica, true);
    verificarFalhaNoFechamento(fabrica);
    verificarRollbackDaCriacao(fabrica);
    verificarCriacaoConcorrente(fabrica);
    verificarConflitosDeConteudo(fabrica);
    verificarRepeticaoAposProcessamento(fabrica);
    verificarVinculosInexistentes(fabrica);

        System.out.println(
                "Todas as " + verificacoes + " verificacoes passaram."
        );
    }

        private static void verificarFalhaNaConfirmacao(
            FabricaConexoes fabrica,
            boolean efetivarCommit
    ) throws SQLException {
        UUID departamentoId = UUID.randomUUID();
        UUID recursoId = UUID.randomUUID();
        UUID requisicaoId = UUID.randomUUID();
        boolean[] falhaInjetada = {false};

        List<ItemSolicitado> itens = List.of(
                new ItemSolicitado(recursoId, 2),
                new ItemSolicitado(recursoId, 3)
        );

        String cenario = efetivarCommit
                ? "Commit efetivado com resposta perdida"
                : "Falha antes de efetivar commit";

        try {
            prepararCadastros(fabrica, departamentoId, recursoId);

            CriarRequisicaoPersistenteUseCase comFalha =
                    new CriarRequisicaoPersistenteUseCase(
                            new CriacaoRequisicaoJdbc(
                                    () -> simularFalha(
                                            abrirConexaoTeste(fabrica),
                                            "commit",
                                            efetivarCommit,
                                            falhaInjetada
                                    )
                            )
                    );

            boolean desconhecida = false;

            try {
                comFalha.executar(requisicaoId, departamentoId, itens);
            } catch (ConfirmacaoCriacaoDesconhecidaException esperado) {
                desconhecida = requisicaoId.equals(
                        esperado.getRequisicaoId()
                );
            }

            conferir(
                    falhaInjetada[0] && desconhecida,
                    cenario + ": informa confirmacao desconhecida com UUID"
            );

            try (Connection conexao = abrirConexaoTeste(fabrica)) {
                conexao.setTransactionIsolation(
                        Connection.TRANSACTION_READ_COMMITTED
                );

                conferir(
                        new RequisicaoRepositoryJdbc(conexao)
                                .buscarPorId(requisicaoId).isPresent()
                                == efetivarCommit,
                        cenario + ": banco apresenta o resultado esperado"
                );
            }

            CriarRequisicaoPersistenteUseCase normal =
                    new CriarRequisicaoPersistenteUseCase(
                            new CriacaoRequisicaoJdbc(
                                    () -> abrirConexaoTeste(fabrica)
                            )
                    );

            ResultadoCriacaoRequisicao reconciliada = normal.executar(
                    requisicaoId, departamentoId, itens
            );

            ResultadoCriacaoRequisicao.Situacao situacaoEsperada =
                    efetivarCommit
                            ? ResultadoCriacaoRequisicao.Situacao.JA_EXISTENTE
                            : ResultadoCriacaoRequisicao.Situacao.CRIADA;

            conferir(
                    reconciliada.situacao() == situacaoEsperada
                            && reconciliada.requisicao().getId()
                                    .equals(requisicaoId)
                            && !reconciliada.falhaNoFechamento(),
                    cenario + ": reconciliacao utiliza o UUID original"
            );

            conferirPedidoUnico(
                    fabrica, departamentoId, requisicaoId, cenario
            );
        } finally {
            limpar(fabrica, departamentoId, recursoId, requisicaoId);
        }
    }

    private static void verificarFalhaNoFechamento(
            FabricaConexoes fabrica
    ) throws SQLException {
        UUID departamentoId = UUID.randomUUID();
        UUID recursoId = UUID.randomUUID();
        UUID requisicaoId = UUID.randomUUID();
        boolean[] falhaInjetada = {false};

        List<ItemSolicitado> itens = List.of(
                new ItemSolicitado(recursoId, 2),
                new ItemSolicitado(recursoId, 3)
        );

        try {
            prepararCadastros(fabrica, departamentoId, recursoId);

            CriarRequisicaoPersistenteUseCase comFalha =
                    new CriarRequisicaoPersistenteUseCase(
                            new CriacaoRequisicaoJdbc(
                                    () -> simularFalha(
                                            abrirConexaoTeste(fabrica),
                                            "close",
                                            true,
                                            falhaInjetada
                                    )
                            )
                    );

            ResultadoCriacaoRequisicao resultado = comFalha.executar(
                    requisicaoId, departamentoId, itens
            );

            conferir(
                    falhaInjetada[0]
                            && resultado.situacao()
                                    == ResultadoCriacaoRequisicao.Situacao.CRIADA
                            && resultado.falhaNoFechamento(),
                    "Falha de fechamento preserva CRIADA com aviso"
            );

            conferirPedidoUnico(
                    fabrica, departamentoId, requisicaoId,
                    "Falha de fechamento"
            );

            CriarRequisicaoPersistenteUseCase normal =
                    new CriarRequisicaoPersistenteUseCase(
                            new CriacaoRequisicaoJdbc(
                                    () -> abrirConexaoTeste(fabrica)
                            )
                    );

            ResultadoCriacaoRequisicao repeticao = normal.executar(
                    requisicaoId, departamentoId, itens
            );

            conferir(
                    repeticao.situacao()
                            == ResultadoCriacaoRequisicao.Situacao.JA_EXISTENTE
                            && !repeticao.falhaNoFechamento(),
                    "Nova tentativa reconhece pedido confirmado antes do aviso"
            );
        } finally {
            limpar(fabrica, departamentoId, recursoId, requisicaoId);
        }
    }

    private static Connection simularFalha(
            Connection real,
            String metodoAlvo,
            boolean executarAntesDeFalhar,
            boolean[] falhaInjetada
    ) {
        return (Connection) Proxy.newProxyInstance(
                VerificarCriacaoTransacionalJdbc.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, metodo, argumentos) -> {
                    boolean interceptar = metodoAlvo.equals(
                            metodo.getName()
                    );

                    if (interceptar && !executarAntesDeFalhar) {
                        falhaInjetada[0] = true;
                        throw new SQLException(
                                "Falha simulada antes da operacao.",
                                "08006"
                        );
                    }

                    Object retorno;

                    try {
                        retorno = metodo.invoke(real, argumentos);
                    } catch (InvocationTargetException excecao) {
                        throw excecao.getCause();
                    }

                    if (interceptar) {
                        falhaInjetada[0] = true;
                        throw new SQLException(
                                "Falha simulada apos a operacao.",
                                "08006"
                        );
                    }

                    return retorno;
                }
        );
    }

    private static void conferirPedidoUnico(
            FabricaConexoes fabrica,
            UUID departamentoId,
            UUID requisicaoId,
            String cenario
    ) throws SQLException {
        try (Connection conexao = abrirConexaoTeste(fabrica)) {
            conexao.setTransactionIsolation(
                    Connection.TRANSACTION_READ_COMMITTED
            );

            List<Requisicao> pedidos =
                    new RequisicaoRepositoryJdbc(conexao)
                            .listarPorDepartamento(departamentoId);

            conferir(
                    pedidos.size() == 1
                            && pedidos.get(0).getId().equals(requisicaoId)
                            && pedidos.get(0).getItens().size() == 2
                            && igual(
                                    pedidos.get(0).getCustoTotalProjetado(),
                                    "62.50"
                            )
                            && igual(
                                    pedidos.get(0).getDepartamento()
                                            .getSaldoDisponivel(),
                                    "800.00"
                            ),
                    cenario + ": unico pedido completo e saldo preservado"
            );
        }
    }

        private static void verificarRollbackDaCriacao(
            FabricaConexoes fabrica
    ) throws SQLException {
        UUID departamentoId = UUID.randomUUID();
        UUID recursoId = UUID.randomUUID();
        UUID requisicaoId = UUID.randomUUID();

        boolean[] falhaInjetada = {false};
        boolean[] gravacaoParcialConfirmada = {false};

        List<ItemSolicitado> itens = List.of(
                new ItemSolicitado(recursoId, 2),
                new ItemSolicitado(recursoId, 3)
        );

        try {
            prepararCadastros(fabrica, departamentoId, recursoId);

            CriarRequisicaoPersistenteUseCase comFalha =
                    new CriarRequisicaoPersistenteUseCase(
                            new CriacaoRequisicaoJdbc(
                                    () -> falharNoSegundoItem(
                                            abrirConexaoTeste(fabrica),
                                            requisicaoId,
                                            falhaInjetada,
                                            gravacaoParcialConfirmada
                                    )
                            )
                    );

            boolean falhaInformada = false;

            try {
                comFalha.executar(requisicaoId, departamentoId, itens);
            } catch (FalhaCriacaoPersistenteException esperado) {
                falhaInformada = true;
            }

            conferir(
                    falhaInjetada[0] && falhaInformada,
                    "Falha no segundo item informa falha de criacao"
            );

            conferir(
                    gravacaoParcialConfirmada[0],
                    "Antes da falha existem cabecalho e primeiro item"
            );

            try (Connection conexao = abrirConexaoTeste(fabrica)) {
                conferir(
                        contarRegistros(
                                conexao,
                                "SELECT COUNT(*) FROM requisicao WHERE id = ?",
                                requisicaoId
                        ) == 0
                                && contarRegistros(
                                        conexao,
                                        "SELECT COUNT(*) FROM item_requisicao "
                                                + "WHERE requisicao_id = ?",
                                        requisicaoId
                                ) == 0
                                && igual(
                                        new DepartamentoRepositoryJdbc(conexao)
                                                .buscarPorId(departamentoId)
                                                .orElseThrow()
                                                .getSaldoDisponivel(),
                                        "800.00"
                                ),
                        "Caso de uso desfaz gravacao parcial e preserva saldo"
                );
            }

            CriarRequisicaoPersistenteUseCase normal =
                    new CriarRequisicaoPersistenteUseCase(
                            new CriacaoRequisicaoJdbc(
                                    () -> abrirConexaoTeste(fabrica)
                            )
                    );

            ResultadoCriacaoRequisicao resultado = normal.executar(
                    requisicaoId, departamentoId, itens
            );

            conferir(
                    resultado.situacao()
                            == ResultadoCriacaoRequisicao.Situacao.CRIADA
                            && resultado.requisicao().getId()
                                    .equals(requisicaoId),
                    "Nova tentativa cria pedido com o UUID original"
            );

            conferirPedidoUnico(
                    fabrica, departamentoId, requisicaoId,
                    "Recuperacao apos rollback"
            );
        } finally {
            limpar(fabrica, departamentoId, recursoId, requisicaoId);
        }
    }

    private static Connection falharNoSegundoItem(
            Connection real,
            UUID requisicaoId,
            boolean[] falhaInjetada,
            boolean[] gravacaoParcialConfirmada
    ) {
        int[] insercoes = {0};

        return (Connection) Proxy.newProxyInstance(
                VerificarCriacaoTransacionalJdbc.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, metodo, argumentos) -> {
                    Object retorno;

                    try {
                        retorno = metodo.invoke(real, argumentos);
                    } catch (InvocationTargetException excecao) {
                        throw excecao.getCause();
                    }

                    if ("prepareStatement".equals(metodo.getName())
                            && argumentos != null
                            && argumentos.length > 0
                            && argumentos[0] instanceof String sql
                            && sql.stripLeading()
                                    .toUpperCase(java.util.Locale.ROOT)
                                    .startsWith("INSERT INTO ITEM_REQUISICAO")) {
                        PreparedStatement comando =
                                (PreparedStatement) retorno;

                        return Proxy.newProxyInstance(
                                VerificarCriacaoTransacionalJdbc.class
                                        .getClassLoader(),
                                new Class<?>[]{PreparedStatement.class},
                                (proxyComando, metodoComando, argumentosComando) -> {
                                    if ("executeUpdate".equals(
                                            metodoComando.getName()
                                    ) && ++insercoes[0] == 2) {
                                        gravacaoParcialConfirmada[0] =
                                                contarRegistros(
                                                        real,
                                                        "SELECT COUNT(*) FROM requisicao "
                                                                + "WHERE id = ?",
                                                        requisicaoId
                                                ) == 1
                                                        && contarRegistros(
                                                                real,
                                                                "SELECT COUNT(*) FROM item_requisicao "
                                                                        + "WHERE requisicao_id = ? "
                                                                        + "AND ordem = 1",
                                                                requisicaoId
                                                        ) == 1;

                                        falhaInjetada[0] = true;

                                        throw new SQLException(
                                                "Falha simulada no segundo item.",
                                                "HY000"
                                        );
                                    }

                                    try {
                                        return metodoComando.invoke(
                                                comando, argumentosComando
                                        );
                                    } catch (InvocationTargetException excecao) {
                                        throw excecao.getCause();
                                    }
                                }
                        );
                    }

                    return retorno;
                }
        );
    }

    private static long contarRegistros(
            Connection conexao,
            String sql,
            UUID id
    ) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, id.toString());

            try (ResultSet resultado = comando.executeQuery()) {
                if (!resultado.next()) {
                    throw new IllegalStateException(
                            "A contagem nao retornou resultado."
                    );
                }

                return resultado.getLong(1);
            }
        }
    }

        private static void verificarCriacaoConcorrente(
            FabricaConexoes fabrica
    ) throws SQLException {
        UUID departamentoId = UUID.randomUUID();
        UUID recursoId = UUID.randomUUID();
        UUID requisicaoId = UUID.randomUUID();

        List<ItemSolicitado> itens = List.of(
                new ItemSolicitado(recursoId, 2),
                new ItemSolicitado(recursoId, 3)
        );

        CyclicBarrier barreira = new CyclicBarrier(2);
        AtomicInteger conexoesAbertas = new AtomicInteger();
        AtomicInteger tentativasInsercao = new AtomicInteger();

        try {
            prepararCadastros(fabrica, departamentoId, recursoId);

            CriarRequisicaoPersistenteUseCase casoDeUso =
                    new CriarRequisicaoPersistenteUseCase(
                            new CriacaoRequisicaoJdbc(() -> {
                                Connection conexao = abrirConexaoTeste(fabrica);
                                conexoesAbertas.incrementAndGet();

                                return sincronizarInsercao(
                                        conexao,
                                        barreira,
                                        tentativasInsercao
                                );
                            })
                    );

            ResultadoCriacaoRequisicao primeiro;
            ResultadoCriacaoRequisicao segundo;

            try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
                Future<ResultadoCriacaoRequisicao> tarefaA =
                        executor.submit(() -> casoDeUso.executar(
                                requisicaoId, departamentoId, itens
                        ));

                Future<ResultadoCriacaoRequisicao> tarefaB =
                        executor.submit(() -> casoDeUso.executar(
                                requisicaoId, departamentoId, itens
                        ));

                try {
                    primeiro = tarefaA.get(45, TimeUnit.SECONDS);
                    segundo = tarefaB.get(45, TimeUnit.SECONDS);
                } catch (InterruptedException excecao) {
                    tarefaA.cancel(true);
                    tarefaB.cancel(true);
                    Thread.currentThread().interrupt();

                    throw new IllegalStateException(
                            "Teste concorrente interrompido."
                    );
                } catch (java.util.concurrent.ExecutionException
                         | java.util.concurrent.TimeoutException excecao) {
                    tarefaA.cancel(true);
                    tarefaB.cancel(true);

                    throw new IllegalStateException(
                            "Teste concorrente falhou ou excedeu o tempo limite."
                    );
                }
            }

            long criadas = List.of(primeiro, segundo).stream()
                    .filter(resultado ->
                            resultado.situacao()
                                    == ResultadoCriacaoRequisicao.Situacao.CRIADA
                    )
                    .count();

            long existentes = List.of(primeiro, segundo).stream()
                    .filter(resultado ->
                            resultado.situacao()
                                    == ResultadoCriacaoRequisicao.Situacao.JA_EXISTENTE
                    )
                    .count();

            conferir(
                    criadas == 1 && existentes == 1,
                    "Concorrencia retorna uma CRIADA e uma JA_EXISTENTE"
            );

            conferir(
                    tentativasInsercao.get() == 2
                            && conexoesAbertas.get() == 3,
                    "Disputa executa duas insercoes e abre nova conexao para conferir"
            );

            conferir(
                    primeiro.requisicao().getId().equals(requisicaoId)
                            && segundo.requisicao().getId().equals(requisicaoId)
                            && !primeiro.falhaNoFechamento()
                            && !segundo.falhaNoFechamento()
                            && igual(
                                    primeiro.requisicao().getCustoTotalProjetado(),
                                    "62.50"
                            )
                            && igual(
                                    segundo.requisicao().getCustoTotalProjetado(),
                                    "62.50"
                            ),
                    "Tentativas concorrentes retornam o mesmo pedido e total"
            );

            conferirPedidoUnico(
                    fabrica, departamentoId, requisicaoId,
                    "Criacao concorrente"
            );
        } finally {
            limpar(fabrica, departamentoId, recursoId, requisicaoId);
        }
    }

    private static Connection sincronizarInsercao(
            Connection real,
            CyclicBarrier barreira,
            AtomicInteger tentativasInsercao
    ) {
        return (Connection) Proxy.newProxyInstance(
                VerificarCriacaoTransacionalJdbc.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, metodo, argumentos) -> {
                    Object retorno;

                    try {
                        retorno = metodo.invoke(real, argumentos);
                    } catch (InvocationTargetException excecao) {
                        throw excecao.getCause();
                    }

                    if ("prepareStatement".equals(metodo.getName())
                            && argumentos != null
                            && argumentos.length > 0
                            && argumentos[0] instanceof String sql
                            && sql.stripLeading()
                                    .toUpperCase(java.util.Locale.ROOT)
                                    .startsWith("INSERT INTO REQUISICAO (")) {
                        PreparedStatement comando =
                                (PreparedStatement) retorno;

                        return Proxy.newProxyInstance(
                                VerificarCriacaoTransacionalJdbc.class
                                        .getClassLoader(),
                                new Class<?>[]{PreparedStatement.class},
                                (proxyComando, metodoComando, argumentosComando) -> {
                                    if ("executeUpdate".equals(
                                            metodoComando.getName()
                                    )) {
                                        tentativasInsercao.incrementAndGet();

                                        try {
                                            barreira.await(15, TimeUnit.SECONDS);
                                        } catch (InterruptedException excecao) {
                                            Thread.currentThread().interrupt();
                                            throw new SQLException(
                                                    "Sincronizacao interrompida.",
                                                    "HY000"
                                            );
                                        } catch (
                                                java.util.concurrent.BrokenBarrierException
                                                | java.util.concurrent.TimeoutException excecao
                                        ) {
                                            throw new SQLException(
                                                    "Falha na sincronizacao do teste.",
                                                    "HY000"
                                            );
                                        }
                                    }

                                    try {
                                        return metodoComando.invoke(
                                                comando, argumentosComando
                                        );
                                    } catch (InvocationTargetException excecao) {
                                        throw excecao.getCause();
                                    }
                                }
                        );
                    }

                    return retorno;
                }
        );
    }

        private static void verificarConflitosDeConteudo(
            FabricaConexoes fabrica
    ) throws SQLException {
        UUID departamentoId = UUID.randomUUID();
        UUID recursoId = UUID.randomUUID();
        UUID requisicaoId = UUID.randomUUID();

        CriarRequisicaoPersistenteUseCase casoDeUso =
                new CriarRequisicaoPersistenteUseCase(
                        new CriacaoRequisicaoJdbc(
                                () -> abrirConexaoTeste(fabrica)
                        )
                );

        List<ItemSolicitado> originais = List.of(
                new ItemSolicitado(recursoId, 2),
                new ItemSolicitado(recursoId, 3)
        );

        try {
            prepararCadastros(fabrica, departamentoId, recursoId);

            casoDeUso.executar(
                    requisicaoId, departamentoId, originais
            );

            exigirConflito(
                    casoDeUso,
                    requisicaoId,
                    UUID.randomUUID(),
                    originais,
                    "Mesmo UUID com departamento diferente informa conflito"
            );

            exigirConflito(
                    casoDeUso,
                    requisicaoId,
                    departamentoId,
                    List.of(
                            new ItemSolicitado(recursoId, 3),
                            new ItemSolicitado(recursoId, 2)
                    ),
                    "Mesmo UUID com ordem diferente informa conflito"
            );

            exigirConflito(
                    casoDeUso,
                    requisicaoId,
                    departamentoId,
                    List.of(
                            new ItemSolicitado(UUID.randomUUID(), 2),
                            new ItemSolicitado(recursoId, 3)
                    ),
                    "Mesmo UUID com recurso diferente informa conflito"
            );

            exigirConflito(
                    casoDeUso,
                    requisicaoId,
                    departamentoId,
                    List.of(
                            new ItemSolicitado(recursoId, 4),
                            new ItemSolicitado(recursoId, 3)
                    ),
                    "Mesmo UUID com quantidade diferente informa conflito"
            );

            ResultadoCriacaoRequisicao preservada = casoDeUso.executar(
                    requisicaoId, departamentoId, originais
            );

            conferir(
                    preservada.situacao()
                            == ResultadoCriacaoRequisicao.Situacao.JA_EXISTENTE
                            && preservada.requisicao().getItens().size() == 2
                            && preservada.requisicao().getItens().get(0)
                                    .getQuantidade() == 2
                            && preservada.requisicao().getItens().get(1)
                                    .getQuantidade() == 3,
                    "Conflitos preservam a sequencia original dos itens"
            );

            conferirPedidoUnico(
                    fabrica, departamentoId, requisicaoId,
                    "Conflitos de conteudo"
            );
        } finally {
            limpar(fabrica, departamentoId, recursoId, requisicaoId);
        }
    }

    private static void exigirConflito(
            CriarRequisicaoPersistenteUseCase casoDeUso,
            UUID requisicaoId,
            UUID departamentoId,
            List<ItemSolicitado> itens,
            String descricao
    ) {
        boolean conflito = false;

        try {
            casoDeUso.executar(requisicaoId, departamentoId, itens);
        } catch (ConflitoIdentificadorException esperado) {
            conflito = requisicaoId.equals(
                    esperado.getRequisicaoId()
            );
        }

        conferir(conflito, descricao);
    }

        private static void verificarRepeticaoAposProcessamento(
            FabricaConexoes fabrica
    ) throws SQLException {
        verificarEstadoPreservado(
                fabrica,
                "PROVISIONAMENTO_LIBERADO",
                null,
                "Provisionamento Liberado",
                null,
                "737.50"
        );

        verificarEstadoPreservado(
                fabrica,
                "REVISAO_PENDENTE",
                "SALDO_INSUFICIENTE",
                "Revisão Pendente",
                ResultadoValidacao.REVISAO_POR_SALDO_INSUFICIENTE,
                "800.00"
        );

        verificarEstadoPreservado(
                fabrica,
                "REVISAO_PENDENTE",
                "RESERVA",
                "Revisão Pendente",
                ResultadoValidacao.REVISAO_POR_RESERVA,
                "800.00"
        );
    }

    private static void verificarEstadoPreservado(
            FabricaConexoes fabrica,
            String estadoBanco,
            String motivoBanco,
            String estadoDominio,
            ResultadoValidacao motivoDominio,
            String saldoEsperado
    ) throws SQLException {
        UUID departamentoId = UUID.randomUUID();
        UUID recursoId = UUID.randomUUID();
        UUID requisicaoId = UUID.randomUUID();

        List<ItemSolicitado> itens = List.of(
                new ItemSolicitado(recursoId, 2),
                new ItemSolicitado(recursoId, 3)
        );

        CriarRequisicaoPersistenteUseCase casoDeUso =
                new CriarRequisicaoPersistenteUseCase(
                        new CriacaoRequisicaoJdbc(
                                () -> abrirConexaoTeste(fabrica)
                        )
                );

        String cenario = estadoBanco + " / " + motivoBanco;

        try {
            prepararCadastros(fabrica, departamentoId, recursoId);
            casoDeUso.executar(requisicaoId, departamentoId, itens);

            try (Connection conexao = abrirConexaoTeste(fabrica)) {
                conexao.setAutoCommit(false);

                try {
                    try (PreparedStatement comando = conexao.prepareStatement(
                            "UPDATE requisicao "
                                    + "SET estado = ?, motivo_revisao = ? "
                                    + "WHERE id = ?"
                    )) {
                        comando.setString(1, estadoBanco);

                        if (motivoBanco == null) {
                            comando.setNull(2, java.sql.Types.VARCHAR);
                        } else {
                            comando.setString(2, motivoBanco);
                        }

                        comando.setString(3, requisicaoId.toString());

                        if (comando.executeUpdate() != 1) {
                            throw new IllegalStateException(
                                    "Falha ao preparar estado do pedido."
                            );
                        }
                    }

                    try (PreparedStatement comando = conexao.prepareStatement(
                            "UPDATE departamento SET saldo_disponivel = ? "
                                    + "WHERE id = ?"
                    )) {
                        comando.setBigDecimal(
                                1, new BigDecimal(saldoEsperado)
                        );
                        comando.setString(2, departamentoId.toString());

                        if (comando.executeUpdate() != 1) {
                            throw new IllegalStateException(
                                    "Falha ao preparar saldo do departamento."
                            );
                        }
                    }

                    conexao.commit();
                } finally {
                    conexao.rollback();
                }
            }

            ResultadoCriacaoRequisicao repeticao = casoDeUso.executar(
                    requisicaoId, departamentoId, itens
            );

            conferir(
                    repeticao.situacao()
                            == ResultadoCriacaoRequisicao.Situacao.JA_EXISTENTE
                            && estadoDominio.equals(
                                    repeticao.requisicao().getStatus()
                            )
                            && repeticao.requisicao().getMotivoRevisao()
                                    == motivoDominio
                            && igual(
                                    repeticao.requisicao().getDepartamento()
                                            .getSaldoDisponivel(),
                                    saldoEsperado
                            ),
                    cenario + ": repeticao preserva estado, motivo e saldo"
            );

            try (Connection conexao = abrirConexaoTeste(fabrica)) {
                conexao.setTransactionIsolation(
                        Connection.TRANSACTION_READ_COMMITTED
                );

                List<Requisicao> pedidos =
                        new RequisicaoRepositoryJdbc(conexao)
                                .listarPorDepartamento(departamentoId);

                conferir(
                        pedidos.size() == 1
                                && pedidos.get(0).getId().equals(requisicaoId)
                                && estadoDominio.equals(
                                        pedidos.get(0).getStatus()
                                )
                                && pedidos.get(0).getMotivoRevisao()
                                        == motivoDominio
                                && pedidos.get(0).getItens().size() == 2
                                && igual(
                                        pedidos.get(0).getCustoTotalProjetado(),
                                        "62.50"
                                )
                                && igual(
                                        pedidos.get(0).getDepartamento()
                                                .getSaldoDisponivel(),
                                        saldoEsperado
                                ),
                        cenario + ": banco permanece sem reiniciar o pedido"
                );
            }
        } finally {
            limpar(fabrica, departamentoId, recursoId, requisicaoId);
        }
    }

        private static void verificarVinculosInexistentes(
            FabricaConexoes fabrica
    ) throws SQLException {
        UUID departamentoId = UUID.randomUUID();
        UUID recursoId = UUID.randomUUID();
        UUID requisicaoId = UUID.randomUUID();

        CriarRequisicaoPersistenteUseCase casoDeUso =
                new CriarRequisicaoPersistenteUseCase(
                        new CriacaoRequisicaoJdbc(
                                () -> abrirConexaoTeste(fabrica)
                        )
                );

        try {
            prepararCadastros(fabrica, departamentoId, recursoId);

            boolean departamentoRejeitado = false;

            try {
                casoDeUso.executar(
                        requisicaoId,
                        UUID.randomUUID(),
                        List.of(new ItemSolicitado(recursoId, 2))
                );
            } catch (IllegalArgumentException esperado) {
                departamentoRejeitado = true;
            }

            conferir(
                    departamentoRejeitado,
                    "Criacao rejeita departamento inexistente"
            );

            conferirAusenciaAposRejeicao(
                    fabrica, departamentoId, requisicaoId,
                    "Departamento inexistente"
            );

            boolean recursoRejeitado = false;

            try {
                casoDeUso.executar(
                        requisicaoId,
                        departamentoId,
                        List.of(
                                new ItemSolicitado(recursoId, 2),
                                new ItemSolicitado(UUID.randomUUID(), 3)
                        )
                );
            } catch (IllegalArgumentException esperado) {
                recursoRejeitado = true;
            }

            conferir(
                    recursoRejeitado,
                    "Criacao rejeita recurso inexistente apos item valido"
            );

            conferirAusenciaAposRejeicao(
                    fabrica, departamentoId, requisicaoId,
                    "Recurso inexistente"
            );

            ResultadoCriacaoRequisicao corrigida = casoDeUso.executar(
                    requisicaoId,
                    departamentoId,
                    List.of(
                            new ItemSolicitado(recursoId, 2),
                            new ItemSolicitado(recursoId, 3)
                    )
            );

            conferir(
                    corrigida.situacao()
                            == ResultadoCriacaoRequisicao.Situacao.CRIADA
                            && corrigida.requisicao().getId()
                                    .equals(requisicaoId),
                    "Solicitacao corrigida utiliza UUID ainda nao cadastrado"
            );

            conferirPedidoUnico(
                    fabrica, departamentoId, requisicaoId,
                    "Criacao apos rejeicoes"
            );
        } finally {
            limpar(fabrica, departamentoId, recursoId, requisicaoId);
        }
    }

    private static void conferirAusenciaAposRejeicao(
            FabricaConexoes fabrica,
            UUID departamentoId,
            UUID requisicaoId,
            String cenario
    ) throws SQLException {
        try (Connection conexao = abrirConexaoTeste(fabrica)) {
            conferir(
                    contarRegistros(
                            conexao,
                            "SELECT COUNT(*) FROM requisicao WHERE id = ?",
                            requisicaoId
                    ) == 0
                            && contarRegistros(
                                    conexao,
                                    "SELECT COUNT(*) FROM item_requisicao "
                                            + "WHERE requisicao_id = ?",
                                    requisicaoId
                            ) == 0
                            && igual(
                                    new DepartamentoRepositoryJdbc(conexao)
                                            .buscarPorId(departamentoId)
                                            .orElseThrow()
                                            .getSaldoDisponivel(),
                                    "800.00"
                            ),
                    cenario + ": nenhuma gravacao parcial e saldo preservado"
            );
        }
    }

    private static void prepararCadastros(
            FabricaConexoes fabrica,
            UUID departamentoId,
            UUID recursoId
    ) throws SQLException {
        try (Connection conexao = abrirConexaoTeste(fabrica)) {
            conexao.setAutoCommit(false);

            try {
                new DepartamentoRepositoryJdbc(conexao).adicionar(
                        Departamento.reconstituir(
                                departamentoId,
                                "Departamento criacao / Issue 65",
                                new BigDecimal("1000.00"),
                                new BigDecimal("800.00")
                        )
                );

                new RecursoCatalogoRepositoryJdbc(conexao).adicionar(
                        new RecursoCatalogo(
                                recursoId,
                                "Recurso criacao / Issue 65",
                                new BigDecimal("12.50")
                        )
                );

                conexao.commit();
            } finally {
                conexao.rollback();
            }
        }
    }

    private static void alterarPreco(
            FabricaConexoes fabrica,
            UUID recursoId
    ) throws SQLException {
        try (Connection conexao = abrirConexaoTeste(fabrica)) {
            conexao.setAutoCommit(false);

            try {
                try (PreparedStatement comando = conexao.prepareStatement(
                        "UPDATE recurso_catalogo "
                                + "SET custo_mensal = 99.00 WHERE id = ?"
                )) {
                    comando.setString(1, recursoId.toString());

                    if (comando.executeUpdate() != 1) {
                        throw new IllegalStateException(
                                "Alteracao do catalogo afetou quantidade inesperada."
                        );
                    }
                }

                conexao.commit();
            } finally {
                conexao.rollback();
            }
        }
    }

    private static void limpar(
            FabricaConexoes fabrica,
            UUID departamentoId,
            UUID recursoId,
            UUID requisicaoId
    ) throws SQLException {
        try (Connection conexao = abrirConexaoTeste(fabrica)) {
            conexao.setAutoCommit(false);

            try {
                excluir(
                        conexao,
                        "DELETE FROM item_requisicao WHERE requisicao_id = ?",
                        requisicaoId
                );
                excluir(
                        conexao,
                        "DELETE FROM requisicao WHERE id = ?",
                        requisicaoId
                );
                excluir(
                        conexao,
                        "DELETE FROM recurso_catalogo WHERE id = ? "
                                + "AND nome = 'Recurso criacao / Issue 65'",
                        recursoId
                );
                excluir(
                        conexao,
                        "DELETE FROM departamento WHERE id = ? "
                                + "AND nome = 'Departamento criacao / Issue 65'",
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

    private static Connection abrirConexaoTeste(
            FabricaConexoes fabrica
    ) throws SQLException {
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
                            "Execucao permitida somente em finops_test "
                                    + "com finops_test_app@localhost."
                    );
                }
            }

            return conexao;
        } catch (SQLException | RuntimeException excecao) {
            try {
                conexao.close();
            } catch (SQLException fechamento) {
                excecao.addSuppressed(new IllegalStateException(
                        "Falha ao fechar conexao de teste."
                ));
            }

            throw excecao;
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