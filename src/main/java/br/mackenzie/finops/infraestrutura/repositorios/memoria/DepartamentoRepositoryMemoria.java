package br.mackenzie.finops.infraestrutura.repositorios.memoria;

import br.mackenzie.finops.aplicacao.portas.DepartamentoRepository;
import br.mackenzie.finops.dominio.entidades.Departamento;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Armazenamento em memória conforme a SPEC-005, recorte A1.
 * Os dados pertencem a esta instância e não sobrevivem ao fim do processo.
 */
public class DepartamentoRepositoryMemoria implements DepartamentoRepository {

    private final Map<UUID, Departamento> departamentos = new HashMap<>();

    @Override
    public synchronized void adicionar(Departamento departamento) {
        if (departamento == null) {
            throw new IllegalArgumentException(
                    "O departamento é obrigatório."
            );
        }

        UUID id = departamento.getId();
        validarId(id);

        if (departamentos.containsKey(id)) {
            throw new IllegalArgumentException(
                    "Já existe um departamento cadastrado com esse identificador."
            );
        }

        departamentos.put(id, departamento);
    }

    @Override
    public synchronized Optional<Departamento> buscarPorId(UUID id) {
        validarId(id);
        return Optional.ofNullable(departamentos.get(id));
    }

    private static void validarId(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException(
                    "O identificador do departamento é obrigatório."
            );
        }
    }
}