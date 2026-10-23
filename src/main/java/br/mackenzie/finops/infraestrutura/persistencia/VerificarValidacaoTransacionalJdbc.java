package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.aplicacao.ValidarRequisicaoPersistenteUseCase;
import br.mackenzie.finops.aplicacao.resultados.ResultadoValidacaoPersistente;
import br.mackenzie.finops.dominio.entidades.Departamento;
import br.mackenzie.finops.dominio.entidades.ItemRequisicao;
import br.mackenzie.finops.dominio.entidades.RecursoCatalogo;
import br.mackenzie.finops.dominio.entidades.Requisicao;
import br.mackenzie.finops.dominio.entidades.ResultadoValidacao;
import br.mackenzie.finops.aplicacao.excecoes.FalhaValidacaoPersistenteException;
import br.mackenzie.finops.aplicacao.excecoes.ConfirmacaoValidacaoDesconhecidaException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Locale;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class VerificarValidacaoTransacionalJdbc {

    private static final String NOME_DEPARTAMENTO =
            "Departamento validacao / Issue 67";
    private static final String NOME_RECURSO =
            "Recurso validacao / Issue 67";

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

        try {
            preparar(fabrica, departamentoId, recursoId, requisicaoId);

            ValidarRequisicaoPersistenteUseCase casoDeUso =
                    new ValidarRequisicaoPersistenteUseCase(
                            new ValidacaoRequisicaoJdbc(
                                    () -> abrirConexaoTeste(fabrica)
                            )
                    );

            ResultadoValidacaoPersistente primeira =
                    casoDeUso.executar(requisicaoId);

            conferir(
                    primeira.resultado() == ResultadoValidacao.LIBERADA
                            && !primeira.falhaNoFechamento(),
                    "Primeira validacao retorna LIBERADA sem aviso de fechamento"
            );

            conferir(
                    requisicaoId.equals(primeira.requisicao().getId())
                            && "Provisionamento Liberado".equals(
                                    primeira.requisicao().getStatus()
                            )
                            && primeira.requisicao().getMotivoRevisao() == null
                            && igual(
                                    primeira.requisicao()
                                            .getCustoTotalProjetado(),
                                    "62.50"
                            )
                            && igual(
                                    primeira.requisicao().getDepartamento()
                                            .getSaldoDisponivel(),
                                    "737.50"
                            ),
                    "Resultado apresenta estado, total e saldo esperados"
            );

            conferirPersistido(fabrica, departamentoId, requisicaoId);

            ResultadoValidacaoPersistente repeticao =
                    casoDeUso.executar(requisicaoId);

            conferir(
                    repeticao.resultado() == ResultadoValidacao.JA_LIBERADA
                            && !repeticao.falhaNoFechamento(),
                    "Repeticao retorna JA_LIBERADA"
            );

            conferir(
                    igual(
                            repeticao.requisicao().getDepartamento()
                                    .getSaldoDisponivel(),
                            "737.50"
                    ),
                    "Repeticao preserva saldo sem novo desconto"
            );

            conferirPersistido(fabrica, departamentoId, requisicaoId);
        } finally {
            limpar(fabrica, departamentoId, recursoId, requisicaoId);
        }

        try (Connection conexao = abrirConexaoTeste(fabrica)) {
            conferir(
                    contar(conexao, "departamento", "id", departamentoId) == 0
                            && contar(
                                    conexao, "recurso_catalogo", "id", recursoId
                            ) == 0
                            && contar(
                                    conexao, "requisicao", "id", requisicaoId
                            ) == 0
                            && contar(
                                    conexao,
                                    "item_requisicao",
                                    "requisicao_id",
                                    requisicaoId
                            ) == 0,
                    "Outra conexao confirma limpeza dos registros do teste"
            );
        }

        verificarRevisao(
                fabrica,
                "50.00",
                ResultadoValidacao.REVISAO_POR_SALDO_INSUFICIENTE,
                "Saldo insuficiente"
        );

        verificarRevisao(
                fabrica,
                "150.00",
                ResultadoValidacao.REVISAO_POR_RESERVA,
                "Saldo projetado abaixo da reserva"
        );

        verificarRevisao(
                fabrica,
                "162.50",
                ResultadoValidacao.REVISAO_POR_RESERVA,
                "Saldo projetado igual a reserva"
        );

        verificarIdentificadoresInvalidos(fabrica);
        verificarRollbackFinanceiro(fabrica);
        verificarConfirmacaoDesconhecida(fabrica, false);
        verificarConfirmacaoDesconhecida(fabrica, true);
        verificarFalhaFechamento(fabrica);
        verificarConcorrenciaMesmoPedido(fabrica);
        verificarConcorrenciaMesmoDepartamento(fabrica);
        verificarValidacaoComPrecoHistorico(fabrica);
        verificarValidacaoCustoZero(fabrica);
        verificarTotalInconsistente(fabrica);
        verificarIsolamentoEntreDepartamentos(fabrica);

        System.out.println(
                "Todas as " + verificacoes + " verificacoes passaram."
        );
    }

    private static void preparar(
            FabricaConexoes fabrica,
            UUID departamentoId,
            UUID recursoId,
            UUID requisicaoId
    ) throws SQLException {
        try (Connection conexao = abrirConexaoTeste(fabrica)) {
            conexao.setAutoCommit(false);

            try {
                Departamento departamento = Departamento.reconstituir(
                        departamentoId,
                        NOME_DEPARTAMENTO,
                        new BigDecimal("1000.00"),
                        new BigDecimal("800.00")
                );

                RecursoCatalogo recurso = new RecursoCatalogo(
                        recursoId,
                        NOME_RECURSO,
                        new BigDecimal("12.50")
                );

                new DepartamentoRepositoryJdbc(conexao)
                        .adicionar(departamento);
                new RecursoCatalogoRepositoryJdbc(conexao)
                        .adicionar(recurso);

                Requisicao requisicao = Requisicao.criarComId(
                        requisicaoId, departamento
                );
                requisicao.adicionarItem(new ItemRequisicao(recurso, 2));
                requisicao.adicionarItem(new ItemRequisicao(recurso, 3));

                new RequisicaoRepositoryJdbc(conexao)
                        .adicionar(requisicao);

                conexao.commit();
            } finally {
                conexao.rollback();
            }
        }
    }

    private static void conferirPersistido(
            FabricaConexoes fabrica,
            UUID departamentoId,
            UUID requisicaoId
    ) throws SQLException {
        try (Connection conexao = abrirConexaoTeste(fabrica)) {
            conexao.setTransactionIsolation(
                    Connection.TRANSACTION_READ_COMMITTED
            );

            Requisicao recuperada = new RequisicaoRepositoryJdbc(conexao)
                    .buscarPorId(requisicaoId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Pedido confirmado nao encontrado."
                    ));

            conferir(
                    departamentoId.equals(
                            recuperada.getDepartamento().getId()
                    )
                            && "Provisionamento Liberado".equals(
                                    recuperada.getStatus()
                            )
                            && recuperada.getMotivoRevisao() == null
                            && igual(
                                    recuperada.getDepartamento()
                                            .getSaldoDisponivel(),
                                    "737.50"
                            )
                            && igual(
                                    recuperada.getCustoTotalProjetado(),
                                    "62.50"
                            )
                            && recuperada.getItens().size() == 2
                            && recuperada.getItens().get(0).getQuantidade() == 2
                            && recuperada.getItens().get(1).getQuantidade() == 3,
                    "Outra conexao recupera liberacao, saldo e itens preservados"
            );

            conferir(
                    contar(
                            conexao, "requisicao", "id", requisicaoId
                    ) == 1
                            && contar(
                                    conexao,
                                    "item_requisicao",
                                    "requisicao_id",
                                    requisicaoId
                            ) == 2,
                    "Banco preserva um pedido e seus dois itens"
            );
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
                conferirMarcador(
                        conexao,
                        "SELECT nome FROM departamento WHERE id = ?",
                        departamentoId,
                        NOME_DEPARTAMENTO
                );
                conferirMarcador(
                        conexao,
                        "SELECT nome FROM recurso_catalogo WHERE id = ?",
                        recursoId,
                        NOME_RECURSO
                );

                try (PreparedStatement comando = conexao.prepareStatement(
                        "SELECT departamento_id FROM requisicao WHERE id = ?"
                )) {
                    comando.setString(1, requisicaoId.toString());

                    try (ResultSet resultado = comando.executeQuery()) {
                        if (resultado.next()
                                && !departamentoId.toString().equals(
                                        resultado.getString(1)
                                )) {
                            throw new IllegalStateException(
                                    "Vinculo inesperado; limpeza cancelada."
                            );
                        }
                    }
                }

                excluir(
                        conexao,
                        "DELETE FROM item_requisicao WHERE requisicao_id = ?",
                        requisicaoId.toString()
                );
                excluir(
                        conexao,
                        "DELETE FROM requisicao WHERE id = ? AND departamento_id = ?",
                        requisicaoId.toString(),
                        departamentoId.toString()
                );
                excluir(
                        conexao,
                        "DELETE FROM recurso_catalogo WHERE id = ? AND nome = ?",
                        recursoId.toString(),
                        NOME_RECURSO
                );
                excluir(
                        conexao,
                        "DELETE FROM departamento WHERE id = ? AND nome = ?",
                        departamentoId.toString(),
                        NOME_DEPARTAMENTO
                );

                conexao.commit();
            } finally {
                conexao.rollback();
            }
        }
    }

    private static void conferirMarcador(
            Connection conexao,
            String sql,
            UUID id,
            String nome
    ) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, id.toString());

            try (ResultSet resultado = comando.executeQuery()) {
                if (resultado.next()
                        && !nome.equals(resultado.getString(1))) {
                    throw new IllegalStateException(
                            "Registro sem marcador do teste; limpeza cancelada."
                    );
                }
            }
        }
    }

    private static void excluir(
            Connection conexao,
            String sql,
            String... parametros
    ) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            for (int indice = 0; indice < parametros.length; indice++) {
                comando.setString(indice + 1, parametros[indice]);
            }
            comando.executeUpdate();
        }
    }

    private static long contar(
            Connection conexao,
            String tabela,
            String coluna,
            UUID id
    ) throws SQLException {
        // Tabela e coluna são constantes internas deste verificador.
        String sql = "SELECT COUNT(*) FROM " + tabela
                + " WHERE " + coluna + " = ?";

        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, id.toString());

            try (ResultSet resultado = comando.executeQuery()) {
                if (!resultado.next()) {
                    throw new IllegalStateException(
                            "Contagem sem resultado."
                    );
                }
                return resultado.getLong(1);
            }
        }
    }

    private static Connection abrirConexaoTeste(FabricaConexoes fabrica)
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

        private static void verificarRevisao(
            FabricaConexoes fabrica,
            String saldoInicial,
            ResultadoValidacao motivoEsperado,
            String cenario
    ) throws SQLException {
        UUID departamentoId = UUID.randomUUID();
        UUID recursoId = UUID.randomUUID();
        UUID requisicaoId = UUID.randomUUID();

        System.out.println("\nCenario: " + cenario);
        System.out.println("Departamento do teste: " + departamentoId);
        System.out.println("Recurso do teste: " + recursoId);
        System.out.println("Requisicao do teste: " + requisicaoId);

        try {
            preparar(fabrica, departamentoId, recursoId, requisicaoId);
            definirSaldoTeste(fabrica, departamentoId, saldoInicial);

            ValidarRequisicaoPersistenteUseCase casoDeUso =
                    new ValidarRequisicaoPersistenteUseCase(
                            new ValidacaoRequisicaoJdbc(
                                    () -> abrirConexaoTeste(fabrica)
                            )
                    );

            ResultadoValidacaoPersistente primeira =
                    casoDeUso.executar(requisicaoId);

            conferir(
                    primeira.resultado() == motivoEsperado
                            && !primeira.falhaNoFechamento()
                            && "Revisão Pendente".equals(
                                    primeira.requisicao().getStatus()
                            )
                            && primeira.requisicao().getMotivoRevisao()
                                    == motivoEsperado,
                    cenario + ": retorna revisao com motivo esperado"
            );

            conferir(
                    igual(
                            primeira.requisicao().getDepartamento()
                                    .getSaldoDisponivel(),
                            saldoInicial
                    ),
                    cenario + ": resultado preserva saldo"
            );

            ResultadoValidacaoPersistente repeticao =
                    casoDeUso.executar(requisicaoId);

            conferir(
                    repeticao.resultado()
                            == ResultadoValidacao.ESTADO_NAO_PERMITIDO
                            && !repeticao.falhaNoFechamento()
                            && "Revisão Pendente".equals(
                                    repeticao.requisicao().getStatus()
                            )
                            && repeticao.requisicao().getMotivoRevisao()
                                    == motivoEsperado
                            && igual(
                                    repeticao.requisicao().getDepartamento()
                                            .getSaldoDisponivel(),
                                    saldoInicial
                            ),
                    cenario + ": repeticao nao reinicia o pedido"
            );

            try (Connection conexao = abrirConexaoTeste(fabrica)) {
                conexao.setTransactionIsolation(
                        Connection.TRANSACTION_READ_COMMITTED
                );

                Requisicao recuperada =
                        new RequisicaoRepositoryJdbc(conexao)
                                .buscarPorId(requisicaoId)
                                .orElseThrow(() -> new IllegalStateException(
                                        "Pedido em revisao nao encontrado."
                                ));

                conferir(
                        departamentoId.equals(
                                recuperada.getDepartamento().getId()
                        )
                                && "Revisão Pendente".equals(
                                        recuperada.getStatus()
                                )
                                && recuperada.getMotivoRevisao()
                                        == motivoEsperado
                                && igual(
                                        recuperada.getDepartamento()
                                                .getSaldoDisponivel(),
                                        saldoInicial
                                )
                                && igual(
                                        recuperada.getCustoTotalProjetado(),
                                        "62.50"
                                )
                                && recuperada.getItens().size() == 2,
                        cenario + ": outra conexao confirma revisao sem desconto"
                );
            }
        } finally {
            limpar(fabrica, departamentoId, recursoId, requisicaoId);
        }

        try (Connection conexao = abrirConexaoTeste(fabrica)) {
            conferir(
                    contar(conexao, "departamento", "id", departamentoId) == 0
                            && contar(
                                    conexao, "recurso_catalogo", "id", recursoId
                            ) == 0
                            && contar(
                                    conexao, "requisicao", "id", requisicaoId
                            ) == 0
                            && contar(
                                    conexao,
                                    "item_requisicao",
                                    "requisicao_id",
                                    requisicaoId
                            ) == 0,
                    cenario + ": limpeza confirmada"
            );
        }
    }

    private static void definirSaldoTeste(
            FabricaConexoes fabrica,
            UUID departamentoId,
            String saldo
    ) throws SQLException {
        try (Connection conexao = abrirConexaoTeste(fabrica)) {
            conexao.setAutoCommit(false);

            try {
                String sql = """
                        UPDATE departamento
                        SET saldo_disponivel = ?
                        WHERE id = ? AND nome = ?
                        """;

                try (PreparedStatement comando =
                             conexao.prepareStatement(sql)) {
                    comando.setBigDecimal(1, new BigDecimal(saldo));
                    comando.setString(2, departamentoId.toString());
                    comando.setString(3, NOME_DEPARTAMENTO);

                    if (comando.executeUpdate() != 1) {
                        throw new IllegalStateException(
                                "A preparacao do saldo nao afetou "
                                        + "exatamente um departamento de teste."
                        );
                    }
                }

                conexao.commit();
            } finally {
                conexao.rollback();
            }
        }
    }

        private static void verificarIdentificadoresInvalidos(
            FabricaConexoes fabrica
    ) throws SQLException {
        int[] aberturas = {0};

        ValidacaoRequisicaoJdbc operacao = new ValidacaoRequisicaoJdbc(
                () -> {
                    aberturas[0]++;
                    return abrirConexaoTeste(fabrica);
                }
        );

        ValidarRequisicaoPersistenteUseCase casoDeUso =
                new ValidarRequisicaoPersistenteUseCase(operacao);

        try {
            casoDeUso.executar(null);
            throw new IllegalStateException(
                    "FALHOU: caso de uso aceitou UUID nulo."
            );
        } catch (IllegalArgumentException esperado) {
            conferir(
                    aberturas[0] == 0,
                    "Caso de uso rejeita UUID nulo sem abrir conexao"
            );
        }

        try {
            operacao.executar(null);
            throw new IllegalStateException(
                    "FALHOU: operacao JDBC aceitou UUID nulo."
            );
        } catch (IllegalArgumentException esperado) {
            conferir(
                    aberturas[0] == 0,
                    "Operacao JDBC rejeita UUID nulo sem abrir conexao"
            );
        }

        UUID inexistente = UUID.randomUUID();

        try (Connection conexao = abrirConexaoTeste(fabrica)) {
            if (contar(
                    conexao, "requisicao", "id", inexistente
            ) != 0) {
                throw new IllegalStateException(
                        "O UUID escolhido para ausencia ja existe."
                );
            }
        }

        try {
            casoDeUso.executar(inexistente);
            throw new IllegalStateException(
                    "FALHOU: validacao aceitou requisicao inexistente."
            );
        } catch (IllegalArgumentException esperado) {
            conferir(
                    aberturas[0] == 1,
                    "Requisicao inexistente e rejeitada em uma tentativa"
            );
        }

        try (Connection conexao = abrirConexaoTeste(fabrica)) {
            conferir(
                    contar(
                            conexao, "requisicao", "id", inexistente
                    ) == 0
                            && contar(
                                    conexao,
                                    "item_requisicao",
                                    "requisicao_id",
                                    inexistente
                            ) == 0,
                    "Validacao de UUID inexistente nao cria pedido ou itens"
            );
        }
    }

        private static void verificarRollbackFinanceiro(
            FabricaConexoes fabrica
    ) throws SQLException {
        UUID departamentoId = UUID.randomUUID();
        UUID recursoId = UUID.randomUUID();
        UUID requisicaoId = UUID.randomUUID();

        boolean[] falhaInjetada = {false};
        boolean[] descontoObservado = {false};

        System.out.println("\nCenario: rollback financeiro");
        System.out.println("Departamento do teste: " + departamentoId);
        System.out.println("Recurso do teste: " + recursoId);
        System.out.println("Requisicao do teste: " + requisicaoId);

        try {
            preparar(fabrica, departamentoId, recursoId, requisicaoId);

            ValidarRequisicaoPersistenteUseCase comFalha =
                    new ValidarRequisicaoPersistenteUseCase(
                            new ValidacaoRequisicaoJdbc(
                                    () -> falharAntesDeAtualizarPedido(
                                            abrirConexaoTeste(fabrica),
                                            requisicaoId,
                                            falhaInjetada,
                                            descontoObservado
                                    )
                            )
                    );

            try {
                comFalha.executar(requisicaoId);
                throw new IllegalStateException(
                        "FALHOU: validacao informou sucesso apos falha simulada."
                );
            } catch (FalhaValidacaoPersistenteException esperado) {
                conferir(
                        falhaInjetada[0],
                        "Falha ao atualizar pedido e informada ao chamador"
                );
            }

            conferir(
                    descontoObservado[0],
                    "Antes da falha existe desconto sem alteracao do pedido"
            );

            try (Connection conexao = abrirConexaoTeste(fabrica)) {
                conexao.setTransactionIsolation(
                        Connection.TRANSACTION_READ_COMMITTED
                );

                Requisicao recuperada =
                        new RequisicaoRepositoryJdbc(conexao)
                                .buscarPorId(requisicaoId)
                                .orElseThrow(() -> new IllegalStateException(
                                        "Pedido nao encontrado apos rollback."
                                ));

                conferir(
                        "INICIAL".equals(recuperada.getStatus())
                                && recuperada.getMotivoRevisao() == null
                                && igual(
                                        recuperada.getDepartamento()
                                                .getSaldoDisponivel(),
                                        "800.00"
                                )
                                && igual(
                                        recuperada.getCustoTotalProjetado(),
                                        "62.50"
                                )
                                && recuperada.getItens().size() == 2,
                        "Outra conexao confirma rollback do desconto"
                );
            }

            ValidarRequisicaoPersistenteUseCase normal =
                    new ValidarRequisicaoPersistenteUseCase(
                            new ValidacaoRequisicaoJdbc(
                                    () -> abrirConexaoTeste(fabrica)
                            )
                    );

            ResultadoValidacaoPersistente novaTentativa =
                    normal.executar(requisicaoId);

            conferir(
                    novaTentativa.resultado() == ResultadoValidacao.LIBERADA
                            && !novaTentativa.falhaNoFechamento()
                            && igual(
                                    novaTentativa.requisicao()
                                            .getDepartamento()
                                            .getSaldoDisponivel(),
                                    "737.50"
                            ),
                    "Nova tentativa libera com um unico desconto"
            );

            conferirPersistido(fabrica, departamentoId, requisicaoId);
        } finally {
            limpar(fabrica, departamentoId, recursoId, requisicaoId);
        }

        try (Connection conexao = abrirConexaoTeste(fabrica)) {
            conferir(
                    contar(conexao, "departamento", "id", departamentoId) == 0
                            && contar(
                                    conexao, "recurso_catalogo", "id", recursoId
                            ) == 0
                            && contar(
                                    conexao, "requisicao", "id", requisicaoId
                            ) == 0
                            && contar(
                                    conexao,
                                    "item_requisicao",
                                    "requisicao_id",
                                    requisicaoId
                            ) == 0,
                    "Cenario de rollback financeiro confirma limpeza"
            );
        }
    }

    private static Connection falharAntesDeAtualizarPedido(
            Connection real,
            UUID requisicaoId,
            boolean[] falhaInjetada,
            boolean[] descontoObservado
    ) {
        return (Connection) Proxy.newProxyInstance(
                VerificarValidacaoTransacionalJdbc.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, metodo, argumentos) -> {
                    try {
                        if ("prepareStatement".equals(metodo.getName())
                                && argumentos != null
                                && argumentos.length > 0
                                && argumentos[0] instanceof String sql
                                && sql.stripLeading()
                                        .toUpperCase(Locale.ROOT)
                                        .startsWith("UPDATE REQUISICAO")
                                && !falhaInjetada[0]) {

                            // Consulta na mesma transação para demonstrar
                            // que o desconto ocorreu antes da falha.
                            Requisicao parcial =
                                    new RequisicaoRepositoryJdbc(real)
                                            .buscarPorId(requisicaoId)
                                            .orElseThrow(
                                                    () -> new IllegalStateException(
                                                            "Pedido ausente durante "
                                                                    + "a injecao da falha."
                                                    )
                                            );

                            descontoObservado[0] =
                                    "INICIAL".equals(parcial.getStatus())
                                            && parcial.getMotivoRevisao() == null
                                            && igual(
                                                    parcial.getDepartamento()
                                                            .getSaldoDisponivel(),
                                                    "737.50"
                                            );

                            falhaInjetada[0] = true;

                            throw new SQLException(
                                    "Falha simulada antes de atualizar requisicao.",
                                    "HY000"
                            );
                        }

                        return metodo.invoke(real, argumentos);
                    } catch (InvocationTargetException excecao) {
                        throw excecao.getCause();
                    }
                }
        );
    }

        private static void verificarConfirmacaoDesconhecida(
            FabricaConexoes fabrica,
            boolean efetivarCommit
    ) throws SQLException {
        UUID departamentoId = UUID.randomUUID();
        UUID recursoId = UUID.randomUUID();
        UUID requisicaoId = UUID.randomUUID();

        boolean[] falhaInjetada = {false};

        String cenario = efetivarCommit
                ? "Commit efetivado com resposta perdida"
                : "Falha antes de efetivar commit";

        System.out.println("\nCenario: " + cenario);
        System.out.println("Departamento do teste: " + departamentoId);
        System.out.println("Recurso do teste: " + recursoId);
        System.out.println("Requisicao do teste: " + requisicaoId);

        try {
            preparar(fabrica, departamentoId, recursoId, requisicaoId);

            ValidarRequisicaoPersistenteUseCase comFalha =
                    new ValidarRequisicaoPersistenteUseCase(
                            new ValidacaoRequisicaoJdbc(
                                    () -> simularFalhaCommitValidacao(
                                            abrirConexaoTeste(fabrica),
                                            efetivarCommit,
                                            falhaInjetada
                                    )
                            )
                    );

            try {
                comFalha.executar(requisicaoId);
                throw new IllegalStateException(
                        "FALHOU: commit com falha simulada retornou sucesso."
                );
            } catch (ConfirmacaoValidacaoDesconhecidaException esperado) {
                conferir(
                        falhaInjetada[0]
                                && requisicaoId.equals(
                                        esperado.getRequisicaoId()
                                ),
                        cenario + ": informa confirmacao desconhecida com UUID"
                );
            }

            try (Connection conexao = abrirConexaoTeste(fabrica)) {
                conexao.setTransactionIsolation(
                        Connection.TRANSACTION_READ_COMMITTED
                );

                Requisicao recuperada =
                        new RequisicaoRepositoryJdbc(conexao)
                                .buscarPorId(requisicaoId)
                                .orElseThrow(() -> new IllegalStateException(
                                        "Pedido nao encontrado apos falha de commit."
                                ));

                String estadoEsperado = efetivarCommit
                        ? "Provisionamento Liberado"
                        : "INICIAL";

                String saldoEsperado = efetivarCommit
                        ? "737.50"
                        : "800.00";

                conferir(
                        estadoEsperado.equals(recuperada.getStatus())
                                && recuperada.getMotivoRevisao() == null
                                && igual(
                                        recuperada.getDepartamento()
                                                .getSaldoDisponivel(),
                                        saldoEsperado
                                ),
                        cenario + ": banco apresenta estado e saldo esperados"
                );
            }

            ValidarRequisicaoPersistenteUseCase normal =
                    new ValidarRequisicaoPersistenteUseCase(
                            new ValidacaoRequisicaoJdbc(
                                    () -> abrirConexaoTeste(fabrica)
                            )
                    );

            ResultadoValidacaoPersistente reconciliacao =
                    normal.executar(requisicaoId);

            ResultadoValidacao decisaoEsperada = efetivarCommit
                    ? ResultadoValidacao.JA_LIBERADA
                    : ResultadoValidacao.LIBERADA;

            conferir(
                    reconciliacao.resultado() == decisaoEsperada
                            && requisicaoId.equals(
                                    reconciliacao.requisicao().getId()
                            )
                            && !reconciliacao.falhaNoFechamento()
                            && igual(
                                    reconciliacao.requisicao()
                                            .getDepartamento()
                                            .getSaldoDisponivel(),
                                    "737.50"
                            ),
                    cenario + ": reconciliacao preserva UUID e desconto unico"
            );

            conferirPersistido(fabrica, departamentoId, requisicaoId);
        } finally {
            limpar(fabrica, departamentoId, recursoId, requisicaoId);
        }

        try (Connection conexao = abrirConexaoTeste(fabrica)) {
            conferir(
                    contar(conexao, "departamento", "id", departamentoId) == 0
                            && contar(
                                    conexao, "recurso_catalogo", "id", recursoId
                            ) == 0
                            && contar(
                                    conexao, "requisicao", "id", requisicaoId
                            ) == 0
                            && contar(
                                    conexao,
                                    "item_requisicao",
                                    "requisicao_id",
                                    requisicaoId
                            ) == 0,
                    cenario + ": limpeza confirmada"
            );
        }
    }

    private static Connection simularFalhaCommitValidacao(
            Connection real,
            boolean efetivarCommit,
            boolean[] falhaInjetada
    ) {
        return (Connection) Proxy.newProxyInstance(
                VerificarValidacaoTransacionalJdbc.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, metodo, argumentos) -> {
                    try {
                        if ("commit".equals(metodo.getName())
                                && !falhaInjetada[0]) {
                            if (efetivarCommit) {
                                real.commit();
                            }

                            falhaInjetada[0] = true;

                            throw new SQLException(
                                    "Falha simulada na confirmacao do commit.",
                                    "08006"
                            );
                        }

                        return metodo.invoke(real, argumentos);
                    } catch (InvocationTargetException excecao) {
                        throw excecao.getCause();
                    }
                }
        );
    }

    private static void verificarFalhaFechamento(
        FabricaConexoes fabrica
) throws SQLException {
    UUID departamentoId = UUID.randomUUID();
    UUID recursoId = UUID.randomUUID();
    UUID requisicaoId = UUID.randomUUID();
    boolean[] falhaInjetada = {false};

    System.out.println("\nCenario: falha de fechamento apos commit");
    System.out.println("Departamento do teste: " + departamentoId);
    System.out.println("Recurso do teste: " + recursoId);
    System.out.println("Requisicao do teste: " + requisicaoId);

    try {
        preparar(fabrica, departamentoId, recursoId, requisicaoId);

        ValidarRequisicaoPersistenteUseCase casoComFalha =
                new ValidarRequisicaoPersistenteUseCase(
                        new ValidacaoRequisicaoJdbc(
                                () -> simularFalhaFechamentoValidacao(
                                        abrirConexaoTeste(fabrica),
                                        falhaInjetada
                                )
                        )
                );

        ResultadoValidacaoPersistente resultado =
                casoComFalha.executar(requisicaoId);

        conferir(
                falhaInjetada[0]
                        && resultado.resultado() == ResultadoValidacao.LIBERADA
                        && resultado.falhaNoFechamento(),
                "Falha de fechamento preserva LIBERADA com aviso"
        );

        conferir(
                resultado.requisicao().getId().equals(requisicaoId)
                        && igual(
                                resultado.requisicao()
                                        .getDepartamento()
                                        .getSaldoDisponivel(),
                                "737.50"
                        ),
                "Resultado confirmado preserva UUID e saldo descontado"
        );

        conferirPersistido(fabrica, departamentoId, requisicaoId);

        ValidarRequisicaoPersistenteUseCase casoNormal =
                new ValidarRequisicaoPersistenteUseCase(
                        new ValidacaoRequisicaoJdbc(
                                () -> abrirConexaoTeste(fabrica)
                        )
                );

        ResultadoValidacaoPersistente repeticao =
                casoNormal.executar(requisicaoId);

        conferir(
                repeticao.resultado() == ResultadoValidacao.JA_LIBERADA
                        && !repeticao.falhaNoFechamento()
                        && igual(
                                repeticao.requisicao()
                                        .getDepartamento()
                                        .getSaldoDisponivel(),
                                "737.50"
                        ),
                "Repeticao apos aviso reconhece liberacao sem novo desconto"
        );
    } finally {
        limpar(fabrica, departamentoId, recursoId, requisicaoId);
    }

    try (Connection conexao = abrirConexaoTeste(fabrica)) {
        conferir(
                contar(conexao, "departamento", "id", departamentoId) == 0
                        && contar(conexao, "recurso_catalogo", "id", recursoId) == 0
                        && contar(conexao, "requisicao", "id", requisicaoId) == 0
                        && contar(
                                conexao,
                                "item_requisicao",
                                "requisicao_id",
                                requisicaoId
                        ) == 0,
                "Cenario de falha de fechamento confirma limpeza"
        );
    }
}

private static Connection simularFalhaFechamentoValidacao(
        Connection real,
        boolean[] falhaInjetada
) {
    return (Connection) Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[]{Connection.class},
            (proxy, metodo, argumentos) -> {
                try {
                    if ("close".equals(metodo.getName())
                            && !falhaInjetada[0]) {
                        // Fecha a conexão real para não deixar recursos
                        // abertos no teste e simula uma falha na resposta.
                        real.close();
                        falhaInjetada[0] = true;

                        throw new SQLException(
                                "Falha simulada na resposta do fechamento.",
                                "08006"
                        );
                    }

                    return metodo.invoke(real, argumentos);
                } catch (InvocationTargetException excecao) {
                    throw excecao.getCause();
                }
            }
    );
}

private static void verificarConcorrenciaMesmoPedido(
        FabricaConexoes fabrica
) throws SQLException {
    UUID departamentoId = UUID.randomUUID();
    UUID recursoId = UUID.randomUUID();
    UUID requisicaoId = UUID.randomUUID();

    CyclicBarrier encontro = new CyclicBarrier(2);
    AtomicInteger consultasCoordenadas = new AtomicInteger();

    System.out.println("\nCenario: validacoes concorrentes do mesmo pedido");
    System.out.println("Departamento do teste: " + departamentoId);
    System.out.println("Recurso do teste: " + recursoId);
    System.out.println("Requisicao do teste: " + requisicaoId);

    try {
        preparar(fabrica, departamentoId, recursoId, requisicaoId);

        ValidarRequisicaoPersistenteUseCase caso =
                new ValidarRequisicaoPersistenteUseCase(
                        new ValidacaoRequisicaoJdbc(
                                () -> coordenarBloqueioDepartamento(
                                        abrirConexaoTeste(fabrica),
                                        encontro,
                                        consultasCoordenadas
                                )
                        )
                );

        // O executor é encerrado antes da limpeza dos registros.
        try (var executor = Executors.newFixedThreadPool(2)) {
            var primeira = executor.submit(
                    () -> caso.executar(requisicaoId)
            );
            var segunda = executor.submit(
                    () -> caso.executar(requisicaoId)
            );

            ResultadoValidacaoPersistente resultado1 =
                    primeira.get(30, TimeUnit.SECONDS);
            ResultadoValidacaoPersistente resultado2 =
                    segunda.get(30, TimeUnit.SECONDS);

            boolean primeiraLiberou =
                    resultado1.resultado() == ResultadoValidacao.LIBERADA
                            && resultado2.resultado()
                            == ResultadoValidacao.JA_LIBERADA;

            boolean segundaLiberou =
                    resultado2.resultado() == ResultadoValidacao.LIBERADA
                            && resultado1.resultado()
                            == ResultadoValidacao.JA_LIBERADA;

            conferir(
                    consultasCoordenadas.get() == 2
                            && (primeiraLiberou || segundaLiberou),
                    "Concorrencia retorna uma LIBERADA e uma JA_LIBERADA"
            );

            conferir(
                    resultado1.requisicao().getId().equals(requisicaoId)
                            && resultado2.requisicao().getId().equals(requisicaoId)
                            && !resultado1.falhaNoFechamento()
                            && !resultado2.falhaNoFechamento(),
                    "As duas chamadas retornam o mesmo UUID sem aviso"
            );

            conferir(
                    igual(
                            resultado1.requisicao()
                                    .getDepartamento()
                                    .getSaldoDisponivel(),
                            "737.50"
                    )
                            && igual(
                                    resultado2.requisicao()
                                            .getDepartamento()
                                            .getSaldoDisponivel(),
                                    "737.50"
                            ),
                    "As duas chamadas observam saldo com um unico desconto"
            );
        } catch (InterruptedException excecao) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "O teste concorrente foi interrompido.",
                    excecao
            );
        } catch (java.util.concurrent.ExecutionException
                 | java.util.concurrent.TimeoutException excecao) {
            throw new IllegalStateException(
                    "A validacao concorrente falhou ou excedeu o prazo.",
                    excecao
            );
        }

        conferirPersistido(fabrica, departamentoId, requisicaoId);
    } finally {
        limpar(fabrica, departamentoId, recursoId, requisicaoId);
    }

    try (Connection conexao = abrirConexaoTeste(fabrica)) {
        conferir(
                contar(conexao, "departamento", "id", departamentoId) == 0
                        && contar(conexao, "recurso_catalogo", "id", recursoId) == 0
                        && contar(conexao, "requisicao", "id", requisicaoId) == 0
                        && contar(
                                conexao,
                                "item_requisicao",
                                "requisicao_id",
                                requisicaoId
                        ) == 0,
                "Concorrencia do mesmo pedido confirma limpeza"
        );
    }
}

private static Connection coordenarBloqueioDepartamento(
        Connection real,
        CyclicBarrier encontro,
        AtomicInteger consultasCoordenadas
) throws SQLException {
    try (Statement comando = real.createStatement()) {
        comando.execute("SET SESSION innodb_lock_wait_timeout = 10");
    } catch (SQLException excecao) {
        try {
            real.close();
        } catch (SQLException fechamento) {
            excecao.addSuppressed(fechamento);
        }
        throw excecao;
    }

    return (Connection) Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[]{Connection.class},
            (proxy, metodo, argumentos) -> {
                try {
                    if ("prepareStatement".equals(metodo.getName())
                            && argumentos != null
                            && argumentos.length > 0
                            && argumentos[0] instanceof String sql) {

                        String normalizado = sql
                                .replaceAll("\\s+", " ")
                                .trim()
                                .toUpperCase(Locale.ROOT);

                        if (normalizado.startsWith("SELECT ")
                                && normalizado.contains(" FROM DEPARTAMENTO ")
                                && normalizado.endsWith("FOR UPDATE")) {

                            PreparedStatement comando =
                                    (PreparedStatement) metodo.invoke(
                                            real, argumentos
                                    );

                            try {
                                comando.setQueryTimeout(15);
                            } catch (SQLException excecao) {
                                try {
                                    comando.close();
                                } catch (SQLException fechamento) {
                                    excecao.addSuppressed(fechamento);
                                }
                                throw excecao;
                            }

                            return Proxy.newProxyInstance(
                                    PreparedStatement.class.getClassLoader(),
                                    new Class<?>[]{PreparedStatement.class},
                                    (proxyComando, metodoComando, parametros) -> {
                                        try {
                                            if ("executeQuery".equals(
                                                    metodoComando.getName()
                                            )) {
                                                consultasCoordenadas.incrementAndGet();

                                                try {
                                                    encontro.await(
                                                            10, TimeUnit.SECONDS
                                                    );
                                                } catch (InterruptedException excecao) {
                                                    Thread.currentThread().interrupt();
                                                    throw new SQLException(
                                                            "Coordenacao interrompida.",
                                                            "HY000"
                                                    );
                                                } catch (
                                                        java.util.concurrent.BrokenBarrierException
                                                        | java.util.concurrent.TimeoutException excecao
                                                ) {
                                                    throw new SQLException(
                                                            "As duas chamadas nao chegaram ao encontro.",
                                                            "HY000"
                                                    );
                                                }
                                            }

                                            return metodoComando.invoke(
                                                    comando, parametros
                                            );
                                        } catch (InvocationTargetException excecao) {
                                            throw excecao.getCause();
                                        }
                                    }
                            );
                        }
                    }

                    return metodo.invoke(real, argumentos);
                } catch (InvocationTargetException excecao) {
                    throw excecao.getCause();
                }
            }
    );
}

private static void verificarConcorrenciaMesmoDepartamento(
        FabricaConexoes fabrica
) throws SQLException {
    UUID departamentoId = UUID.randomUUID();
    UUID recursoId = UUID.randomUUID();
    UUID pedido1Id = UUID.randomUUID();
    UUID pedido2Id = UUID.randomUUID();

    CyclicBarrier encontro = new CyclicBarrier(2);
    AtomicInteger consultasCoordenadas = new AtomicInteger();

    System.out.println("\nCenario: dois pedidos do mesmo departamento");
    System.out.println("Departamento do teste: " + departamentoId);
    System.out.println("Recurso do teste: " + recursoId);
    System.out.println("Primeiro pedido: " + pedido1Id);
    System.out.println("Segundo pedido: " + pedido2Id);

    try {
        preparar(fabrica, departamentoId, recursoId, pedido1Id);
        definirSaldoTeste(fabrica, departamentoId, "200.00");
        prepararSegundoPedido(
                fabrica, departamentoId, recursoId, pedido2Id
        );

        ValidarRequisicaoPersistenteUseCase caso =
                new ValidarRequisicaoPersistenteUseCase(
                        new ValidacaoRequisicaoJdbc(
                                () -> coordenarBloqueioDepartamento(
                                        abrirConexaoTeste(fabrica),
                                        encontro,
                                        consultasCoordenadas
                                )
                        )
                );

        try (var executor = Executors.newFixedThreadPool(2)) {
            var primeira = executor.submit(
                    () -> caso.executar(pedido1Id)
            );
            var segunda = executor.submit(
                    () -> caso.executar(pedido2Id)
            );

            ResultadoValidacaoPersistente resultado1 =
                    primeira.get(30, TimeUnit.SECONDS);
            ResultadoValidacaoPersistente resultado2 =
                    segunda.get(30, TimeUnit.SECONDS);

            boolean primeiroLiberado =
                    resultado1.resultado() == ResultadoValidacao.LIBERADA
                            && resultado2.resultado()
                            == ResultadoValidacao.REVISAO_POR_RESERVA;

            boolean segundoLiberado =
                    resultado2.resultado() == ResultadoValidacao.LIBERADA
                            && resultado1.resultado()
                            == ResultadoValidacao.REVISAO_POR_RESERVA;

            conferir(
                    consultasCoordenadas.get() == 2
                            && (primeiroLiberado || segundoLiberado),
                    "Disputa pelo saldo libera um pedido e revisa o outro"
            );

            conferir(
                    resultado1.requisicao().getId().equals(pedido1Id)
                            && resultado2.requisicao().getId().equals(pedido2Id)
                            && !resultado1.falhaNoFechamento()
                            && !resultado2.falhaNoFechamento(),
                    "Resultados preservam os dois UUIDs sem aviso"
            );

            conferir(
                    igual(
                            resultado1.requisicao()
                                    .getDepartamento()
                                    .getSaldoDisponivel(),
                            "137.50"
                    )
                            && igual(
                                    resultado2.requisicao()
                                            .getDepartamento()
                                            .getSaldoDisponivel(),
                                    "137.50"
                            ),
                    "Segunda validacao considera o saldo apos o primeiro desconto"
            );

            try (Connection conexao = abrirConexaoTeste(fabrica)) {
                conexao.setTransactionIsolation(
                        Connection.TRANSACTION_READ_COMMITTED
                );

                RequisicaoRepositoryJdbc repositorio =
                        new RequisicaoRepositoryJdbc(conexao);

                Requisicao pedido1 = repositorio.buscarPorId(pedido1Id)
                        .orElseThrow();
                Requisicao pedido2 = repositorio.buscarPorId(pedido2Id)
                        .orElseThrow();

                Requisicao liberado = primeiroLiberado ? pedido1 : pedido2;
                Requisicao revisado = primeiroLiberado ? pedido2 : pedido1;

                conferir(
                        "Provisionamento Liberado".equals(liberado.getStatus())
                                && liberado.getMotivoRevisao() == null
                                && "Revisão Pendente".equals(revisado.getStatus())
                                && revisado.getMotivoRevisao()
                                == ResultadoValidacao.REVISAO_POR_RESERVA,
                        "Outra conexao confirma liberacao e revisao por reserva"
                );

                conferir(
                        igual(
                                pedido1.getDepartamento().getSaldoDisponivel(),
                                "137.50"
                        )
                                && igual(
                                        pedido2.getDepartamento().getSaldoDisponivel(),
                                        "137.50"
                                )
                                && igual(pedido1.getCustoTotalProjetado(), "62.50")
                                && igual(pedido2.getCustoTotalProjetado(), "62.50")
                                && pedido1.getItens().size() == 2
                                && pedido2.getItens().size() == 2,
                        "Banco preserva saldo acima da reserva e os dois pedidos"
                );
            }
        } catch (InterruptedException excecao) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "O teste concorrente foi interrompido.",
                    excecao
            );
        } catch (java.util.concurrent.ExecutionException
                 | java.util.concurrent.TimeoutException excecao) {
            throw new IllegalStateException(
                    "A disputa pelo saldo falhou ou excedeu o prazo.",
                    excecao
            );
        }
    } finally {
        // Remove os dois pedidos antes dos cadastros compartilhados.
        limparDoisPedidos(
                fabrica, departamentoId, recursoId, pedido1Id, pedido2Id
        );
    }

    try (Connection conexao = abrirConexaoTeste(fabrica)) {
        conferir(
                contar(conexao, "departamento", "id", departamentoId) == 0
                        && contar(conexao, "recurso_catalogo", "id", recursoId) == 0
                        && contar(conexao, "requisicao", "id", pedido1Id) == 0
                        && contar(conexao, "requisicao", "id", pedido2Id) == 0
                        && contar(
                                conexao, "item_requisicao",
                                "requisicao_id", pedido1Id
                        ) == 0
                        && contar(
                                conexao, "item_requisicao",
                                "requisicao_id", pedido2Id
                        ) == 0,
                "Disputa pelo saldo confirma limpeza dos dois pedidos"
        );
    }
}

private static void prepararSegundoPedido(
        FabricaConexoes fabrica,
        UUID departamentoId,
        UUID recursoId,
        UUID requisicaoId
) throws SQLException {
    try (Connection conexao = abrirConexaoTeste(fabrica)) {
        conexao.setTransactionIsolation(
                Connection.TRANSACTION_READ_COMMITTED
        );
        conexao.setAutoCommit(false);

        try {
            Departamento departamento =
                    new DepartamentoRepositoryJdbc(conexao)
                            .buscarPorId(departamentoId)
                            .orElseThrow();

            RecursoCatalogo recurso =
                    new RecursoCatalogoRepositoryJdbc(conexao)
                            .buscarPorId(recursoId)
                            .orElseThrow();

            Requisicao pedido = Requisicao.criarComId(
                    requisicaoId, departamento
            );
            pedido.adicionarItem(new ItemRequisicao(recurso, 2));
            pedido.adicionarItem(new ItemRequisicao(recurso, 3));

            new RequisicaoRepositoryJdbc(conexao).adicionar(pedido);
            conexao.commit();
        } finally {
            conexao.rollback();
        }
    }
}

private static void limparDoisPedidos(
        FabricaConexoes fabrica,
        UUID departamentoId,
        UUID recursoId,
        UUID pedido1Id,
        UUID pedido2Id
) throws SQLException {
    try (Connection conexao = abrirConexaoTeste(fabrica)) {
        conexao.setAutoCommit(false);

        try {
            conferirMarcador(
                    conexao,
                    "SELECT nome FROM departamento WHERE id = ?",
                    departamentoId,
                    NOME_DEPARTAMENTO
            );
            conferirMarcador(
                    conexao,
                    "SELECT nome FROM recurso_catalogo WHERE id = ?",
                    recursoId,
                    NOME_RECURSO
            );

            // Confere ambos os vínculos antes de excluir qualquer registro.
            for (UUID pedidoId : new UUID[]{pedido1Id, pedido2Id}) {
                try (PreparedStatement comando = conexao.prepareStatement(
                        "SELECT departamento_id FROM requisicao WHERE id = ?"
                )) {
                    comando.setString(1, pedidoId.toString());

                    try (ResultSet resultado = comando.executeQuery()) {
                        if (resultado.next()
                                && !departamentoId.toString().equals(
                                        resultado.getString(1)
                                )) {
                            throw new IllegalStateException(
                                    "Pedido de outro departamento; limpeza cancelada."
                            );
                        }
                    }
                }
            }

            for (UUID pedidoId : new UUID[]{pedido1Id, pedido2Id}) {
                excluir(
                        conexao,
                        "DELETE FROM item_requisicao WHERE requisicao_id = ?",
                        pedidoId.toString()
                );
                excluir(
                        conexao,
                        "DELETE FROM requisicao WHERE id = ? AND departamento_id = ?",
                        pedidoId.toString(),
                        departamentoId.toString()
                );
            }

            excluir(
                    conexao,
                    "DELETE FROM recurso_catalogo WHERE id = ? AND nome = ?",
                    recursoId.toString(),
                    NOME_RECURSO
            );
            excluir(
                    conexao,
                    "DELETE FROM departamento WHERE id = ? AND nome = ?",
                    departamentoId.toString(),
                    NOME_DEPARTAMENTO
            );

            conexao.commit();
        } finally {
            conexao.rollback();
        }
    }
}

private static void verificarValidacaoComPrecoHistorico(
        FabricaConexoes fabrica
) throws SQLException {
    UUID departamentoId = UUID.randomUUID();
    UUID recursoId = UUID.randomUUID();
    UUID requisicaoId = UUID.randomUUID();

    System.out.println("\nCenario: validacao com preco historico");
    System.out.println("Departamento do teste: " + departamentoId);
    System.out.println("Recurso do teste: " + recursoId);
    System.out.println("Requisicao do teste: " + requisicaoId);

    try {
        // Pedido criado com preço unitário de 12.50 e total de 62.50.
        preparar(fabrica, departamentoId, recursoId, requisicaoId);

        // Altera somente o catálogo, em transação separada.
        try (Connection conexao = abrirConexaoTeste(fabrica)) {
            conexao.setAutoCommit(false);

            try {
                try (PreparedStatement comando = conexao.prepareStatement(
                        "UPDATE recurso_catalogo SET custo_mensal = ? "
                                + "WHERE id = ? AND nome = ?"
                )) {
                    comando.setBigDecimal(1, new BigDecimal("999.00"));
                    comando.setString(2, recursoId.toString());
                    comando.setString(3, NOME_RECURSO);

                    if (comando.executeUpdate() != 1) {
                        throw new IllegalStateException(
                                "A alteracao do catalogo deve afetar um registro."
                        );
                    }
                }

                conexao.commit();
            } finally {
                conexao.rollback();
            }
        }

        ValidarRequisicaoPersistenteUseCase caso =
                new ValidarRequisicaoPersistenteUseCase(
                        new ValidacaoRequisicaoJdbc(
                                () -> abrirConexaoTeste(fabrica)
                        )
                );

        ResultadoValidacaoPersistente resultado =
                caso.executar(requisicaoId);

        conferir(
                resultado.resultado() == ResultadoValidacao.LIBERADA
                        && !resultado.falhaNoFechamento()
                        && igual(
                                resultado.requisicao().getCustoTotalProjetado(),
                                "62.50"
                        )
                        && igual(
                                resultado.requisicao()
                                        .getDepartamento()
                                        .getSaldoDisponivel(),
                                "737.50"
                        ),
                "Validacao desconta o total historico apos alteracao do catalogo"
        );

        var itens = resultado.requisicao().getItens();

        conferir(
                itens.size() == 2
                        && itens.get(0).getQuantidade() == 2
                        && itens.get(1).getQuantidade() == 3
                        && igual(itens.get(0).getPrecoUnitario(), "12.50")
                        && igual(itens.get(1).getPrecoUnitario(), "12.50")
                        && igual(itens.get(0).getCustoSubtotal(), "25.00")
                        && igual(itens.get(1).getCustoSubtotal(), "37.50")
                        && igual(
                                itens.get(0).getRecurso().getCustoMensal(),
                                "999.00"
                        )
                        && igual(
                                itens.get(1).getRecurso().getCustoMensal(),
                                "999.00"
                        ),
                "Itens preservam precos historicos com catalogo atualizado"
        );

        conferirPersistido(fabrica, departamentoId, requisicaoId);
    } finally {
        limpar(fabrica, departamentoId, recursoId, requisicaoId);
    }

    try (Connection conexao = abrirConexaoTeste(fabrica)) {
        conferir(
                contar(conexao, "departamento", "id", departamentoId) == 0
                        && contar(conexao, "recurso_catalogo", "id", recursoId) == 0
                        && contar(conexao, "requisicao", "id", requisicaoId) == 0
                        && contar(
                                conexao,
                                "item_requisicao",
                                "requisicao_id",
                                requisicaoId
                        ) == 0,
                "Cenario de preco historico confirma limpeza"
        );
    }
}

private static void verificarValidacaoCustoZero(
        FabricaConexoes fabrica
) throws SQLException {
    UUID departamentoId = UUID.randomUUID();
    UUID recursoId = UUID.randomUUID();
    UUID requisicaoId = UUID.randomUUID();

    System.out.println("\nCenario: validacao de pedido com custo zero");
    System.out.println("Departamento do teste: " + departamentoId);
    System.out.println("Recurso do teste: " + recursoId);
    System.out.println("Requisicao do teste: " + requisicaoId);

    try {
        try (Connection conexao = abrirConexaoTeste(fabrica)) {
            conexao.setAutoCommit(false);

            try {
                Departamento departamento = Departamento.reconstituir(
                        departamentoId,
                        NOME_DEPARTAMENTO,
                        new BigDecimal("1000.00"),
                        new BigDecimal("800.00")
                );

                RecursoCatalogo recurso = new RecursoCatalogo(
                        recursoId,
                        NOME_RECURSO,
                        new BigDecimal("0.00")
                );

                new DepartamentoRepositoryJdbc(conexao)
                        .adicionar(departamento);
                new RecursoCatalogoRepositoryJdbc(conexao)
                        .adicionar(recurso);

                Requisicao pedido = Requisicao.criarComId(
                        requisicaoId, departamento
                );
                pedido.adicionarItem(new ItemRequisicao(recurso, 2));

                new RequisicaoRepositoryJdbc(conexao).adicionar(pedido);
                conexao.commit();
            } finally {
                conexao.rollback();
            }
        }

        ValidarRequisicaoPersistenteUseCase caso =
                new ValidarRequisicaoPersistenteUseCase(
                        new ValidacaoRequisicaoJdbc(
                                () -> abrirConexaoTeste(fabrica)
                        )
                );

        ResultadoValidacaoPersistente resultado =
                caso.executar(requisicaoId);

        conferir(
                resultado.resultado() == ResultadoValidacao.LIBERADA
                        && !resultado.falhaNoFechamento()
                        && igual(
                                resultado.requisicao().getCustoTotalProjetado(),
                                "0.00"
                        )
                        && igual(
                                resultado.requisicao()
                                        .getDepartamento()
                                        .getSaldoDisponivel(),
                                "800.00"
                        ),
                "Pedido gratuito e liberado sem alterar saldo"
        );

        ResultadoValidacaoPersistente repeticao =
                caso.executar(requisicaoId);

        conferir(
                repeticao.resultado() == ResultadoValidacao.JA_LIBERADA
                        && !repeticao.falhaNoFechamento()
                        && igual(
                                repeticao.requisicao()
                                        .getDepartamento()
                                        .getSaldoDisponivel(),
                                "800.00"
                        ),
                "Repeticao do pedido gratuito preserva saldo"
        );

        try (Connection conexao = abrirConexaoTeste(fabrica)) {
            conexao.setTransactionIsolation(
                    Connection.TRANSACTION_READ_COMMITTED
            );

            Requisicao armazenada =
                    new RequisicaoRepositoryJdbc(conexao)
                            .buscarPorId(requisicaoId)
                            .orElseThrow();

            conferir(
                    "Provisionamento Liberado".equals(armazenada.getStatus())
                            && armazenada.getMotivoRevisao() == null
                            && armazenada.getDepartamento()
                                    .getId().equals(departamentoId)
                            && igual(
                                    armazenada.getDepartamento()
                                            .getSaldoDisponivel(),
                                    "800.00"
                            )
                            && igual(
                                    armazenada.getCustoTotalProjetado(),
                                    "0.00"
                            )
                            && armazenada.getItens().size() == 1
                            && armazenada.getItens().get(0).getQuantidade() == 2
                            && igual(
                                    armazenada.getItens().get(0).getPrecoUnitario(),
                                    "0.00"
                            )
                            && igual(
                                    armazenada.getItens().get(0).getCustoSubtotal(),
                                    "0.00"
                            ),
                    "Outra conexao confirma liberacao gratuita e item preservado"
            );

            conferir(
                    contar(conexao, "requisicao", "id", requisicaoId) == 1
                            && contar(
                                    conexao,
                                    "item_requisicao",
                                    "requisicao_id",
                                    requisicaoId
                            ) == 1,
                    "Banco preserva um pedido gratuito e seu item"
            );
        }
    } finally {
        limpar(fabrica, departamentoId, recursoId, requisicaoId);
    }

    try (Connection conexao = abrirConexaoTeste(fabrica)) {
        conferir(
                contar(conexao, "departamento", "id", departamentoId) == 0
                        && contar(conexao, "recurso_catalogo", "id", recursoId) == 0
                        && contar(conexao, "requisicao", "id", requisicaoId) == 0
                        && contar(
                                conexao,
                                "item_requisicao",
                                "requisicao_id",
                                requisicaoId
                        ) == 0,
                "Cenario de custo zero confirma limpeza"
        );
    }
}

private static void verificarTotalInconsistente(
        FabricaConexoes fabrica
) throws SQLException {
    UUID departamentoId = UUID.randomUUID();
    UUID recursoId = UUID.randomUUID();
    UUID requisicaoId = UUID.randomUUID();

    System.out.println("\nCenario: total armazenado inconsistente");
    System.out.println("Departamento do teste: " + departamentoId);
    System.out.println("Recurso do teste: " + recursoId);
    System.out.println("Requisicao do teste: " + requisicaoId);

    try {
        preparar(fabrica, departamentoId, recursoId, requisicaoId);

        try (Connection conexao = abrirConexaoTeste(fabrica)) {
            conexao.setAutoCommit(false);

            try {
                try (PreparedStatement comando = conexao.prepareStatement(
                        "UPDATE requisicao SET custo_total = ? "
                                + "WHERE id = ? AND departamento_id = ?"
                )) {
                    comando.setBigDecimal(1, new BigDecimal("63.50"));
                    comando.setString(2, requisicaoId.toString());
                    comando.setString(3, departamentoId.toString());

                    if (comando.executeUpdate() != 1) {
                        throw new IllegalStateException(
                                "A preparacao deve alterar exatamente um pedido."
                        );
                    }
                }

                conexao.commit();
            } finally {
                conexao.rollback();
            }
        }

        ValidarRequisicaoPersistenteUseCase caso =
                new ValidarRequisicaoPersistenteUseCase(
                        new ValidacaoRequisicaoJdbc(
                                () -> abrirConexaoTeste(fabrica)
                        )
                );

        boolean rejeitada = false;

        try {
            caso.executar(requisicaoId);
        } catch (FalhaValidacaoPersistenteException esperada) {
            rejeitada = true;
        }

        conferir(
                rejeitada,
                "Validacao rejeita total diferente da soma dos itens"
        );

        // Consulta SQL direta porque o repositório deve rejeitar
        // a reconstituição desse pedido inconsistente.
        try (Connection conexao = abrirConexaoTeste(fabrica)) {
            try (PreparedStatement comando = conexao.prepareStatement("""
                    SELECT r.estado, r.motivo_revisao, r.custo_total,
                           d.saldo_disponivel
                    FROM requisicao r
                    JOIN departamento d ON d.id = r.departamento_id
                    WHERE r.id = ? AND d.id = ?
                    """)) {
                comando.setString(1, requisicaoId.toString());
                comando.setString(2, departamentoId.toString());

                try (ResultSet dados = comando.executeQuery()) {
                    conferir(
                            dados.next()
                                    && "INICIAL".equals(dados.getString("estado"))
                                    && dados.getString("motivo_revisao") == null
                                    && igual(
                                            dados.getBigDecimal("saldo_disponivel"),
                                            "800.00"
                                    )
                                    && igual(
                                            dados.getBigDecimal("custo_total"),
                                            "63.50"
                                    ),
                            "Rejeicao preserva saldo e estado sem corrigir dados silenciosamente"
                    );
                }
            }

            try (PreparedStatement comando = conexao.prepareStatement("""
                    SELECT COUNT(*), SUM(subtotal)
                    FROM item_requisicao
                    WHERE requisicao_id = ?
                    """)) {
                comando.setString(1, requisicaoId.toString());

                try (ResultSet dados = comando.executeQuery()) {
                    conferir(
                            dados.next()
                                    && dados.getLong(1) == 2
                                    && igual(dados.getBigDecimal(2), "62.50"),
                            "Rejeicao preserva os dois itens e seus subtotais"
                    );
                }
            }
        }
    } finally {
        limpar(fabrica, departamentoId, recursoId, requisicaoId);
    }

    try (Connection conexao = abrirConexaoTeste(fabrica)) {
        conferir(
                contar(conexao, "departamento", "id", departamentoId) == 0
                        && contar(conexao, "recurso_catalogo", "id", recursoId) == 0
                        && contar(conexao, "requisicao", "id", requisicaoId) == 0
                        && contar(
                                conexao,
                                "item_requisicao",
                                "requisicao_id",
                                requisicaoId
                        ) == 0,
                "Cenario de total inconsistente confirma limpeza"
        );
    }
}

private static void verificarIsolamentoEntreDepartamentos(
        FabricaConexoes fabrica
) throws SQLException {
    UUID departamentoA = UUID.randomUUID();
    UUID recursoA = UUID.randomUUID();
    UUID pedidoA = UUID.randomUUID();

    UUID departamentoB = UUID.randomUUID();
    UUID recursoB = UUID.randomUUID();
    UUID pedidoB = UUID.randomUUID();

    System.out.println("\nCenario: isolamento entre departamentos");
    System.out.println("Departamento A: " + departamentoA);
    System.out.println("Recurso A: " + recursoA);
    System.out.println("Pedido A: " + pedidoA);
    System.out.println("Departamento B: " + departamentoB);
    System.out.println("Recurso B: " + recursoB);
    System.out.println("Pedido B: " + pedidoB);

    try {
        preparar(fabrica, departamentoA, recursoA, pedidoA);
        preparar(fabrica, departamentoB, recursoB, pedidoB);

        ValidarRequisicaoPersistenteUseCase caso =
                new ValidarRequisicaoPersistenteUseCase(
                        new ValidacaoRequisicaoJdbc(
                                () -> abrirConexaoTeste(fabrica)
                        )
                );

        try (Connection bloqueadora = abrirConexaoTeste(fabrica)) {
            bloqueadora.setTransactionIsolation(
                    Connection.TRANSACTION_READ_COMMITTED
            );
            bloqueadora.setAutoCommit(false);

            try (var executor = Executors.newSingleThreadExecutor()) {
                try {
                    try (PreparedStatement comando =
                                 bloqueadora.prepareStatement(
                                         "SELECT id FROM departamento "
                                                 + "WHERE id = ? FOR UPDATE"
                                 )) {
                        comando.setString(1, departamentoA.toString());

                        try (ResultSet dados = comando.executeQuery()) {
                            if (!dados.next()) {
                                throw new IllegalStateException(
                                        "Departamento A nao encontrado."
                                );
                            }
                        }
                    }

                    // A permanece bloqueado enquanto B é validado.
                    var tarefa = executor.submit(
                            () -> caso.executar(pedidoB)
                    );

                    ResultadoValidacaoPersistente resultado =
                            tarefa.get(10, TimeUnit.SECONDS);

                    conferir(
                            resultado.resultado() == ResultadoValidacao.LIBERADA
                                    && !resultado.falhaNoFechamento()
                                    && resultado.requisicao().getId().equals(pedidoB)
                                    && igual(
                                            resultado.requisicao()
                                                    .getDepartamento()
                                                    .getSaldoDisponivel(),
                                            "737.50"
                                    ),
                            "Departamento B conclui validacao enquanto A esta bloqueado"
                    );
                } finally {
                    // Libera A antes de aguardar o encerramento do executor,
                    // inclusive quando o teste falhar por tempo excedido.
                    bloqueadora.rollback();
                }
            } catch (InterruptedException excecao) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "O teste de isolamento foi interrompido.",
                        excecao
                );
            } catch (java.util.concurrent.ExecutionException
                     | java.util.concurrent.TimeoutException excecao) {
                throw new IllegalStateException(
                        "Departamento B falhou ou nao concluiu com A bloqueado.",
                        excecao
                );
            }
        }

        try (Connection conexao = abrirConexaoTeste(fabrica)) {
            conexao.setTransactionIsolation(
                    Connection.TRANSACTION_READ_COMMITTED
            );

            Requisicao armazenada =
                    new RequisicaoRepositoryJdbc(conexao)
                            .buscarPorId(pedidoA)
                            .orElseThrow();

            conferir(
                    "INICIAL".equals(armazenada.getStatus())
                            && armazenada.getMotivoRevisao() == null
                            && igual(
                                    armazenada.getDepartamento()
                                            .getSaldoDisponivel(),
                                    "800.00"
                            ),
                    "Validacao de B preserva pedido e saldo do departamento A"
            );
        }

        conferirPersistido(fabrica, departamentoB, pedidoB);
    } finally {
        try {
            limpar(fabrica, departamentoA, recursoA, pedidoA);
        } finally {
            limpar(fabrica, departamentoB, recursoB, pedidoB);
        }
    }

    try (Connection conexao = abrirConexaoTeste(fabrica)) {
        boolean limpo = true;

        for (UUID id : new UUID[]{departamentoA, departamentoB}) {
            limpo &= contar(conexao, "departamento", "id", id) == 0;
        }

        for (UUID id : new UUID[]{recursoA, recursoB}) {
            limpo &= contar(conexao, "recurso_catalogo", "id", id) == 0;
        }

        for (UUID id : new UUID[]{pedidoA, pedidoB}) {
            limpo &= contar(conexao, "requisicao", "id", id) == 0;
            limpo &= contar(
                    conexao, "item_requisicao", "requisicao_id", id
            ) == 0;
        }

        conferir(
                limpo,
                "Cenario de isolamento confirma limpeza dos dois departamentos"
        );
    }
}
}