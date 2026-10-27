package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.dominio.entidades.ItemRequisicao;
import br.mackenzie.finops.dominio.entidades.Requisicao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

public final class VerificarAutoriaJdbc {

    private static int verificacoes;

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "Informe: requisicaoAntigaUUID departamentoUUID recursoUUID"
            );
        }

        UUID pedidoAntigoId = UUID.fromString(args[0]);
        UUID departamentoId = UUID.fromString(args[1]);
        UUID recursoId = UUID.fromString(args[2]);

        FabricaConexoes fabrica = new FabricaConexoes(
                ConfiguracaoBanco.carregarDoAmbiente()
        );

        UUID pedidoNovoId = UUID.randomUUID();
        UUID autorId = UUID.randomUUID();

        try (Connection conexao = abrirTeste(fabrica)) {
            conexao.setAutoCommit(false);

            try {
                RequisicaoRepositoryJdbc pedidos =
                        new RequisicaoRepositoryJdbc(conexao);

                Requisicao antigo = pedidos.buscarPorId(pedidoAntigoId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Pedido anterior a migracao nao encontrado."
                        ));

                conferir(
                        antigo.getDesenvolvedorId() == null,
                        "Pedido anterior a migracao permanece sem autor"
                );

                conferir(
                        departamentoId.equals(
                                antigo.getDepartamento().getId()
                        )
                                && "INICIAL".equals(antigo.getStatus())
                                && antigo.getMotivoRevisao() == null
                                && igual(
                                        antigo.getCustoTotalProjetado(),
                                        "25.00"
                                )
                                && igual(
                                        antigo.getDepartamento()
                                                .getSaldoDisponivel(),
                                        "800.00"
                                ),
                        "Pedido antigo preserva departamento, estado e valores"
                );

                conferir(
                        antigo.getItens().size() == 1
                                && recursoId.equals(
                                        antigo.getItens().get(0)
                                                .getRecurso().getId()
                                )
                                && antigo.getItens().get(0)
                                        .getQuantidade() == 2
                                && igual(
                                        antigo.getItens().get(0)
                                                .getPrecoUnitario(),
                                        "12.50"
                                ),
                        "Pedido antigo preserva item e preco historico"
                );

                Requisicao novo = Requisicao.criarComId(
                        pedidoNovoId,
                        antigo.getDepartamento(),
                        autorId
                );

                novo.adicionarItem(new ItemRequisicao(
                        antigo.getItens().get(0).getRecurso(),
                        2
                ));

                pedidos.adicionar(novo);

                Requisicao recuperado = pedidos.buscarPorId(pedidoNovoId)
                        .orElseThrow();

                conferir(
                        autorId.equals(recuperado.getDesenvolvedorId())
                                && recuperado != novo,
                        "Busca reconstitui nova instancia com o autor gravado"
                );

                var lista = pedidos.listarPorDepartamento(departamentoId);

                conferir(
                        lista.stream().anyMatch(pedido ->
                                pedidoNovoId.equals(pedido.getId())
                                        && autorId.equals(
                                                pedido.getDesenvolvedorId()
                                        )
                        ),
                        "Listagem preserva autoria do pedido novo"
                );

                conferir(
                        lista.stream().anyMatch(pedido ->
                                pedidoAntigoId.equals(pedido.getId())
                                        && pedido.getDesenvolvedorId() == null
                        ),
                        "Listagem preserva autoria desconhecida do pedido antigo"
                );

                conferir(
                        "INICIAL".equals(recuperado.getStatus())
                                && igual(
                                        recuperado.getCustoTotalProjetado(),
                                        "25.00"
                                )
                                && igual(
                                        recuperado.getDepartamento()
                                                .getSaldoDisponivel(),
                                        "800.00"
                                ),
                        "Cadastro com autoria preserva estado, total e saldo"
                );
            } finally {
                conexao.rollback();
            }
        }

        try (Connection conexao = abrirTeste(fabrica)) {
            RequisicaoRepositoryJdbc pedidos =
                    new RequisicaoRepositoryJdbc(conexao);

            conferir(
                    pedidos.buscarPorId(pedidoNovoId).isEmpty(),
                    "Outra conexao confirma rollback do pedido novo"
            );

            conferir(
                    pedidos.buscarPorId(pedidoAntigoId)
                            .orElseThrow()
                            .getDesenvolvedorId() == null,
                    "Rollback preserva o pedido anterior a migracao"
            );
        }

        System.out.println(
                "Todas as " + verificacoes + " verificacoes passaram."
        );
    }

    private static Connection abrirTeste(FabricaConexoes fabrica)
            throws Exception {
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
        } catch (Exception falha) {
            try {
                conexao.close();
            } catch (Exception fechamento) {
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