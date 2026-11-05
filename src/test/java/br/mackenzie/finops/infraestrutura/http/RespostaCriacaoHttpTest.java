package br.mackenzie.finops.infraestrutura.http;

import br.mackenzie.finops.aplicacao.resultados.ResultadoCriacaoRequisicao;
import br.mackenzie.finops.dominio.entidades.Departamento;
import br.mackenzie.finops.dominio.entidades.ItemRequisicao;
import br.mackenzie.finops.dominio.entidades.RecursoCatalogo;
import br.mackenzie.finops.dominio.entidades.Requisicao;
import br.mackenzie.finops.dominio.entidades.ResultadoValidacao;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RespostaCriacaoHttpTest {

    @ParameterizedTest
    @CsvSource({
            "INICIAL, INICIAL, ,",
            "Provisionamento Liberado, PROVISIONAMENTO_LIBERADO, ,",
            "Revisão Pendente, REVISAO_PENDENTE, REVISAO_POR_SALDO_INSUFICIENTE, SALDO_INSUFICIENTE",
            "Revisão Pendente, REVISAO_PENDENTE, REVISAO_POR_RESERVA, RESERVA"
    })
    void representaEstadoEMotivoConformeContrato(
            String estadoInterno,
            String estadoPublico,
            String motivoInterno,
            String motivoPublico
    ) {
        var departamento = new Departamento(
                UUID.randomUUID(), "Engenharia", new BigDecimal("1000.00")
        );
        var recurso = new RecursoCatalogo(
                UUID.randomUUID(), "Armazenamento", new BigDecimal("12.50")
        );
        var itens = List.of(new ItemRequisicao(recurso, 2));
        var motivo = motivoInterno == null
                ? null
                : ResultadoValidacao.valueOf(motivoInterno);

        var requisicao = Requisicao.reconstituir(
                UUID.randomUUID(),
                departamento,
                itens,
                estadoInterno,
                new BigDecimal("25.00"),
                motivo
        );

        var resposta = RespostaCriacaoHttp.de(
                ResultadoCriacaoRequisicao.jaExistente(requisicao)
        );

        assertEquals(estadoPublico, resposta.requisicao().estado());
        assertEquals(motivoPublico, resposta.requisicao().motivoRevisao());
        assertEquals(estadoInterno, requisicao.getStatus());
        assertEquals(motivo, requisicao.getMotivoRevisao());
    }
}