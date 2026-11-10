package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.aplicacao.resultados.FotografiaRequisicao;
import br.mackenzie.finops.dominio.entidades.Departamento;
import br.mackenzie.finops.dominio.entidades.ItemRequisicao;
import br.mackenzie.finops.dominio.entidades.RecursoCatalogo;
import br.mackenzie.finops.dominio.entidades.Requisicao;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MigracaoEstadosMysqlIT {
    @Test
    void migraDadosAnterioresSemPerderAutoriaItensPrecosOuMotivos() throws Exception {
        var schema = "finops_test_migracao_" + UUID.randomUUID().toString().replace("-", "");
        var fabrica = new FabricaConexoes(ConfiguracaoBanco.carregarDoAmbiente());
        try (var conexao = fabrica.abrir()) {
            assertEquals("finops_test", conexao.getCatalog());
            try (var comando = conexao.createStatement()) {
                comando.execute("CREATE DATABASE " + schema);
            }
            try {
                conexao.setCatalog(schema);
                for (String script : List.of("V001__criar_tabelas.sql", "V002__adicionar_autoria_requisicao.sql",
                        "V003__criar_usuarios.sql", "V004__criar_intervencoes.sql")) {
                    aplicar(conexao, script);
                }
                conexao.setAutoCommit(false);
                var departamento = new Departamento(UUID.randomUUID(), "Legado", new BigDecimal("1000.00"));
                var recurso = new RecursoCatalogo(UUID.randomUUID(), "Recurso", new BigDecimal("12.50"));
                new DepartamentoRepositoryJdbc(conexao).adicionar(departamento);
                new RecursoCatalogoRepositoryJdbc(conexao).adicionar(recurso);
                var ids = new ArrayList<UUID>();
                var autor = UUID.randomUUID();
                for (int i = 0; i < 3; i++) {
                    UUID id = UUID.randomUUID();
                    ids.add(id);
                    var pedido = i == 0 ? Requisicao.criarComId(id, departamento) : Requisicao.criarComId(id, departamento, autor);
                    pedido.adicionarItem(new ItemRequisicao(recurso, 1));
                    new RequisicaoRepositoryJdbc(conexao).adicionar(pedido);
                }
                CenarioUsuariosMysql.executar(conexao,
                        "UPDATE requisicao SET estado = 'REVISAO_PENDENTE', motivo_revisao = 'RESERVA' WHERE id = ?", ids.get(1).toString());
                CenarioUsuariosMysql.executar(conexao,
                        "UPDATE requisicao SET estado = 'PROVISIONAMENTO_LIBERADO' WHERE id = ?", ids.get(2).toString());
                CenarioUsuariosMysql.executar(conexao,
                        "UPDATE departamento SET saldo_disponivel = 987.50 WHERE id = ?", departamento.getId().toString());
                conexao.commit();
                conexao.setAutoCommit(true);
                var antes = capturar(conexao);
                aplicar(conexao, "V005__ampliar_estados_requisicao.sql");
                assertEquals(antes, capturar(conexao));
                var repositorio = new RequisicaoRepositoryJdbc(conexao);
                assertNull(repositorio.buscarPorId(ids.getFirst()).orElseThrow().getDesenvolvedorId());
                assertEquals(autor, repositorio.buscarPorId(ids.get(1)).orElseThrow().getDesenvolvedorId());
                assertEquals("RESERVA", FotografiaRequisicao.de(repositorio.buscarPorId(ids.get(1)).orElseThrow()).motivoRevisao());
                for (String estado : List.of("AGUARDANDO_AJUSTE_ORCAMENTARIO", "AGUARDANDO_AUTORIZACAO_FINANCEIRA",
                        "AGUARDANDO_LIBERACAO_FINANCEIRA", "REJEITADO")) {
                    CenarioUsuariosMysql.executar(conexao, "UPDATE requisicao SET estado = ?, motivo_revisao = NULL WHERE id = ?",
                            estado, ids.getFirst().toString());
                    var foto = FotografiaRequisicao.de(repositorio.buscarPorId(ids.getFirst()).orElseThrow());
                    assertEquals(estado, foto.estado());
                    assertEquals("12.50", foto.itens().getFirst().precoUnitario());
                    assertNull(foto.motivoRevisao());
                }
                assertEquals(3819, assertThrows(SQLException.class, () -> CenarioUsuariosMysql.executar(conexao,
                        "UPDATE requisicao SET motivo_revisao = 'RESERVA' WHERE id = ?", ids.getFirst().toString())).getErrorCode());
                assertEquals(3819, assertThrows(SQLException.class, () -> CenarioUsuariosMysql.executar(conexao,
                        "UPDATE requisicao SET estado = 'DESCONHECIDO' WHERE id = ?", ids.getFirst().toString())).getErrorCode());
            } finally {
                if (!conexao.getAutoCommit()) {
                    conexao.rollback();
                }
                conexao.setAutoCommit(true);
                conexao.setCatalog("finops_test");
                // Nome exclusivo gerado neste teste, sem dados externos.
                try (var comando = conexao.createStatement()) {
                    comando.execute("DROP DATABASE " + schema);
                }
            }
        }
    }

    private static void aplicar(Connection conexao, String nome) throws Exception {
        try (var recurso = MigracaoEstadosMysqlIT.class.getResourceAsStream("/db/mysql/" + nome)) {
            assertNotNull(recurso);
            String sql = new String(recurso.readAllBytes(), StandardCharsets.UTF_8).replaceAll("(?m)--[^\\r\\n]*", "");
            for (String instrucao : sql.split(";")) {
                if (!instrucao.isBlank()) {
                    try (var comando = conexao.createStatement()) {
                        comando.execute(instrucao);
                    }
                }
            }
        }
    }

    private static List<String> capturar(Connection conexao) throws SQLException {
        var linhas = new ArrayList<String>();
        try (var consulta = conexao.createStatement(); var dados = consulta.executeQuery("""
                SELECT q.id, q.estado, q.motivo_revisao, q.desenvolvedor_id, q.custo_total,
                       i.ordem, i.preco_unitario, i.quantidade, i.subtotal, d.teto_mensal, d.saldo_disponivel
                FROM requisicao q JOIN item_requisicao i ON i.requisicao_id = q.id
                JOIN departamento d ON d.id = q.departamento_id ORDER BY q.id, i.ordem
                """)) {
            while (dados.next()) {
                for (int coluna = 1; coluna <= 11; coluna++) {
                    linhas.add(dados.getString(coluna));
                }
            }
        }
        return linhas;
    }
}
