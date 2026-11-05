package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.aplicacao.CriarRequisicaoCoordenadaUseCase;
import br.mackenzie.finops.aplicacao.seguranca.ContextoIdentidade;
import br.mackenzie.finops.aplicacao.seguranca.PerfilUsuario;
import br.mackenzie.finops.infraestrutura.http.RequisicaoController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(
        classes = CriacaoHttpMysqlIT.Configuracao.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.address=127.0.0.1"
)
class CriacaoHttpMysqlIT {

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(RequisicaoController.class)
    static class Configuracao {

        @Bean
        CriarRequisicaoCoordenadaUseCase criarRequisicao() {
            var configuracao = ConfiguracaoBanco.carregarDoAmbiente();
            var fabrica = new FabricaConexoes(configuracao);

            var autor = idDoCenario("FINOPS_IT_AUTOR");
            var departamento = idDoCenario("FINOPS_IT_DEPARTAMENTO");
            var identidade = new ContextoIdentidade(
                    autor, PerfilUsuario.DESENVOLVEDOR, true, departamento
            );

            return new CriarRequisicaoCoordenadaUseCase(
                    new IdentidadeCoordenadaTeste(autor, identidade),
                    new CriacaoRequisicaoJdbc(fabrica)
            );
        }
    }

    @LocalServerPort
    private int porta;

    @Test
    void criaRepeteERejeitaAlteracaoDoMesmoPedidoNoMysql() throws Exception {
        UUID pedidoId = UUID.randomUUID();

        conferirPost(pedidoId, 201, "CRIADA");
        conferirPost(pedidoId, 200, "JA_EXISTENTE");
        conferirConflito(pedidoId);
    }

        @Test
    void recursoInexistenteRetorna422SemGravarPedidoNoMysql() throws Exception {
        var pedidoId = UUID.randomUUID();
        var recursoInexistenteId = UUID.randomUUID();
        var recursoValidoId = idDoCenario("FINOPS_IT_RECURSO");

        var fabrica = new FabricaConexoes(
                ConfiguracaoBanco.carregarDoAmbiente()
        );

        try (var conexao = fabrica.abrir()) {
            assertEquals("finops_test", conexao.getCatalog());

            var recursos = new RecursoCatalogoRepositoryJdbc(conexao);

            org.junit.jupiter.api.Assertions.assertTrue(
                    recursos.buscarPorId(recursoValidoId).isPresent(),
                    "O recurso válido do cenário precisa estar cadastrado."
            );

            org.junit.jupiter.api.Assertions.assertTrue(
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
                                recursoValidoId,
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

            org.junit.jupiter.api.Assertions.assertTrue(
                    requisicoes.buscarPorId(pedidoId).isEmpty(),
                    "Uma solicitação rejeitada não deve deixar o pedido gravado."
            );
        }
    }

    private void conferirPost(UUID pedidoId, int statusEsperado, String situacaoEsperada)
            throws Exception {
        var resposta = enviar(pedidoId, 2);

        assertEquals(statusEsperado, resposta.statusCode(), resposta.body());

        var corpo = new ObjectMapper().readTree(resposta.body());
        assertEquals(situacaoEsperada, corpo.get("situacao").stringValue());

        var gravada = corpo.get("requisicao");
        assertEquals(pedidoId.toString(), gravada.get("id").stringValue());
        assertEquals("INICIAL", gravada.get("estado").stringValue());
        assertEquals("25.00", gravada.get("custoTotalProjetado").stringValue());
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
        var recursoId = idDoCenario("FINOPS_IT_RECURSO");
        var pedido = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + porta + "/api/v1/requisicoes"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {
                          "requisicaoId": "%s",
                          "itens": [{"recursoId": "%s", "quantidade": %d}]
                        }
                        """.formatted(pedidoId, recursoId, quantidade)))
                .build();

        try (var cliente = HttpClient.newHttpClient()) {
            return cliente.send(pedido, HttpResponse.BodyHandlers.ofString());
        }
    }

    private static UUID idDoCenario(String variavel) {
        String valor = System.getenv(variavel);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Defina a variável de teste " + variavel);
        }
        return UUID.fromString(valor);
    }
}