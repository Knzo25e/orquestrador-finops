package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.dominio.entidades.ResultadoValidacao;

/**
 * Converte os códigos persistidos para os estados do domínio.
 * Não executa roteamento nem altera entidades.
 */
final class EstadoRequisicaoJdbc {

    private EstadoRequisicaoJdbc() {
    }

    record EstadoRecuperado(
            String status,
            ResultadoValidacao motivoRevisao
    ) {
    }

    static EstadoRecuperado recuperar(
            String estado,
            String motivo
    ) {
        if (estado == null) {
            throw inconsistencia();
        }

        return switch (estado) {
            case "INICIAL" -> semMotivo("INICIAL", motivo);

            case "PROVISIONAMENTO_LIBERADO" ->
                    semMotivo("Provisionamento Liberado", motivo);

            case "REVISAO_PENDENTE" -> recuperarRevisao(motivo);

            default -> throw inconsistencia();
        };
    }

    private static EstadoRecuperado semMotivo(
            String status,
            String motivo
    ) {
        if (motivo != null) {
            throw inconsistencia();
        }

        return new EstadoRecuperado(status, null);
    }

    private static EstadoRecuperado recuperarRevisao(String motivo) {
        if (motivo == null) {
            throw inconsistencia();
        }

        ResultadoValidacao motivoDominio = switch (motivo) {
            case "SALDO_INSUFICIENTE" ->
                    ResultadoValidacao.REVISAO_POR_SALDO_INSUFICIENTE;

            case "RESERVA" ->
                    ResultadoValidacao.REVISAO_POR_RESERVA;

            default -> throw inconsistencia();
        };

        return new EstadoRecuperado(
                "Revisão Pendente",
                motivoDominio
        );
    }

    private static FalhaPersistenciaException inconsistencia() {
        return new FalhaPersistenciaException(
                "A requisição armazenada contém estado ou motivo inválido."
        );
    }
}