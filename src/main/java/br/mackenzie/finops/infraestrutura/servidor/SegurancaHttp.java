package br.mackenzie.finops.infraestrutura.servidor;

import br.mackenzie.finops.infraestrutura.persistencia.UsuariosJdbc;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;

@Configuration(proxyBeanMethods = false)
class SegurancaHttp {
    @Bean
    PasswordEncoder codificadorSenha() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService credenciais(UsuariosJdbc usuarios) {
        return login -> {
            var conta = usuarios.buscarCredencial(login)
                    .orElseThrow(() -> new UsernameNotFoundException("Credenciais inválidas."));
            return User.withUsername(conta.id().toString())
                    .password(conta.hash())
                    .disabled(!conta.ativo())
                    .authorities("SESSAO")
                    .build();
        };
    }

    @Bean
    SecurityFilterChain sessao(HttpSecurity http) {
        var api = PathPatternRequestMatcher.withDefaults().matcher("/api/**");
        var confianca = new AuthenticationTrustResolverImpl();
        return http
                .authorizeHttpRequests(regras -> regras
                        .requestMatchers("/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/portal-assets/**").permitAll()
                        .requestMatchers("/", "/portal", "/portal/desenvolvedor", "/portal/arquiteto", "/api/**").authenticated()
                        .anyRequest().denyAll())
                .formLogin(login -> login.loginPage("/login").loginProcessingUrl("/login")
                        .defaultSuccessUrl("/portal", true).permitAll())
                .logout(logout -> logout.deleteCookies("JSESSIONID").logoutSuccessUrl("/login?logout"))
                .sessionManagement(sessao -> sessao.sessionFixation().changeSessionId())
                .requestCache(cache -> cache.disable())
                .csrf(Customizer.withDefaults())
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'")))
                .exceptionHandling(erros -> erros
                        .defaultAuthenticationEntryPointFor(
                                (req, resp, falha) -> responder(resp, "SESSAO_NECESSARIA"), api)
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/login"), AnyRequestMatcher.INSTANCE)
                        .accessDeniedHandler((req, resp, falha) -> {
                            var atual = SecurityContextHolder.getContext().getAuthentication();
                            if (api.matches(req) && !confianca.isAuthenticated(atual)) {
                                responder(resp, "SESSAO_NECESSARIA");
                            } else {
                                responder(resp, falha instanceof CsrfException ? "CSRF_INVALIDO" : "ACESSO_NEGADO");
                            }
                        }))
                .build();
    }

    private static void responder(HttpServletResponse resposta, String codigo) throws IOException {
        String mensagem = switch (codigo) {
            case "SESSAO_NECESSARIA" -> "Inicie uma sessão pelo formulário de login.";
            case "CSRF_INVALIDO" -> "Token CSRF ausente ou inválido.";
            default -> "Operação não autorizada.";
        };
        resposta.setStatus(HttpServletResponse.SC_FORBIDDEN);
        resposta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        resposta.setCharacterEncoding("UTF-8");
        resposta.getWriter().write(
                "{\"erro\":{\"codigo\":\"" + codigo + "\",\"mensagem\":\"" + mensagem + "\"}}"
        );
    }
}
