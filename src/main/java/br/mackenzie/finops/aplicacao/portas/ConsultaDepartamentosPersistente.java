package br.mackenzie.finops.aplicacao.portas;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface ConsultaDepartamentosPersistente {
    List<Resumo> listarAutorizado(UUID usuario, UUID apos, int limite);

    record Resumo(UUID id, String nome, BigDecimal tetoMensal, BigDecimal saldoDisponivel) {}
}
