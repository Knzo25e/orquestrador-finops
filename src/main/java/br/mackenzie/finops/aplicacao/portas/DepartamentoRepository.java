package br.mackenzie.finops.aplicacao.portas;

import br.mackenzie.finops.dominio.entidades.Departamento;

import java.util.Optional;
import java.util.UUID;

/**
 * Contrato de cadastro e consulta definido na SPEC-005, recorte A1.
 */
public interface DepartamentoRepository {

    /**
     * Cadastra um departamento sem substituir registros existentes.
     *
     * @throws IllegalArgumentException se o departamento ou seu identificador
     *                                  forem nulos, ou se o identificador existir
     */
    void adicionar(Departamento departamento);

    /**
     * Retorna ausência quando o identificador não estiver cadastrado.
     *
     * @throws IllegalArgumentException se o identificador for nulo
     */
    Optional<Departamento> buscarPorId(UUID id);
}