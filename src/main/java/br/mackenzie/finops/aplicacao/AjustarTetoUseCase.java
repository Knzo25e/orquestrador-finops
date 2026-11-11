package br.mackenzie.finops.aplicacao;

import br.mackenzie.finops.aplicacao.entradas.SolicitacaoAjusteTeto;
import br.mackenzie.finops.aplicacao.excecoes.AcessoNegadoException;
import br.mackenzie.finops.aplicacao.excecoes.EntradaRequisicaoInvalidaException;
import br.mackenzie.finops.aplicacao.portas.AjusteTetoAutorizadoPersistente;
import br.mackenzie.finops.aplicacao.resultados.ResultadoAjusteTeto;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class AjustarTetoUseCase {
    private final Supplier<Optional<UUID>> usuario;
    private final AjusteTetoAutorizadoPersistente ajuste;

    public AjustarTetoUseCase(Supplier<Optional<UUID>> usuario, AjusteTetoAutorizadoPersistente ajuste) {
        this.usuario = usuario;
        this.ajuste = ajuste;
    }

    public ResultadoAjusteTeto executar(UUID departamento, SolicitacaoAjusteTeto pedido) {
        UUID autenticado = usuario.get().orElseThrow(AcessoNegadoException::new);
        if (departamento == null || pedido == null) {
            throw new EntradaRequisicaoInvalidaException();
        }
        return ajuste.executarAutorizado(autenticado, departamento, pedido);
    }
}
