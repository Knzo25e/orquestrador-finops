package br.mackenzie.finops.infraestrutura.persistencia;

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
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = FinopsServidor.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SessaoMysqlIT {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern CAMPO_CSRF = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");
    private static Path pastaTls;
    private static Path chaveTls;
    private static SSLContext tlsCliente;

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
        assertEquals(403, resposta.statusCode(), resposta.body());
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
