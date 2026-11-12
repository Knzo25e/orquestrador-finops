package br.mackenzie.finops.infraestrutura.servidor;

import br.mackenzie.finops.aplicacao.ConsultarSessaoUseCase;
import br.mackenzie.finops.aplicacao.ListarDepartamentosUseCase;
import br.mackenzie.finops.aplicacao.excecoes.AcessoNegadoException;
import br.mackenzie.finops.aplicacao.excecoes.EntradaRequisicaoInvalidaException;
import br.mackenzie.finops.aplicacao.excecoes.ServicoIndisponivelException;
import br.mackenzie.finops.aplicacao.seguranca.PerfilUsuario;
import br.mackenzie.finops.infraestrutura.http.TratamentoErrosHttp.Erro;
import br.mackenzie.finops.infraestrutura.http.TratamentoErrosHttp.RespostaErro;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
class LeiturasPortalController {
    private final ConsultarSessaoUseCase consultarSessao;
    private final ListarDepartamentosUseCase listarDepartamentos;

    LeiturasPortalController(ConsultarSessaoUseCase consultarSessao, ListarDepartamentosUseCase listarDepartamentos) {
        this.consultarSessao = consultarSessao;
        this.listarDepartamentos = listarDepartamentos;
    }

    @GetMapping("/sessao")
    ResponseEntity<Sessao> sessao(HttpServletRequest chamada) {
        if (chamada.getQueryString() != null) {
            throw new EntradaRequisicaoInvalidaException();
        }
        var identidade = consultarSessao.executar();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                new Sessao(identidade.usuarioId(), identidade.perfil(), identidade.departamentoId()));
    }

    @GetMapping("/departamentos")
    ResponseEntity<PaginaDepartamentos> departamentos(HttpServletRequest chamada) {
        var parametros = chamada.getParameterMap();
        if (!Set.of("limite", "apos").containsAll(parametros.keySet())
                || parametros.values().stream().anyMatch(valores -> valores.length != 1)) {
            throw new EntradaRequisicaoInvalidaException();
        }

        var pagina = listarDepartamentos.executar(cursor(chamada.getParameter("apos")),
                limite(chamada.getParameter("limite")));
        var departamentos = pagina.departamentos().stream()
                .map(d -> new Departamento(d.id(), d.nome(), d.tetoMensal().toPlainString(),
                        d.saldoDisponivel().toPlainString()))
                .toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new PaginaDepartamentos(departamentos, pagina.proximoCursor()));
    }

    private static UUID cursor(String texto) {
        if (texto == null) {
            return null;
        }
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

    private static int limite(String texto) {
        if (texto == null) {
            return ListarDepartamentosUseCase.LIMITE_PADRAO;
        }
        try {
            return Integer.parseInt(texto);
        } catch (NumberFormatException falha) {
            throw new EntradaRequisicaoInvalidaException();
        }
    }

    @ExceptionHandler(AcessoNegadoException.class)
    ResponseEntity<RespostaErro> acessoNegado() {
        return erro(HttpStatus.FORBIDDEN, "ACESSO_NEGADO", "Operação não autorizada.");
    }

    @ExceptionHandler(EntradaRequisicaoInvalidaException.class)
    ResponseEntity<RespostaErro> entradaInvalida() {
        return erro(HttpStatus.BAD_REQUEST, "ENTRADA_INVALIDA", "Os parâmetros da consulta são inválidos.");
    }

    @ExceptionHandler(ServicoIndisponivelException.class)
    ResponseEntity<RespostaErro> servicoIndisponivel() {
        return erro(HttpStatus.SERVICE_UNAVAILABLE, "SERVICO_INDISPONIVEL", "O serviço está indisponível.");
    }

    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<RespostaErro> falhaInterna() {
        return erro(HttpStatus.INTERNAL_SERVER_ERROR, "FALHA_INTERNA", "Não foi possível consultar os dados do portal.");
    }

    private static ResponseEntity<RespostaErro> erro(HttpStatus status, String codigo, String mensagem) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .body(new RespostaErro(new Erro(codigo, mensagem)));
    }

    record Sessao(UUID usuarioId, PerfilUsuario perfil, UUID departamentoId) {}
    record PaginaDepartamentos(List<Departamento> departamentos, UUID proximoCursor) {}
    record Departamento(UUID id, String nome, String tetoMensal, String saldoDisponivel) {}
}
