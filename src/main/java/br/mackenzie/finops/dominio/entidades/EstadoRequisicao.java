package br.mackenzie.finops.dominio.entidades;

/** Códigos estáveis, preservando as representações internas anteriores. */
public enum EstadoRequisicao {
    INICIAL("INICIAL"),
    REVISAO_PENDENTE("Revisão Pendente"),
    PROVISIONAMENTO_LIBERADO("Provisionamento Liberado"),
    AGUARDANDO_AJUSTE_ORCAMENTARIO("AGUARDANDO_AJUSTE_ORCAMENTARIO"),
    AGUARDANDO_AUTORIZACAO_FINANCEIRA("AGUARDANDO_AUTORIZACAO_FINANCEIRA"),
    AGUARDANDO_LIBERACAO_FINANCEIRA("AGUARDANDO_LIBERACAO_FINANCEIRA"),
    REJEITADO("REJEITADO");

    private final String status;

    EstadoRequisicao(String status) {
        this.status = status;
    }

    public String statusDominio() {
        return status;
    }

    public static EstadoRequisicao deStatus(String status) {
        for (var estado : values()) {
            if (estado.status.equals(status)) {
                return estado;
            }
        }
        throw new IllegalArgumentException("Estado de requisição inválido.");
    }
}
