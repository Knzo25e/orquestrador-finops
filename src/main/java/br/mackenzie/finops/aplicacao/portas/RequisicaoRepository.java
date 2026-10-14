package br.mackenzie.finops.aplicacao.portas;

import br.mackenzie.finops.dominio.entidades.Requisicao;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Contrato de cadastro e consulta definido na SPEC-005, recorte A1.
 */
public interface RequisicaoRepository {

    /**
     * Cadastra uma requisição em INICIAL, com itens e vínculos válidos,
     * sem substituir registros existentes ou executar roteamento.
     *
     * @throws IllegalArgumentException se a requisição, seu identificador,
     *                                  estado, itens ou vínculos forem inválidos,
     *                                  ou se o identificador existir
     */
    void adicionar(Requisicao requisicao);

    /**
     * Retorna ausência quando o identificador não estiver cadastrado.
     *
     * @throws IllegalArgumentException se o identificador for nulo
     */
    Optional<Requisicao> buscarPorId(UUID id);

    /**
     * Retorna uma lista não modificável, sem ordem garantida.
     * Os objetos contidos na lista não são necessariamente imutáveis.
     * Retorna lista vazia quando não houver requisições para o departamento.
     *
     * @throws IllegalArgumentException se o identificador do departamento for nulo
     */
    List<Requisicao> listarPorDepartamento(UUID departamentoId);
}