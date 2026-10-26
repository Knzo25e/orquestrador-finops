package br.mackenzie.finops.aplicacao.seguranca;

import java.util.UUID;

/**
 * Informacoes de identidade fornecidas por uma integracao confiavel.
 *
 * Construir este objeto nao autentica o usuario.
 * Nao deve ser preenchido diretamente com dados enviados pelo cliente.
 *
 * Representa uma fotografia da identidade. Nao garante que o vinculo
 * permaneca vigente durante uma futura transacao JDBC.
 */
public record ContextoIdentidade(
        UUID usuarioId,
        PerfilUsuario perfil,
        boolean ativo,
        UUID departamentoId
) {

    public ContextoIdentidade {
        if (usuarioId == null) {
            throw new IllegalArgumentException(
                    "O identificador do usuario e obrigatorio."
            );
        }

        if (perfil == null) {
            throw new IllegalArgumentException(
                    "Um perfil reconhecido e obrigatorio."
            );
        }
    }
}