package br.mackenzie.finops.aplicacao;

import br.mackenzie.finops.aplicacao.excecoes.AcessoNegadoException;
import br.mackenzie.finops.aplicacao.excecoes.EntradaRequisicaoInvalidaException;
import br.mackenzie.finops.aplicacao.portas.ConsultaDepartamentosPersistente;
import br.mackenzie.finops.aplicacao.portas.ConsultaDepartamentosPersistente.Resumo;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class ListarDepartamentosUseCase {
    public static final int LIMITE_PADRAO = 25;
    private static final int LIMITE_MAXIMO = 100;

    private final Supplier<Optional<UUID>> usuario;
    private final ConsultaDepartamentosPersistente consulta;

    public ListarDepartamentosUseCase(Supplier<Optional<UUID>> usuario, ConsultaDepartamentosPersistente consulta) {
        this.usuario = usuario;
        this.consulta = consulta;
    }

    public Pagina executar(UUID apos, int limite) {
        var id = usuario.get().orElseThrow(AcessoNegadoException::new);
        if (limite < 1 || limite > LIMITE_MAXIMO) {
            throw new EntradaRequisicaoInvalidaException();
        }

        var encontrados = consulta.listarAutorizado(id, apos, limite + 1);
        var departamentos = List.copyOf(encontrados.subList(0, Math.min(limite, encontrados.size())));
        UUID proximoCursor = encontrados.size() > limite ? departamentos.getLast().id() : null;
        return new Pagina(departamentos, proximoCursor);
    }

    public record Pagina(List<Resumo> departamentos, UUID proximoCursor) {}
}
