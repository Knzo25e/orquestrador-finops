package br.mackenzie.finops.aplicacao;

import br.mackenzie.finops.aplicacao.excecoes.AcessoNegadoException;
import br.mackenzie.finops.aplicacao.portas.ConsultaCatalogoPersistente;
import br.mackenzie.finops.aplicacao.portas.ProvedorIdentidade;
import br.mackenzie.finops.aplicacao.seguranca.ContextoIdentidade;
import br.mackenzie.finops.aplicacao.seguranca.PoliticaAutorizacao;
import br.mackenzie.finops.dominio.entidades.RecursoCatalogo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Lista o catálogo após verificar a identidade e a autorização.
 *
 * Utiliza a identidade obtida no início da chamada.
 * Não exige vínculo departamental para consultar o catálogo.
 */
public final class ListarCatalogoAutorizadoUseCase {

    private final ProvedorIdentidade identidades;
    private final ConsultaCatalogoPersistente consultas;
    private final PoliticaAutorizacao politica;

    public ListarCatalogoAutorizadoUseCase(
            ProvedorIdentidade identidades,
            ConsultaCatalogoPersistente consultas
    ) {
        if (identidades == null || consultas == null) {
            throw new IllegalArgumentException(
                    "O provedor de identidade e a consulta são obrigatórios."
            );
        }

        this.identidades = identidades;
        this.consultas = consultas;
        this.politica = new PoliticaAutorizacao();
    }

    public List<RecursoCatalogo> executar() {
        Optional<ContextoIdentidade> resultado =
                identidades.obterIdentidadeAtual();

        if (resultado == null) {
            throw new IllegalStateException(
                    "O provedor de identidade retornou um resultado inválido."
            );
        }

        ContextoIdentidade identidade = resultado.orElseThrow(
                AcessoNegadoException::new
        );

        if (!politica.podeConsultarCatalogo(identidade)) {
            throw new AcessoNegadoException();
        }

        List<RecursoCatalogo> encontrados = consultas.listar();

        if (encontrados == null) {
            throw new IllegalStateException(
                    "A consulta retornou um resultado inválido."
            );
        }

        List<RecursoCatalogo> copia = new ArrayList<>(encontrados);
        String identificadorAnterior = null;

        for (RecursoCatalogo recurso : copia) {
            if (recurso == null || recurso.getId() == null) {
                throw inconsistencia();
            }

            String identificadorAtual = recurso.getId().toString();

            // A ordem contratada é textual, não UUID.compareTo().
            // Igualdade também indica um identificador duplicado.
            if (identificadorAnterior != null
                    && identificadorAnterior.compareTo(
                            identificadorAtual
                    ) >= 0) {
                throw inconsistencia();
            }

            identificadorAnterior = identificadorAtual;
        }

        return List.copyOf(copia);
    }

    private static IllegalStateException inconsistencia() {
        return new IllegalStateException(
                "A consulta retornou recursos inconsistentes "
                        + "ou fora da ordem contratada."
        );
    }
}