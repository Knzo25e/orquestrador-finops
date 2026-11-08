package br.mackenzie.finops.infraestrutura.http;

import br.mackenzie.finops.aplicacao.ListarIntervencoesUseCase;
import br.mackenzie.finops.aplicacao.excecoes.AcessoNegadoException;
import br.mackenzie.finops.aplicacao.excecoes.DepartamentoNaoEncontradoException;
import br.mackenzie.finops.aplicacao.excecoes.EntradaRequisicaoInvalidaException;
import br.mackenzie.finops.aplicacao.excecoes.ServicoIndisponivelException;
import java.util.Set;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class IntervencoesController {
    private final ListarIntervencoesUseCase listagem;

    public IntervencoesController(ListarIntervencoesUseCase listagem) {
        this.listagem = listagem;
    }

    @GetMapping(path = "/api/v1/departamentos/{id}/intervencoes", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> listar(@PathVariable("id") String id, HttpServletRequest chamada) {
        var parametros = chamada.getParameterMap();
        if (!Set.of("requisicaoId", "apos", "limite").containsAll(parametros.keySet())
                || parametros.values().stream().anyMatch(valores -> valores.length != 1)) {
            throw new EntradaRequisicaoInvalidaException();
        }
        int limite = 25;
        if (parametros.containsKey("limite")) {
            try {
                limite = Integer.parseInt(chamada.getParameter("limite"));
            } catch (NumberFormatException falha) {
                throw new EntradaRequisicaoInvalidaException();
            }
        }
        UUID requisicao = parametros.containsKey("requisicaoId") ? uuid(chamada.getParameter("requisicaoId")) : null;
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                listagem.executar(uuid(id), requisicao, chamada.getParameter("apos"), limite));
    }

    private static UUID uuid(String texto) {
        try {
            var id = UUID.fromString(texto);
            if (id.toString().equals(texto)) {
                return id;
            }
        } catch (IllegalArgumentException falha) {
            // Mesmo erro público para todas as formas inválidas.
        }
        throw new EntradaRequisicaoInvalidaException();
    }

    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<TratamentoErrosHttp.RespostaErro> erro(RuntimeException falha) {
        int status = 500;
        String codigo = "FALHA_INTERNA";
        String mensagem = "Não foi possível consultar o histórico.";
        if (falha instanceof AcessoNegadoException) {
            status = 403;
            codigo = "ACESSO_NEGADO";
            mensagem = "Operação não autorizada.";
        } else if (falha instanceof EntradaRequisicaoInvalidaException) {
            status = 400;
            codigo = "ENTRADA_INVALIDA";
            mensagem = "A entrada da requisição é inválida.";
        } else if (falha instanceof DepartamentoNaoEncontradoException) {
            status = 404;
            codigo = "DEPARTAMENTO_NAO_ENCONTRADO";
            mensagem = "Departamento não encontrado.";
        } else if (falha instanceof ServicoIndisponivelException) {
            status = 503;
            codigo = "SERVICO_INDISPONIVEL";
            mensagem = "O serviço está indisponível.";
        }
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(
                new TratamentoErrosHttp.RespostaErro(new TratamentoErrosHttp.Erro(codigo, mensagem)));
    }
}
