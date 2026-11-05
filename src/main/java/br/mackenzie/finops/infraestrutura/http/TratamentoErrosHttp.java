package br.mackenzie.finops.infraestrutura.http;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class TratamentoErrosHttp {

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<RespostaErro> tratarTipoDeConteudoNaoSuportado(
            HttpMediaTypeNotSupportedException excecao) {

        var resposta = new RespostaErro(
                new Erro(
                        "TIPO_DE_CONTEUDO_NAO_SUPORTADO",
                        "O Content-Type deve ser application/json."
                )
        );

        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(resposta);
    }

    public record RespostaErro(Erro erro) {
    }

    public record Erro(String codigo, String mensagem) {
    }
}