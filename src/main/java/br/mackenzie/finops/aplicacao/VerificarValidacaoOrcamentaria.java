package br.mackenzie.finops.aplicacao;

import br.mackenzie.finops.dominio.entidades.Departamento;
import br.mackenzie.finops.dominio.entidades.ItemRequisicao;
import br.mackenzie.finops.dominio.entidades.RecursoCatalogo;
import br.mackenzie.finops.dominio.entidades.Requisicao;
import br.mackenzie.finops.dominio.entidades.ResultadoValidacao;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class VerificarValidacaoOrcamentaria {
    private static int verificacoes;

    public static void main(String[] args) {
        verificacoes = 0;

        System.out.println("--- Validacao orcamentaria / Issue #39 ---");

        verificarCenario(
                "C1: saldo resultante acima da reserva",
                "10000.00", "5000.00", "3000.00",
                ResultadoValidacao.LIBERADA, "2000.00"
        );

        verificarCenario(
                "C2: custo igual ao saldo",
                "10000.00", "3000.00", "3000.00",
                ResultadoValidacao.REVISAO_POR_RESERVA, "3000.00"
        );

        verificarCenario(
                "C3: saldo insuficiente",
                "10000.00", "2000.00", "3000.00",
                ResultadoValidacao.REVISAO_POR_SALDO_INSUFICIENTE, "2000.00"
        );

        verificarCenario(
                "C10: imediatamente abaixo do limite",
                "10000.00", "10000.00", "8999.99",
                ResultadoValidacao.LIBERADA, "1000.01"
        );

        verificarCenario(
                "C11: exatamente no limite",
                "10000.00", "10000.00", "9000.00",
                ResultadoValidacao.REVISAO_POR_RESERVA, "10000.00"
        );

        verificarCenario(
                "C12: acima do limite, dentro do saldo",
                "10000.00", "10000.00", "9000.01",
                ResultadoValidacao.REVISAO_POR_RESERVA, "10000.00"
        );

        verificarCenario(
                "C13: reserva baseada no teto, nao no saldo",
                "10000.00", "2000.00", "1500.00",
                ResultadoValidacao.REVISAO_POR_RESERVA, "2000.00"
        );

        verificarCenario(
                "Pedido gratuito acima da reserva",
                "10000.00", "5000.00", "0.00",
                ResultadoValidacao.LIBERADA, "5000.00"
        );

        verificarCenario(
                "Pedido gratuito com saldo na reserva",
                "10000.00", "1000.00", "0.00",
                ResultadoValidacao.REVISAO_POR_RESERVA, "1000.00"
        );

        verificarCenario(
                "Pedido gratuito com teto zero",
                "0.00", "0.00", "0.00",
                ResultadoValidacao.REVISAO_POR_RESERVA, "0.00"
        );

        verificar(
                "Reserva arredonda 1.004 para 1.00",
                dinheiro("1.00").equals(
                        departamento("10.04", "10.04").calcularReserva()
                )
        );

        verificar(
                "Reserva arredonda 1.005 para 1.01",
                dinheiro("1.01").equals(
                        departamento("10.05", "10.05").calcularReserva()
                )
        );

        verificarCenario(
                "Roteamento utiliza a reserva arredondada",
                "10.05", "10.05", "9.04",
                ResultadoValidacao.REVISAO_POR_RESERVA, "10.05"
        );

        verificarProtecoes();

        System.out.println(
                "Todas as " + verificacoes + " verificacoes passaram."
        );
    }

    private static void verificarCenario(
            String descricao,
            String teto,
            String saldoInicial,
            String custo,
            ResultadoValidacao esperado,
            String saldoFinal
    ) {
        Departamento departamento = departamento(teto, saldoInicial);
        Requisicao requisicao = requisicao(departamento, custo);

        ResultadoValidacao resultado =
                new ValidarOrcamentoUseCase().executar(requisicao);

        boolean liberada = esperado == ResultadoValidacao.LIBERADA;
        String estadoEsperado = liberada
                ? "Provisionamento Liberado"
                : "Revisão Pendente";

        verificar(
                descricao,
                resultado == esperado
                        && estadoEsperado.equals(requisicao.getStatus())
                        && dinheiro(saldoFinal)
                                .equals(departamento.getSaldoDisponivel())
                        && dinheiro(teto)
                                .equals(departamento.getOrcamentoMensal())
                        && (liberada
                                ? requisicao.getMotivoRevisao() == null
                                : requisicao.getMotivoRevisao() == esperado)
        );
    }

    private static void verificarProtecoes() {
        ValidarOrcamentoUseCase casoDeUso = new ValidarOrcamentoUseCase();
        Departamento departamentoA = departamento("10000.00", "5000.00");
        Departamento departamentoB = departamento("20000.00", "20000.00");

        Requisicao pedido = requisicao(departamentoA, "3000.00");
        List<ItemRequisicao> copia = pedido.getItens();

        esperarExcecao(
                "Lista retornada impede alteracao externa",
                UnsupportedOperationException.class,
                () -> copia.clear()
        );

        verificar(
                "Tentativa de alterar copia preserva itens e total",
                pedido.getItens().size() == 1
                        && dinheiro("3000.00")
                                .equals(pedido.getCustoTotalProjetado())
        );

        verificar(
                "Primeira validacao libera o pedido",
                casoDeUso.executar(pedido) == ResultadoValidacao.LIBERADA
        );

        verificar(
                "C5: somente o departamento vinculado e afetado",
                dinheiro("2000.00").equals(departamentoA.getSaldoDisponivel())
                        && dinheiro("20000.00")
                                .equals(departamentoB.getSaldoDisponivel())
        );

        verificar(
                "C4: repeticao preserva estado e saldo",
                casoDeUso.executar(pedido) == ResultadoValidacao.JA_LIBERADA
                        && "Provisionamento Liberado".equals(pedido.getStatus())
                        && dinheiro("2000.00")
                                .equals(departamentoA.getSaldoDisponivel())
        );

        esperarExcecao(
                "Pedido liberado rejeita novos itens",
                IllegalStateException.class,
                () -> pedido.adicionarItem(item("1.00"))
        );

        verificar(
                "Adicao rejeitada preserva pedido e saldo",
                pedido.getItens().size() == 1
                        && dinheiro("3000.00")
                                .equals(pedido.getCustoTotalProjetado())
                        && dinheiro("2000.00")
                                .equals(departamentoA.getSaldoDisponivel())
        );

        Requisicao emRevisao = requisicao(departamentoA, "1500.00");
        casoDeUso.executar(emRevisao);

        verificar(
                "C8: pedido em revisao nao retorna ao fluxo automatico",
                casoDeUso.executar(emRevisao)
                        == ResultadoValidacao.ESTADO_NAO_PERMITIDO
                        && "Revisão Pendente".equals(emRevisao.getStatus())
                        && emRevisao.getMotivoRevisao()
                                == ResultadoValidacao.REVISAO_POR_RESERVA
                        && dinheiro("2000.00")
                                .equals(departamentoA.getSaldoDisponivel())
        );

        esperarExcecao(
                "Pedido em revisao rejeita novos itens",
                IllegalStateException.class,
                () -> emRevisao.adicionarItem(item("1.00"))
        );

        esperarExcecao(
                "Caso de uso rejeita requisicao nula",
                IllegalArgumentException.class,
                () -> casoDeUso.executar(null)
        );

        Requisicao vazia = new Requisicao(departamentoA);

        esperarExcecao(
                "Validacao rejeita requisicao sem itens",
                IllegalArgumentException.class,
                () -> casoDeUso.executar(vazia)
        );

        verificar(
                "Pedido vazio preserva estado inicial e saldo",
                "INICIAL".equals(vazia.getStatus())
                        && dinheiro("2000.00")
                                .equals(departamentoA.getSaldoDisponivel())
        );
    }

    private static BigDecimal dinheiro(String valor) {
        return new BigDecimal(valor);
    }

    private static Departamento departamento(String teto, String saldo) {
        Departamento departamento = new Departamento(
                UUID.randomUUID(), "Teste", dinheiro(teto)
        );

        // Preparacao do saldo anterior ao pedido testado.
        departamento.descontarSaldo(dinheiro(teto).subtract(dinheiro(saldo)));
        return departamento;
    }

    private static ItemRequisicao item(String custo) {
        RecursoCatalogo recurso = new RecursoCatalogo(
                UUID.randomUUID(), "Recurso teste", dinheiro(custo)
        );

        return new ItemRequisicao(recurso, 1);
    }

    private static Requisicao requisicao(
            Departamento departamento,
            String custo
    ) {
        return new CriarRequisicaoUseCase().executar(
                departamento, List.of(item(custo))
        );
    }

    private static void verificar(String descricao, boolean condicao) {
        if (!condicao) {
            throw new IllegalStateException("FALHA: " + descricao);
        }

        verificacoes++;
        System.out.println("PASSOU: " + descricao);
    }

    private static void esperarExcecao(
            String descricao,
            Class<? extends RuntimeException> tipo,
            Runnable acao
    ) {
        try {
            acao.run();
        } catch (RuntimeException excecao) {
            if (!tipo.isInstance(excecao)) {
                throw new IllegalStateException(
                        "FALHA: " + descricao + " — tipo de excecao inesperado.",
                        excecao
                );
            }

            verificar(descricao, true);
            return;
        }

        throw new IllegalStateException(
                "FALHA: " + descricao + " — esperava " + tipo.getSimpleName()
        );
    }
}