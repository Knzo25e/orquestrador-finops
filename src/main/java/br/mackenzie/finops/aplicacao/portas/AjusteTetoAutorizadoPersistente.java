package br.mackenzie.finops.aplicacao.portas;

import br.mackenzie.finops.aplicacao.entradas.SolicitacaoAjusteTeto;
import br.mackenzie.finops.aplicacao.resultados.ResultadoAjusteTeto;
import java.util.UUID;

public interface AjusteTetoAutorizadoPersistente {
    ResultadoAjusteTeto executarAutorizado(UUID usuario, UUID departamento, SolicitacaoAjusteTeto pedido);
}
