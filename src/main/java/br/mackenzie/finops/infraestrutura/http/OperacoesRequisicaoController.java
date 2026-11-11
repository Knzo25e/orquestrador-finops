package br.mackenzie.finops.infraestrutura.http;

import br.mackenzie.finops.aplicacao.ConsultarRequisicaoAutorizadaUseCase;
import br.mackenzie.finops.aplicacao.ListarRequisicoesAutorizadasUseCase;
import br.mackenzie.finops.aplicacao.ValidarRequisicaoAutorizadaUseCase;
import br.mackenzie.finops.aplicacao.excecoes.AcessoNegadoException;
import br.mackenzie.finops.aplicacao.excecoes.ConfirmacaoValidacaoDesconhecidaException;
import br.mackenzie.finops.aplicacao.excecoes.EntradaRequisicaoInvalidaException;
import br.mackenzie.finops.aplicacao.excecoes.RequisicaoNaoEncontradaException;
import br.mackenzie.finops.aplicacao.excecoes.ServicoIndisponivelException;
import br.mackenzie.finops.aplicacao.resultados.ResumoRequisicao;
import br.mackenzie.finops.dominio.entidades.ResultadoValidacao;
import br.mackenzie.finops.infraestrutura.http.RequisicaoController.Erro;
import br.mackenzie.finops.infraestrutura.http.RequisicaoController.ErroConfirmacaoDesconhecida;
import br.mackenzie.finops.infraestrutura.http.RequisicaoController.RespostaConfirmacaoDesconhecida;
import br.mackenzie.finops.infraestrutura.http.RequisicaoController.RespostaErro;
import br.mackenzie.finops.infraestrutura.http.RespostaCriacaoHttp.Aviso;
import br.mackenzie.finops.infraestrutura.http.RespostaCriacaoHttp.Pedido;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartHttpServletRequest;

@RestController
@RequestMapping(path = "/api/v1/requisicoes", produces = MediaType.APPLICATION_JSON_VALUE)
public class OperacoesRequisicaoController {
    private static final Set<String> PARAMETROS_LISTAGEM = Set.of("departamentoId", "apos", "limite");
    private final ConsultarRequisicaoAutorizadaUseCase consulta;
    private final ValidarRequisicaoAutorizadaUseCase validacao;
    private final ListarRequisicoesAutorizadasUseCase listagem;

    public OperacoesRequisicaoController(
            ConsultarRequisicaoAutorizadaUseCase consulta, ValidarRequisicaoAutorizadaUseCase validacao,
            ListarRequisicoesAutorizadasUseCase listagem
    ) {
        this.consulta = consulta;
        this.validacao = validacao;
        this.listagem = listagem;
    }

    @GetMapping
    ResponseEntity<RespostaListagem> listar(HttpServletRequest chamada) {
        var parametros = chamada.getParameterMap();
        if (!PARAMETROS_LISTAGEM.containsAll(parametros.keySet())
                || parametros.values().stream().anyMatch(valores -> valores.length != 1)) {
            throw new EntradaRequisicaoInvalidaException();
        }
        var pagina = listagem.executarPagina(
                uuidOpcional(parametros, "departamentoId"), uuidOpcional(parametros, "apos"),
                limite(parametros.get("limite"))
        );
        var resumos = pagina.requisicoes().stream().map(Resumo::de).toList();
        return responder(HttpStatus.OK, new RespostaListagem(resumos, pagina.proximoCursor()));
    }

    @GetMapping("/{id}")
    ResponseEntity<RespostaConsulta> consultar(@PathVariable("id") String id, HttpServletRequest chamada) {
        rejeitarParametros(chamada);
        var pedido = consulta.executar(uuid(id)).orElseThrow(RequisicaoNaoEncontradaException::new);
        return responder(HttpStatus.OK, new RespostaConsulta(RespostaCriacaoHttp.pedido(pedido)));
    }

    @PostMapping("/{id}/validacao")
    ResponseEntity<?> validar(@PathVariable("id") String id, HttpServletRequest chamada) throws IOException {
        rejeitarParametros(chamada);
        if (chamada.getContentLengthLong() > 0 || chamada instanceof MultipartHttpServletRequest
                || chamada.getInputStream().read() != -1
                || !chamada.getParameterMap().isEmpty()) {
            throw new EntradaRequisicaoInvalidaException();
        }
        var resultado = validacao.executar(uuid(id));
        if (resultado.resultado() == ResultadoValidacao.ESTADO_NAO_PERMITIDO) {
            return erro(HttpStatus.CONFLICT, "ESTADO_NAO_PERMITIDO",
                    "O estado atual da requisição não permite validação automática.");
        }
        return responder(HttpStatus.OK, new RespostaValidacao(
                resultado.resultado().name(), RespostaCriacaoHttp.pedido(resultado.requisicao()),
                RespostaCriacaoHttp.avisos(resultado.falhaNoFechamento())
        ));
    }

    private static void rejeitarParametros(HttpServletRequest chamada) {
        if (chamada.getQueryString() != null) {
            throw new EntradaRequisicaoInvalidaException();
        }
    }

    private static UUID uuid(String texto) {
        try {
            var id = UUID.fromString(texto);
            if (id.toString().equals(texto)) {
                return id;
            }
        } catch (IllegalArgumentException ignorada) {
            // Todas as formas inválidas recebem o mesmo erro público.
        }
        throw new EntradaRequisicaoInvalidaException();
    }

    private static UUID uuidOpcional(Map<String, String[]> parametros, String nome) {
        var valor = parametros.get(nome);
        return valor == null ? null : uuid(valor[0]);
    }

    private static int limite(String[] valor) {
        if (valor == null) {
            return ListarRequisicoesAutorizadasUseCase.LIMITE_PADRAO;
        }
        try {
            return Integer.parseInt(valor[0]);
        } catch (NumberFormatException falha) {
            throw new EntradaRequisicaoInvalidaException();
        }
    }

    @ExceptionHandler(EntradaRequisicaoInvalidaException.class)
    ResponseEntity<RespostaErro> entradaInvalida() {
        return erro(HttpStatus.BAD_REQUEST, "ENTRADA_INVALIDA", "A entrada da requisição é inválida.");
    }

    @ExceptionHandler(AcessoNegadoException.class)
    ResponseEntity<RespostaErro> acessoNegado() {
        return erro(HttpStatus.FORBIDDEN, "ACESSO_NEGADO", "Operação não autorizada.");
    }

    @ExceptionHandler(RequisicaoNaoEncontradaException.class)
    ResponseEntity<RespostaErro> naoEncontrada() {
        return erro(HttpStatus.NOT_FOUND, "REQUISICAO_NAO_ENCONTRADA", "Requisição não encontrada.");
    }

    @ExceptionHandler(ConfirmacaoValidacaoDesconhecidaException.class)
    ResponseEntity<RespostaConfirmacaoDesconhecida> confirmacaoDesconhecida(
            ConfirmacaoValidacaoDesconhecidaException falha
    ) {
        return responder(HttpStatus.SERVICE_UNAVAILABLE, new RespostaConfirmacaoDesconhecida(
                new ErroConfirmacaoDesconhecida("CONFIRMACAO_VALIDACAO_DESCONHECIDA",
                        "Não foi possível confirmar a validação. Consulte ou repita com o mesmo identificador.",
                        falha.getRequisicaoId())
        ));
    }

    @ExceptionHandler(ServicoIndisponivelException.class)
    ResponseEntity<RespostaErro> servicoIndisponivel() {
        return erro(HttpStatus.SERVICE_UNAVAILABLE, "SERVICO_INDISPONIVEL", "O serviço está indisponível.");
    }

    @ExceptionHandler({RuntimeException.class, IOException.class})
    ResponseEntity<RespostaErro> falhaInterna() {
        return erro(HttpStatus.INTERNAL_SERVER_ERROR, "FALHA_INTERNA", "Não foi possível concluir a operação.");
    }

    private static ResponseEntity<RespostaErro> erro(HttpStatus status, String codigo, String mensagem) {
        return responder(status, new RespostaErro(new Erro(codigo, mensagem)));
    }

    private static <T> ResponseEntity<T> responder(HttpStatus status, T corpo) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(corpo);
    }

    record RespostaConsulta(Pedido requisicao) {}

    record RespostaValidacao(String resultado, Pedido requisicao, List<Aviso> avisos) {}

    record RespostaListagem(List<Resumo> requisicoes, UUID proximoCursor) {}

    record Resumo(UUID id, String estado, String motivoRevisao, String custoTotalProjetado) {
        private static Resumo de(ResumoRequisicao resumo) {
            return new Resumo(resumo.id(), RespostaCriacaoHttp.estadoPublico(resumo.estado()),
                    RespostaCriacaoHttp.motivoPublico(resumo.motivoRevisao()),
                    resumo.custoTotalProjetado().toPlainString());
        }
    }
}
