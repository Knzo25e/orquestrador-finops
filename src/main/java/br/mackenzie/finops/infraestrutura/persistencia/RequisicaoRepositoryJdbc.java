package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.aplicacao.portas.RequisicaoRepository;
import br.mackenzie.finops.dominio.entidades.Requisicao;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Armazenamento JDBC de requisições e itens.
 *
 * A conexão e a transação pertencem ao chamador.
 * Não compartilhar a conexão simultaneamente entre threads.
 *
 * Não substitui diretamente o repositório em memória
 * nos casos de uso A2.
 */
public final class RequisicaoRepositoryJdbc
        implements RequisicaoRepository {

    private final GravadorRequisicaoJdbc gravador;
    private final LeitorRequisicaoJdbc leitor;

    public RequisicaoRepositoryJdbc(Connection conexao) {
        if (conexao == null) {
            throw new IllegalArgumentException(
                    "A conexão é obrigatória."
            );
        }

        this.gravador = new GravadorRequisicaoJdbc(conexao);
        this.leitor = new LeitorRequisicaoJdbc(conexao);
    }

    /**
     * Insere cabeçalho e itens, sem confirmar a transação.
     *
     * Exige autocommit desabilitado.
     * Qualquer falha exige rollback pelo chamador.
     */
    @Override
    public void adicionar(Requisicao requisicao) {
        gravador.adicionar(requisicao);
    }

    /**
     * Recupera a requisição sem executar efeitos financeiros.
     *
     * Exige READ COMMITTED ou REPEATABLE READ.
     */
    @Override
    public Optional<Requisicao> buscarPorId(UUID id) {
        return leitor.buscarPorId(id);
    }

    /**
     * Retorna lista não modificável, sem garantia contratual de ordem.
     *
     * Exige READ COMMITTED ou REPEATABLE READ.
     */
    @Override
    public List<Requisicao> listarPorDepartamento(UUID departamentoId) {
        return leitor.listarPorDepartamento(departamentoId);
    }

    /**
     * Busca pelo UUID dentro do escopo de autor e departamento.
     * Ausência e pedido fora do escopo retornam Optional.empty().
     *
     * Os identificadores do escopo devem vir da aplicação.
     * Este método não autentica nem verifica o perfil do usuário.
     */
    public Optional<Requisicao> buscarPorIdEAutorEDepartamento(
            UUID id,
            UUID autorId,
            UUID departamentoId
    ) {
        return leitor.buscarPorIdEAutorEDepartamento(
                id, autorId, departamentoId
        );
    }

    /**
     * Lista somente pedidos do autor e departamento informados.
     * Retorna lista não modificável.
     *
     * Os identificadores do escopo devem vir da aplicação.
     * Este método não autentica nem verifica o perfil do usuário.
     */
    public List<Requisicao> listarPorAutorEDepartamento(
            UUID autorId,
            UUID departamentoId
    ) {
        return leitor.listarPorAutorEDepartamento(
                autorId, departamentoId
        );
    }
}