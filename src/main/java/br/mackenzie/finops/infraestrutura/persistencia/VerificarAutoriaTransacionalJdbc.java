package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.aplicacao.CriarRequisicaoPersistenteUseCase;
import br.mackenzie.finops.aplicacao.ValidarRequisicaoPersistenteUseCase;
import br.mackenzie.finops.aplicacao.entradas.ItemSolicitado;
import br.mackenzie.finops.aplicacao.excecoes.ConflitoIdentificadorException;
import br.mackenzie.finops.aplicacao.resultados.ResultadoCriacaoRequisicao;
import br.mackenzie.finops.dominio.entidades.Departamento;
import br.mackenzie.finops.dominio.entidades.RecursoCatalogo;
import br.mackenzie.finops.dominio.entidades.Requisicao;
import br.mackenzie.finops.dominio.entidades.ResultadoValidacao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

public final class VerificarAutoriaTransacionalJdbc {

    private static int verificacoes;

    public static void main(String[] args) throws Exception {
        FabricaConexoes fabrica = new FabricaConexoes(
                ConfiguracaoBanco.carregarDoAmbiente()
        );

        UUID departamentoId = UUID.randomUUID();
        UUID recursoId = UUID.randomUUID();
        UUID pedidoId = UUID.randomUUID();
        UUID autorId = UUID.randomUUID();
        UUID outroAutorId = UUID.randomUUID();

        System.out.println("Departamento: " + departamentoId);
        System.out.println("Recurso: " + recursoId);
        System.out.println("Requisicao: " + pedidoId);

        FornecedorConexaoJdbc fornecedor = () -> abrirTeste(fabrica);

        CriarRequisicaoPersistenteUseCase criar =
                new CriarRequisicaoPersistenteUseCase(
                        new CriacaoRequisicaoJdbc(fornecedor)
                );

        ValidarRequisicaoPersistenteUseCase validar =
                new ValidarRequisicaoPersistenteUseCase(
                        new ValidacaoRequisicaoJdbc(fornecedor)
                );

        List<ItemSolicitado> itens = List.of(
                new ItemSolicitado(recursoId, 2)
        );

        try {
            preparar(fabrica, departamentoId, recursoId);

            var criacao = criar.executar(
                    pedidoId, departamentoId, itens, autorId
            );

            conferir(
                    criacao.situacao()
                            == ResultadoCriacaoRequisicao.Situacao.CRIADA
                            && !criacao.falhaNoFechamento()
                            && autorId.equals(
                                    criacao.requisicao().getDesenvolvedorId()
                            ),
                    "Criacao transacional confirma pedido com autor"
            );

            conferirPedido(
                    fabrica, pedidoId, autorId, "INICIAL", "800.00"
            );

            var repeticao = criar.executar(
                    pedidoId, departamentoId, itens, autorId
            );

            conferir(
                    repeticao.situacao()
                            == ResultadoCriacaoRequisicao.Situacao.JA_EXISTENTE
                            && !repeticao.falhaNoFechamento()
                            && autorId.equals(
                                    repeticao.requisicao().getDesenvolvedorId()
                            ),
                    "Mesmo autor reconhece criacao equivalente"
            );

            exigirConflito(
                    () -> criar.executar(
                            pedidoId, departamentoId, itens, outroAutorId
                    ),
                    "Outro autor nao reutiliza o pedido existente"
            );

            exigirConflito(
                    () -> criar.executar(
                            pedidoId, departamentoId, itens
                    ),
                    "Chamada sem autoria nao assume pedido com autor"
            );

            conferirPedido(
                    fabrica, pedidoId, autorId, "INICIAL", "800.00"
            );

            var liberacao = validar.executar(pedidoId);

            conferir(
                    liberacao.resultado() == ResultadoValidacao.LIBERADA
                            && !liberacao.falhaNoFechamento()
                            && autorId.equals(
                                    liberacao.requisicao()
                                            .getDesenvolvedorId()
                            ),
                    "Validacao financeira retorna pedido com autor preservado"
            );

            conferirPedido(
                    fabrica,
                    pedidoId,
                    autorId,
                    "Provisionamento Liberado",
                    "775.00"
            );

            var novaValidacao = validar.executar(pedidoId);

            conferir(
                    novaValidacao.resultado()
                            == ResultadoValidacao.JA_LIBERADA
                            && !novaValidacao.falhaNoFechamento()
                            && autorId.equals(
                                    novaValidacao.requisicao()
                                            .getDesenvolvedorId()
                            ),
                    "Repeticao financeira preserva autoria"
            );

            var criacaoAposLiberacao = criar.executar(
                    pedidoId, departamentoId, itens, autorId
            );

            conferir(
                    criacaoAposLiberacao.situacao()
                            == ResultadoCriacaoRequisicao.Situacao.JA_EXISTENTE
                            && !criacaoAposLiberacao.falhaNoFechamento()
                            && autorId.equals(
                                    criacaoAposLiberacao.requisicao()
                                            .getDesenvolvedorId()
                            )
                            && "Provisionamento Liberado".equals(
                                    criacaoAposLiberacao.requisicao()
                                            .getStatus()
                            ),
                    "Repeticao de criacao preserva autor e pedido liberado"
            );

            conferirPedido(
                    fabrica,
                    pedidoId,
                    autorId,
                    "Provisionamento Liberado",
                    "775.00"
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
                    new RequisicaoRepositoryJdbc(conexao)
                            .buscarPorId(pedidoId).isEmpty()
                            && new DepartamentoRepositoryJdbc(conexao)
                                    .buscarPorId(departamentoId).isEmpty()
                            && new RecursoCatalogoRepositoryJdbc(conexao)
                                    .buscarPorId(recursoId).isEmpty(),
                    "Outra conexao confirma limpeza dos registros do teste"
            );
        }

        System.out.println(
                "Todas as " + verificacoes + " verificacoes passaram."
        );
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
                                "Teste autoria transacional / Issue 74",
                                new BigDecimal("1000.00"),
                                new BigDecimal("800.00")
                        )
                );

                new RecursoCatalogoRepositoryJdbc(conexao).adicionar(
                        new RecursoCatalogo(
                                recursoId,
                                "Recurso autoria transacional / Issue 74",
                                new BigDecimal("12.50")
                        )
                );

                conexao.commit();
            } finally {
                conexao.rollback();
            }
        }
    }

    private static void conferirPedido(
            FabricaConexoes fabrica,
            UUID pedidoId,
            UUID autorId,
            String estado,
            String saldo
    ) throws SQLException {
        try (Connection conexao = abrirTeste(fabrica)) {
            Requisicao pedido = new RequisicaoRepositoryJdbc(conexao)
                    .buscarPorId(pedidoId)
                    .orElseThrow();

            conferir(
                    autorId.equals(pedido.getDesenvolvedorId())
                            && estado.equals(pedido.getStatus())
                            && pedido.getMotivoRevisao() == null
                            && pedido.getItens().size() == 1
                            && pedido.getItens().get(0).getQuantidade() == 2
                            && igual(
                                    pedido.getItens().get(0).getPrecoUnitario(),
                                    "12.50"
                            )
                            && igual(pedido.getCustoTotalProjetado(), "25.00")
                            && igual(
                                    pedido.getDepartamento().getSaldoDisponivel(),
                                    saldo
                            ),
                    "Outra conexao confirma autor, itens, estado "
                            + estado + " e saldo " + saldo
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
                // Somente os UUIDs gerados por esta execucao.
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

    private static void exigirConflito(
            Runnable operacao,
            String descricao
    ) {
        try {
            operacao.run();
        } catch (ConflitoIdentificadorException esperada) {
            conferir(true, descricao);
            return;
        }

        throw new IllegalStateException("FALHOU: " + descricao);
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