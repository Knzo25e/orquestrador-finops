package br.mackenzie.finops.infraestrutura.http;

import br.mackenzie.finops.dominio.entidades.RecursoCatalogo;
import java.math.BigDecimal;
import tools.jackson.databind.ObjectMapper;
import br.mackenzie.finops.aplicacao.ListarCatalogoAutorizadoUseCase;
import br.mackenzie.finops.aplicacao.seguranca.ContextoIdentidade;
import br.mackenzie.finops.aplicacao.seguranca.PerfilUsuario;
import br.mackenzie.finops.aplicacao.portas.ConsultaCatalogoPersistente;
import br.mackenzie.finops.aplicacao.portas.ProvedorIdentidade;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = CatalogoHttpTest.Configuracao.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.address=127.0.0.1"
)
class CatalogoHttpTest {

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(CatalogoController.class)
    static class Configuracao {

               @Bean
        ListarCatalogoAutorizadoUseCase listarCatalogo(
                ProvedorIdentidade identidades,
                ConsultaCatalogoPersistente consulta
        ) {
            return new ListarCatalogoAutorizadoUseCase(identidades, consulta);
        }
    }

    @MockitoBean
    private ProvedorIdentidade identidades;

    @MockitoBean
    private ConsultaCatalogoPersistente consulta;

    @LocalServerPort
    private int porta;

    @Test
    void desenvolvedorAtivoSemDepartamentoConsultaCatalogoVazio() throws Exception {

                var desenvolvedor = new ContextoIdentidade(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                PerfilUsuario.DESENVOLVEDOR,
                true,
                null
        );
        when(identidades.obterIdentidadeAtual()).thenReturn(Optional.of(desenvolvedor));
        when(consulta.listar()).thenReturn(List.of());

        URI uri = URI.create(
                "http://127.0.0.1:" + porta + "/api/v1/catalogo/recursos"
        );
        HttpRequest pedido = HttpRequest.newBuilder(uri).GET().build();

        try (HttpClient cliente = HttpClient.newHttpClient()) {
            var resposta = cliente.send(pedido, HttpResponse.BodyHandlers.ofString());

            assertEquals(200, resposta.statusCode(), resposta.body());
            String tipo = resposta.headers().firstValue("Content-Type").orElse("");
            assertTrue(tipo.startsWith("application/json"), tipo);
            assertEquals("{\"recursos\":[]}", resposta.body());
        }
    }

        @Test
    void identidadeAusenteBloqueiaConsultaMesmoComPerfilInformadoPeloCliente()
            throws Exception {
        when(identidades.obterIdentidadeAtual()).thenReturn(Optional.empty());

        URI uri = URI.create(
                "http://127.0.0.1:" + porta + "/api/v1/catalogo/recursos"
        );
        HttpRequest pedido = HttpRequest.newBuilder(uri)
                .header("X-Perfil", "ARQUITETO_CLOUD")
                .header("X-Usuario", "00000000-0000-0000-0000-000000000001")
                .GET()
                .build();

        try (HttpClient cliente = HttpClient.newHttpClient()) {
            var resposta = cliente.send(pedido, HttpResponse.BodyHandlers.ofString());

            assertEquals(403, resposta.statusCode(), resposta.body());
            assertEquals(
                    "{\"erro\":{\"codigo\":\"ACESSO_NEGADO\","
                            + "\"mensagem\":\"Operação não autorizada.\"}}",
                    resposta.body()
            );
            verifyNoInteractions(consulta);
        }
    }

        @Test
    void arquitetoInativoComDepartamentoRecebe403SemConsultarCatalogo()
            throws Exception {
        var arquitetoInativo = new ContextoIdentidade(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                PerfilUsuario.ARQUITETO_CLOUD,
                false,
                UUID.fromString("00000000-0000-0000-0000-000000000010")
        );
        when(identidades.obterIdentidadeAtual())
                .thenReturn(Optional.of(arquitetoInativo));

        URI uri = URI.create(
                "http://127.0.0.1:" + porta + "/api/v1/catalogo/recursos"
        );
        HttpRequest pedido = HttpRequest.newBuilder(uri).GET().build();

        try (HttpClient cliente = HttpClient.newHttpClient()) {
            var resposta = cliente.send(pedido, HttpResponse.BodyHandlers.ofString());

            assertEquals(403, resposta.statusCode(), resposta.body());
            assertEquals(
                    "{\"erro\":{\"codigo\":\"ACESSO_NEGADO\","
                            + "\"mensagem\":\"Operação não autorizada.\"}}",
                    resposta.body()
            );
            verifyNoInteractions(consulta);
        }
    }

        @Test
    void arquitetoAtivoSemDepartamentoRecebeCatalogoEmOrdemComCustosEmTexto()
            throws Exception {
        var arquiteto = new ContextoIdentidade(
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                PerfilUsuario.ARQUITETO_CLOUD,
                true,
                null
        );
        when(identidades.obterIdentidadeAtual()).thenReturn(Optional.of(arquiteto));

        var armazenamento = new RecursoCatalogo(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "Armazenamento",
                new BigDecimal("12.5")
        );
        var processamento = new RecursoCatalogo(
                UUID.fromString("80000000-0000-0000-0000-000000000002"),
                "Processamento",
                new BigDecimal("80")
        );
        when(consulta.listar()).thenReturn(List.of(armazenamento, processamento));

        URI uri = URI.create(
                "http://127.0.0.1:" + porta + "/api/v1/catalogo/recursos"
        );
        HttpRequest pedido = HttpRequest.newBuilder(uri).GET().build();

        try (HttpClient cliente = HttpClient.newHttpClient()) {
            var resposta = cliente.send(pedido, HttpResponse.BodyHandlers.ofString());

            assertEquals(200, resposta.statusCode(), resposta.body());

            var json = new ObjectMapper();
            var esperado = json.readTree("""
                    {
                      "recursos": [
                        {
                          "id": "00000000-0000-0000-0000-000000000001",
                          "nome": "Armazenamento",
                          "custoMensal": "12.50"
                        },
                        {
                          "id": "80000000-0000-0000-0000-000000000002",
                          "nome": "Processamento",
                          "custoMensal": "80.00"
                        }
                      ]
                    }
                    """);
            assertEquals(esperado, json.readTree(resposta.body()));
        }
    }

        @Test
    void falhaDaConsultaRetorna500SemExporDetalhesInternos() throws Exception {
        var desenvolvedor = new ContextoIdentidade(
                UUID.fromString("00000000-0000-0000-0000-000000000004"),
                PerfilUsuario.DESENVOLVEDOR,
                true,
                null
        );
        when(identidades.obterIdentidadeAtual()).thenReturn(Optional.of(desenvolvedor));
        when(consulta.listar()).thenThrow(
                new IllegalStateException("Detalhe interno simulado: leitura JDBC falhou.")
        );

        URI uri = URI.create(
                "http://127.0.0.1:" + porta + "/api/v1/catalogo/recursos"
        );
        HttpRequest pedido = HttpRequest.newBuilder(uri).GET().build();

        try (HttpClient cliente = HttpClient.newHttpClient()) {
            var resposta = cliente.send(pedido, HttpResponse.BodyHandlers.ofString());

            assertEquals(500, resposta.statusCode(), resposta.body());

            var json = new ObjectMapper();
            var esperado = json.readTree("""
                    {
                      "erro": {
                        "codigo": "FALHA_INTERNA",
                        "mensagem": "Não foi possível consultar o catálogo."
                      }
                    }
                    """);
            assertEquals(esperado, json.readTree(resposta.body()));
        }

        org.mockito.Mockito.verify(consulta).listar();
    }
}