package br.mackenzie.finops.infraestrutura.persistencia;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Auxiliar exclusivo dos verificadores.
 *
 * Pausa o primeiro commit antes de encaminhá-lo à conexão real.
 * O teste deve liberar a pausa em um bloco finally.
 */
final class PausaCommitTesteJdbc {

    private final CountDownLatch commitAlcancado = new CountDownLatch(1);
    private final CountDownLatch permitirCommit = new CountDownLatch(1);
    private final AtomicBoolean primeiroCommit = new AtomicBoolean(true);
    private final AtomicBoolean commitConfirmado = new AtomicBoolean();

    Connection envolver(Connection real) {
        if (real == null) {
            throw new IllegalArgumentException(
                    "A conexão de teste é obrigatória."
            );
        }

        return (Connection) Proxy.newProxyInstance(
                PausaCommitTesteJdbc.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, metodo, argumentos) -> {
                    boolean commitPausado =
                            "commit".equals(metodo.getName())
                                    && primeiroCommit.compareAndSet(true, false);

                    if (commitPausado) {
                        commitAlcancado.countDown();
                        aguardarLiberacao();
                    }

                    Object retorno;

                    try {
                        retorno = metodo.invoke(real, argumentos);
                    } catch (InvocationTargetException falha) {
                        throw falha.getCause();
                    }

                    if (commitPausado) {
                        commitConfirmado.set(true);
                    }

                    return retorno;
                }
        );
    }

    void aguardarChegadaAoCommit() {
        try {
            if (!commitAlcancado.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "A criação não alcançou o commit no prazo do teste."
                );
            }
        } catch (InterruptedException falha) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Espera pelo commit interrompida.",
                    falha
            );
        }
    }

    void liberarCommit() {
        permitirCommit.countDown();
    }

    boolean commitFoiConfirmado() {
        return commitConfirmado.get();
    }

    private void aguardarLiberacao() throws SQLException {
        try {
            if (!permitirCommit.await(15, TimeUnit.SECONDS)) {
                throw new SQLException(
                        "O teste não liberou o commit no prazo.",
                        "HY000"
                );
            }
        } catch (InterruptedException falha) {
            Thread.currentThread().interrupt();

            throw new SQLException(
                    "Pausa anterior ao commit interrompida.",
                    "HY000",
                    falha
            );
        }
    }
}