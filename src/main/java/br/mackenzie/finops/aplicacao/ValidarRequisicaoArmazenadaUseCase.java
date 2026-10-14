package br.mackenzie.finops.aplicacao;

import br.mackenzie.finops.aplicacao.portas.DepartamentoRepository;
import br.mackenzie.finops.aplicacao.portas.RecursoCatalogoRepository;
import br.mackenzie.finops.aplicacao.portas.RequisicaoRepository;
import br.mackenzie.finops.dominio.entidades.Departamento;
import br.mackenzie.finops.dominio.entidades.ItemRequisicao;
import br.mackenzie.finops.dominio.entidades.RecursoCatalogo;
import br.mackenzie.finops.dominio.entidades.Requisicao;
import br.mackenzie.finops.dominio.entidades.ResultadoValidacao;

import java.util.UUID;

/**
 * Validação integrada aos repositórios em memória.
 * Base: SPEC-003 e SPEC-005, recorte A2 aprovado na Issue #51.
 *
 * Exige entidades compartilhadas no mesmo contexto.
 * Não oferece transação MySQL ou rollback.
 */
public class ValidarRequisicaoArmazenadaUseCase {

    private final DepartamentoRepository departamentos;
    private final RecursoCatalogoRepository recursos;
    private final RequisicaoRepository requisicoes;
    private final ValidarOrcamentoUseCase validarOrcamento =
            new ValidarOrcamentoUseCase();

    public ValidarRequisicaoArmazenadaUseCase(
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

    public ResultadoValidacao executar(UUID requisicaoId) {
        if (requisicaoId == null) {
            throw new IllegalArgumentException(
                    "O identificador da requisição é obrigatório."
            );
        }

        Requisicao requisicao = requisicoes.buscarPorId(requisicaoId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "A requisição solicitada não está cadastrada."
                ));

        Departamento departamento = requisicao.getDepartamento();

        if (departamento == null) {
            throw new IllegalArgumentException(
                    "O departamento da requisição é obrigatório."
            );
        }

        synchronized (departamento) {
            conferirRequisicao(requisicaoId, requisicao);
            conferirDepartamento(departamento);

            for (ItemRequisicao item : requisicao.getItens()) {
                conferirRecurso(item);
            }

            // Todas as consultas e conferências precedem a mutação.
            // O domínio reutiliza o mesmo bloqueio do departamento.
            return validarOrcamento.executar(requisicao);
        }
    }

    private void conferirRequisicao(
            UUID requisicaoId,
            Requisicao requisicao
    ) {
        Requisicao cadastrada = requisicoes.buscarPorId(requisicaoId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "A requisição não está cadastrada neste contexto."
                ));

        if (cadastrada != requisicao
                || !requisicaoId.equals(requisicao.getId())) {
            throw new IllegalArgumentException(
                    "A validação deve utilizar a mesma instância da requisição cadastrada."
            );
        }
    }

    private void conferirDepartamento(Departamento departamento) {
        UUID departamentoId = departamento.getId();

        if (departamentoId == null) {
            throw new IllegalArgumentException(
                    "O identificador do departamento é obrigatório."
            );
        }

        Departamento cadastrado = departamentos.buscarPorId(departamentoId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "O departamento da requisição não está cadastrado."
                ));

        if (cadastrado != departamento) {
            throw new IllegalArgumentException(
                    "A requisição deve utilizar a mesma instância do departamento cadastrado."
            );
        }
    }

    private void conferirRecurso(ItemRequisicao item) {
        if (item == null || item.getRecurso() == null) {
            throw new IllegalArgumentException(
                    "O item e seu recurso são obrigatórios."
            );
        }

        RecursoCatalogo recurso = item.getRecurso();
        UUID recursoId = recurso.getId();

        if (recursoId == null) {
            throw new IllegalArgumentException(
                    "O identificador do recurso é obrigatório."
            );
        }

        RecursoCatalogo cadastrado = recursos.buscarPorId(recursoId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "O recurso do item não está cadastrado."
                ));

        if (cadastrado != recurso) {
            throw new IllegalArgumentException(
                    "O item deve utilizar a mesma instância do recurso cadastrado."
            );
        }
    }
}