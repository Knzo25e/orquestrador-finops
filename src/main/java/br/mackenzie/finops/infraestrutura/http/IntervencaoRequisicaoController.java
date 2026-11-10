package br.mackenzie.finops.infraestrutura.http;

import br.mackenzie.finops.aplicacao.IntervirRequisicaoUseCase;
import br.mackenzie.finops.aplicacao.excecoes.AcessoNegadoException;
import br.mackenzie.finops.aplicacao.excecoes.ConfirmacaoIntervencaoDesconhecidaException;
import br.mackenzie.finops.aplicacao.excecoes.ConflitoOperacaoException;
import br.mackenzie.finops.aplicacao.excecoes.EntradaRequisicaoInvalidaException;
import br.mackenzie.finops.aplicacao.excecoes.RequisicaoNaoEncontradaException;
import br.mackenzie.finops.aplicacao.excecoes.ServicoIndisponivelException;
import br.mackenzie.finops.aplicacao.resultados.FotografiaRequisicao;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/requisicoes", produces = MediaType.APPLICATION_JSON_VALUE)
public final class IntervencaoRequisicaoController {
    private final IntervirRequisicaoUseCase casoDeUso;

    public IntervencaoRequisicaoController(IntervirRequisicaoUseCase casoDeUso) {
        this.casoDeUso = casoDeUso;
    }

    @PostMapping(path = "/{id}/revisao-tecnica", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> revisar(@PathVariable("id") String id, HttpServletRequest chamada) throws IOException {
        return executar(id, chamada, true);
    }

    @PostMapping(path = "/{id}/liberacao-financeira", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> liberar(@PathVariable("id") String id, HttpServletRequest chamada) throws IOException {
        return executar(id, chamada, false);
    }

    private ResponseEntity<?> executar(String id, HttpServletRequest chamada, boolean tecnica) throws IOException {
        if (chamada.getQueryString() != null) {
            throw new EntradaRequisicaoInvalidaException();
        }
        var requisicao = LeitorPedidoCriacaoHttp.lerUuid(id);
        var pedido = LeitorIntervencaoRequisicaoHttp.ler(chamada.getInputStream());
        var resultado = tecnica ? casoDeUso.revisar(requisicao, pedido) : casoDeUso.liberar(requisicao, pedido);
        var avisos = RespostaCriacaoHttp.avisos(resultado.falhaNoFechamento());
        if (resultado.statusHttp() == 409) {
            return responder(409, new RespostaErroIntervencao(new ErroIntervencao(resultado.resultado(),
                    "O estado da requisição na tentativa registrada não permite esta intervenção.", resultado.operacaoId()), avisos));
        }
        return responder(resultado.statusHttp(), new RespostaIntervencao(resultado.operacaoId(), resultado.resultado(),
                resultado.requisicao(), avisos));
    }

    @ExceptionHandler(ConfirmacaoIntervencaoDesconhecidaException.class)
    ResponseEntity<?> confirmacaoDesconhecida(ConfirmacaoIntervencaoDesconhecidaException falha) {
        return responder(503, new RespostaErroIntervencao(new ErroIntervencao("CONFIRMACAO_INTERVENCAO_DESCONHECIDA",
                "Não foi possível confirmar a intervenção. Reconcilie explicitamente com a mesma entrada.",
                falha.getOperacaoId()), List.of()));
    }

    @ExceptionHandler({RuntimeException.class, IOException.class})
    ResponseEntity<?> erro(Exception falha) {
        int status = 500;
        String codigo = "FALHA_INTERNA";
        String mensagem = "Não foi possível concluir a intervenção.";
        if (falha instanceof AcessoNegadoException) {
            status = 403;
            codigo = "ACESSO_NEGADO";
            mensagem = "Operação não autorizada.";
        } else if (falha instanceof ConflitoOperacaoException) {
            status = 409;
            codigo = "OPERACAO_ID_EM_CONFLITO";
            mensagem = "O identificador já está associado a uma operação diferente.";
        } else if (falha instanceof EntradaRequisicaoInvalidaException
                || falha instanceof LeitorPedidoCriacaoHttp.EntradaInvalidaException) {
            status = 400;
            codigo = "ENTRADA_INVALIDA";
            mensagem = "A entrada da requisição é inválida.";
        } else if (falha instanceof LeitorPedidoCriacaoHttp.CorpoMuitoGrandeException) {
            status = 413;
            codigo = "CORPO_MUITO_GRANDE";
            mensagem = "O corpo da requisição excede o limite de 65536 bytes.";
        } else if (falha instanceof RequisicaoNaoEncontradaException) {
            status = 404;
            codigo = "REQUISICAO_NAO_ENCONTRADA";
            mensagem = "Requisição não encontrada.";
        } else if (falha instanceof ServicoIndisponivelException) {
            status = 503;
            codigo = "SERVICO_INDISPONIVEL";
            mensagem = "O serviço está indisponível.";
        }
        return responder(status, new TratamentoErrosHttp.RespostaErro(new TratamentoErrosHttp.Erro(codigo, mensagem)));
    }

    private static <T> ResponseEntity<T> responder(int status, T corpo) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(corpo);
    }

    record RespostaIntervencao(UUID operacaoId, String resultado, FotografiaRequisicao requisicao,
                              List<RespostaCriacaoHttp.Aviso> avisos) {}
    record ErroIntervencao(String codigo, String mensagem, UUID operacaoId) {}
    record RespostaErroIntervencao(ErroIntervencao erro, List<RespostaCriacaoHttp.Aviso> avisos) {}
}
