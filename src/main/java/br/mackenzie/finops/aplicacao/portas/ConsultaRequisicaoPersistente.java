package br.mackenzie.finops.aplicacao.portas;

import br.mackenzie.finops.dominio.entidades.Requisicao;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Consultas persistentes utilizadas pelos casos de uso de acesso.
 *
 * A implementação controla os recursos necessários à consulta.
 * O contrato não expõe tipos JDBC e não executa alterações de negócio.
 *
 * A aplicação deve verificar identidade e perfil antes da chamada.
 * Informar identificadores não autentica nem autoriza o solicitante.
 */
public interface ConsultaRequisicaoPersistente {

    /**
     * Busca dentro do escopo de autoria e departamento.
     *
     * Todos os identificadores são obrigatórios.
     * Pedido inexistente ou fora do escopo retorna Optional.empty().
     * O filtro deve ser aplicado no armazenamento, antes da reconstituição.
     */
    Optional<Requisicao> buscarPorIdEAutorEDepartamento(
            UUID requisicaoId,
            UUID autorId,
            UUID departamentoId
    );

    /**
     * Lista somente pedidos do autor e departamento informados.
     *
     * Os dois identificadores são obrigatórios.
     * Retorna lista não modificável; ausência de pedidos retorna lista vazia.
     * O filtro deve ser aplicado no armazenamento.
     */
    List<Requisicao> listarPorAutorEDepartamento(
            UUID autorId,
            UUID departamentoId
    );

    /**
     * Busca sem restrição de autoria ou departamento.
     *
     * A aplicação deve reservar esta operação ao Arquiteto Cloud ativo.
     * O identificador é obrigatório.
     * Pedido inexistente retorna Optional.empty().
     */
    Optional<Requisicao> buscarPorId(UUID requisicaoId);

    /**
     * Lista pedidos de um departamento, independentemente da autoria.
     *
     * A aplicação deve reservar esta operação ao Arquiteto Cloud ativo.
     * O identificador é obrigatório.
     * Retorna lista não modificável; ausência de pedidos retorna lista vazia.
     */
    List<Requisicao> listarPorDepartamento(UUID departamentoId);
}