package br.mackenzie.finops.infraestrutura.repositorios.memoria;

import br.mackenzie.finops.aplicacao.portas.RecursoCatalogoRepository;
import br.mackenzie.finops.dominio.entidades.RecursoCatalogo;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Armazenamento em memória conforme a SPEC-005, recorte A1.
 * Os dados pertencem a esta instância e não sobrevivem ao fim do processo.
 */
public class RecursoCatalogoRepositoryMemoria
        implements RecursoCatalogoRepository {

    private final Map<UUID, RecursoCatalogo> recursos = new HashMap<>();

    @Override
    public synchronized void adicionar(RecursoCatalogo recurso) {
        if (recurso == null) {
            throw new IllegalArgumentException(
                    "O recurso do catálogo é obrigatório."
            );
        }

        UUID id = recurso.getId();
        validarId(id);

        if (recursos.containsKey(id)) {
            throw new IllegalArgumentException(
                    "Já existe um recurso cadastrado com esse identificador."
            );
        }

        recursos.put(id, recurso);
    }

    @Override
    public synchronized Optional<RecursoCatalogo> buscarPorId(UUID id) {
        validarId(id);
        return Optional.ofNullable(recursos.get(id));
    }

    private static void validarId(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException(
                    "O identificador do recurso é obrigatório."
            );
        }
    }
}