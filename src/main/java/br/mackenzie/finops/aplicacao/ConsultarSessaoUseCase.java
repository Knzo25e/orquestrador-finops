package br.mackenzie.finops.aplicacao;

import br.mackenzie.finops.aplicacao.excecoes.AcessoNegadoException;
import br.mackenzie.finops.aplicacao.portas.ProvedorIdentidade;
import br.mackenzie.finops.aplicacao.seguranca.ContextoIdentidade;
import br.mackenzie.finops.aplicacao.seguranca.PoliticaAutorizacao;

public final class ConsultarSessaoUseCase {
    private final ProvedorIdentidade identidade;
    private final PoliticaAutorizacao politica = new PoliticaAutorizacao();

    public ConsultarSessaoUseCase(ProvedorIdentidade identidade) {
        this.identidade = identidade;
    }

    public ContextoIdentidade executar() {
        return identidade.obterIdentidadeAtual()
                .filter(politica::podeConsultarCatalogo)
                .orElseThrow(AcessoNegadoException::new);
    }
}
