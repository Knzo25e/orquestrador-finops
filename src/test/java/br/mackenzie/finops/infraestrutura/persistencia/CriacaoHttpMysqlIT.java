package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.aplicacao.CriarRequisicaoCoordenadaUseCase;
import br.mackenzie.finops.aplicacao.ValidarRequisicaoPersistenteUseCase;
import br.mackenzie.finops.aplicacao.seguranca.ContextoIdentidade;
import br.mackenzie.finops.aplicacao.seguranca.PerfilUsuario;
import br.mackenzie.finops.dominio.entidades.Departamento;
import br.mackenzie.finops.dominio.entidades.RecursoCatalogo;
import br.mackenzie.finops.dominio.entidades.ResultadoValidacao;
import br.mackenzie.finops.infraestrutura.http.RequisicaoController;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = CriacaoHttpMysqlIT.Configuracao.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.address=127.0.0.1"
)
class CriacaoHttpMysqlIT {

    private static final UUID AUTOR_ID = UUID.randomUUID();
    private static final UUID DEPARTAMENTO_ID = UUID.randomUUID();
    private static final UUID RECURSO_ID = UUID.randomUUID();
    private static final BigDecimal SALDO_INICIAL = new BigDecimal("1000.00");
    private static final BigDecimal SALDO_APOS_LIBERACAO = new BigDecimal("975.00");
    private static boolean cenarioPreparado;

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(excludeName = {
            "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
            "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration"
    })
    @Import(RequisicaoController.class)
    static class Configuracao {

        @Bean
        CriarRequisicaoCoordenadaUseCase criarRequisicao() {
            var identidade = new ContextoIdentidade(
                    AUTOR_ID, PerfilUsuario.DESENVOLVEDOR, true, DEPARTAMENTO_ID
            );

            return new CriarRequisicaoCoordenadaUseCase(
                    new IdentidadeCoordenadaTeste(AUTOR_ID, identidade),
                    new CriacaoRequisicaoJdbc(fabrica())
            );
        }
    }

    @BeforeAll
    static void prepararCenario() throws SQLException {
        try (var conexao = fabrica().abrir()) {
            assertEquals("finops_test", conexao.getCatalog());
            conexao.setAutoCommit(false);
            new DepartamentoRepositoryJdbc(conexao).adicionar(
                    new Departamento(DEPARTAMENTO_ID, "Departamento do IT", SALDO_INICIAL)
            );
            new RecursoCatalogoRepositoryJdbc(conexao).adicionar(
                    new RecursoCatalogo(RECURSO_ID, "Recurso do IT", new BigDecimal("12.50"))
            );
            conexao.commit();
            cenarioPreparado = true;
        }
    }

    @AfterAll
    static void limparCenario() throws SQLException {
        if (!cenarioPreparado) {
            return;
        }
        try (var conexao = fabrica().abrir()) {
            assertEquals("finops_test", conexao.getCatalog());
            conexao.setAutoCommit(false);
            excluir(conexao, """
                    DELETE FROM item_requisicao WHERE requisicao_id IN (
                        SELECT id FROM requisicao WHERE departamento_id = ? AND desenvolvedor_id = ?
                    )
                    """, DEPARTAMENTO_ID, AUTOR_ID);
            excluir(conexao, "DELETE FROM requisicao WHERE departamento_id = ? AND desenvolvedor_id = ?",
                    DEPARTAMENTO_ID, AUTOR_ID);
            excluir(conexao, "DELETE FROM recurso_catalogo WHERE id = ?", RECURSO_ID);
            excluir(conexao, "DELETE FROM departamento WHERE id = ?", DEPARTAMENTO_ID);
            conexao.commit();
        }
    }

    private static void excluir(Connection conexao, String sql, UUID... ids) throws SQLException {
        try (var comando = conexao.prepareStatement(sql)) {
            for (int i = 0; i < ids.length; i++) {
                comando.setString(i + 1, ids[i].toString());
            }
            comando.executeUpdate();
        }
    }

    @LocalServerPort
    private int porta;

    @Test
    void criaPorHttpValidaSeparadamenteERepeteSemNovoDebitoNoMysql() throws Exception {
        UUID pedidoId = UUID.randomUUID();

        conferirPost(pedidoId, 201, "CRIADA", "INICIAL");
        conferirPost(pedidoId, 200, "JA_EXISTENTE", "INICIAL");
        conferirConflito(pedidoId);
        conferirSaldo(SALDO_INICIAL);

        // Usa a entrada interna vigente. A submissão autorizada por HTTP tem contrato próprio.
        var validacao = new ValidarRequisicaoPersistenteUseCase(new ValidacaoRequisicaoJdbc(fabrica()));
        assertEquals(ResultadoValidacao.LIBERADA, validacao.executar(pedidoId).resultado());
        conferirSaldo(SALDO_APOS_LIBERACAO);
        assertEquals(ResultadoValidacao.JA_LIBERADA, validacao.executar(pedidoId).resultado());
        conferirPost(pedidoId, 200, "JA_EXISTENTE", "PROVISIONAMENTO_LIBERADO");
        conferirSaldo(SALDO_APOS_LIBERACAO);
    }

    @Test
    void recursoInexistenteRetorna422SemGravarPedidoNoMysql() throws Exception {
        var pedidoId = UUID.randomUUID();
        var recursoInexistenteId = UUID.randomUUID();
        var fabrica = fabrica();

        try (var conexao = fabrica.abrir()) {
            assertEquals("finops_test", conexao.getCatalog());

            var recursos = new RecursoCatalogoRepositoryJdbc(conexao);

            assertTrue(
                    recursos.buscarPorId(RECURSO_ID).isPresent(),
                    "O recurso válido do cenário precisa estar cadastrado."
            );

            assertTrue(
                    recursos.buscarPorId(recursoInexistenteId).isEmpty(),
                    "O recurso usado para provocar a rejeição deve estar ausente."
            );
        }

        var pedido = HttpRequest.newBuilder(
                        URI.create(
                                "http://127.0.0.1:" + porta
                                        + "/api/v1/requisicoes"
                        ))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {
                          "requisicaoId": "%s",
                          "itens": [
                            {"recursoId": "%s", "quantidade": 2},
                            {"recursoId": "%s", "quantidade": 1}
                          ]
                        }
                        """.formatted(
                                pedidoId,
                                RECURSO_ID,
                                recursoInexistenteId
                        )))
                .build();

        try (var cliente = HttpClient.newHttpClient()) {
            var resposta = cliente.send(
                    pedido,
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(422, resposta.statusCode(), resposta.body());

            var json = new ObjectMapper();
            var esperado = json.readTree("""
                    {
                      "erro": {
                        "codigo": "SOLICITACAO_INVALIDA",
                        "mensagem": "A solicitação não pode ser atendida com os dados informados."
                      }
                    }
                    """);

            assertEquals(esperado, json.readTree(resposta.body()));
        }

        try (var conexao = fabrica.abrir()) {
            var requisicoes = new RequisicaoRepositoryJdbc(conexao);

            assertTrue(
                    requisicoes.buscarPorId(pedidoId).isEmpty(),
                    "Uma solicitação rejeitada não deve deixar o pedido gravado."
            );
        }
    }

    private void conferirPost(UUID pedidoId, int statusEsperado, String situacaoEsperada, String estadoEsperado)
            throws Exception {
        var resposta = enviar(pedidoId, 2);

        assertEquals(statusEsperado, resposta.statusCode(), resposta.body());

        var corpo = new ObjectMapper().readTree(resposta.body());
        assertEquals(situacaoEsperada, corpo.get("situacao").stringValue());

        var gravada = corpo.get("requisicao");
        assertEquals(pedidoId.toString(), gravada.get("id").stringValue());
        assertEquals(estadoEsperado, gravada.get("estado").stringValue());
        assertEquals("25.00", gravada.get("custoTotalProjetado").stringValue());
    }

    private void conferirSaldo(BigDecimal esperado) throws SQLException {
        try (var conexao = fabrica().abrir()) {
            var departamento = new DepartamentoRepositoryJdbc(conexao)
                    .buscarPorId(DEPARTAMENTO_ID).orElseThrow();
            assertEquals(esperado, departamento.getSaldoDisponivel());
        }
    }

    private void conferirConflito(UUID pedidoId) throws Exception {
        var resposta = enviar(pedidoId, 3);

        assertEquals(409, resposta.statusCode(), resposta.body());

        var json = new ObjectMapper();
        var esperado = json.readTree("""
                {
                  "erro": {
                    "codigo": "CONFLITO_IDENTIFICADOR",
                    "mensagem": "O identificador já está associado a uma solicitação diferente."
                  }
                }
                """);
        assertEquals(esperado, json.readTree(resposta.body()));
    }

    private HttpResponse<String> enviar(UUID pedidoId, int quantidade) throws Exception {
        var pedido = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + porta + "/api/v1/requisicoes"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {
                          "requisicaoId": "%s",
                          "itens": [{"recursoId": "%s", "quantidade": %d}]
                        }
                        """.formatted(pedidoId, RECURSO_ID, quantidade)))
                .build();

        try (var cliente = HttpClient.newHttpClient()) {
            return cliente.send(pedido, HttpResponse.BodyHandlers.ofString());
        }
    }

    private static FabricaConexoes fabrica() {
        return new FabricaConexoes(ConfiguracaoBanco.carregarDoAmbiente());
    }
}
