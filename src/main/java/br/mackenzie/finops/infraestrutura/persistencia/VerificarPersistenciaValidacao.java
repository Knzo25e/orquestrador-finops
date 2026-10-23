package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.aplicacao.ValidarRequisicaoPersistenteUseCase;
import br.mackenzie.finops.dominio.entidades.ItemRequisicao;
import br.mackenzie.finops.dominio.entidades.Requisicao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

public final class VerificarPersistenciaValidacao {

    public static void main(String[] args) {
        try {
            executar(args);
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

    private static void executar(String[] args) throws SQLException {
        if (args.length != 4
                || !List.of("liberar", "repetir").contains(args[0])) {
            throw new IllegalArgumentException(
                    "Informe: liberar|repetir "
                            + "departamentoUUID recursoUUID requisicaoUUID"
            );
        }

        String operacao = args[0];
        UUID departamentoId = UUID.fromString(args[1]);
        UUID recursoId = UUID.fromString(args[2]);
        UUID requisicaoId = UUID.fromString(args[3]);

        FabricaConexoes fabrica = new FabricaConexoes(
                ConfiguracaoBanco.carregarDoAmbiente()
        );

        // Confere os dados antes de executar a operação financeira.
        try (Connection conexao = abrirConexaoTeste(fabrica)) {
            Requisicao pedido = buscar(conexao, requisicaoId);

            conferirPedido(
                    pedido,
                    departamentoId,
                    recursoId,
                    requisicaoId,
                    "liberar".equals(operacao) ? "INICIAL" : "Provisionamento Liberado",
                    "liberar".equals(operacao) ? "800.00" : "737.50"
            );
        }

        ValidarRequisicaoPersistenteUseCase caso =
                new ValidarRequisicaoPersistenteUseCase(
                        new ValidacaoRequisicaoJdbc(
                                () -> abrirConexaoTeste(fabrica)
                        )
                );

        var resultado = caso.executar(requisicaoId);

        String resultadoEsperado =
                "liberar".equals(operacao) ? "LIBERADA" : "JA_LIBERADA";

        exigir(
                resultadoEsperado.equals(resultado.resultado().name())
                        && !resultado.falhaNoFechamento(),
                "Resultado da validacao difere de " + resultadoEsperado + "."
        );

        conferirPedido(
                resultado.requisicao(),
                departamentoId,
                recursoId,
                requisicaoId,
                "Provisionamento Liberado",
                "737.50"
        );

        System.out.println(
                "PASSOU: processo retorna " + resultadoEsperado
                        + " com saldo de 737.50 e precos historicos."
        );

        // Uma conexão independente confirma o estado efetivamente gravado.
        try (Connection conexao = abrirConexaoTeste(fabrica)) {
            conferirPedido(
                    buscar(conexao, requisicaoId),
                    departamentoId,
                    recursoId,
                    requisicaoId,
                    "Provisionamento Liberado",
                    "737.50"
            );

            var pedidos = new RequisicaoRepositoryJdbc(conexao)
                    .listarPorDepartamento(departamentoId);

            exigir(
                    pedidos.size() == 1
                            && pedidos.get(0).getId().equals(requisicaoId),
                    "A validacao alterou a quantidade de pedidos."
            );
        }

        System.out.println(
                "PASSOU: outra conexao confirma pedido unico, "
                        + "dois itens e saldo com um unico desconto."
        );
    }

    private static Requisicao buscar(
            Connection conexao,
            UUID requisicaoId
    ) throws SQLException {
        return new RequisicaoRepositoryJdbc(conexao)
                .buscarPorId(requisicaoId)
                .orElseThrow(() -> new IllegalStateException(
                        "Requisicao do teste nao encontrada."
                ));
    }

    private static void conferirPedido(
            Requisicao pedido,
            UUID departamentoId,
            UUID recursoId,
            UUID requisicaoId,
            String estadoEsperado,
            String saldoEsperado
    ) {
        var departamento = pedido.getDepartamento();

               exigir(
                pedido.getId().equals(requisicaoId),
                "UUID do pedido diferente do esperado."
        );

        exigir(
                departamento.getId().equals(departamentoId),
                "UUID do departamento diferente do esperado."
        );

        exigir(
                "Teste entre processos / Departamento / Issue 65"
                        .equals(departamento.getNome()),
                "Nome do departamento inesperado: ["
                        + departamento.getNome() + "]"
        );

        exigir(
                igual(departamento.getOrcamentoMensal(), "1000.00"),
                "Orcamento mensal: esperado 1000.00; encontrado "
                        + departamento.getOrcamentoMensal()
        );

        exigir(
                igual(departamento.getSaldoDisponivel(), saldoEsperado),
                "Saldo: esperado " + saldoEsperado
                        + "; encontrado "
                        + departamento.getSaldoDisponivel()
        );

        exigir(
                estadoEsperado.equals(pedido.getStatus()),
                "Estado: esperado " + estadoEsperado
                        + "; encontrado " + pedido.getStatus()
        );

        exigir(
                pedido.getMotivoRevisao() == null,
                "Motivo de revisao: esperado null; encontrado "
                        + pedido.getMotivoRevisao()
        );

        exigir(
                igual(pedido.getCustoTotalProjetado(), "62.50"),
                "Total: esperado 62.50; encontrado "
                        + pedido.getCustoTotalProjetado()
        );
    }

    private static Connection abrirConexaoTeste(
            FabricaConexoes fabrica
    ) throws SQLException {
        Connection conexao = fabrica.abrir();

        try {
            conferirAmbiente(conexao);
            conexao.setTransactionIsolation(
                    Connection.TRANSACTION_READ_COMMITTED
            );
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

    private static void conferirAmbiente(Connection conexao)
            throws SQLException {
        try (
                Statement comando = conexao.createStatement();
                ResultSet resultado = comando.executeQuery(
                        "SELECT DATABASE(), CURRENT_USER()"
                )
        ) {
            exigir(
                    resultado.next()
                            && "finops_test".equals(resultado.getString(1))
                            && "finops_test_app@localhost".equals(
                                    resultado.getString(2)
                            ),
                    "Execucao permitida somente em finops_test "
                            + "com finops_test_app@localhost."
            );
        }
    }

    private static boolean igual(BigDecimal valor, String esperado) {
        return valor != null
                && valor.compareTo(new BigDecimal(esperado)) == 0;
    }

    private static void exigir(boolean condicao, String mensagem) {
        if (!condicao) {
            throw new IllegalStateException(mensagem);
        }
    }
}