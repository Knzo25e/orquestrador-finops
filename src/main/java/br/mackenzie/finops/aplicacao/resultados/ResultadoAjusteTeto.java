package br.mackenzie.finops.aplicacao.resultados;

import java.util.Map;
import java.util.UUID;

/** O conteúdo financeiro sempre provém da fotografia gravada no recibo. */
public record ResultadoAjusteTeto(
        UUID operacaoId, String resultado, Map<String, String> dados,
        int statusHttp, boolean falhaNoFechamento
) {
    public ResultadoAjusteTeto {
        dados = Map.copyOf(dados);
    }
}
