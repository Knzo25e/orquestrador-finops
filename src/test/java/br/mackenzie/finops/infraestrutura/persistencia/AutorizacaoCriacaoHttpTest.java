package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.aplicacao.CriarRequisicaoCoordenadaUseCase;
import br.mackenzie.finops.aplicacao.portas.CriacaoRequisicaoAutorizadaPersistente;
import br.mackenzie.finops.aplicacao.seguranca.ContextoIdentidade;
import br.mackenzie.finops.aplicacao.seguranca.PerfilUsuario;
import br.mackenzie.finops.infraestrutura.http.RequisicaoController;
import br.mackenzie.finops.aplicacao.entradas.ItemSolicitado;
import br.mackenzie.finops.aplicacao.entradas.SolicitacaoCriacaoRequisicao;
import br.mackenzie.finops.aplicacao.resultados.ResultadoCriacaoRequisicao;
import br.mackenzie.finops.dominio.entidades.Departamento;
import br.mackenzie.finops.dominio.entidades.ItemRequisicao;
import br.mackenzie.finops.dominio.entidades.RecursoCatalogo;
import br.mackenzie.finops.dominio.entidades.Requisicao;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(
        classes = AutorizacaoCriacaoHttpTest.Configuracao.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.address=127.0.0.1"
)
class AutorizacaoCriacaoHttpTest {

    private static final UUID USUARIO = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID DEPARTAMENTO = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(excludeName = {
            "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
            "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration"
    })
    @Import(RequisicaoController.class)
    static class Configuracao {

        @Bean
        IdentidadeCoordenadaTeste identidades() {
            return new IdentidadeCoordenadaTeste(USUARIO, null);
        }

        @Bean
        CriarRequisicaoCoordenadaUseCase criarRequisicao(
                IdentidadeCoordenadaTeste identidades,
                CriacaoRequisicaoAutorizadaPersistente persistencia
        ) {
            return new CriarRequisicaoCoordenadaUseCase(identidades, persistencia);
        }
    }

    @Autowired
    private IdentidadeCoordenadaTeste identidades;

    @MockitoBean
    private CriacaoRequisicaoAutorizadaPersistente persistencia;

    @LocalServerPort
    private int porta;

    @ParameterizedTest
    @ValueSource(strings = {"ausente", "inativo", "arquiteto", "sem departamento"})
    void bloqueiaAntesDaPersistenciaMesmoComCabecalhosDoCliente(String cenario)
            throws Exception {
        ContextoIdentidade identidade = switch (cenario) {
            case "ausente" -> null;
            case "inativo" -> new ContextoIdentidade(
                    USUARIO, PerfilUsuario.DESENVOLVEDOR, false, DEPARTAMENTO);
            case "arquiteto" -> new ContextoIdentidade(
                    USUARIO, PerfilUsuario.ARQUITETO_CLOUD, true, DEPARTAMENTO);
            case "sem departamento" -> new ContextoIdentidade(
                    USUARIO, PerfilUsuario.DESENVOLVEDOR, true, null);
            default -> throw new IllegalArgumentException(cenario);
        };
        identidades.atualizar(identidade);

        var pedido = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + porta + "/api/v1/requisicoes"))
                .header("Content-Type", "application/json")
                .header("X-Perfil", "DESENVOLVEDOR")
                .header("X-Usuario", USUARIO.toString())
                .header("X-Departamento", DEPARTAMENTO.toString())
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

            assertEquals(403, resposta.statusCode(), resposta.body());
            var json = new ObjectMapper();
            var esperado = json.readTree("""
                    {"erro":{"codigo":"ACESSO_NEGADO","mensagem":"Operação não autorizada."}}
                    """);
            assertEquals(esperado, json.readTree(resposta.body()));
            verifyNoInteractions(persistencia);
        }
    }

        @Test
    void desenvolvedorAtivoCriaComAutorEDepartamentoDoServidor() throws Exception {
        var identidade = new ContextoIdentidade(
                USUARIO, PerfilUsuario.DESENVOLVEDOR, true, DEPARTAMENTO);
        identidades.atualizar(identidade);

        var requisicaoId = UUID.fromString("0116c1b1-c55e-4ce7-b9e2-0dad46269507");
        var recursoId = UUID.fromString("ebcd32eb-7ac9-4938-be36-25efeb24083f");
        var itens = List.of(new ItemSolicitado(recursoId, 2));
        var esperada = SolicitacaoCriacaoRequisicao.comAutoria(
                requisicaoId, DEPARTAMENTO, itens, USUARIO);

        var departamento = new Departamento(DEPARTAMENTO, "Engenharia", new BigDecimal("1000.00"));
        var recurso = new RecursoCatalogo(recursoId, "Armazenamento", new BigDecimal("12.50"));
        var criada = Requisicao.criarComId(requisicaoId, departamento, USUARIO);
        criada.adicionarItem(new ItemRequisicao(recurso, 2));
        when(persistencia.executar(esperada, identidade))
                .thenReturn(ResultadoCriacaoRequisicao.criada(criada));

        var pedido = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + porta + "/api/v1/requisicoes"))
                .header("Content-Type", "application/json")
                .header("X-Perfil", "ARQUITETO_CLOUD")
                .header("X-Usuario", "00000000-0000-0000-0000-000000000099")
                .header("X-Departamento", "00000000-0000-0000-0000-000000000098")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {
                          "requisicaoId": "%s",
                          "itens": [{"recursoId": "%s", "quantidade": 2}]
                        }
                        """.formatted(requisicaoId, recursoId)))
                .build();

        try (var cliente = HttpClient.newHttpClient()) {
            var resposta = cliente.send(pedido, HttpResponse.BodyHandlers.ofString());

            assertEquals(201, resposta.statusCode(), resposta.body());
            verify(persistencia).executar(esperada, identidade);

            var corpo = new ObjectMapper().readTree(resposta.body());
            assertEquals("CRIADA", corpo.get("situacao").stringValue());
            assertEquals(requisicaoId.toString(), corpo.get("requisicao").get("id").stringValue());
            assertEquals("INICIAL", corpo.get("requisicao").get("estado").stringValue());
        }
    }
}
