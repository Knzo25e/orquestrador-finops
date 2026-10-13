package br.mackenzie.finops.aplicacao;

import br.mackenzie.finops.dominio.entidades.Departamento;
import br.mackenzie.finops.dominio.entidades.ItemRequisicao;
import br.mackenzie.finops.dominio.entidades.RecursoCatalogo;
import br.mackenzie.finops.dominio.entidades.Requisicao;
import br.mackenzie.finops.dominio.entidades.ResultadoValidacao;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class MedirDesempenhoOrcamentario {
    private static final int AQUECIMENTO = 100;
    private static final int AMOSTRAS = 1000;
    private static final long LIMITE_NS = 2_000_000_000L;

    private enum Cenario {
        LIBERACAO,
        RESERVA,
        INSUFICIENCIA
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Issue #41 — Medicao de desempenho");
        System.out.println("Inicio UTC: " + Instant.now());
        System.out.println("Java: " + System.getProperty("java.version"));
        System.out.println("Fornecedor: " + System.getProperty("java.vendor"));
        System.out.println(
                "SO: " + System.getProperty("os.name")
                        + " " + System.getProperty("os.version")
        );
        System.out.println(
                "Processadores logicos disponiveis: "
                        + Runtime.getRuntime().availableProcessors()
        );
        System.out.println(
                "itens;cenario;tarefas;amostras;mediana_ms;p95_ms;"
                        + "maximo_ms;duracoes_ge_2s;funcional"
        );

        int combinacoes = 0;
        long totalForaDoLimite = 0;

        for (int itens : new int[]{1, 10, 100}) {
            for (Cenario cenario : Cenario.values()) {
                for (int tarefas : new int[]{1, 8}) {
                    executarLote(itens, cenario, tarefas, AQUECIMENTO);

                    long[] duracoes =
                            executarLote(itens, cenario, tarefas, AMOSTRAS);

                    totalForaDoLimite += apresentar(
                            itens, cenario, tarefas, duracoes
                    );
                    combinacoes++;
                }
            }
        }

        System.out.println("Combinacoes concluidas: " + combinacoes);
        System.out.println("Validacoes medidas: " + combinacoes * AMOSTRAS);
        System.out.println(
                "Validacoes com duracao >= 2 segundos: " + totalForaDoLimite
        );

        if (totalForaDoLimite > 0) {
            throw new IllegalStateException(
                    "REPROVADO: houve validacao fora do limite de tempo."
            );
        }

        System.out.println(
                "APROVADO nesta execucao, para o ambiente e as cargas medidas."
        );
    }

    private static long[] executarLote(
            int quantidadeItens,
            Cenario cenario,
            int tarefas,
            int quantidadePedidos
    ) throws Exception {
        BigDecimal teto = new BigDecimal("1000000.00");
        BigDecimal saldoInicial;

        switch (cenario) {
            case LIBERACAO:
                saldoInicial = teto;
                break;
            case RESERVA:
                saldoInicial = new BigDecimal("100000.00");
                break;
            case INSUFICIENCIA:
                saldoInicial = new BigDecimal("0.00");
                break;
            default:
                throw new IllegalStateException("Cenario desconhecido.");
        }

        Departamento departamento = new Departamento(
                UUID.randomUUID(), "Desempenho", teto
        );
        departamento.descontarSaldo(teto.subtract(saldoInicial));

        RecursoCatalogo recurso = new RecursoCatalogo(
                UUID.randomUUID(), "Recurso teste", new BigDecimal("1.00")
        );

        // Toda a criacao acontece antes dos intervalos medidos.
        List<Requisicao> pedidos = new ArrayList<>();

        for (int i = 0; i < quantidadePedidos; i++) {
            Requisicao pedido = new Requisicao(departamento);

            for (int j = 0; j < quantidadeItens; j++) {
                pedido.adicionarItem(new ItemRequisicao(recurso, 1));
            }

            pedidos.add(pedido);
        }

        long[] duracoes = new long[quantidadePedidos];
        ResultadoValidacao[] resultados =
                new ResultadoValidacao[quantidadePedidos];

        ValidarOrcamentoUseCase casoDeUso = new ValidarOrcamentoUseCase();

        if (tarefas == 1) {
            for (int i = 0; i < quantidadePedidos; i++) {
                medirUma(casoDeUso, pedidos.get(i), i, duracoes, resultados);
            }
        } else {
            executarConcorrente(
                    tarefas, pedidos, casoDeUso, duracoes, resultados
            );
        }

        // Conferencia fora dos intervalos e apos terminar todas as tarefas.
        conferirLote(
                cenario, quantidadeItens, saldoInicial,
                departamento, pedidos, resultados
        );

        return duracoes;
    }

    private static void executarConcorrente(
            int tarefas,
            List<Requisicao> pedidos,
            ValidarOrcamentoUseCase casoDeUso,
            long[] duracoes,
            ResultadoValidacao[] resultados
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tarefas);
        CountDownLatch prontas = new CountDownLatch(tarefas);
        CountDownLatch inicio = new CountDownLatch(1);
        List<Future<?>> futuros = new ArrayList<>();

        try {
            for (int t = 0; t < tarefas; t++) {
                final int indiceInicial = t;

                futuros.add(executor.submit(() -> {
                    prontas.countDown();

                    if (!inicio.await(30, TimeUnit.SECONDS)) {
                        throw new IllegalStateException(
                                "Tempo esgotado aguardando sinal de inicio."
                        );
                    }

                    for (int i = indiceInicial; i < pedidos.size(); i += tarefas) {
                        medirUma(
                                casoDeUso, pedidos.get(i),
                                i, duracoes, resultados
                        );
                    }

                    return null;
                }));
            }

            if (!prontas.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "As tarefas nao ficaram prontas no prazo."
                );
            }

            inicio.countDown();

            for (Future<?> futuro : futuros) {
                futuro.get(60, TimeUnit.SECONDS);
            }
        } finally {
            inicio.countDown();
            executor.shutdownNow();

            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "As tarefas nao encerraram no prazo."
                );
            }
        }
    }

    private static void medirUma(
            ValidarOrcamentoUseCase casoDeUso,
            Requisicao pedido,
            int indice,
            long[] duracoes,
            ResultadoValidacao[] resultados
    ) {
        long inicio = System.nanoTime();
        ResultadoValidacao resultado = casoDeUso.executar(pedido);
        long fim = System.nanoTime();

        duracoes[indice] = fim - inicio;
        resultados[indice] = resultado;
    }

    private static void conferirLote(
            Cenario cenario,
            int quantidadeItens,
            BigDecimal saldoInicial,
            Departamento departamento,
            List<Requisicao> pedidos,
            ResultadoValidacao[] resultados
    ) {
        ResultadoValidacao esperado;
        String estadoEsperado;
        BigDecimal saldoEsperado = saldoInicial;

        switch (cenario) {
            case LIBERACAO:
                esperado = ResultadoValidacao.LIBERADA;
                estadoEsperado = "Provisionamento Liberado";
                saldoEsperado = saldoInicial.subtract(
                        BigDecimal.valueOf(quantidadeItens)
                                .multiply(BigDecimal.valueOf(pedidos.size()))
                );
                break;
            case RESERVA:
                esperado = ResultadoValidacao.REVISAO_POR_RESERVA;
                estadoEsperado = "Revisão Pendente";
                break;
            case INSUFICIENCIA:
                esperado = ResultadoValidacao.REVISAO_POR_SALDO_INSUFICIENTE;
                estadoEsperado = "Revisão Pendente";
                break;
            default:
                throw new IllegalStateException("Cenario desconhecido.");
        }

        BigDecimal custoEsperado =
                BigDecimal.valueOf(quantidadeItens).setScale(2);

        for (int i = 0; i < pedidos.size(); i++) {
            Requisicao pedido = pedidos.get(i);
            ResultadoValidacao motivoEsperado =
                    cenario == Cenario.LIBERACAO ? null : esperado;

            if (resultados[i] != esperado
                    || !estadoEsperado.equals(pedido.getStatus())
                    || pedido.getMotivoRevisao() != motivoEsperado
                    || !custoEsperado.equals(pedido.getCustoTotalProjetado())) {
                throw new IllegalStateException(
                        "FALHA FUNCIONAL no pedido " + i + " de " + cenario
                );
            }
        }

        if (saldoEsperado.compareTo(departamento.getSaldoDisponivel()) != 0
                || departamento.getOrcamentoMensal().compareTo(
                        new BigDecimal("1000000.00")
                ) != 0) {
            throw new IllegalStateException(
                    "FALHA FUNCIONAL: saldo ou teto incorreto em " + cenario
            );
        }
    }

    private static long apresentar(
            int itens,
            Cenario cenario,
            int tarefas,
            long[] duracoes
    ) {
        long[] ordenadas = duracoes.clone();
        Arrays.sort(ordenadas);

        int n = ordenadas.length;
        double medianaNs = n % 2 == 0
                ? ordenadas[n / 2 - 1] / 2.0 + ordenadas[n / 2] / 2.0
                : ordenadas[n / 2];

        int indiceP95 = (int) Math.ceil(0.95 * n) - 1;
        long foraDoLimite = Arrays.stream(ordenadas)
                .filter(duracao -> duracao >= LIMITE_NS)
                .count();

        System.out.printf(
                Locale.ROOT,
                "%d;%s;%d;%d;%.6f;%.6f;%.6f;%d;OK%n",
                itens,
                cenario,
                tarefas,
                n,
                medianaNs / 1_000_000.0,
                ordenadas[indiceP95] / 1_000_000.0,
                ordenadas[n - 1] / 1_000_000.0,
                foraDoLimite
        );

        return foraDoLimite;
    }
}