package br.mackenzie.finops.aplicacao.seguranca;

import java.util.UUID;

/**
 * Decisoes de acesso da SPEC-006 / Issue #72.
 *
 * Nao autentica usuarios, consulta armazenamento ou altera entidades.
 * A integracao com os casos de uso permanece pendente.
 */
public final class PoliticaAutorizacao {

    public boolean podeConsultarCatalogo(ContextoIdentidade identidade) {
        return identidadeAtiva(identidade);
    }

    public boolean podeCriarRequisicao(
            ContextoIdentidade identidade,
            UUID departamentoSolicitado
    ) {
        return identidadeAtiva(identidade)
                && identidade.perfil() == PerfilUsuario.DESENVOLVEDOR
                && identidade.departamentoId() != null
                && identidade.departamentoId().equals(departamentoSolicitado);
    }

    /**
     * Autor e departamento devem ser obtidos de uma fonte confiavel.
     * Autor nulo representa pedido sem autoria conhecida.
     */
    public boolean podeConsultarRequisicao(
            ContextoIdentidade identidade,
            UUID autorId,
            UUID departamentoPedido
    ) {
        if (!identidadeAtiva(identidade) || departamentoPedido == null) {
            return false;
        }

        if (identidade.perfil() == PerfilUsuario.ARQUITETO_CLOUD) {
            return true;
        }

        return identidade.perfil() == PerfilUsuario.DESENVOLVEDOR
                && identidade.departamentoId() != null
                && identidade.usuarioId().equals(autorId)
                && identidade.departamentoId().equals(departamentoPedido);
    }

    private boolean identidadeAtiva(ContextoIdentidade identidade) {
        if (identidade == null || !identidade.ativo()) {
            return false;
        }

        return identidade.perfil() == PerfilUsuario.DESENVOLVEDOR
                || identidade.perfil() == PerfilUsuario.ARQUITETO_CLOUD;
    }
}