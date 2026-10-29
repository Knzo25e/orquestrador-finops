package br.mackenzie.finops.infraestrutura.persistencia;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Locale;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Auxiliar exclusivo dos verificadores de concorrência.
 *
 * Sincroniza duas tentativas antes da inserção do cabeçalho.
 * Não deve ser utilizado na configuração normal da aplicação.
 */
final class BarreiraCriacaoAutorizadaTesteJdbc {

    private final CyclicBarrier barreira = new CyclicBarrier(2);
    private final AtomicInteger conexoes = new AtomicInteger();
    private final AtomicInteger insercoes = new AtomicInteger();

    Connection envolver(Connection real) {
        if (real == null) {
            throw new IllegalArgumentException(
                    "A conexão de teste é obrigatória."
            );
        }

        Connection controlada = (Connection) Proxy.newProxyInstance(
                BarreiraCriacaoAutorizadaTesteJdbc.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, metodo, argumentos) -> {
                    Object retorno = invocar(metodo, real, argumentos);

                    if ("prepareStatement".equals(metodo.getName())
                            && argumentos != null
                            && argumentos.length > 0
                            && argumentos[0] instanceof String sql
                            && insercaoDeRequisicao(sql)) {
                        return envolverComando(
                                (PreparedStatement) retorno
                        );
                    }

                    return retorno;
                }
        );

        conexoes.incrementAndGet();
        return controlada;
    }

    int conexoesAbertas() {
        return conexoes.get();
    }

    int tentativasInsercao() {
        return insercoes.get();
    }

    private PreparedStatement envolverComando(PreparedStatement real) {
        return (PreparedStatement) Proxy.newProxyInstance(
                BarreiraCriacaoAutorizadaTesteJdbc.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, metodo, argumentos) -> {
                    if ("executeUpdate".equals(metodo.getName())) {
                        int tentativa = insercoes.incrementAndGet();

                        if (tentativa > 2) {
                            throw new SQLException(
                                    "O teste tentou inserir mais de duas vezes.",
                                    "HY000"
                            );
                        }

                        aguardarOutraTentativa();
                    }

                    return invocar(metodo, real, argumentos);
                }
        );
    }

    private void aguardarOutraTentativa() throws SQLException {
        try {
            barreira.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException falha) {
            Thread.currentThread().interrupt();

            throw new SQLException(
                    "Sincronização do teste interrompida.",
                    "HY000",
                    falha
            );
        } catch (BrokenBarrierException | TimeoutException falha) {
            throw new SQLException(
                    "As duas tentativas não alcançaram a barreira.",
                    "HY000",
                    falha
            );
        }
    }

    private static boolean insercaoDeRequisicao(String sql) {
        String normalizado = sql.strip()
                .replaceAll("\\s+", " ")
                .toUpperCase(Locale.ROOT);

        return normalizado.matches(
                "^INSERT INTO REQUISICAO\\s*\\(.*"
        );
    }

    private static Object invocar(
            Method metodo,
            Object destino,
            Object[] argumentos
    ) throws Throwable {
        try {
            return metodo.invoke(destino, argumentos);
        } catch (InvocationTargetException falha) {
            throw falha.getCause();
        }
    }
}