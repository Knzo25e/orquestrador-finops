package br.mackenzie.finops.aplicacao;

import br.mackenzie.finops.dominio.entidades.Departamento;
import br.mackenzie.finops.dominio.entidades.ItemRequisicao;
import br.mackenzie.finops.dominio.entidades.RecursoCatalogo;
import br.mackenzie.finops.dominio.entidades.Requisicao;
import br.mackenzie.finops.dominio.entidades.ResultadoValidacao;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class VerificarConcorrenciaOrcamentaria {

    public static void main(String[] args) throws Exception {
        System.out.println("--- Concorrencia orcamentaria / Issue #39 ---");

        verificarPedidosDistintos(
                "C6: apenas um pedido e liberado por limite de saldo",
                "3000.00",
                ResultadoValidacao.REVISAO_POR_SALDO_INSUFICIENTE,
                "2000.00"
        );

        verificarMesmoPedido();

        verificarPedidosDistintos(
                "C14: apenas um pedido e liberado por limite de reserva",
                "2000.00",
                ResultadoValidacao.REVISAO_POR_RESERVA,
                "3000.00"
        );

        System.out.println("Todos os 3 cenarios concorrentes passaram.");
    }

    private static void verificarPedidosDistintos(
            String descricao,
            String custo,
            ResultadoValidacao revisaoEsperada,
            String saldoEsperado
    ) throws Exception {
        Departamento departamento = departamento();
        Requisicao primeira = requisicao(departamento, custo);
        Requisicao segunda = requisicao(departamento, custo);

        List<ResultadoValidacao> resultados =
                executarSimultaneamente(primeira, segunda);

        boolean primeiraLiberada =
                resultados.get(0) == ResultadoValidacao.LIBERADA;
        boolean segundaLiberada =
                resultados.get(1) == ResultadoValidacao.LIBERADA;

        verificar(
                descricao + ": resultados",
                (primeiraLiberada && resultados.get(1) == revisaoEsperada)
                        || (segundaLiberada
                                && resultados.get(0) == revisaoEsperada)
        );

        Requisicao liberada = primeiraLiberada ? primeira : segunda;
        Requisicao emRevisao = primeiraLiberada ? segunda : primeira;

        verificar(
                descricao + ": estados, motivo e saldo",
                "Provisionamento Liberado".equals(liberada.getStatus())
                        && liberada.getMotivoRevisao() == null
                        && "Revisão Pendente".equals(emRevisao.getStatus())
                        && emRevisao.getMotivoRevisao() == revisaoEsperada
                        && new BigDecimal(saldoEsperado)
                                .equals(departamento.getSaldoDisponivel())
                        && new BigDecimal("10000.00")
                                .equals(departamento.getOrcamentoMensal())
        );
    }

    private static void verificarMesmoPedido() throws Exception {
        Departamento departamento = departamento();
        Requisicao pedido = requisicao(departamento, "3000.00");

        List<ResultadoValidacao> resultados =
                executarSimultaneamente(pedido, pedido);

        verificar(
                "C7: uma tentativa libera e a outra reconhece a liberacao",
                (resultados.get(0) == ResultadoValidacao.LIBERADA
                        && resultados.get(1) == ResultadoValidacao.JA_LIBERADA)
                        || (resultados.get(1) == ResultadoValidacao.LIBERADA
                        && resultados.get(0) == ResultadoValidacao.JA_LIBERADA)
        );

        verificar(
                "C7: valor comprometido uma unica vez",
                "Provisionamento Liberado".equals(pedido.getStatus())
                        && pedido.getMotivoRevisao() == null
                        && new BigDecimal("2000.00")
                                .equals(departamento.getSaldoDisponivel())
                        && new BigDecimal("10000.00")
                                .equals(departamento.getOrcamentoMensal())
        );
    }

    private static List<ResultadoValidacao> executarSimultaneamente(
            Requisicao primeira,
            Requisicao segunda
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch prontas = new CountDownLatch(2);
        CountDownLatch inicio = new CountDownLatch(1);

        try {
            Future<ResultadoValidacao> resultadoA = executor.submit(
                    () -> executarAposSinal(primeira, prontas, inicio)
            );

            Future<ResultadoValidacao> resultadoB = executor.submit(
                    () -> executarAposSinal(segunda, prontas, inicio)
            );

            if (!prontas.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "As duas tarefas nao ficaram prontas no prazo."
                );
            }

            inicio.countDown();

            return List.of(
                    resultadoA.get(5, TimeUnit.SECONDS),
                    resultadoB.get(5, TimeUnit.SECONDS)
            );
        } finally {
            inicio.countDown();
            executor.shutdownNow();

            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "As tarefas concorrentes nao encerraram no prazo."
                );
            }
        }
    }

    private static ResultadoValidacao executarAposSinal(
            Requisicao requisicao,
            CountDownLatch prontas,
            CountDownLatch inicio
    ) throws InterruptedException {
        prontas.countDown();

        if (!inicio.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException(
                    "O sinal de inicio nao foi recebido no prazo."
            );
        }

        return new ValidarOrcamentoUseCase().executar(requisicao);
    }

    private static Departamento departamento() {
        Departamento departamento = new Departamento(
                UUID.randomUUID(),
                "Concorrencia",
                new BigDecimal("10000.00")
        );

        // Prepara saldo de 5000.00 antes das tentativas concorrentes.
        departamento.descontarSaldo(new BigDecimal("5000.00"));
        return departamento;
    }

    private static Requisicao requisicao(
            Departamento departamento,
            String custo
    ) {
        RecursoCatalogo recurso = new RecursoCatalogo(
                UUID.randomUUID(),
                "Recurso teste",
                new BigDecimal(custo)
        );

        return new CriarRequisicaoUseCase().executar(
                departamento,
                List.of(new ItemRequisicao(recurso, 1))
        );
    }

    private static void verificar(String descricao, boolean condicao) {
        if (!condicao) {
            throw new IllegalStateException("FALHA: " + descricao);
        }

        System.out.println("PASSOU: " + descricao);
    }
}