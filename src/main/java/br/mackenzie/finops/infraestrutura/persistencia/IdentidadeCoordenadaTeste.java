package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.aplicacao.excecoes.AcessoNegadoException;
import br.mackenzie.finops.aplicacao.portas.ExecucaoComIdentidadeCoordenada;
import br.mackenzie.finops.aplicacao.portas.ProvedorIdentidade;
import br.mackenzie.finops.aplicacao.seguranca.ContextoIdentidade;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

/**
 * Fonte de identidade exclusiva dos verificadores.
 *
 * Coordena operações e alterações de uma identidade no mesmo processo.
 * Não autentica usuários e não coordena outros processos ou fontes externas.
 *
 * Cada instância representa um único usuário.
 */
final class IdentidadeCoordenadaTeste
        implements ExecucaoComIdentidadeCoordenada, ProvedorIdentidade {

    private final UUID usuarioId;

    private final ReentrantReadWriteLock bloqueio =
            new ReentrantReadWriteLock(true);

    private ContextoIdentidade atual;

    IdentidadeCoordenadaTeste(
            UUID usuarioId,
            ContextoIdentidade identidadeInicial
    ) {
        if (usuarioId == null) {
            throw new IllegalArgumentException(
                    "O identificador do usuário é obrigatório."
            );
        }

        this.usuarioId = usuarioId;
        conferirUsuario(identidadeInicial);
        this.atual = identidadeInicial;
    }

    /**
     * Retorna apenas uma fotografia.
     * A proteção termina quando este método retorna.
     */
    @Override
    public Optional<ContextoIdentidade> obterIdentidadeAtual() {
        bloqueio.readLock().lock();

        try {
            return Optional.ofNullable(atual);
        } finally {
            bloqueio.readLock().unlock();
        }
    }

    /**
     * Mantém a identidade estável durante toda a função recebida.
     * Exceções da função são propagadas sem repetição automática.
     */
    @Override
    public <T> T executar(
            Function<ContextoIdentidade, T> operacao
    ) {
        if (operacao == null) {
            throw new IllegalArgumentException(
                    "A operação é obrigatória."
            );
        }

        bloqueio.readLock().lock();

        try {
            if (atual == null) {
                throw new AcessoNegadoException();
            }

            return operacao.apply(atual);
        } finally {
            bloqueio.readLock().unlock();
        }
    }

    /**
     * Substitui os atributos da identidade depois que as operações
     * coordenadas em andamento terminarem.
     *
     * Null representa identidade ausente.
     */
    void atualizar(ContextoIdentidade novaIdentidade) {
        conferirUsuario(novaIdentidade);

        // Impede tentar converter o bloqueio de leitura da própria
        // execução em bloqueio de escrita, o que causaria espera indefinida.
        if (bloqueio.getReadHoldCount() > 0) {
            throw new IllegalStateException(
                    "Não é permitido alterar a identidade "
                            + "dentro da própria operação coordenada."
            );
        }

        bloqueio.writeLock().lock();

        try {
            atual = novaIdentidade;
        } finally {
            bloqueio.writeLock().unlock();
        }
    }

    private void conferirUsuario(ContextoIdentidade identidade) {
        if (identidade != null
                && !usuarioId.equals(identidade.usuarioId())) {
            throw new IllegalArgumentException(
                    "A fonte de teste pertence a outro usuário."
            );
        }
    }
}