package br.mackenzie.finops.dominio.entidades;

public enum DecisaoManual {
    APROVAR, REJEITAR, LIBERAR_SEM_RESERVA, AUTORIZAR_RESERVA, NEGAR_RESERVA;

    public boolean tecnica() {
        return this == APROVAR || this == REJEITAR;
    }
}
