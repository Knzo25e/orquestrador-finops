package br.mackenzie.finops.aplicacao;

import br.mackenzie.finops.dominio.entidades.Departamento;
import br.mackenzie.finops.dominio.entidades.ItemRequisicao;
import br.mackenzie.finops.dominio.entidades.RecursoCatalogo;
import br.mackenzie.finops.dominio.entidades.Requisicao;

import java.math.BigDecimal;
import java.util.UUID;

public class VerificarValoresMonetarios {
    private static int verificacoes;

    public static void main(String[] args) {
        verificacoes = 0;

        System.out.println("--- Entradas e precisao monetaria / Issue #37 ---");

        BigDecimal[] invalidos = {
                null,
                new BigDecimal("-0.01"),
                new BigDecimal("1.001")
        };

        String[] motivos = {
                "nulo",
                "negativo",
                "com fracao de centavo"
        };

        Departamento departamento = departamento("10.00");

        for (int i = 0; i < invalidos.length; i++) {
            BigDecimal valor = invalidos[i];
            String motivo = motivos[i];

            esperarRejeicao(
                    "Recurso rejeita custo " + motivo,
                    () -> recurso(valor)
            );

            esperarRejeicao(
                    "Departamento rejeita teto " + motivo,
                    () -> new Departamento(
                            UUID.randomUUID(), "Teste", valor
                    )
            );

            esperarRejeicao(
                    "Consulta rejeita valor " + motivo,
                    () -> departamento.verificarDisponibilidade(valor)
            );

            esperarRejeicao(
                    "Desconto rejeita valor " + motivo,
                    () -> departamento.descontarSaldo(valor)
            );

            verificar(
                    "Entradas invalidas preservam saldo e teto: " + motivo,
                    dinheiro("10.00").equals(departamento.getSaldoDisponivel())
                            && dinheiro("10.00")
                                    .equals(departamento.getOrcamentoMensal())
            );
        }

        RecursoCatalogo gratuito = recurso(BigDecimal.ZERO);

        verificar(
                "Custo zero e normalizado para duas casas",
                dinheiro("0.00").equals(gratuito.getCustoMensal())
        );

        verificar(
                "Recurso gratuito produz subtotal zero",
                dinheiro("0.00").equals(
                        new ItemRequisicao(gratuito, 3).getCustoSubtotal()
                )
        );

        Departamento semOrcamento = departamento("0");

        verificar(
                "Teto zero e permitido e normalizado",
                dinheiro("0.00").equals(semOrcamento.getOrcamentoMensal())
                        && dinheiro("0.00")
                                .equals(semOrcamento.getSaldoDisponivel())
        );

        verificar(
                "Zeros adicionais nao representam fracao de centavo",
                dinheiro("1.23").equals(
                        recurso(dinheiro("1.230")).getCustoMensal()
                )
        );

        esperarRejeicao(
                "Item rejeita recurso nulo",
                () -> new ItemRequisicao(null, 1)
        );

        Integer[] quantidadesInvalidas = {null, 0, -1};

        for (Integer quantidade : quantidadesInvalidas) {
            esperarRejeicao(
                    "Item rejeita quantidade " + quantidade,
                    () -> new ItemRequisicao(gratuito, quantidade)
            );
        }

        Requisicao requisicao = new Requisicao(departamento);

        requisicao.adicionarItem(
                new ItemRequisicao(recurso(dinheiro("0.10")), 3)
        );

        verificar(
                "Multiplicacao de 0.10 por 3 resulta em 0.30 exato",
                dinheiro("0.30").equals(requisicao.getCustoTotalProjetado())
        );

        requisicao.adicionarItem(
                new ItemRequisicao(recurso(dinheiro("0.20")), 1)
        );

        verificar(
                "Soma dos itens resulta em 0.50 exato",
                dinheiro("0.50").equals(requisicao.getCustoTotalProjetado())
        );

        esperarRejeicao(
                "Requisicao rejeita item nulo",
                () -> requisicao.adicionarItem(null)
        );

        verificar(
                "Item nulo nao altera lista, total ou estado",
                requisicao.getItens().size() == 2
                        && dinheiro("0.50")
                                .equals(requisicao.getCustoTotalProjetado())
                        && "INICIAL".equals(requisicao.getStatus())
        );

        verificar(
                "Consulta reconhece valor igual ao saldo",
                departamento.verificarDisponibilidade(dinheiro("10.00"))
        );

        verificar(
                "Consulta identifica valor acima do saldo",
                !departamento.verificarDisponibilidade(dinheiro("10.01"))
        );

        esperarRejeicao(
                "Desconto acima do saldo e rejeitado",
                () -> departamento.descontarSaldo(dinheiro("10.01"))
        );

        verificar(
                "Desconto rejeitado preserva saldo",
                dinheiro("10.00").equals(departamento.getSaldoDisponivel())
        );

        departamento.descontarSaldo(dinheiro("0.10"));
        departamento.descontarSaldo(dinheiro("0.20"));

        verificar(
                "Descontos decimais deixam saldo exato de 9.70",
                dinheiro("9.70").equals(departamento.getSaldoDisponivel())
        );

        departamento.descontarSaldo(BigDecimal.ZERO);

        verificar(
                "Desconto zero preserva saldo",
                dinheiro("9.70").equals(departamento.getSaldoDisponivel())
        );

        verificar(
                "Descontos nao alteram teto mensal",
                dinheiro("10.00").equals(departamento.getOrcamentoMensal())
        );

        System.out.println(
                "Todas as " + verificacoes + " verificacoes passaram."
        );
    }

    private static BigDecimal dinheiro(String valor) {
        return new BigDecimal(valor);
    }

    private static RecursoCatalogo recurso(BigDecimal custo) {
        return new RecursoCatalogo(UUID.randomUUID(), "Recurso teste", custo);
    }

    private static Departamento departamento(String teto) {
        return new Departamento(
                UUID.randomUUID(), "Departamento teste", dinheiro(teto)
        );
    }

    private static void verificar(String descricao, boolean condicao) {
        if (!condicao) {
            throw new IllegalStateException("FALHA: " + descricao);
        }

        verificacoes++;
        System.out.println("PASSOU: " + descricao);
    }

    private static void esperarRejeicao(String descricao, Runnable acao) {
        try {
            acao.run();
        } catch (IllegalArgumentException excecao) {
            verificar(
                    descricao + " com mensagem explicativa",
                    excecao.getMessage() != null
                            && !excecao.getMessage().isBlank()
            );
            return;
        }

        throw new IllegalStateException(
                "FALHA: " + descricao + " — esperava IllegalArgumentException."
        );
    }
}