package br.mackenzie.finops.aplicacao;

import br.mackenzie.finops.aplicacao.entradas.ItemSolicitado;
import br.mackenzie.finops.dominio.entidades.Departamento;
import br.mackenzie.finops.dominio.entidades.RecursoCatalogo;
import br.mackenzie.finops.dominio.entidades.Requisicao;
import br.mackenzie.finops.dominio.entidades.ResultadoValidacao;
import br.mackenzie.finops.infraestrutura.repositorios.memoria.DepartamentoRepositoryMemoria;
import br.mackenzie.finops.infraestrutura.repositorios.memoria.RecursoCatalogoRepositoryMemoria;
import br.mackenzie.finops.infraestrutura.repositorios.memoria.RequisicaoRepositoryMemoria;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class VerificarConcorrenciaIntegracao {

    private static int verificacoes;

    public static void main(String[] args) throws Exception {
        System.out.println("--- Concorrencia da integracao / Issue #53 ---");

        verificarMesmoPedido();
        verificarPedidosDistintos(
                30,
                ResultadoValidacao.REVISAO_POR_SALDO_INSUFICIENTE,
                "2000.00",
                "Disputa por saldo"
        );
        verificarPedidosDistintos(
                20,
                ResultadoValidacao.REVISAO_POR_RESERVA,
                "3000.00",
                "Disputa por reserva"
        );

        System.out.println(
                "Todas as " + verificacoes
                        + " verificacoes passaram em 3 cenarios concorrentes."
        );
    }

    private static void verificarMesmoPedido() throws Exception {
        Contexto c = new Contexto();
        Requisicao pedido = c.criar(20);

        List<ResultadoValidacao> resultados =
                executarConcorrente(c, pedido.getId(), pedido.getId());

        verificar("Mesmo pedido: uma liberacao e um reconhecimento",
                contar(resultados, ResultadoValidacao.LIBERADA) == 1
                        && contar(resultados, ResultadoValidacao.JA_LIBERADA) == 1);

        Requisicao consultado = c.requisicoes
                .buscarPorId(pedido.getId()).orElseThrow();

        verificar("Mesmo pedido: estado e comprometimento unico",
                consultado == pedido
                        && "Provisionamento Liberado".equals(consultado.getStatus())
                        && consultado.getMotivoRevisao() == null
                        && saldoIgual(c, "3000.00"));

        verificar("Mesmo pedido: repeticao posterior preserva saldo",
                c.validar.executar(pedido.getId())
                        == ResultadoValidacao.JA_LIBERADA
                        && saldoIgual(c, "3000.00"));
    }

    private static void verificarPedidosDistintos(
            int quantidade,
            ResultadoValidacao motivoEsperado,
            String saldoEsperado,
            String descricao
    ) throws Exception {
        Contexto c = new Contexto();
        Requisicao primeiro = c.criar(quantidade);
        Requisicao segundo = c.criar(quantidade);

        List<ResultadoValidacao> resultados =
                executarConcorrente(c, primeiro.getId(), segundo.getId());

        verificar(descricao + ": uma liberacao e uma revisao",
                contar(resultados, ResultadoValidacao.LIBERADA) == 1
                        && contar(resultados, motivoEsperado) == 1);

        Requisicao primeiroConsultado = c.requisicoes
                .buscarPorId(primeiro.getId()).orElseThrow();
        Requisicao segundoConsultado = c.requisicoes
                .buscarPorId(segundo.getId()).orElseThrow();

        boolean primeiroLiberado =
                resultados.get(0) == ResultadoValidacao.LIBERADA;

        Requisicao liberado = primeiroLiberado
                ? primeiroConsultado : segundoConsultado;
        Requisicao revisao = primeiroLiberado
                ? segundoConsultado : primeiroConsultado;

        verificar(descricao + ": estados, motivo e saldo",
                "Provisionamento Liberado".equals(liberado.getStatus())
                        && liberado.getMotivoRevisao() == null
                        && "Revisão Pendente".equals(revisao.getStatus())
                        && revisao.getMotivoRevisao() == motivoEsperado
                        && saldoIgual(c, saldoEsperado));

        verificar(descricao + ": novas tentativas preservam resultado",
                c.validar.executar(liberado.getId())
                        == ResultadoValidacao.JA_LIBERADA
                        && c.validar.executar(revisao.getId())
                        == ResultadoValidacao.ESTADO_NAO_PERMITIDO
                        && revisao.getMotivoRevisao() == motivoEsperado
                        && saldoIgual(c, saldoEsperado));
    }

    private static List<ResultadoValidacao> executarConcorrente(
            Contexto c,
            UUID primeiroId,
            UUID segundoId
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch prontas = new CountDownLatch(2);
        CountDownLatch inicio = new CountDownLatch(1);

        try {
            Future<ResultadoValidacao> primeira = executor.submit(() -> {
                aguardarInicio(prontas, inicio);
                return c.validar.executar(primeiroId);
            });

            Future<ResultadoValidacao> segunda = executor.submit(() -> {
                aguardarInicio(prontas, inicio);
                return c.validar.executar(segundoId);
            });

            if (!prontas.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "As tarefas nao ficaram prontas no prazo."
                );
            }

            inicio.countDown();

            return List.of(
                    primeira.get(5, TimeUnit.SECONDS),
                    segunda.get(5, TimeUnit.SECONDS)
            );
        } finally {
            inicio.countDown();
            executor.shutdownNow();

            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "As tarefas nao encerraram no prazo."
                );
            }
        }
    }

    private static void aguardarInicio(
            CountDownLatch prontas,
            CountDownLatch inicio
    ) throws InterruptedException {
        prontas.countDown();

        if (!inicio.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException(
                    "O sinal de inicio nao foi recebido no prazo."
            );
        }
    }

    private static long contar(
            List<ResultadoValidacao> resultados,
            ResultadoValidacao esperado
    ) {
        return resultados.stream()
                .filter(resultado -> resultado == esperado)
                .count();
    }

    private static boolean saldoIgual(Contexto c, String esperado) {
        return c.departamento.getSaldoDisponivel()
                .compareTo(new BigDecimal(esperado)) == 0;
    }

    private static void verificar(String descricao, boolean condicao) {
        if (!condicao) {
            throw new IllegalStateException("FALHA: " + descricao);
        }

        verificacoes++;
        System.out.println("PASSOU: " + descricao);
    }

    private static class Contexto {
        final DepartamentoRepositoryMemoria departamentos =
                new DepartamentoRepositoryMemoria();
        final RecursoCatalogoRepositoryMemoria recursos =
                new RecursoCatalogoRepositoryMemoria();
        final RequisicaoRepositoryMemoria requisicoes =
                new RequisicaoRepositoryMemoria(departamentos, recursos);

        final Departamento departamento = new Departamento(
                UUID.randomUUID(), "Departamento",
                new BigDecimal("10000.00")
        );
        final RecursoCatalogo recurso = new RecursoCatalogo(
                UUID.randomUUID(), "Recurso",
                new BigDecimal("100.00")
        );

        final CriarRequisicaoArmazenadaUseCase criar =
                new CriarRequisicaoArmazenadaUseCase(
                        departamentos, recursos, requisicoes
                );
        final ValidarRequisicaoArmazenadaUseCase validar =
                new ValidarRequisicaoArmazenadaUseCase(
                        departamentos, recursos, requisicoes
                );

        Contexto() {
            departamentos.adicionar(departamento);
            recursos.adicionar(recurso);

            // Preparação do cenário: teto 10000 e saldo disponível 5000.
            departamento.descontarSaldo(new BigDecimal("5000.00"));
        }

        Requisicao criar(int quantidade) {
            return criar.executar(
                    departamento.getId(),
                    List.of(new ItemSolicitado(recurso.getId(), quantidade))
            );
        }
    }
}