package br.mackenzie.finops.infraestrutura.http;

import br.mackenzie.finops.aplicacao.ConsultarRequisicaoAutorizadaUseCase;
import br.mackenzie.finops.aplicacao.ListarRequisicoesAutorizadasUseCase;
import br.mackenzie.finops.aplicacao.ValidarOrcamentoUseCase;
import br.mackenzie.finops.aplicacao.ValidarRequisicaoAutorizadaUseCase;
import br.mackenzie.finops.aplicacao.excecoes.ConfirmacaoValidacaoDesconhecidaException;
import br.mackenzie.finops.aplicacao.excecoes.ServicoIndisponivelException;
import br.mackenzie.finops.aplicacao.resultados.ResultadoValidacaoPersistente;
import br.mackenzie.finops.dominio.entidades.Departamento;
import br.mackenzie.finops.dominio.entidades.ItemRequisicao;
import br.mackenzie.finops.dominio.entidades.RecursoCatalogo;
import br.mackenzie.finops.dominio.entidades.Requisicao;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OperacoesRequisicaoHttpTest {
    private final ConsultarRequisicaoAutorizadaUseCase consulta = mock(ConsultarRequisicaoAutorizadaUseCase.class);
    private final ValidarRequisicaoAutorizadaUseCase validacao = mock(ValidarRequisicaoAutorizadaUseCase.class);
    private final ListarRequisicoesAutorizadasUseCase listagem = mock(ListarRequisicoesAutorizadasUseCase.class);
    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new OperacoesRequisicaoController(consulta, validacao, listagem)).build();

    @Test
    void commitIncertoTemCodigoProprioEIdentificador() throws Exception {
        UUID id = UUID.randomUUID();
        when(validacao.executar(id)).thenThrow(new ConfirmacaoValidacaoDesconhecidaException(id));
        mvc.perform(post("/api/v1/requisicoes/{id}/validacao", id))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.erro.codigo").value("CONFIRMACAO_VALIDACAO_DESCONHECIDA"))
                .andExpect(jsonPath("$.erro.requisicaoId").value(id.toString()));
    }

    @Test
    void falhaDeConsultaNuncaViraAusenciaOuColecaoVazia() throws Exception {
        UUID id = UUID.randomUUID();
        when(consulta.executar(id)).thenThrow(new IllegalArgumentException("Dado persistido inconsistente."));
        when(listagem.executarPagina(null, null, 25)).thenThrow(new ServicoIndisponivelException());
        mvc.perform(get("/api/v1/requisicoes/{id}", id))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.erro.codigo").value("FALHA_INTERNA"));
        mvc.perform(get("/api/v1/requisicoes"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.erro.codigo").value("SERVICO_INDISPONIVEL"));
    }

    @Test
    void corpoNaoVazioEUuidNaoCanonicoSaoRejeitadosAntesDaValidacao() throws Exception {
        mvc.perform(post("/api/v1/requisicoes/{id}/validacao", UUID.randomUUID()).content(" "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro.codigo").value("ENTRADA_INVALIDA"));
        mvc.perform(post("/api/v1/requisicoes/1-1-1-1-1/validacao"))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/v1/requisicoes/{id}/validacao", UUID.randomUUID())
                        .file(new MockMultipartFile("arquivo", new byte[]{1})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro.codigo").value("ENTRADA_INVALIDA"));
        verifyNoInteractions(validacao);
    }

    @Test
    void fechamentoPosteriorNaoConverteSucessoEmErro() throws Exception {
        UUID id = UUID.randomUUID();
        var departamento = new Departamento(UUID.randomUUID(), "Teste", new BigDecimal("100.00"));
        var requisicao = Requisicao.criarComId(id, departamento);
        requisicao.adicionarItem(new ItemRequisicao(
                new RecursoCatalogo(UUID.randomUUID(), "Teste", new BigDecimal("2.50")), 2
        ));
        var decisao = new ValidarOrcamentoUseCase().executar(requisicao);
        when(validacao.executar(id)).thenReturn(
                ResultadoValidacaoPersistente.concluida(requisicao, decisao).comFalhaNoFechamento()
        );
        mvc.perform(post("/api/v1/requisicoes/{id}/validacao", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("LIBERADA"))
                .andExpect(jsonPath("$.requisicao.custoTotalProjetado").value("5.00"))
                .andExpect(jsonPath("$.avisos[0].codigo").value("FALHA_NO_FECHAMENTO"));
    }
}
