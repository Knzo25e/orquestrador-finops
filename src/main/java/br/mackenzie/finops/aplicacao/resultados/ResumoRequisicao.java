package br.mackenzie.finops.aplicacao.resultados;

import br.mackenzie.finops.dominio.entidades.ResultadoValidacao;

import java.math.BigDecimal;
import java.util.UUID;

/** Projeção do cabeçalho. Autor e departamento servem à conferência de escopo. */
public record ResumoRequisicao(
        UUID id, UUID departamentoId, UUID autorId, String estado,
        ResultadoValidacao motivoRevisao, BigDecimal custoTotalProjetado
) {
}
