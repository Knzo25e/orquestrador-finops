package br.mackenzie.finops.aplicacao.portas;

import br.mackenzie.finops.aplicacao.resultados.ResultadoValidacaoPersistente;

import java.util.UUID;

public interface ValidacaoRequisicaoAutorizadaPersistente {
    /** Relê e protege a identidade autenticada na mesma transação da validação. */
    ResultadoValidacaoPersistente executarAutorizado(UUID usuarioAutenticado, UUID requisicaoId);
}
