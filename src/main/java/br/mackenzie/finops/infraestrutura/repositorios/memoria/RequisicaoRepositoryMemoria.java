package br.mackenzie.finops.infraestrutura.repositorios.memoria;

import br.mackenzie.finops.aplicacao.portas.DepartamentoRepository;
import br.mackenzie.finops.aplicacao.portas.RecursoCatalogoRepository;
import br.mackenzie.finops.aplicacao.portas.RequisicaoRepository;
import br.mackenzie.finops.dominio.entidades.Departamento;
import br.mackenzie.finops.dominio.entidades.ItemRequisicao;
import br.mackenzie.finops.dominio.entidades.RecursoCatalogo;
import br.mackenzie.finops.dominio.entidades.Requisicao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Cadastro e consulta em memória conforme a SPEC-005, recorte A1.
 * Mantém referências compartilhadas e não oferece rollback.
 */
public class RequisicaoRepositoryMemoria implements RequisicaoRepository {

    private final DepartamentoRepository departamentos;
    private final RecursoCatalogoRepository recursos;
    private final Map<UUID, Requisicao> requisicoes = new HashMap<>();
    private final Object bloqueioRegistros = new Object();

    public RequisicaoRepositoryMemoria(
            DepartamentoRepository departamentos,
            RecursoCatalogoRepository recursos
    ) {
        if (departamentos == null || recursos == null) {
            throw new IllegalArgumentException(
                    "Os repositórios de departamentos e recursos são obrigatórios."
            );
        }

        this.departamentos = departamentos;
        this.recursos = recursos;
    }

    @Override
    public void adicionar(Requisicao requisicao) {
        if (requisicao == null) {
            throw new IllegalArgumentException(
                    "A requisição é obrigatória."
            );
        }

        UUID id = requisicao.getId();
        validarId(id, "O identificador da requisição");

        Departamento departamento = requisicao.getDepartamento();

        if (departamento == null) {
            throw new IllegalArgumentException(
                    "O departamento da requisição é obrigatório."
            );
        }

        // Primeiro coordena pelo departamento; só depois acessa o mapa.
        synchronized (departamento) {
            validarDepartamento(departamento);

            if (!"INICIAL".equals(requisicao.getStatus())) {
                throw new IllegalArgumentException(
                        "Somente requisições em INICIAL podem ser cadastradas."
                );
            }

            List<ItemRequisicao> itens = requisicao.getItens();

            if (itens.isEmpty()) {
                throw new IllegalArgumentException(
                        "A requisição deve conter pelo menos um item."
                );
            }

            for (ItemRequisicao item : itens) {
                validarItem(item);
            }

            synchronized (bloqueioRegistros) {
                if (requisicoes.containsKey(id)) {
                    throw new IllegalArgumentException(
                            "Já existe uma requisição cadastrada com esse identificador."
                    );
                }

                requisicoes.put(id, requisicao);
            }
        }
    }

    @Override
    public Optional<Requisicao> buscarPorId(UUID id) {
        validarId(id, "O identificador da requisição");

        synchronized (bloqueioRegistros) {
            return Optional.ofNullable(requisicoes.get(id));
        }
    }

    @Override
    public List<Requisicao> listarPorDepartamento(UUID departamentoId) {
        validarId(departamentoId, "O identificador do departamento");

        List<Requisicao> registros;

        synchronized (bloqueioRegistros) {
            registros = List.copyOf(requisicoes.values());
        }

        // Filtra após liberar o bloqueio do mapa.
        return registros.stream()
                .filter(requisicao -> departamentoId.equals(
                        requisicao.getDepartamento().getId()
                ))
                .toList();
    }

    private void validarDepartamento(Departamento departamento) {
        UUID id = departamento.getId();
        validarId(id, "O identificador do departamento");

        Departamento cadastrado = departamentos.buscarPorId(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "O departamento da requisição não está cadastrado."
                ));

        if (cadastrado != departamento) {
            throw new IllegalArgumentException(
                    "A requisição deve utilizar a mesma instância do departamento cadastrado."
            );
        }
    }

    private void validarItem(ItemRequisicao item) {
        if (item == null || item.getRecurso() == null) {
            throw new IllegalArgumentException(
                    "O item e seu recurso são obrigatórios."
            );
        }

        if (item.getQuantidade() == null || item.getQuantidade() <= 0) {
            throw new IllegalArgumentException(
                    "A quantidade do item deve ser maior que zero."
            );
        }

        RecursoCatalogo recurso = item.getRecurso();
        UUID id = recurso.getId();
        validarId(id, "O identificador do recurso");

        RecursoCatalogo cadastrado = recursos.buscarPorId(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "O recurso do item não está cadastrado."
                ));

        if (cadastrado != recurso) {
            throw new IllegalArgumentException(
                    "O item deve utilizar a mesma instância do recurso cadastrado."
            );
        }
    }

    private static void validarId(UUID id, String campo) {
        if (id == null) {
            throw new IllegalArgumentException(
                    campo + " é obrigatório."
            );
        }
    }
}