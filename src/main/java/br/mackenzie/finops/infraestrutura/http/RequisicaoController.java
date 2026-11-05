package br.mackenzie.finops.infraestrutura.http;

import br.mackenzie.finops.aplicacao.CriarRequisicaoCoordenadaUseCase;
import br.mackenzie.finops.aplicacao.excecoes.ConflitoIdentificadorException;
import br.mackenzie.finops.aplicacao.excecoes.AcessoNegadoException;
import jakarta.servlet.http.HttpServletRequest;
import br.mackenzie.finops.aplicacao.excecoes.ConfirmacaoCriacaoDesconhecidaException;
import java.util.UUID;
import br.mackenzie.finops.aplicacao.excecoes.FalhaCriacaoPersistenteException;
import br.mackenzie.finops.aplicacao.excecoes.SolicitacaoInvalidaException;
import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RequisicaoController {

    private final CriarRequisicaoCoordenadaUseCase criarRequisicao;

    public RequisicaoController(CriarRequisicaoCoordenadaUseCase criarRequisicao) {
        this.criarRequisicao = criarRequisicao;
    }

    @PostMapping(
            path = "/api/v1/requisicoes",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<RespostaCriacaoHttp> criar(HttpServletRequest chamada)
            throws IOException {
        var pedido = LeitorPedidoCriacaoHttp.lerPedido(chamada.getInputStream());
        var resultado = criarRequisicao.executar(pedido.requisicaoId(), pedido.itens());

        HttpStatus status = switch (resultado.situacao()) {
            case CRIADA -> HttpStatus.CREATED;
            case JA_EXISTENTE -> HttpStatus.OK;
        };

        return ResponseEntity.status(status).body(RespostaCriacaoHttp.de(resultado));
    }

        @ExceptionHandler(LeitorPedidoCriacaoHttp.EntradaInvalidaException.class)
    public ResponseEntity<RespostaErro> entradaInvalida() {
        return ResponseEntity.badRequest().body(
                new RespostaErro(new Erro(
                        "ENTRADA_INVALIDA",
                        "O corpo da requisição é inválido."
                ))
        );
    }

    @ExceptionHandler(LeitorPedidoCriacaoHttp.CorpoMuitoGrandeException.class)
    public ResponseEntity<RespostaErro> corpoMuitoGrande() {
        return ResponseEntity.status(413).body(
                new RespostaErro(new Erro(
                        "CORPO_MUITO_GRANDE",
                        "O corpo da requisição excede o limite de 65536 bytes."
                ))
        );
    }

        @ExceptionHandler(ConflitoIdentificadorException.class)
    public ResponseEntity<RespostaErro> conflitoIdentificador() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                new RespostaErro(new Erro(
                        "CONFLITO_IDENTIFICADOR",
                        "O identificador já está associado a uma solicitação diferente."
                ))
        );
    }

        @ExceptionHandler(AcessoNegadoException.class)
    public ResponseEntity<RespostaErro> acessoNegado() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                new RespostaErro(new Erro(
                        "ACESSO_NEGADO",
                        "Operação não autorizada."
                ))
        );
    }

        @ExceptionHandler(ConfirmacaoCriacaoDesconhecidaException.class)
    public ResponseEntity<RespostaConfirmacaoDesconhecida> confirmacaoDesconhecida(
            ConfirmacaoCriacaoDesconhecidaException excecao
    ) {
        var erro = new ErroConfirmacaoDesconhecida(
                "CONFIRMACAO_CRIACAO_DESCONHECIDA",
                "Não foi possível confirmar o resultado da criação. "
                        + "Preserve o identificador e o conteúdo para uma nova tentativa.",
                excecao.getRequisicaoId()
        );

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new RespostaConfirmacaoDesconhecida(erro));
    }

    public record RespostaConfirmacaoDesconhecida(ErroConfirmacaoDesconhecida erro) {}

    public record ErroConfirmacaoDesconhecida(
            String codigo,
            String mensagem,
            UUID requisicaoId
    ) {}

        @ExceptionHandler(FalhaCriacaoPersistenteException.class)
    public ResponseEntity<RespostaErro> falhaNaCriacaoPersistente() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                new RespostaErro(new Erro(
                        "FALHA_INTERNA",
                        "Não foi possível concluir a operação."
                ))
        );
    }

        @ExceptionHandler(SolicitacaoInvalidaException.class)
    public ResponseEntity<RespostaErro> solicitacaoInvalida() {
        return ResponseEntity.status(422).body(
                new RespostaErro(new Erro(
                        "SOLICITACAO_INVALIDA",
                        "A solicitação não pode ser atendida com os dados informados."
                ))
        );
    }

    public record RespostaErro(Erro erro) {}

    public record Erro(String codigo, String mensagem) {}
}