package br.mackenzie.finops.infraestrutura.persistencia;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Entrada interna. Cada adaptador de comando fornece parâmetros já normalizados. */
record ComandoIntervencao(
        UUID operacaoId, UUID responsavelId, String acao, UUID departamentoId,
        UUID requisicaoId, String justificativa, Map<String, String> parametros
) {
    ComandoIntervencao {
        Objects.requireNonNull(operacaoId);
        Objects.requireNonNull(responsavelId);
        Objects.requireNonNull(departamentoId);
        if (acao == null || !acao.matches("[A-Z][A-Z_]{0,63}") || justificativa == null) {
            throw new IllegalArgumentException("Comando inválido.");
        }
        justificativa = justificativa.strip();
        if (justificativa.isEmpty() || justificativa.codePointCount(0, justificativa.length()) > 500) {
            throw new IllegalArgumentException("Justificativa inválida.");
        }
        parametros = Map.copyOf(parametros);
    }
}
