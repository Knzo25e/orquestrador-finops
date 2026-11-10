package br.mackenzie.finops.infraestrutura.http;

import br.mackenzie.finops.aplicacao.AjustarTetoUseCase;
import br.mackenzie.finops.aplicacao.excecoes.AcessoNegadoException;
import br.mackenzie.finops.aplicacao.excecoes.ConfirmacaoIntervencaoDesconhecidaException;
import br.mackenzie.finops.aplicacao.excecoes.ConflitoOperacaoException;
import br.mackenzie.finops.aplicacao.excecoes.DepartamentoNaoEncontradoException;
import br.mackenzie.finops.aplicacao.excecoes.EntradaRequisicaoInvalidaException;
import br.mackenzie.finops.aplicacao.excecoes.ServicoIndisponivelException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class AjusteOrcamentarioController {
    private final AjustarTetoUseCase casoDeUso;

    public AjusteOrcamentarioController(AjustarTetoUseCase casoDeUso) {
        this.casoDeUso = casoDeUso;
    }

    @PostMapping(path = "/api/v1/departamentos/{id}/ajustes-orcamentarios",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> ajustar(@PathVariable("id") String id, HttpServletRequest chamada) throws IOException {
        if (chamada.getQueryString() != null) {
            throw new EntradaRequisicaoInvalidaException();
        }
        var resultado = casoDeUso.executar(LeitorPedidoCriacaoHttp.lerUuid(id),
                LeitorAjusteTetoHttp.ler(chamada.getInputStream()));
        var avisos = RespostaCriacaoHttp.avisos(resultado.falhaNoFechamento());
        if (resultado.statusHttp() == 409) {
            return responder(409, new RespostaErroIntervencao(new ErroIntervencao(resultado.resultado(),
                    "O teto solicitado é inferior ao valor comprometido.", resultado.operacaoId()), avisos));
        }
        return responder(resultado.statusHttp(), new RespostaAjuste(resultado.operacaoId(), resultado.resultado(),
                Ajuste.de(resultado.dados()), avisos));
    }

    @ExceptionHandler(ConfirmacaoIntervencaoDesconhecidaException.class)
    ResponseEntity<?> confirmacaoDesconhecida(ConfirmacaoIntervencaoDesconhecidaException falha) {
        return responder(503, new RespostaErroIntervencao(new ErroIntervencao(
                "CONFIRMACAO_INTERVENCAO_DESCONHECIDA",
                "Não foi possível confirmar a intervenção. Reconcilie explicitamente com a mesma entrada.",
                falha.getOperacaoId()), List.of()));
    }

    @ExceptionHandler({RuntimeException.class, IOException.class})
    ResponseEntity<?> erro(Exception falha) {
        int status = 500;
        String codigo = "FALHA_INTERNA";
        String mensagem = "Não foi possível concluir o ajuste.";
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
        } else if (falha instanceof DepartamentoNaoEncontradoException) {
            status = 404;
            codigo = "DEPARTAMENTO_NAO_ENCONTRADO";
            mensagem = "Departamento não encontrado.";
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

    record RespostaAjuste(UUID operacaoId, String resultado, Ajuste ajuste, List<RespostaCriacaoHttp.Aviso> avisos) {}
    record ErroIntervencao(String codigo, String mensagem, UUID operacaoId) {}
    record RespostaErroIntervencao(ErroIntervencao erro, List<RespostaCriacaoHttp.Aviso> avisos) {}
    record Ajuste(String departamentoId, String tetoAnterior, String tetoNovo, String variacaoTeto,
                  String saldoAnterior, String saldoNovo, String comprometido) {
        static Ajuste de(Map<String, String> dados) {
            return new Ajuste(dados.get("departamentoId"), dados.get("tetoAnterior"), dados.get("tetoNovo"),
                    dados.get("variacaoTeto"), dados.get("saldoAnterior"), dados.get("saldoNovo"), dados.get("comprometido"));
        }
    }
}
