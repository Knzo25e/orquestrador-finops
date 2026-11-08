package br.mackenzie.finops.infraestrutura.http;

import br.mackenzie.finops.aplicacao.CriarRequisicaoCoordenadaUseCase;
import br.mackenzie.finops.aplicacao.entradas.ItemSolicitado;
import br.mackenzie.finops.aplicacao.resultados.ResultadoCriacaoRequisicao;
import br.mackenzie.finops.dominio.entidades.Departamento;
import br.mackenzie.finops.dominio.entidades.ItemRequisicao;
import br.mackenzie.finops.dominio.entidades.RecursoCatalogo;
import br.mackenzie.finops.dominio.entidades.Requisicao;
import br.mackenzie.finops.aplicacao.excecoes.ConflitoIdentificadorException;
import br.mackenzie.finops.aplicacao.excecoes.FalhaCriacaoPersistenteException;
import br.mackenzie.finops.aplicacao.excecoes.AcessoNegadoException;
import br.mackenzie.finops.aplicacao.excecoes.ConfirmacaoCriacaoDesconhecidaException;



import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = RequisicaoHttpTest.Configuracao.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.address=127.0.0.1"
)
class RequisicaoHttpTest {

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(excludeName = {
            "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
            "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration"
    })
@Import({
        RequisicaoController.class,
        TratamentoErrosHttp.class
})
    static class Configuracao {
    }

    @MockitoBean
    private CriarRequisicaoCoordenadaUseCase criarRequisicao;

    @LocalServerPort
    private int porta;

    @Test
    void quantidadeEmTextoRetorna400AntesDaCriacao() throws Exception {
        String corpo = """
                {
                  "requisicaoId": "0116c1b1-c55e-4ce7-b9e2-0dad46269507",
                  "itens": [
                    {
                      "recursoId": "ebcd32eb-7ac9-4938-be36-25efeb24083f",
                      "quantidade": "2"
                    }
                  ]
                }
                """;

        var resposta = enviar(corpo);

        conferirErro(resposta, 400, "ENTRADA_INVALIDA",
                "O corpo da requisição é inválido.");
        verifyNoInteractions(criarRequisicao);
    }

    @Test
    void corpoCom65537BytesRetorna413AntesDaCriacao() throws Exception {
        // Cada espaço ocupa um byte em UTF-8.
        var resposta = enviar(" ".repeat(65_537));

        conferirErro(resposta, 413, "CORPO_MUITO_GRANDE",
                "O corpo da requisição excede o limite de 65536 bytes.");
        verifyNoInteractions(criarRequisicao);
    }
        @Test
    void criacaoConfirmadaRetorna201ComPedidoInicialEValoresEmTexto() throws Exception {
        var requisicaoId = UUID.fromString("0116c1b1-c55e-4ce7-b9e2-0dad46269507");
        var recursoId = UUID.fromString("ebcd32eb-7ac9-4938-be36-25efeb24083f");
        var itensEsperados = List.of(new ItemSolicitado(recursoId, 2));

        var departamento = new Departamento(
                UUID.randomUUID(), "Engenharia", new BigDecimal("1000.00")
        );
        var recurso = new RecursoCatalogo(recursoId, "Armazenamento", new BigDecimal("12.50"));
        var requisicao = Requisicao.criarComId(requisicaoId, departamento, UUID.randomUUID());
        requisicao.adicionarItem(new ItemRequisicao(recurso, 2));

        when(criarRequisicao.executar(requisicaoId, itensEsperados))
                .thenReturn(ResultadoCriacaoRequisicao.criada(requisicao));

        var resposta = enviar("""
                {
                  "requisicaoId": "%s",
                  "itens": [{"recursoId": "%s", "quantidade": 2}]
                }
                """.formatted(requisicaoId, recursoId));

        assertEquals(201, resposta.statusCode(), resposta.body());
        verify(criarRequisicao).executar(requisicaoId, itensEsperados);

        var json = new ObjectMapper();
        var esperado = json.readTree("""
                {
                  "situacao": "CRIADA",
                  "requisicao": {
                    "id": "%s",
                    "estado": "INICIAL",
                    "motivoRevisao": null,
                    "custoTotalProjetado": "25.00",
                    "itens": [{
                      "recursoId": "%s",
                      "quantidade": 2,
                      "precoUnitario": "12.50",
                      "subtotal": "25.00"
                    }]
                  },
                  "avisos": []
                }
                """.formatted(requisicaoId, recursoId));

        assertEquals(esperado, json.readTree(resposta.body()));
    }

        @Test
    void repeticaoReconhecidaRetorna200ComEstadoAtualDoPedido() throws Exception {
        var requisicaoId = UUID.fromString("0116c1b1-c55e-4ce7-b9e2-0dad46269507");
        var recursoId = UUID.fromString("ebcd32eb-7ac9-4938-be36-25efeb24083f");
        var itensEsperados = List.of(new ItemSolicitado(recursoId, 2));

        var departamento = new Departamento(
                UUID.randomUUID(), "Engenharia", new BigDecimal("1000.00")
        );
        var recurso = new RecursoCatalogo(recursoId, "Armazenamento", new BigDecimal("12.50"));
        var existente = Requisicao.criarComId(requisicaoId, departamento, UUID.randomUUID());
        existente.adicionarItem(new ItemRequisicao(recurso, 2));

        // A validação ocorreu antes da nova tentativa de POST.
        existente.processarRoteamento();
        when(criarRequisicao.executar(requisicaoId, itensEsperados))
                .thenReturn(ResultadoCriacaoRequisicao.jaExistente(existente));

        var resposta = enviar("""
                {
                  "requisicaoId": "%s",
                  "itens": [{"recursoId": "%s", "quantidade": 2}]
                }
                """.formatted(requisicaoId, recursoId));

        assertEquals(200, resposta.statusCode(), resposta.body());
        verify(criarRequisicao).executar(requisicaoId, itensEsperados);

        var json = new ObjectMapper().readTree(resposta.body());
        assertEquals("JA_EXISTENTE", json.get("situacao").stringValue());

        var pedido = json.get("requisicao");
        assertEquals(requisicaoId.toString(), pedido.get("id").stringValue());
        assertEquals("PROVISIONAMENTO_LIBERADO", pedido.get("estado").stringValue());
        assertEquals("25.00", pedido.get("custoTotalProjetado").stringValue());
        assertEquals("12.50", pedido.get("itens").get(0).get("precoUnitario").stringValue());
    }

        @Test
    void conflitoInformadoPelaAplicacaoRetorna409() throws Exception {
        var requisicaoId = UUID.fromString("0116c1b1-c55e-4ce7-b9e2-0dad46269507");
        var recursoId = UUID.fromString("ebcd32eb-7ac9-4938-be36-25efeb24083f");
        var itensAlterados = List.of(new ItemSolicitado(recursoId, 3));

        when(criarRequisicao.executar(requisicaoId, itensAlterados))
                .thenThrow(new ConflitoIdentificadorException(requisicaoId));

        var resposta = enviar("""
                {
                  "requisicaoId": "%s",
                  "itens": [{"recursoId": "%s", "quantidade": 3}]
                }
                """.formatted(requisicaoId, recursoId));

        conferirErro(resposta, 409, "CONFLITO_IDENTIFICADOR",
                "O identificador já está associado a uma solicitação diferente.");
        verify(criarRequisicao).executar(requisicaoId, itensAlterados);
    }

        @Test
    void negativaDaAplicacaoRetorna403SemExporDadosDoPedido() throws Exception {
        var requisicaoId = UUID.fromString("0116c1b1-c55e-4ce7-b9e2-0dad46269507");
        var recursoId = UUID.fromString("ebcd32eb-7ac9-4938-be36-25efeb24083f");
        var itens = List.of(new ItemSolicitado(recursoId, 2));

        when(criarRequisicao.executar(requisicaoId, itens))
                .thenThrow(new AcessoNegadoException());

        var resposta = enviar("""
                {
                  "requisicaoId": "%s",
                  "itens": [{"recursoId": "%s", "quantidade": 2}]
                }
                """.formatted(requisicaoId, recursoId));

        conferirErro(resposta, 403, "ACESSO_NEGADO", "Operação não autorizada.");
        verify(criarRequisicao).executar(requisicaoId, itens);
    }

        @Test
    void confirmacaoDesconhecidaRetorna503ComIdentificadorOriginal() throws Exception {
        var requisicaoId = UUID.fromString("0116c1b1-c55e-4ce7-b9e2-0dad46269507");
        var recursoId = UUID.fromString("ebcd32eb-7ac9-4938-be36-25efeb24083f");
        var itens = List.of(new ItemSolicitado(recursoId, 2));

        when(criarRequisicao.executar(requisicaoId, itens))
                .thenThrow(new ConfirmacaoCriacaoDesconhecidaException(requisicaoId));

        var resposta = enviar("""
                {
                  "requisicaoId": "%s",
                  "itens": [{"recursoId": "%s", "quantidade": 2}]
                }
                """.formatted(requisicaoId, recursoId));

        assertEquals(503, resposta.statusCode(), resposta.body());
        verify(criarRequisicao).executar(requisicaoId, itens);

        var json = new ObjectMapper();
        var esperado = json.readTree("""
                {
                  "erro": {
                    "codigo": "CONFIRMACAO_CRIACAO_DESCONHECIDA",
                    "mensagem": "Não foi possível confirmar o resultado da criação. Preserve o identificador e o conteúdo para uma nova tentativa.",
                    "requisicaoId": "%s"
                  }
                }
                """.formatted(requisicaoId));

        assertEquals(esperado, json.readTree(resposta.body()));
    }

        @ParameterizedTest
    @CsvSource({"CRIADA, 201", "JA_EXISTENTE, 200"})
    void falhaNoFechamentoPreservaSucessoEAvisaOCliente(
            ResultadoCriacaoRequisicao.Situacao situacao, int statusEsperado
    ) throws Exception {
        var requisicaoId = UUID.fromString("0116c1b1-c55e-4ce7-b9e2-0dad46269507");
        var recursoId = UUID.fromString("ebcd32eb-7ac9-4938-be36-25efeb24083f");
        var itens = List.of(new ItemSolicitado(recursoId, 2));

        var departamento = new Departamento(
                UUID.randomUUID(), "Engenharia", new BigDecimal("1000.00")
        );
        var recurso = new RecursoCatalogo(recursoId, "Armazenamento", new BigDecimal("12.50"));
        var requisicao = Requisicao.criarComId(requisicaoId, departamento, UUID.randomUUID());
        requisicao.adicionarItem(new ItemRequisicao(recurso, 2));

        var confirmado = switch (situacao) {
            case CRIADA -> ResultadoCriacaoRequisicao.criada(requisicao);
            case JA_EXISTENTE -> ResultadoCriacaoRequisicao.jaExistente(requisicao);
        };
        when(criarRequisicao.executar(requisicaoId, itens))
                .thenReturn(confirmado.comFalhaNoFechamento());

        var resposta = enviar("""
                {
                  "requisicaoId": "%s",
                  "itens": [{"recursoId": "%s", "quantidade": 2}]
                }
                """.formatted(requisicaoId, recursoId));

        assertEquals(statusEsperado, resposta.statusCode(), resposta.body());
        verify(criarRequisicao).executar(requisicaoId, itens);

        var json = new ObjectMapper();
        var corpo = json.readTree(resposta.body());
        assertEquals(situacao.name(), corpo.get("situacao").stringValue());
        assertEquals(requisicaoId.toString(), corpo.get("requisicao").get("id").stringValue());

        var avisosEsperados = json.readTree("""
                [{
                  "codigo": "FALHA_NO_FECHAMENTO",
                  "mensagem": "A operação foi concluída, mas houve uma falha ao encerrar seus recursos."
                }]
                """);
        assertEquals(avisosEsperados, corpo.get("avisos"));
    }

        @Test
    void falhaDePersistenciaRetorna500ComMensagemPublica() throws Exception {
        var requisicaoId = UUID.fromString("0116c1b1-c55e-4ce7-b9e2-0dad46269507");
        var recursoId = UUID.fromString("ebcd32eb-7ac9-4938-be36-25efeb24083f");
        var itens = List.of(new ItemSolicitado(recursoId, 2));

        when(criarRequisicao.executar(requisicaoId, itens))
                .thenThrow(new FalhaCriacaoPersistenteException());

        var resposta = enviar("""
                {
                  "requisicaoId": "%s",
                  "itens": [{"recursoId": "%s", "quantidade": 2}]
                }
                """.formatted(requisicaoId, recursoId));

        conferirErro(resposta, 500, "FALHA_INTERNA",
                "Não foi possível concluir a operação.");
        verify(criarRequisicao).executar(requisicaoId, itens);
    }

        @Test
    void postComTextoPlanoRetorna415AntesDaCriacao() throws Exception {
        var pedido = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + porta + "/api/v1/requisicoes"))
                .header("Content-Type", "text/plain")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {
                          "requisicaoId": "0116c1b1-c55e-4ce7-b9e2-0dad46269507",
                          "itens": [{
                            "recursoId": "ebcd32eb-7ac9-4938-be36-25efeb24083f",
                            "quantidade": 2
                          }]
                        }
                        """))
                .build();

        try (var cliente = HttpClient.newHttpClient()) {
            var resposta = cliente.send(pedido, HttpResponse.BodyHandlers.ofString());

            assertEquals(415, resposta.statusCode(), resposta.body());
            verifyNoInteractions(criarRequisicao);

            String tipo = resposta.headers().firstValue("Content-Type").orElse("");
            assertTrue(tipo.startsWith("application/json"), tipo);

            var corpo = new ObjectMapper().readTree(resposta.body());
            var erro = corpo.path("erro");
            assertEquals("TIPO_DE_CONTEUDO_NAO_SUPORTADO",
                    erro.path("codigo").asString(""), resposta.body());
            assertTrue(erro.path("mensagem").isString(), resposta.body());
        }
    }

        @Test
    void solicitacaoInvalidaRetorna422() throws Exception {
        var requisicaoId = UUID.fromString(
                "11111111-1111-4111-8111-111111111111"
        );
        var recursoId = UUID.fromString(
                "22222222-2222-4222-8222-222222222222"
        );

        var itens = java.util.List.of(
                new br.mackenzie.finops.aplicacao.entradas.ItemSolicitado(
                        recursoId, 2
                )
        );

        org.mockito.Mockito.when(
                criarRequisicao.executar(requisicaoId, itens)
        ).thenThrow(
                new br.mackenzie.finops.aplicacao.excecoes
                        .SolicitacaoInvalidaException(
                                "Detalhe interno que não deve aparecer na resposta."
                        )
        );

        var resposta = enviar("""
                {
                  "requisicaoId": "11111111-1111-4111-8111-111111111111",
                  "itens": [
                    {
                      "recursoId": "22222222-2222-4222-8222-222222222222",
                      "quantidade": 2
                    }
                  ]
                }
                """);

        conferirErro(
                resposta,
                422,
                "SOLICITACAO_INVALIDA",
                "A solicitação não pode ser atendida com os dados informados."
        );

        org.junit.jupiter.api.Assertions.assertFalse(
                resposta.body().contains("Detalhe interno"),
                resposta.body()
        );

        org.mockito.Mockito.verify(criarRequisicao)
                .executar(requisicaoId, itens);
    }

    private HttpResponse<String> enviar(String corpo) throws Exception {
        URI uri = URI.create("http://127.0.0.1:" + porta + "/api/v1/requisicoes");
        var pedido = HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(corpo))
                .build();

        try (var cliente = HttpClient.newHttpClient()) {
            return cliente.send(pedido, HttpResponse.BodyHandlers.ofString());
        }
    }

    private void conferirErro(HttpResponse<String> resposta, int status,
                              String codigo, String mensagem) throws Exception {
        assertEquals(status, resposta.statusCode(), resposta.body());

        String tipo = resposta.headers().firstValue("Content-Type").orElse("");
        assertTrue(tipo.startsWith("application/json"), tipo);

        var json = new ObjectMapper();
        var esperado = json.createObjectNode();
        esperado.putObject("erro")
                .put("codigo", codigo)
                .put("mensagem", mensagem);

        assertEquals(esperado, json.readTree(resposta.body()));
    }
}
