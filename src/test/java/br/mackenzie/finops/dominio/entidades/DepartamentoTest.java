package br.mackenzie.finops.dominio.entidades;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DepartamentoTest {

    @Test
    public void testVerificarDisponibilidadeComSaldoSuficiente() {
        Departamento depto = new Departamento();
        depto.setOrcamentoMensal(1000.0);
        depto.setSaldoDisponivel(1000.0);

        assertTrue(depto.verificarDisponibilidade(500.0), "Deve retornar true quando há saldo suficiente.");
    }

    @Test
    public void testVerificarDisponibilidadeComSaldoInsuficiente() {
        Departamento depto = new Departamento();
        depto.setOrcamentoMensal(1000.0);
        depto.setSaldoDisponivel(200.0);

        assertFalse(depto.verificarDisponibilidade(500.0), "Deve retornar false e bloquear quando não houver saldo.");
    }
}
