package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.aplicacao.entradas.ItemSolicitado;
import br.mackenzie.finops.aplicacao.entradas.SolicitacaoCriacaoRequisicao;
import br.mackenzie.finops.infraestrutura.servidor.FinopsServidor;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = FinopsServidor.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(SessaoMysqlIT.BancoRuntime.class)
class SessaoMysqlIT {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String REQUISICOES = "/api/v1/requisicoes";
    private static final String PREFIXO_PAGINACAO = UUID.randomUUID().toString().substring(0, 24);
    private static final Pattern CAMPO_CSRF = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");
    private static Path pastaTls;
    private static Path chaveTls;
    private static SSLContext tlsCliente;

    @TestConfiguration(proxyBeanMethods = false)
    static class BancoRuntime {
        @Bean
        @Primary
        FabricaConexoes runtime() {
            return new FabricaConexoes(new ConfiguracaoBanco(System.getenv("FINOPS_DB_URL"),
                    System.getenv("FINOPS_RUNTIME_DB_USER"), System.getenv("FINOPS_RUNTIME_DB_PASSWORD")));
        }
    }

    @LocalServerPort
    private int porta;

    @DynamicPropertySource
    static void configurarTls(DynamicPropertyRegistry registro) throws Exception {
        pastaTls = Files.createTempDirectory("finops-tls-it-");
        chaveTls = pastaTls.resolve("servidor.p12");
        String senhaTls = UUID.randomUUID().toString();
        String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        var comando = new ProcessBuilder(keytool, "-genkeypair", "-alias", "finops",
                "-keyalg", "RSA", "-storetype", "PKCS12", "-keystore", chaveTls.toString(),
                "-storepass:env", "FINOPS_IT_TLS", "-dname", "CN=localhost",
                "-ext", "SAN=dns:localhost,ip:127.0.0.1", "-validity", "1")
                .redirectErrorStream(true);
        comando.environment().put("FINOPS_IT_TLS", senhaTls);
        var processo = comando.start();
        if (!processo.waitFor(20, TimeUnit.SECONDS)) {
            processo.destroyForcibly();
            throw new IllegalStateException("Tempo excedido ao preparar TLS de teste.");
        }
        assertEquals(0, processo.exitValue());

        var chaves = KeyStore.getInstance("PKCS12");
        try (var entrada = Files.newInputStream(chaveTls)) {
            chaves.load(entrada, senhaTls.toCharArray());
        }
        var certificados = KeyStore.getInstance("PKCS12");
        certificados.load(null, null);
        certificados.setCertificateEntry("finops", chaves.getCertificate("finops"));
        var confianca = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        confianca.init(certificados);
        tlsCliente = SSLContext.getInstance("TLS");
        tlsCliente.init(null, confianca.getTrustManagers(), null);

        registro.add("server.ssl.key-store", () -> chaveTls.toUri().toString());
        registro.add("server.ssl.key-store-password", () -> senhaTls);
    }

    @AfterAll
    static void limparTls() throws Exception {
        if (pastaTls != null) {
            Files.deleteIfExists(chaveTls);
            Files.deleteIfExists(pastaTls);
        }
    }

    @Test
    void autenticaPerfisPorHttpsEConfereSessaoCsrfEAutorizacaoAtual() throws Exception {
        try (var cenario = new CenarioUsuariosMysql();
             var anonimo = new Navegador();
             var dev = new Navegador();
             var arq = new Navegador()) {
            var catalogo = "/api/v1/catalogo/recursos";
            var pedido = "/api/v1/requisicoes";
            var id = UUID.randomUUID();
            String corpo = """
                    {"requisicaoId":"%s","itens":[{"recursoId":"%s","quantidade":2}]}
                    """.formatted(id, cenario.recurso);

            erro(anonimo.get(catalogo), "SESSAO_NECESSARIA");
            assertEquals(302, anonimo.get("/").statusCode());
            erro(anonimo.post("/login", "application/x-www-form-urlencoded", "username=x&password=y", null),
                    "CSRF_INVALIDO");
            entrar(dev, cenario.desenvolvedor, cenario.senha);
            entrar(arq, cenario.arquiteto, cenario.senha);
            assertEquals(200, dev.get(catalogo).statusCode());
            assertEquals(200, arq.get(catalogo).statusCode());

            erro(dev.post(pedido, "application/json", corpo, null), "CSRF_INVALIDO");
            erro(arq.post(pedido, "application/json", corpo, arq.csrf()), "ACESSO_NEGADO");
            var criado = dev.post(pedido, "application/json", corpo, dev.csrf());
            assertEquals(201, criado.statusCode(), criado.body());
            assertEquals("INICIAL", JSON.readTree(criado.body()).get("requisicao").get("estado").stringValue());
            try (var conexao = cenario.abrir()) {
                var gravado = new RequisicaoRepositoryJdbc(conexao).buscarPorId(id).orElseThrow();
                assertEquals(cenario.autor, gravado.getDesenvolvedorId());
                assertEquals(cenario.departamento, gravado.getDepartamento().getId());
                assertEquals("1000.00", gravado.getDepartamento().getSaldoDisponivel().toPlainString());
                CenarioUsuariosMysql.executar(conexao, "UPDATE usuario SET ativo = FALSE WHERE id = ?",
                        cenario.autor.toString());
            }
            erro(dev.get(catalogo), "ACESSO_NEGADO");
            erro(dev.post(pedido, "application/json", corpo, dev.csrf()), "ACESSO_NEGADO");
            erro(dev.post("/logout", "application/x-www-form-urlencoded", "", null), "CSRF_INVALIDO");
            assertEquals(302, dev.post("/logout", "application/x-www-form-urlencoded", "", dev.csrf()).statusCode());
            erro(dev.get(catalogo), "SESSAO_NECESSARIA");
        }
    }

    @Test
    void consultaHistoricoPorHttpsComSessaoRealEReautorizaCadaPagina() throws Exception {
        try (var c = new CenarioIntervencoesMysql(); var anonimo = new Navegador();
             var dev = new Navegador(); var arq = new Navegador()) {
            var rota = "/api/v1/departamentos/" + c.usuarios.departamento + "/intervencoes";
            erro(anonimo.get(rota), "SESSAO_NECESSARIA");
            entrar(dev, c.usuarios.desenvolvedor, c.usuarios.senha);
            entrar(arq, c.usuarios.arquiteto, c.usuarios.senha);
            erro(dev.get(rota), "ACESSO_NEGADO");
            var vazia = arq.get(rota);
            assertEquals(200, vazia.statusCode(), vazia.body());
            assertTrue(JSON.readTree(vazia.body()).get("intervencoes").isEmpty());
            var primeiro = c.executar(c.comando(UUID.randomUUID(), c.usuarios.departamento));
            var segundo = c.executar(c.comando(UUID.randomUUID(), c.usuarios.departamento));
            var resposta = arq.get(rota + "?limite=1");
            assertEquals(200, resposta.statusCode(), resposta.body());
            assertTrue(resposta.headers().firstValue("Cache-Control").orElseThrow().contains("no-store"));
            var pagina = JSON.readTree(resposta.body());
            var evento = pagina.get("intervencoes").get(0);
            assertEquals(segundo.evento().operacaoId().toString(), evento.get("operacaoId").stringValue());
            assertEquals(c.arquiteto.toString(), evento.get("responsavelId").stringValue());
            assertEquals("998.00", evento.get("dados").get("saldoPosterior").stringValue());
            assertEquals(9, evento.size());
            assertFalse(resposta.body().contains("parametros"));
            assertFalse(resposta.body().contains("senha"));
            var cursor = pagina.get("proximoCursor").stringValue();
            var seguinte = arq.get(rota + "?limite=1&apos=" + cursor);
            assertEquals(primeiro.evento().operacaoId().toString(),
                    JSON.readTree(seguinte.body()).get("intervencoes").get(0).get("operacaoId").stringValue());
            assertTrue(JSON.readTree(seguinte.body()).get("proximoCursor").isNull());
            assertTrue(JSON.readTree(arq.get(rota + "?requisicaoId=" + UUID.randomUUID()).body()).get("intervencoes").isEmpty());
            for (String filtro : List.of("?limite=0", "?limite=101", "?limite=abc", "?apos=invalido",
                    "?limite=1&limite=2", "?perfil=ARQUITETO_CLOUD", "?requisicaoId=1-1-1-1-1")) {
                erro(arq.get(rota + filtro), 400, "ENTRADA_INVALIDA");
            }
            erro(arq.get("/api/v1/departamentos/" + UUID.randomUUID() + "/intervencoes"), 404,
                    "DEPARTAMENTO_NAO_ENCONTRADO");
            erro(arq.get("/api/v1/departamentos/invalido/intervencoes"), 400, "ENTRADA_INVALIDA");
            try (var conexao = c.usuarios.abrir()) {
                CenarioUsuariosMysql.executar(conexao, "UPDATE usuario SET ativo = FALSE WHERE id = ?", c.arquiteto.toString());
            }
            erro(arq.get(rota + "?apos=" + cursor), "ACESSO_NEGADO");
            assertEquals(2, c.quantidade());
        }
    }

    @Test
    void criaSubmeteEConsultaComRegrasFinanceirasEIsolamento() throws Exception {
        try (var cenario = new CenarioUsuariosMysql();
             var dev = new Navegador();
             var arq = new Navegador();
             var anonimo = new Navegador()) {
            entrar(dev, cenario.desenvolvedor, cenario.senha);
            entrar(arq, cenario.arquiteto, cenario.senha);
            assertEquals(200, dev.get("/api/v1/catalogo/recursos").statusCode());
            UUID liberar = criar(dev, cenario.recurso, 56);
            UUID reserva = criar(dev, cenario.recurso, 16);
            UUID insuficiente = criar(dev, cenario.recurso, 32);
            assertEquals("INICIAL", detalhe(dev, liberar).get("estado").stringValue());
            assertEquals("1000.00", saldo(cenario));

            UUID outroAutor = criarInterno(cenario, cenario.departamento, UUID.randomUUID());
            UUID outroDepartamento = criarInterno(cenario, cenario.outroDepartamento, cenario.autor);
            UUID legado = criarInterno(cenario, cenario.departamento, null);
            UUID inexistente = UUID.randomUUID();
            var ausente = dev.get(REQUISICOES + "/" + inexistente);
            erro(ausente, 404, "REQUISICAO_NAO_ENCONTRADA");
            assertTrue(ausente.headers().firstValue("Cache-Control").orElseThrow().contains("no-store"));
            for (UUID fora : List.of(outroAutor, outroDepartamento, legado)) {
                var negado = dev.get(REQUISICOES + "/" + fora);
                erro(negado, 404, "REQUISICAO_NAO_ENCONTRADA");
                assertEquals(ausente.body(), negado.body());
                var submissaoNegada = submeter(dev, fora);
                erro(submissaoNegada, 404, "REQUISICAO_NAO_ENCONTRADA");
                assertEquals(ausente.body(), submissaoNegada.body());
                assertEquals("INICIAL", detalhe(arq, fora).get("estado").stringValue());
            }
            erro(submeter(arq, inexistente), "ACESSO_NEGADO");
            var sessaoInvalida = anonimo.enviar(anonimo.requisicao(REQUISICOES + "/" + liberar)
                    .header("Cookie", "JSESSIONID=expirado").GET());
            erro(sessaoInvalida, "SESSAO_NECESSARIA");
            assertTrue(sessaoInvalida.headers().firstValue("Cache-Control").orElseThrow().contains("no-store"));
            erro(dev.post(REQUISICOES + "/" + liberar + "/validacao", "application/json", "", null), "CSRF_INVALIDO");
            erro(dev.post(REQUISICOES + "/" + liberar + "/validacao", "application/json", "{}", dev.csrf()),
                    400, "ENTRADA_INVALIDA");

            try (var conexao = cenario.abrir()) {
                CenarioUsuariosMysql.executar(conexao, "UPDATE recurso_catalogo SET custo_mensal = 99.99 WHERE id = ?",
                        cenario.recurso.toString());
            }
            var liberada = resultado(submeter(dev, liberar), "LIBERADA", "PROVISIONAMENTO_LIBERADO", null);
            assertEquals("700.00", liberada.get("custoTotalProjetado").stringValue());
            assertEquals("12.50", liberada.get("itens").get(0).get("precoUnitario").stringValue());
            assertEquals(liberada, detalhe(dev, liberar));
            assertEquals("300.00", saldo(cenario));
            resultado(submeter(dev, liberar), "JA_LIBERADA", "PROVISIONAMENTO_LIBERADO", null);
            var emRevisao = resultado(submeter(dev, reserva), "REVISAO_POR_RESERVA", "REVISAO_PENDENTE", "RESERVA");
            resultado(submeter(dev, insuficiente), "REVISAO_POR_SALDO_INSUFICIENTE",
                    "REVISAO_PENDENTE", "SALDO_INSUFICIENTE");
            assertEquals("300.00", saldo(cenario));
            try (var conexao = cenario.abrir()) {
                CenarioUsuariosMysql.executar(conexao,
                        "UPDATE departamento SET teto_mensal = 2000, saldo_disponivel = 1300 WHERE id = ?",
                        cenario.departamento.toString());
            }
            erro(submeter(dev, reserva), 409, "ESTADO_NAO_PERMITIDO");
            assertEquals(emRevisao, detalhe(dev, reserva));
            assertEquals("1300.00", saldo(cenario));
            try (var conexao = cenario.abrir()) {
                CenarioUsuariosMysql.executar(conexao, "UPDATE usuario SET departamento_id = ? WHERE id = ?",
                        cenario.outroDepartamento.toString(), cenario.autor.toString());
            }
            erro(dev.get(REQUISICOES + "/" + liberar), 404, "REQUISICAO_NAO_ENCONTRADA");
            erro(submeter(dev, liberar), 404, "REQUISICAO_NAO_ENCONTRADA");
            assertEquals("INICIAL", detalhe(dev, outroDepartamento).get("estado").stringValue());
        }
    }

    @Test
    void paginaResumosNoEscopoAntesDoLimiteEReleVinculoPorPagina() throws Exception {
        try (var cenario = new CenarioUsuariosMysql(); var dev = new Navegador(); var arq = new Navegador()) {
            entrar(dev, cenario.desenvolvedor, cenario.senha);
            entrar(arq, cenario.arquiteto, cenario.senha);
            UUID outroAutor = UUID.randomUUID();
            criarInterno(cenario, idOrdenado(1), cenario.departamento, outroAutor);
            criarInterno(cenario, idOrdenado(2), cenario.departamento, cenario.autor);
            criarInterno(cenario, idOrdenado(3), cenario.departamento, null);
            criarInterno(cenario, idOrdenado(4), cenario.departamento, cenario.autor);
            criarInterno(cenario, idOrdenado(5), cenario.outroDepartamento, cenario.autor);
            criarInterno(cenario, idOrdenado(6), cenario.departamento, cenario.autor);

            var primeira = pagina(dev, "?limite=2");
            assertEquals(List.of(idOrdenado(2), idOrdenado(4)), ids(primeira));
            assertEquals(idOrdenado(4).toString(), primeira.get("proximoCursor").stringValue());
            var resumo = primeira.get("requisicoes").get(0);
            assertEquals(4, resumo.size());
            assertEquals("12.50", resumo.get("custoTotalProjetado").stringValue());
            assertEquals("INICIAL", resumo.get("estado").stringValue());
            assertTrue(resumo.get("motivoRevisao").isNull());
            var segunda = pagina(dev, "?limite=2&apos=" + idOrdenado(4));
            assertEquals(List.of(idOrdenado(6)), ids(segunda));
            assertTrue(segunda.get("proximoCursor").isNull());
            assertEquals(List.of(idOrdenado(4), idOrdenado(6)), ids(pagina(dev, "?apos=" + idOrdenado(3))));

            var filtroArq = "?departamentoId=" + cenario.departamento;
            assertEquals(List.of(idOrdenado(1), idOrdenado(2), idOrdenado(3), idOrdenado(4), idOrdenado(6)),
                    ids(pagina(arq, filtroArq)));
            assertEquals(List.of(idOrdenado(5)), ids(pagina(arq, "?departamentoId=" + cenario.outroDepartamento)));
            assertTrue(ids(pagina(arq, "?departamentoId=" + UUID.randomUUID())).isEmpty());
            erro(dev.get(REQUISICOES + filtroArq), 400, "ENTRADA_INVALIDA");
            erro(arq.get(REQUISICOES), 400, "ENTRADA_INVALIDA");
            for (String entrada : List.of("?limite=101", "?limite=0", "?limite=1&limite=2", "?apos=invalido",
                    "?autorId=" + outroAutor)) {
                erro(dev.get(REQUISICOES + entrada), 400, "ENTRADA_INVALIDA");
            }

            for (int n = 10; n < 35; n++) {
                criarInterno(cenario, idOrdenado(n), cenario.departamento, cenario.autor);
            }
            var padrao = pagina(dev, "");
            assertEquals(25, ids(padrao).size());
            assertEquals(idOrdenado(31).toString(), padrao.get("proximoCursor").stringValue());
            assertEquals(28, ids(pagina(dev, "?limite=100")).size());

            try (var conexao = cenario.abrir()) {
                CenarioUsuariosMysql.executar(conexao, "UPDATE usuario SET departamento_id = ? WHERE id = ?",
                        cenario.outroDepartamento.toString(), cenario.autor.toString());
            }
            assertEquals(List.of(idOrdenado(5)), ids(pagina(dev, "?apos=" + idOrdenado(4))));
            try (var conexao = cenario.abrir()) {
                CenarioUsuariosMysql.executar(conexao, "UPDATE usuario SET ativo = FALSE WHERE id = ?",
                        cenario.autor.toString());
            }
            erro(dev.get(REQUISICOES), "ACESSO_NEGADO");
        }
    }

    private static UUID idOrdenado(int ordem) {
        return UUID.fromString(PREFIXO_PAGINACAO + "%012d".formatted(ordem));
    }

    private static JsonNode pagina(Navegador navegador, String parametros) throws Exception {
        var resposta = navegador.get(REQUISICOES + parametros);
        assertEquals(200, resposta.statusCode(), resposta.body());
        return JSON.readTree(resposta.body());
    }

    private static List<UUID> ids(JsonNode pagina) {
        var ids = new java.util.ArrayList<UUID>();
        for (var resumo : pagina.get("requisicoes")) {
            ids.add(UUID.fromString(resumo.get("id").stringValue()));
        }
        return ids;
    }

    private static UUID criar(Navegador dev, UUID recurso, int quantidade) throws Exception {
        UUID id = UUID.randomUUID();
        var resposta = dev.post(REQUISICOES, "application/json", """
                {"requisicaoId":"%s","itens":[{"recursoId":"%s","quantidade":%d}]}
                """.formatted(id, recurso, quantidade), dev.csrf());
        assertEquals(201, resposta.statusCode(), resposta.body());
        return id;
    }

    private static UUID criarInterno(CenarioUsuariosMysql cenario, UUID departamento, UUID autor) {
        UUID id = UUID.randomUUID();
        criarInterno(cenario, id, departamento, autor);
        return id;
    }

    private static void criarInterno(CenarioUsuariosMysql cenario, UUID id, UUID departamento, UUID autor) {
        new CriacaoRequisicaoJdbc(cenario.fabrica).executar(
                new SolicitacaoCriacaoRequisicao(id, departamento, List.of(new ItemSolicitado(cenario.recurso, 1)), autor)
        );
    }

    private static String saldo(CenarioUsuariosMysql cenario) throws Exception {
        try (var conexao = cenario.abrir()) {
            return new DepartamentoRepositoryJdbc(conexao).buscarPorId(cenario.departamento)
                    .orElseThrow().getSaldoDisponivel().toPlainString();
        }
    }

    private static HttpResponse<String> submeter(Navegador navegador, UUID id) throws Exception {
        return navegador.post(REQUISICOES + "/" + id + "/validacao", "application/json", "", navegador.csrf());
    }

    private static JsonNode detalhe(Navegador navegador, UUID id) throws Exception {
        var resposta = navegador.get(REQUISICOES + "/" + id);
        assertEquals(200, resposta.statusCode(), resposta.body());
        return JSON.readTree(resposta.body()).get("requisicao");
    }

    private static JsonNode resultado(
            HttpResponse<String> resposta, String resultado, String estado, String motivo
    ) {
        assertEquals(200, resposta.statusCode(), resposta.body());
        var corpo = JSON.readTree(resposta.body());
        assertEquals(resultado, corpo.get("resultado").stringValue());
        var pedido = corpo.get("requisicao");
        assertEquals(estado, pedido.get("estado").stringValue());
        var motivoGravado = pedido.get("motivoRevisao");
        assertEquals(motivo, motivoGravado.isNull() ? null : motivoGravado.stringValue());
        assertTrue(corpo.get("avisos").isEmpty());
        return pedido;
    }

    private void entrar(Navegador navegador, String login, String senha) throws Exception {
        var pagina = navegador.get("/login");
        assertEquals(200, pagina.statusCode());
        String cookie = pagina.headers().firstValue("Set-Cookie").orElseThrow().toLowerCase(java.util.Locale.ROOT);
        assertTrue(cookie.contains("secure") && cookie.contains("httponly") && cookie.contains("samesite=lax"));
        String idAntes = navegador.cookies.getCookieStore().getCookies().getFirst().getValue();
        var campo = CAMPO_CSRF.matcher(pagina.body());
        assertTrue(campo.find(), "O formulário deve incluir um token CSRF.");
        var resposta = navegador.post("/login", "application/x-www-form-urlencoded",
                "username=" + login + "&password=" + senha + "&_csrf=" + campo.group(1), null);
        assertEquals(302, resposta.statusCode());
        assertTrue(resposta.headers().firstValue("Location").orElseThrow().endsWith("/api/v1/catalogo/recursos"));
        assertNotEquals(idAntes, navegador.cookies.getCookieStore().getCookies().getFirst().getValue());
    }

    private static void erro(HttpResponse<String> resposta, String codigo) {
        erro(resposta, 403, codigo);
    }

    private static void erro(HttpResponse<String> resposta, int status, String codigo) {
        assertEquals(status, resposta.statusCode(), resposta.body());
        assertFalse(resposta.headers().firstValue("WWW-Authenticate").isPresent());
        assertEquals(codigo, JSON.readTree(resposta.body()).get("erro").get("codigo").stringValue());
    }

    private final class Navegador implements AutoCloseable {
        private final CookieManager cookies = new CookieManager();
        private final HttpClient http = HttpClient.newBuilder().sslContext(tlsCliente)
                .cookieHandler(cookies).connectTimeout(Duration.ofSeconds(5)).build();

        HttpResponse<String> get(String caminho) throws Exception {
            return enviar(requisicao(caminho).GET());
        }

        HttpResponse<String> post(String caminho, String tipo, String corpo, String csrf) throws Exception {
            var pedido = requisicao(caminho).header("Content-Type", tipo)
                    .POST(HttpRequest.BodyPublishers.ofString(corpo));
            if (csrf != null) {
                pedido.header("X-CSRF-TOKEN", csrf);
            }
            return enviar(pedido);
        }

        String csrf() throws Exception {
            var resposta = get("/api/v1/csrf");
            assertEquals(200, resposta.statusCode(), resposta.body());
            var corpo = JSON.readTree(resposta.body());
            assertEquals("X-CSRF-TOKEN", corpo.get("cabecalho").stringValue());
            return corpo.get("token").stringValue();
        }

        private HttpRequest.Builder requisicao(String caminho) {
            return HttpRequest.newBuilder(URI.create("https://localhost:" + porta + caminho))
                    .timeout(Duration.ofSeconds(8));
        }

        private HttpResponse<String> enviar(HttpRequest.Builder pedido) throws Exception {
            return http.send(pedido.build(), HttpResponse.BodyHandlers.ofString());
        }

        @Override
        public void close() {
            http.close();
        }
    }
}
