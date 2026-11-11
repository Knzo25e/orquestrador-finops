package br.mackenzie.finops.aplicacao.auditoria;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Fotografia pública imutável. Nunca contém credenciais ou dados de sessão. */
public record EventoIntervencao(
        UUID operacaoId, UUID responsavelId, String acao, Instant instante,
        String justificativa, UUID departamentoId, UUID requisicaoId,
        String resultado, Map<String, String> dados
) {
    public EventoIntervencao {
        dados = Map.copyOf(dados);
    }
}
