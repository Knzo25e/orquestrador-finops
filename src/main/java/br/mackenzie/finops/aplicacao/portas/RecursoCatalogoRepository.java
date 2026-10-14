package br.mackenzie.finops.aplicacao.portas;

import br.mackenzie.finops.dominio.entidades.RecursoCatalogo;

import java.util.Optional;
import java.util.UUID;

/**
 * Contrato de cadastro e consulta definido na SPEC-005, recorte A1.
 */
public interface RecursoCatalogoRepository {

    /**
     * Cadastra um recurso sem substituir registros existentes.
     *
     * @throws IllegalArgumentException se o recurso ou seu identificador
     *                                  forem nulos, ou se o identificador existir
     */
    void adicionar(RecursoCatalogo recurso);

    /**
     * Retorna ausência quando o identificador não estiver cadastrado.
     *
     * @throws IllegalArgumentException se o identificador for nulo
     */
    Optional<RecursoCatalogo> buscarPorId(UUID id);
}