package br.mackenzie.finops.aplicacao;

import br.mackenzie.finops.aplicacao.auditoria.CursorIntervencao;
import br.mackenzie.finops.aplicacao.auditoria.EventoIntervencao;
import br.mackenzie.finops.aplicacao.excecoes.AcessoNegadoException;
import br.mackenzie.finops.aplicacao.excecoes.EntradaRequisicaoInvalidaException;
import br.mackenzie.finops.aplicacao.portas.ConsultaIntervencoesPersistente;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class ListarIntervencoesUseCase {
    private final Supplier<Optional<UUID>> usuario;
    private final ConsultaIntervencoesPersistente consulta;

    public ListarIntervencoesUseCase(Supplier<Optional<UUID>> usuario, ConsultaIntervencoesPersistente consulta) {
        this.usuario = usuario;
        this.consulta = consulta;
    }

    public Pagina executar(UUID departamento, UUID requisicao, String apos, int limite) {
        var id = usuario.get().orElseThrow(AcessoNegadoException::new);
        if (departamento == null || limite < 1 || limite > 100) {
            throw new EntradaRequisicaoInvalidaException();
        }
        var eventos = consulta.listarAutorizado(id, departamento, requisicao, CursorIntervencao.ler(apos), limite + 1);
        var pagina = List.copyOf(eventos.subList(0, Math.min(limite, eventos.size())));
        String proximo = eventos.size() > limite
                ? new CursorIntervencao(pagina.getLast().instante(), pagina.getLast().operacaoId()).codificar() : null;
        return new Pagina(pagina, proximo);
    }

    public record Pagina(List<EventoIntervencao> intervencoes, String proximoCursor) {}
}
