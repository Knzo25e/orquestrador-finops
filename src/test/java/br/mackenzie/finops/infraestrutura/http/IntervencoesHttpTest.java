package br.mackenzie.finops.infraestrutura.http;

import br.mackenzie.finops.aplicacao.ListarIntervencoesUseCase;
import br.mackenzie.finops.aplicacao.excecoes.ServicoIndisponivelException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class IntervencoesHttpTest {
    @Test
    void falhasTecnicasRecebemErroPublicoSemMensagemInternaESemCache() throws Exception {
        var casoDeUso = mock(ListarIntervencoesUseCase.class);
        var http = MockMvcBuilders.standaloneSetup(new IntervencoesController(casoDeUso)).build();
        var departamento = UUID.randomUUID();
        when(casoDeUso.executar(departamento, null, null, 25))
                .thenThrow(new ServicoIndisponivelException())
                .thenThrow(new IllegalStateException("Mensagem interna protegida"));
        var rota = "/api/v1/departamentos/" + departamento + "/intervencoes";
        http.perform(get(rota)).andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.erro.codigo").value("SERVICO_INDISPONIVEL"));
        http.perform(get(rota)).andExpect(status().isInternalServerError())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.erro.codigo").value("FALHA_INTERNA"))
                .andExpect(jsonPath("$.erro.mensagem").value("Não foi possível consultar o histórico."));
    }
}
