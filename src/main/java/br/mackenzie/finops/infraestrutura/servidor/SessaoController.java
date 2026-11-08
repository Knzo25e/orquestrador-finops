package br.mackenzie.finops.infraestrutura.servidor;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SessaoController {
    @GetMapping("/")
    ResponseEntity<Void> inicio() {
        return ResponseEntity.status(303).header("Location", "/api/v1/catalogo/recursos").build();
    }

    @GetMapping("/api/v1/csrf")
    ResponseEntity<TokenCsrf> csrf(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new TokenCsrf(token.getHeaderName(), token.getToken()));
    }

    record TokenCsrf(String cabecalho, String token) {}
}
