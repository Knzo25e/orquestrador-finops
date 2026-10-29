package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.dominio.entidades.Departamento;
import br.mackenzie.finops.dominio.entidades.ItemRequisicao;
import br.mackenzie.finops.dominio.entidades.RecursoCatalogo;
import br.mackenzie.finops.dominio.entidades.Requisicao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Recupera cada consulta em uma única instrução SQL.
 * Não altera entidades persistidas nem a configuração da conexão.
 */
final class LeitorRequisicaoJdbc {

    private static final String CONSULTA = """
            SELECT
                q.id AS requisicao_id,
                q.departamento_id AS vinculo_departamento,
                q.estado,
                q.custo_total,
                q.motivo_revisao,
                q.desenvolvedor_id,
                d.id AS departamento_id,
                d.nome AS departamento_nome,
                d.teto_mensal,
                d.saldo_disponivel,
                i.ordem,
                i.recurso_id AS vinculo_recurso,
                i.preco_unitario,
                i.quantidade,
                i.subtotal,
                r.id AS recurso_id,
                r.nome AS recurso_nome,
                r.custo_mensal
            FROM requisicao q
            LEFT JOIN departamento d ON d.id = q.departamento_id
            LEFT JOIN item_requisicao i ON i.requisicao_id = q.id
            LEFT JOIN recurso_catalogo r ON r.id = i.recurso_id
            """;

    private final Connection conexao;

    LeitorRequisicaoJdbc(Connection conexao) {
        if (conexao == null) {
            throw new IllegalArgumentException("A conexão é obrigatória.");
        }

        this.conexao = conexao;
    }

    Optional<Requisicao> buscarPorId(UUID id) {
        ValidacaoCadastroJdbc.validarId(
                id, "O identificador da requisição"
        );

        List<Requisicao> encontradas = consultar(
                CONSULTA + " WHERE q.id = ? ORDER BY i.ordem",
                id
        );

        if (encontradas.isEmpty()) {
            return Optional.empty();
        }

        if (encontradas.size() != 1
                || !id.equals(encontradas.get(0).getId())) {
            throw inconsistencia();
        }

        return Optional.of(encontradas.get(0));
    }

    List<Requisicao> listarPorDepartamento(UUID departamentoId) {
        ValidacaoCadastroJdbc.validarId(
                departamentoId, "O identificador do departamento"
        );

        List<Requisicao> encontradas = consultar(
                CONSULTA
                        + " WHERE q.departamento_id = ?"
                        + " ORDER BY q.id, i.ordem",
                departamentoId
        );

        for (Requisicao requisicao : encontradas) {
            if (!departamentoId.equals(
                    requisicao.getDepartamento().getId()
            )) {
                throw inconsistencia();
            }
        }

        return encontradas;
    }

    Optional<Requisicao> buscarPorIdEAutorEDepartamento(
            UUID id,
            UUID autorId,
            UUID departamentoId
    ) {
        ValidacaoCadastroJdbc.validarId(
                id, "O identificador da requisição"
        );
        ValidacaoCadastroJdbc.validarId(
                autorId, "O identificador do autor"
        );
        ValidacaoCadastroJdbc.validarId(
                departamentoId, "O identificador do departamento"
        );

        List<Requisicao> encontradas = consultar(
                CONSULTA
                        + " WHERE q.id = ?"
                        + " AND q.desenvolvedor_id = ?"
                        + " AND q.departamento_id = ?"
                        + " ORDER BY i.ordem",
                id,
                autorId,
                departamentoId
        );

        if (encontradas.isEmpty()) {
            return Optional.empty();
        }

        if (encontradas.size() != 1) {
            throw inconsistencia();
        }

        Requisicao pedido = encontradas.get(0);

        if (!id.equals(pedido.getId())
                || !autorId.equals(pedido.getDesenvolvedorId())
                || !departamentoId.equals(
                        pedido.getDepartamento().getId()
                )) {
            throw inconsistencia();
        }

        return Optional.of(pedido);
    }

    List<Requisicao> listarPorAutorEDepartamento(
            UUID autorId,
            UUID departamentoId
    ) {
        ValidacaoCadastroJdbc.validarId(
                autorId, "O identificador do autor"
        );
        ValidacaoCadastroJdbc.validarId(
                departamentoId, "O identificador do departamento"
        );

        List<Requisicao> encontradas = consultar(
                CONSULTA
                        + " WHERE q.desenvolvedor_id = ?"
                        + " AND q.departamento_id = ?"
                        + " ORDER BY q.id, i.ordem",
                autorId,
                departamentoId
        );

        for (Requisicao pedido : encontradas) {
            if (!autorId.equals(pedido.getDesenvolvedorId())
                    || !departamentoId.equals(
                            pedido.getDepartamento().getId()
                    )) {
                throw inconsistencia();
            }
        }

        return encontradas;
    }

    private List<Requisicao> consultar(String sql, UUID... parametros) {
        try {
            conferirIsolamento();

            try (PreparedStatement comando = conexao.prepareStatement(sql)) {
                for (int indice = 0; indice < parametros.length; indice++) {
                    comando.setString(
                            indice + 1,
                            parametros[indice].toString()
                    );
                }

                try (ResultSet resultado = comando.executeQuery()) {
                    Map<UUID, PedidoEmLeitura> pedidos = new LinkedHashMap<>();

                    while (resultado.next()) {
                        UUID id = ValidacaoCadastroJdbc.interpretarId(
                                resultado.getString("requisicao_id")
                        );

                        PedidoEmLeitura pedido = pedidos.get(id);

                        if (pedido == null) {
                            pedido = iniciarPedido(resultado, id);
                            pedidos.put(id, pedido);
                        }

                        adicionarItem(resultado, pedido);
                    }

                    List<Requisicao> requisicoes = new ArrayList<>();

                    for (PedidoEmLeitura pedido : pedidos.values()) {
                        requisicoes.add(Requisicao.reconstituir(
                                pedido.id,
                                pedido.departamento,
                                pedido.itens,
                                pedido.estado.status(),
                                pedido.total,
                                pedido.estado.motivoRevisao(),
                                pedido.desenvolvedorId
                        ));
                    }

                    return List.copyOf(requisicoes);
                }
            }
        } catch (SQLException excecao) {
            throw new FalhaPersistenciaException(
                    "Falha ao consultar requisições no banco."
            );
        } catch (IllegalArgumentException excecao) {
            throw inconsistencia();
        }
    }

    private void conferirIsolamento() throws SQLException {
        int isolamento = conexao.getTransactionIsolation();

        if (isolamento != Connection.TRANSACTION_READ_COMMITTED
                && isolamento != Connection.TRANSACTION_REPEATABLE_READ) {
            throw new IllegalStateException(
                    "A consulta exige READ COMMITTED ou REPEATABLE READ."
            );
        }
    }

    private static PedidoEmLeitura iniciarPedido(
            ResultSet resultado,
            UUID id
    ) throws SQLException {
        UUID departamentoId = ValidacaoCadastroJdbc.interpretarId(
                resultado.getString("departamento_id")
        );

        UUID vinculo = ValidacaoCadastroJdbc.interpretarId(
                resultado.getString("vinculo_departamento")
        );

        if (!departamentoId.equals(vinculo)) {
            throw inconsistencia();
        }

        String nome = ValidacaoCadastroJdbc.validarNome(
                resultado.getString("departamento_nome")
        );

        BigDecimal teto = moeda(resultado, "teto_mensal");
        BigDecimal saldo = moeda(resultado, "saldo_disponivel");
        ValidacaoCadastroJdbc.validarSaldo(teto, saldo);

        Departamento departamento = Departamento.reconstituir(
                departamentoId, nome, teto, saldo
        );

        EstadoRequisicaoJdbc.EstadoRecuperado estado =
                EstadoRequisicaoJdbc.recuperar(
                        resultado.getString("estado"),
                        resultado.getString("motivo_revisao")
                );

        String autorArmazenado = resultado.getString("desenvolvedor_id");

        UUID desenvolvedorId = autorArmazenado == null
                ? null
                : ValidacaoCadastroJdbc.interpretarId(autorArmazenado);

        return new PedidoEmLeitura(
                id,
                departamento,
                estado,
                moeda(resultado, "custo_total"),
                desenvolvedorId
        );
    }

    private static void adicionarItem(
            ResultSet resultado,
            PedidoEmLeitura pedido
    ) throws SQLException {
        int ordem = resultado.getInt("ordem");

        if (resultado.wasNull()
                || (long) ordem != (long) pedido.itens.size() + 1) {
            throw inconsistencia();
        }

        UUID recursoId = ValidacaoCadastroJdbc.interpretarId(
                resultado.getString("recurso_id")
        );

        UUID vinculo = ValidacaoCadastroJdbc.interpretarId(
                resultado.getString("vinculo_recurso")
        );

        if (!recursoId.equals(vinculo)) {
            throw inconsistencia();
        }

        String nome = ValidacaoCadastroJdbc.validarNome(
                resultado.getString("recurso_nome")
        );

        RecursoCatalogo recurso = new RecursoCatalogo(
                recursoId,
                nome,
                moeda(resultado, "custo_mensal")
        );

        int quantidade = resultado.getInt("quantidade");

        if (resultado.wasNull() || quantidade <= 0) {
            throw inconsistencia();
        }

        ItemRequisicao item = ItemRequisicao.reconstituir(
                recurso,
                quantidade,
                moeda(resultado, "preco_unitario"),
                moeda(resultado, "subtotal")
        );

        pedido.itens.add(item);
    }

    private static BigDecimal moeda(
            ResultSet resultado,
            String coluna
    ) throws SQLException {
        return ValidacaoCadastroJdbc.validarValorMonetario(
                resultado.getBigDecimal(coluna),
                "O valor armazenado em " + coluna
        );
    }

    private static FalhaPersistenciaException inconsistencia() {
        return new FalhaPersistenciaException(
                "A requisição armazenada contém dados ou vínculos inconsistentes."
        );
    }

    private static final class PedidoEmLeitura {
        private final UUID id;
        private final Departamento departamento;
        private final EstadoRequisicaoJdbc.EstadoRecuperado estado;
        private final BigDecimal total;
        private final UUID desenvolvedorId;
        private final List<ItemRequisicao> itens = new ArrayList<>();

        private PedidoEmLeitura(
                UUID id,
                Departamento departamento,
                EstadoRequisicaoJdbc.EstadoRecuperado estado,
                BigDecimal total,
                UUID desenvolvedorId
        ) {
            this.id = id;
            this.departamento = departamento;
            this.estado = estado;
            this.total = total;
            this.desenvolvedorId = desenvolvedorId;
        }
    }
}