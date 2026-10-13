package br.mackenzie.finops.dominio.entidades;

public enum ResultadoValidacao {
    LIBERADA,
    REVISAO_POR_SALDO_INSUFICIENTE,
    REVISAO_POR_RESERVA,
    JA_LIBERADA,
    ESTADO_NAO_PERMITIDO
}