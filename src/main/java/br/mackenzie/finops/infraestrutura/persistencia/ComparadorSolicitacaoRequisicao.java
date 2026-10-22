package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.aplicacao.entradas.ItemSolicitado;
import br.mackenzie.finops.aplicacao.entradas.SolicitacaoCriacaoRequisicao;
import br.mackenzie.finops.aplicacao.excecoes.ConflitoIdentificadorException;
import br.mackenzie.finops.dominio.entidades.ItemRequisicao;
import br.mackenzie.finops.dominio.entidades.Requisicao;

import java.util.List;

/**
 * Confere se um pedido armazenado corresponde à solicitação repetida.
 *
 * Não compara preços atuais, saldo, nomes ou estado.
 * Não altera a requisição.
 */
final class ComparadorSolicitacaoRequisicao {

    private ComparadorSolicitacaoRequisicao() {
    }

    static void conferir(
            SolicitacaoCriacaoRequisicao solicitacao,
            Requisicao existente
    ) {
        if (solicitacao == null || existente == null) {
            throw new IllegalArgumentException(
                    "A solicitação e a requisição existente são obrigatórias."
            );
        }

        if (!solicitacao.requisicaoId().equals(existente.getId())) {
            throw new FalhaPersistenciaException(
                    "A requisição recuperada difere do identificador consultado."
            );
        }

        if (existente.getDepartamento() == null
                || existente.getDepartamento().getId() == null) {
            throw new FalhaPersistenciaException(
                    "A requisição recuperada não possui departamento identificável."
            );
        }

        if (!solicitacao.departamentoId().equals(
                existente.getDepartamento().getId()
        )) {
            throw new ConflitoIdentificadorException(
                    solicitacao.requisicaoId()
            );
        }

        List<ItemSolicitado> solicitados = solicitacao.itens();
        List<ItemRequisicao> armazenados = existente.getItens();

        if (solicitados.size() != armazenados.size()) {
            throw new ConflitoIdentificadorException(
                    solicitacao.requisicaoId()
            );
        }

        for (int indice = 0; indice < solicitados.size(); indice++) {
            ItemSolicitado solicitado = solicitados.get(indice);
            ItemRequisicao armazenado = armazenados.get(indice);

            if (armazenado == null
                    || armazenado.getRecurso() == null
                    || armazenado.getRecurso().getId() == null
                    || armazenado.getQuantidade() == null) {
                throw new FalhaPersistenciaException(
                        "A requisição recuperada contém um item inconsistente."
                );
            }

            if (!solicitado.recursoId().equals(
                    armazenado.getRecurso().getId()
            ) || !solicitado.quantidade().equals(
                    armazenado.getQuantidade()
            )) {
                throw new ConflitoIdentificadorException(
                        solicitacao.requisicaoId()
                );
            }
        }
    }
}