package br.mackenzie.finops.aplicacao.resultados;

import br.mackenzie.finops.dominio.entidades.Requisicao;
import br.mackenzie.finops.dominio.entidades.ResultadoValidacao;

/**
 * Resultado de uma validação persistente concluída.
 *
 * A infraestrutura somente entrega este resultado após confirmar
 * as alterações ou encerrar a transação sem alterações.
 *
 * A requisição representa os dados daquela execução.
 * Alterações no objeto retornado não atualizam automaticamente o banco.
 */
public record ResultadoValidacaoPersistente(
        Requisicao requisicao,
        ResultadoValidacao resultado,
        boolean falhaNoFechamento
) {
    public ResultadoValidacaoPersistente {
        if (requisicao == null || requisicao.getId() == null) {
            throw new IllegalArgumentException(
                    "O resultado deve possuir uma requisição com identificador."
            );
        }

        if (requisicao.getDepartamento() == null
                || requisicao.getDepartamento().getId() == null) {
            throw new IllegalArgumentException(
                    "A requisição deve possuir um departamento com identificador."
            );
        }

        if (resultado == null) {
            throw new IllegalArgumentException(
                    "O resultado da validação é obrigatório."
            );
        }
    }

    public static ResultadoValidacaoPersistente concluida(
            Requisicao requisicao,
            ResultadoValidacao resultado
    ) {
        return new ResultadoValidacaoPersistente(
                requisicao,
                resultado,
                false
        );
    }

    public ResultadoValidacaoPersistente comFalhaNoFechamento() {
        return new ResultadoValidacaoPersistente(
                requisicao,
                resultado,
                true
        );
    }
}