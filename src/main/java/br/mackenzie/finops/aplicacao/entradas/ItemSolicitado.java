package br.mackenzie.finops.aplicacao.entradas;

import java.util.UUID;

/**
 * Entrada da criação integrada, conforme a SPEC-005, recorte A2.
 * Os valores financeiros são obtidos do recurso cadastrado.
 */
public record ItemSolicitado(UUID recursoId, Integer quantidade) {

    public ItemSolicitado {
        if (recursoId == null) {
            throw new IllegalArgumentException(
                    "O identificador do recurso é obrigatório."
            );
        }

        if (quantidade == null || quantidade <= 0) {
            throw new IllegalArgumentException(
                    "A quantidade solicitada deve ser maior que zero."
            );
        }
    }
}