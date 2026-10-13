package br.mackenzie.finops.aplicacao;

import br.mackenzie.finops.dominio.entidades.Requisicao;
import br.mackenzie.finops.dominio.entidades.ResultadoValidacao;

public class ValidarOrcamentoUseCase {

    public ResultadoValidacao executar(Requisicao requisicao) {
        if (requisicao == null) {
            throw new IllegalArgumentException(
                    "A requisição é obrigatória para validar o orçamento."
            );
        }

        return requisicao.processarRoteamento();
    }
}