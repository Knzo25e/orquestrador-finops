package br.mackenzie.finops.aplicacao;

import br.mackenzie.finops.dominio.entidades.Departamento;
import br.mackenzie.finops.dominio.entidades.ItemRequisicao;
import br.mackenzie.finops.dominio.entidades.RecursoCatalogo;
import br.mackenzie.finops.dominio.entidades.Requisicao;
import br.mackenzie.finops.infraestrutura.repositorios.memoria.DepartamentoRepositoryMemoria;
import br.mackenzie.finops.infraestrutura.repositorios.memoria.RecursoCatalogoRepositoryMemoria;
import br.mackenzie.finops.infraestrutura.repositorios.memoria.RequisicaoRepositoryMemoria;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class VerificarConcorrenciaRepositorios {

    public static void main(String[] args) throws Exception {
        System.out.println("--- Concorrencia dos repositorios / Issue #49 ---");

        verificarDepartamentos();
        verificarRecursos();
        verificarRequisicoes();

        System.out.println("Todos os 3 cenarios concorrentes passaram.");
    }

    private static void verificarDepartamentos() throws Exception {
        DepartamentoRepositoryMemoria repositorio =
                new DepartamentoRepositoryMemoria();

        UUID id = UUID.randomUUID();

        Departamento primeiro = new Departamento(
                id, "Primeiro", new BigDecimal("1000.00")
        );
        Departamento segundo = new Departamento(
                id, "Segundo", new BigDecimal("2000.00")
        );

        boolean primeiroVenceu = disputar(
                () -> repositorio.adicionar(primeiro),
                () -> repositorio.adicionar(segundo)
        );

        Departamento esperado = primeiroVenceu ? primeiro : segundo;

        verificar("Departamento: preserva exatamente a instancia vencedora",
                repositorio.buscarPorId(id).orElseThrow() == esperado);

        verificar("Departamento: cadastro preserva saldo e teto",
                esperado.getSaldoDisponivel().equals(esperado.getOrcamentoMensal()));
    }

    private static void verificarRecursos() throws Exception {
        RecursoCatalogoRepositoryMemoria repositorio =
                new RecursoCatalogoRepositoryMemoria();

        UUID id = UUID.randomUUID();

        RecursoCatalogo primeiro = new RecursoCatalogo(
                id, "Primeiro", new BigDecimal("100.00")
        );
        RecursoCatalogo segundo = new RecursoCatalogo(
                id, "Segundo", new BigDecimal("200.00")
        );

        boolean primeiroVenceu = disputar(
                () -> repositorio.adicionar(primeiro),
                () -> repositorio.adicionar(segundo)
        );

        RecursoCatalogo esperado = primeiroVenceu ? primeiro : segundo;

        verificar("Recurso: preserva exatamente a instancia vencedora",
                repositorio.buscarPorId(id).orElseThrow() == esperado);

        verificar("Recurso: cadastro preserva custo",
                esperado.getCustoMensal().equals(new BigDecimal(
                        primeiroVenceu ? "100.00" : "200.00"
                )));
    }

    private static void verificarRequisicoes() throws Exception {
        DepartamentoRepositoryMemoria departamentos =
                new DepartamentoRepositoryMemoria();
        RecursoCatalogoRepositoryMemoria recursos =
                new RecursoCatalogoRepositoryMemoria();
        RequisicaoRepositoryMemoria requisicoes =
                new RequisicaoRepositoryMemoria(departamentos, recursos);

        Departamento departamento = new Departamento(
                UUID.randomUUID(), "Departamento", new BigDecimal("1000.00")
        );
        RecursoCatalogo recurso = new RecursoCatalogo(
                UUID.randomUUID(), "Recurso", new BigDecimal("100.00")
        );

        departamentos.adicionar(departamento);
        recursos.adicionar(recurso);

        Requisicao pedido = new Requisicao(departamento);
        pedido.adicionarItem(new ItemRequisicao(recurso, 1));

        disputar(
                () -> requisicoes.adicionar(pedido),
                () -> requisicoes.adicionar(pedido)
        );

        verificar("Requisicao: apenas um registro e mesma instancia",
                requisicoes.buscarPorId(pedido.getId()).orElseThrow() == pedido
                        && requisicoes.listarPorDepartamento(departamento.getId())
                        .size() == 1);

        verificar("Requisicao: cadastro nao executa roteamento ou desconto",
                "INICIAL".equals(pedido.getStatus())
                        && pedido.getMotivoRevisao() == null
                        && pedido.getCustoTotalProjetado()
                        .equals(new BigDecimal("100.00"))
                        && departamento.getSaldoDisponivel()
                        .equals(new BigDecimal("1000.00")));
    }

    /**
     * Confirma uma inclusao e uma rejeicao.
     * Retorna true se a primeira tarefa conseguiu cadastrar.
     */
    private static boolean disputar(
            Runnable primeira, Runnable segunda
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch prontas = new CountDownLatch(2);
        CountDownLatch inicio = new CountDownLatch(1);

        try {
            Future<Boolean> resultadoPrimeira = executor.submit(
                    () -> tentarCadastro(primeira, prontas, inicio)
            );
            Future<Boolean> resultadoSegunda = executor.submit(
                    () -> tentarCadastro(segunda, prontas, inicio)
            );

            if (!prontas.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "As tarefas nao ficaram prontas no prazo."
                );
            }

            inicio.countDown();

            boolean primeiraCadastrou = resultadoPrimeira.get(5, TimeUnit.SECONDS);
            boolean segundaCadastrou = resultadoSegunda.get(5, TimeUnit.SECONDS);

            if (primeiraCadastrou == segundaCadastrou) {
                throw new IllegalStateException(
                        "Esperava uma inclusao e uma rejeicao por duplicidade."
                );
            }

            System.out.println("PASSOU: uma inclusao e uma rejeicao");
            return primeiraCadastrou;
        } finally {
            inicio.countDown();
            executor.shutdownNow();

            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "As tarefas concorrentes nao terminaram."
                );
            }
        }
    }

    private static boolean tentarCadastro(
            Runnable cadastro,
            CountDownLatch prontas,
            CountDownLatch inicio
    ) throws InterruptedException {
        prontas.countDown();

        if (!inicio.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Inicio concorrente nao liberado.");
        }

        try {
            cadastro.run();
            return true;
        } catch (IllegalArgumentException excecao) {
            if (excecao.getMessage() == null || excecao.getMessage().isBlank()) {
                throw new IllegalStateException(
                        "Rejeicao sem mensagem explicativa.", excecao
                );
            }
            return false;
        }
    }

    private static void verificar(String descricao, boolean condicao) {
        if (!condicao) {
            throw new IllegalStateException("FALHA: " + descricao);
        }

        System.out.println("PASSOU: " + descricao);
    }
}