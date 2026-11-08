package br.mackenzie.finops.aplicacao.portas;

import br.mackenzie.finops.aplicacao.entradas.ItemSolicitado;
import br.mackenzie.finops.aplicacao.resultados.ResultadoCriacaoRequisicao;

import java.util.List;
import java.util.UUID;

/**
 * Deriva e protege os atributos do usuário autenticado na mesma transação
 * da criação. Cada tentativa, inclusive a reconciliação, reautoriza o usuário.
 */
public interface CriacaoComIdentidadeTransacional {
    ResultadoCriacaoRequisicao executarComIdentidade(
            UUID usuarioAutenticado, UUID requisicaoId, List<ItemSolicitado> itens
    );
}
