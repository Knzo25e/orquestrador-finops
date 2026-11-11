package br.mackenzie.finops.infraestrutura.servidor;

import br.mackenzie.finops.aplicacao.ConsultarSessaoUseCase;
import br.mackenzie.finops.aplicacao.excecoes.AcessoNegadoException;
import br.mackenzie.finops.aplicacao.excecoes.ServicoIndisponivelException;
import br.mackenzie.finops.aplicacao.seguranca.PerfilUsuario;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.util.HtmlUtils;

@Controller
class PortalController {
    private static final String DESENVOLVEDOR = "/portal/desenvolvedor";
    private static final String ARQUITETO = "/portal/arquiteto";
    private static final Map<PerfilUsuario, String> PAINEIS = Map.of(
            PerfilUsuario.DESENVOLVEDOR, DESENVOLVEDOR, PerfilUsuario.ARQUITETO_CLOUD, ARQUITETO);

    private final ConsultarSessaoUseCase sessao;
    private final String login;
    private final String erro;
    private final Resource desenvolvedor = new ClassPathResource("portal/desenvolvedor.html");
    private final Resource arquiteto = new ClassPathResource("portal/arquiteto.html");

    PortalController(ConsultarSessaoUseCase sessao) throws IOException {
        this.sessao = sessao;
        this.login = new ClassPathResource("portal/login.html").getContentAsString(StandardCharsets.UTF_8);
        this.erro = new ClassPathResource("portal/erro.html").getContentAsString(StandardCharsets.UTF_8);
    }

    @GetMapping(path = "/login", produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> login(CsrfToken token, HttpServletRequest chamada) {
        String aviso = "";
        if (chamada.getParameterMap().containsKey("error")) {
            aviso = "Usuário ou senha inválidos.";
        } else if (chamada.getParameterMap().containsKey("logout")) {
            aviso = "Sessão encerrada.";
        } else if (chamada.getParameterMap().containsKey("expirada")) {
            aviso = "A sessão expirou. Entre novamente.";
        }

        String pagina = login.replace("{{aviso}}", aviso)
                .replace("{{visibilidadeAviso}}", aviso.isEmpty() ? "hidden" : "")
                .replace("{{nomeCsrf}}", HtmlUtils.htmlEscape(token.getParameterName()))
                .replace("{{csrf}}", HtmlUtils.htmlEscape(token.getToken()));
        return pagina(HttpStatus.OK, pagina);
    }

    @GetMapping({"/", "/portal"})
    ResponseEntity<Void> inicio() {
        return redirecionar(PAINEIS.get(sessao.executar().perfil()));
    }

    @GetMapping(path = DESENVOLVEDOR, produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<Resource> desenvolvedor() {
        return painel(PerfilUsuario.DESENVOLVEDOR, desenvolvedor);
    }

    @GetMapping(path = ARQUITETO, produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<Resource> arquiteto() {
        return painel(PerfilUsuario.ARQUITETO_CLOUD, arquiteto);
    }

    private ResponseEntity<Resource> painel(PerfilUsuario perfil, Resource recurso) {
        var atual = sessao.executar().perfil();
        if (perfil != atual) {
            return redirecionar(PAINEIS.get(atual));
        }
        return pagina(HttpStatus.OK, recurso);
    }

    @ExceptionHandler(AcessoNegadoException.class)
    ResponseEntity<String> acessoNegado(HttpServletRequest chamada) {
        return pagina(HttpStatus.FORBIDDEN, erroComLogout(chamada, "Esta conta não tem acesso ao portal."));
    }

    @ExceptionHandler(ServicoIndisponivelException.class)
    ResponseEntity<String> indisponivel(HttpServletRequest chamada) {
        return pagina(HttpStatus.SERVICE_UNAVAILABLE,
                erroComLogout(chamada, "O portal está temporariamente indisponível."));
    }

    private String erroComLogout(HttpServletRequest chamada, String mensagem) {
        var token = (CsrfToken) chamada.getAttribute(CsrfToken.class.getName());
        return erro.replace("{{mensagem}}", mensagem)
                .replace("{{nomeCsrf}}", HtmlUtils.htmlEscape(token.getParameterName()))
                .replace("{{csrf}}", HtmlUtils.htmlEscape(token.getToken()));
    }

    private static <T> ResponseEntity<T> redirecionar(String caminho) {
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create(caminho))
                .cacheControl(CacheControl.noStore()).build();
    }

    private static <T> ResponseEntity<T> pagina(HttpStatus status, T conteudo) {
        return ResponseEntity.status(status).contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .cacheControl(CacheControl.noStore()).body(conteudo);
    }
}
