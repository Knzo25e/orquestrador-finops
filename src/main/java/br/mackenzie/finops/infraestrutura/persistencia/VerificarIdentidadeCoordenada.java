package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.aplicacao.excecoes.AcessoNegadoException;
import br.mackenzie.finops.aplicacao.seguranca.ContextoIdentidade;
import br.mackenzie.finops.aplicacao.seguranca.PerfilUsuario;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public final class VerificarIdentidadeCoordenada {

    private static int verificacoes;

    public static void main(String[] args) throws Exception {
        UUID usuario = UUID.randomUUID();

        ContextoIdentidade anterior = identidade(
                usuario, UUID.randomUUID()
        );
        ContextoIdentidade nova = identidade(
                usuario, UUID.randomUUID()
        );

        IdentidadeCoordenadaTeste fonte =
                new IdentidadeCoordenadaTeste(usuario, anterior);

        conferir(
                fonte.obterIdentidadeAtual().orElseThrow().equals(anterior),
                "Fonte apresenta identidade inicial"
        );

        verificarAlteracaoConcorrente(fonte, anterior, nova);
        verificarFalhaLiberaBloqueio(fonte, anterior);
        verificarRejeicoes(fonte, usuario, anterior);

        System.out.println(
                "Todas as " + verificacoes + " verificacoes passaram."
        );
    }

    private static void verificarAlteracaoConcorrente(
            IdentidadeCoordenadaTeste fonte,
            ContextoIdentidade anterior,
            ContextoIdentidade nova
    ) throws Exception {
        CountDownLatch operacaoIniciada = new CountDownLatch(1);
        CountDownLatch permitirConclusao = new CountDownLatch(1);
        CountDownLatch alteracaoIniciada = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var operacao = executor.submit(() ->
                    fonte.executar(contexto -> {
                        operacaoIniciada.countDown();
                        aguardar(permitirConclusao);

                        return contexto;
                    })
            );

            try {
                aguardar(operacaoIniciada);

                var alteracao = executor.submit(() -> {
                    alteracaoIniciada.countDown();
                    fonte.atualizar(nova);
                });

                aguardar(alteracaoIniciada);

                boolean aguardou = false;

                try {
                    alteracao.get(300, TimeUnit.MILLISECONDS);
                } catch (TimeoutException esperado) {
                    aguardou = true;
                }

                conferir(
                        aguardou,
                        "Alteracao nao conclui enquanto operacao permanece aberta"
                );

                permitirConclusao.countDown();

                conferir(
                        operacao.get(5, TimeUnit.SECONDS).equals(anterior),
                        "Operacao em andamento utiliza identidade anterior"
                );

                alteracao.get(5, TimeUnit.SECONDS);

                conferir(
                        fonte.obterIdentidadeAtual().orElseThrow().equals(nova),
                        "Alteracao conclui depois da operacao"
                );

                conferir(
                        fonte.executar(contexto -> contexto.departamentoId())
                                .equals(nova.departamentoId()),
                        "Proxima operacao utiliza o novo departamento"
                );
            } finally {
                permitirConclusao.countDown();
            }
        }
    }

    private static void verificarFalhaLiberaBloqueio(
            IdentidadeCoordenadaTeste fonte,
            ContextoIdentidade identidade
    ) throws Exception {
        IllegalStateException esperada =
                new IllegalStateException("Falha controlada.");

        AtomicInteger execucoes = new AtomicInteger();

        try {
            fonte.executar(contexto -> {
                execucoes.incrementAndGet();
                throw esperada;
            });

            throw new AssertionError("A operacao deveria falhar.");
        } catch (IllegalStateException recebida) {
            conferir(
                    recebida == esperada && execucoes.get() == 1,
                    "Falha original e propagada sem repetir operacao"
            );
        }

        try (var executor = Executors.newSingleThreadExecutor()) {
            var alteracao = executor.submit(
                    () -> fonte.atualizar(identidade)
            );

            alteracao.get(5, TimeUnit.SECONDS);
        }

        conferir(
                fonte.obterIdentidadeAtual().orElseThrow().equals(identidade),
                "Falha libera bloqueio para alteracao por outra thread"
        );
    }

    private static void verificarRejeicoes(
            IdentidadeCoordenadaTeste fonte,
            UUID usuario,
            ContextoIdentidade identidade
    ) {
        rejeitar(
                IllegalStateException.class,
                () -> fonte.executar(contexto -> {
                    fonte.atualizar(null);
                    return null;
                }),
                "Alteracao dentro da propria operacao e rejeitada"
        );

        conferir(
                fonte.obterIdentidadeAtual().orElseThrow().equals(identidade),
                "Alteracao rejeitada preserva identidade"
        );

        rejeitar(
                IllegalArgumentException.class,
                () -> fonte.atualizar(
                        identidade(UUID.randomUUID(), UUID.randomUUID())
                ),
                "Fonte rejeita identidade de outro usuario"
        );

        fonte.atualizar(null);
        AtomicInteger chamadas = new AtomicInteger();

        rejeitar(
                AcessoNegadoException.class,
                () -> fonte.executar(contexto -> {
                    chamadas.incrementAndGet();
                    return contexto;
                }),
                "Identidade ausente impede operacao coordenada"
        );

        conferir(
                chamadas.get() == 0
                        && fonte.obterIdentidadeAtual().isEmpty(),
                "Ausencia nao executa a funcao recebida"
        );

        rejeitar(
                IllegalArgumentException.class,
                () -> fonte.executar(null),
                "Funcao nula e rejeitada"
        );

        fonte.atualizar(identidade);

        conferir(
                fonte.executar(ContextoIdentidade::usuarioId).equals(usuario),
                "Fonte volta a executar apos restaurar identidade"
        );
    }

    private static ContextoIdentidade identidade(
            UUID usuario,
            UUID departamento
    ) {
        return new ContextoIdentidade(
                usuario,
                PerfilUsuario.DESENVOLVEDOR,
                true,
                departamento
        );
    }

    private static void aguardar(CountDownLatch sinal) {
        try {
            if (!sinal.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Tempo limite aguardando sincronizacao."
                );
            }
        } catch (InterruptedException falha) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Verificacao interrompida.",
                    falha
            );
        }
    }

    private static void rejeitar(
            Class<? extends RuntimeException> tipo,
            Runnable operacao,
            String descricao
    ) {
        try {
            operacao.run();
        } catch (RuntimeException falha) {
            if (!tipo.isInstance(falha)) {
                throw new IllegalStateException(
                        "FALHOU: excecao inesperada em " + descricao,
                        falha
                );
            }

            conferir(true, descricao);
            return;
        }

        throw new IllegalStateException("FALHOU: " + descricao);
    }

    private static void conferir(boolean condicao, String descricao) {
        if (!condicao) {
            throw new IllegalStateException("FALHOU: " + descricao);
        }

        verificacoes++;
        System.out.println("PASSOU: " + descricao);
    }
}