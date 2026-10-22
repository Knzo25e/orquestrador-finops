package br.mackenzie.finops.aplicacao.resultados;

import br.mackenzie.finops.dominio.entidades.Requisicao;

/**
 * Resultado de uma criação confirmada ou repetição reconhecida.
 *
 * Uma falha de fechamento não desfaz a confirmação da operação.
 * A requisição retornada não possui atualização automática no banco.
 */
public record ResultadoCriacaoRequisicao(
        Requisicao requisicao,
        Situacao situacao,
        boolean falhaNoFechamento
) {
    public enum Situacao {
        CRIADA,
        JA_EXISTENTE
    }

    public ResultadoCriacaoRequisicao {
        if (requisicao == null || requisicao.getId() == null) {
            throw new IllegalArgumentException(
                    "O resultado deve possuir uma requisição com identificador."
            );
        }

        if (situacao == null) {
            throw new IllegalArgumentException(
                    "A situação da criação é obrigatória."
            );
        }
    }

    public static ResultadoCriacaoRequisicao criada(
            Requisicao requisicao
    ) {
        return new ResultadoCriacaoRequisicao(
                requisicao,
                Situacao.CRIADA,
                false
        );
    }

    public static ResultadoCriacaoRequisicao jaExistente(
            Requisicao requisicao
    ) {
        return new ResultadoCriacaoRequisicao(
                requisicao,
                Situacao.JA_EXISTENTE,
                false
        );
    }

    public ResultadoCriacaoRequisicao comFalhaNoFechamento() {
        return new ResultadoCriacaoRequisicao(
                requisicao,
                situacao,
                true
        );
    }
}