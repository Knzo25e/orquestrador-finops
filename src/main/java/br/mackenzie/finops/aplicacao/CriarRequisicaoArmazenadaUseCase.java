package br.mackenzie.finops.aplicacao;

import br.mackenzie.finops.aplicacao.entradas.ItemSolicitado;
import br.mackenzie.finops.aplicacao.portas.DepartamentoRepository;
import br.mackenzie.finops.aplicacao.portas.RecursoCatalogoRepository;
import br.mackenzie.finops.aplicacao.portas.RequisicaoRepository;
import br.mackenzie.finops.dominio.entidades.Departamento;
import br.mackenzie.finops.dominio.entidades.ItemRequisicao;
import br.mackenzie.finops.dominio.entidades.RecursoCatalogo;
import br.mackenzie.finops.dominio.entidades.Requisicao;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Criação integrada aos repositórios em memória.
 * Base: SPEC-002 e SPEC-005, recorte A2 aprovado na Issue #51.
 */
public class CriarRequisicaoArmazenadaUseCase {

    private final DepartamentoRepository departamentos;
    private final RecursoCatalogoRepository recursos;
    private final RequisicaoRepository requisicoes;
    private final CriarRequisicaoUseCase criarRequisicao =
            new CriarRequisicaoUseCase();

    public CriarRequisicaoArmazenadaUseCase(
            DepartamentoRepository departamentos,
            RecursoCatalogoRepository recursos,
            RequisicaoRepository requisicoes
    ) {
        if (departamentos == null || recursos == null || requisicoes == null) {
            throw new IllegalArgumentException(
                    "Os repositórios de departamentos, recursos e requisições são obrigatórios."
            );
        }

        this.departamentos = departamentos;
        this.recursos = recursos;
        this.requisicoes = requisicoes;
    }

    public Requisicao executar(
            UUID departamentoId,
            List<ItemSolicitado> itensSolicitados
    ) {
        if (departamentoId == null) {
            throw new IllegalArgumentException(
                    "O identificador do departamento é obrigatório."
            );
        }

        List<ItemSolicitado> entradas = copiarEntradas(itensSolicitados);

        Departamento departamento = departamentos.buscarPorId(departamentoId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "O departamento solicitado não está cadastrado."
                ));

        List<ItemRequisicao> itens = new ArrayList<>();

        for (ItemSolicitado entrada : entradas) {
            RecursoCatalogo recurso = recursos.buscarPorId(entrada.recursoId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "O recurso solicitado não está cadastrado: "
                                    + entrada.recursoId()
                    ));

            itens.add(new ItemRequisicao(recurso, entrada.quantidade()));
        }

        Requisicao novaRequisicao =
                criarRequisicao.executar(departamento, itens);

        requisicoes.adicionar(novaRequisicao);

        return novaRequisicao;
    }

    private static List<ItemSolicitado> copiarEntradas(
            List<ItemSolicitado> itensSolicitados
    ) {
        if (itensSolicitados == null) {
            throw new IllegalArgumentException(
                    "A lista de itens solicitados é obrigatória."
            );
        }

        List<ItemSolicitado> copia = new ArrayList<>(itensSolicitados);

        if (copia.isEmpty()) {
            throw new IllegalArgumentException(
                    "A requisição deve conter pelo menos um item solicitado."
            );
        }

        for (ItemSolicitado item : copia) {
            if (item == null) {
                throw new IllegalArgumentException(
                        "A lista não pode conter um item solicitado nulo."
                );
            }
        }

        return List.copyOf(copia);
    }
}