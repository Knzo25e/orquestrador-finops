package br.mackenzie.finops.infraestrutura.http;

import br.mackenzie.finops.aplicacao.ListarCatalogoAutorizadoUseCase;
import br.mackenzie.finops.aplicacao.excecoes.AcessoNegadoException;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CatalogoController {

    private final ListarCatalogoAutorizadoUseCase listarCatalogo;

    public CatalogoController(ListarCatalogoAutorizadoUseCase listarCatalogo) {
        this.listarCatalogo = listarCatalogo;
    }

    @GetMapping(
            path = "/api/v1/catalogo/recursos",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public RespostaCatalogo listar() {
        List<ItemCatalogo> recursos = listarCatalogo.executar().stream()
                .map(recurso -> new ItemCatalogo(
                        recurso.getId(),
                        recurso.getNome(),
                        recurso.getCustoMensal().toPlainString()
                ))
                .toList();

        return new RespostaCatalogo(recursos);
    }

    @ExceptionHandler(AcessoNegadoException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public RespostaErro acessoNegado() {
        return new RespostaErro(
                new Erro("ACESSO_NEGADO", "Operação não autorizada.")
        );
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public RespostaErro falhaInterna() {
        return new RespostaErro(
                new Erro("FALHA_INTERNA", "Não foi possível consultar o catálogo.")
        );
    }

    public record RespostaCatalogo(List<ItemCatalogo> recursos) {}

    public record ItemCatalogo(UUID id, String nome, String custoMensal) {}

    public record RespostaErro(Erro erro) {}

    public record Erro(String codigo, String mensagem) {}
}