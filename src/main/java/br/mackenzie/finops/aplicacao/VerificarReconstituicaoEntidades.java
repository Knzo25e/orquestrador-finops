package br.mackenzie.finops.aplicacao;

import br.mackenzie.finops.dominio.entidades.Departamento;
import br.mackenzie.finops.dominio.entidades.ItemRequisicao;
import br.mackenzie.finops.dominio.entidades.RecursoCatalogo;
import br.mackenzie.finops.dominio.entidades.Requisicao;
import br.mackenzie.finops.dominio.entidades.ResultadoValidacao;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class VerificarReconstituicaoEntidades {
    private static int verificacoes;

    public static void main(String[] args) {
        System.out.println("--- Reconstituicao das entidades / Issue #57 ---");

        verificarDepartamento();
        verificarItem();
        verificarRequisicao();

        System.out.println(
                "Todas as " + verificacoes + " verificacoes passaram."
        );
    }

    private static void verificarDepartamento() {
        UUID id = UUID.randomUUID();
        Departamento departamento = Departamento.reconstituir(
                id, "Engenharia", valor("1000"), valor("350")
        );

        conferir(
                "Departamento preserva identidade, nome, teto e saldo",
                departamento.getId().equals(id)
                        && "Engenharia".equals(departamento.getNome())
                        && departamento.getOrcamentoMensal().equals(valor("1000.00"))
                        && departamento.getSaldoDisponivel().equals(valor("350.00"))
        );

        conferir(
                "Reserva continua baseada no teto",
                departamento.calcularReserva().equals(valor("100.00"))
        );

        rejeitar("Departamento rejeita UUID nulo", () ->
                Departamento.reconstituir(
                        null, "Engenharia", valor("1000"), valor("350")
                )
        );

        for (BigDecimal invalido : valoresInvalidos()) {
            rejeitar("Departamento rejeita teto: " + invalido, () ->
                    Departamento.reconstituir(
                            id, "Engenharia", invalido, valor("0")
                    )
            );

            rejeitar("Departamento rejeita saldo: " + invalido, () ->
                    Departamento.reconstituir(
                            id, "Engenharia", valor("1000"), invalido
                    )
            );
        }

        rejeitar("Departamento rejeita saldo superior ao teto", () ->
                Departamento.reconstituir(
                        id, "Engenharia", valor("1000"), valor("1000.01")
                )
        );

        conferir(
                "Departamento aceita teto e saldo zero",
                Departamento.reconstituir(
                        id, "Engenharia", valor("0"), valor("0")
                ).getSaldoDisponivel().equals(valor("0.00"))
        );

        conferir(
                "Departamento aceita saldo igual ao teto",
                Departamento.reconstituir(
                        id, "Engenharia", valor("1000"), valor("1000")
                ).getSaldoDisponivel().equals(valor("1000.00"))
        );
    }

    private static void verificarItem() {
        RecursoCatalogo recurso = new RecursoCatalogo(
                UUID.randomUUID(), "Servidor", valor("150")
        );

        ItemRequisicao historico = ItemRequisicao.reconstituir(
                recurso, 2, valor("100"), valor("200")
        );

        conferir(
                "Item preserva recurso, quantidade e preco historico",
                historico.getRecurso() == recurso
                        && historico.getQuantidade() == 2
                        && historico.getPrecoUnitario().equals(valor("100.00"))
                        && historico.getCustoSubtotal().equals(valor("200.00"))
        );

        conferir(
                "Reconstituicao nao modifica o preco do catalogo",
                recurso.getCustoMensal().equals(valor("150.00"))
        );

        ItemRequisicao novo = new ItemRequisicao(recurso, 2);

        conferir(
                "Criacao normal utiliza o preco atual",
                novo.getPrecoUnitario().equals(valor("150.00"))
                        && novo.getCustoSubtotal().equals(valor("300.00"))
        );

        rejeitar("Item rejeita recurso nulo", () ->
                ItemRequisicao.reconstituir(
                        null, 2, valor("100"), valor("200")
                )
        );

        RecursoCatalogo semId = new RecursoCatalogo(
                null, "Sem ID", valor("100")
        );

        rejeitar("Item rejeita recurso sem UUID", () ->
                ItemRequisicao.reconstituir(
                        semId, 2, valor("100"), valor("200")
                )
        );

        for (Integer quantidade : Arrays.asList(null, 0, -1)) {
            rejeitar("Item rejeita quantidade: " + quantidade, () ->
                    ItemRequisicao.reconstituir(
                            recurso, quantidade, valor("100"), valor("200")
                    )
            );
        }

        for (BigDecimal invalido : valoresInvalidos()) {
            rejeitar("Item rejeita preco: " + invalido, () ->
                    ItemRequisicao.reconstituir(
                            recurso, 2, invalido, valor("200")
                    )
            );

            rejeitar("Item rejeita subtotal: " + invalido, () ->
                    ItemRequisicao.reconstituir(
                            recurso, 2, valor("100"), invalido
                    )
            );
        }

        rejeitar("Item rejeita subtotal inconsistente", () ->
                ItemRequisicao.reconstituir(
                        recurso, 2, valor("100"), valor("201")
                )
        );

        conferir(
                "Item aceita preco historico zero",
                ItemRequisicao.reconstituir(
                        recurso, 2, valor("0"), valor("0")
                ).getCustoSubtotal().equals(valor("0.00"))
        );

        conferir(
                "Item preserva precisao decimal",
                ItemRequisicao.reconstituir(
                        recurso, 3, valor("0.10"), valor("0.300")
                ).getCustoSubtotal().equals(valor("0.30"))
        );
    }

    private static void verificarRequisicao() {
        UUID id = UUID.randomUUID();

        Departamento departamento = Departamento.reconstituir(
                UUID.randomUUID(), "Engenharia", valor("1000"), valor("100")
        );

        RecursoCatalogo recurso = new RecursoCatalogo(
                UUID.randomUUID(), "Servidor", valor("150")
        );

        ItemRequisicao primeiro = ItemRequisicao.reconstituir(
                recurso, 2, valor("100"), valor("200")
        );

        ItemRequisicao segundo = ItemRequisicao.reconstituir(
                recurso, 1, valor("50"), valor("50")
        );

        List<ItemRequisicao> itens = List.of(primeiro, segundo);
        BigDecimal saldoAnterior = departamento.getSaldoDisponivel();

        Requisicao criada = Requisicao.criarComId(id, departamento);

        conferir(
                "Criacao explicita preserva UUID e estado inicial",
                criada.getId().equals(id)
                        && criada.getDepartamento() == departamento
                        && "INICIAL".equals(criada.getStatus())
                        && criada.getItens().isEmpty()
                        && criada.getCustoTotalProjetado().equals(valor("0.00"))
                        && criada.getMotivoRevisao() == null
        );

        criada.adicionarItem(primeiro);

        conferir(
                "Criacao explicita permite adicionar itens sem desconto",
                criada.getCustoTotalProjetado().equals(valor("200.00"))
                        && departamento.getSaldoDisponivel().equals(saldoAnterior)
        );

        List<ItemRequisicao> entrada = new ArrayList<>(itens);

        Requisicao inicial = Requisicao.reconstituir(
                id, departamento, entrada, "INICIAL", valor("250"), null
        );

        conferir(
                "Requisicao preserva identidade, ordem e total",
                inicial.getId().equals(id)
                        && inicial.getDepartamento() == departamento
                        && inicial.getItens().get(0) == primeiro
                        && inicial.getItens().get(1) == segundo
                        && inicial.getCustoTotalProjetado().equals(valor("250.00"))
                        && "INICIAL".equals(inicial.getStatus())
                        && inicial.getMotivoRevisao() == null
        );

        entrada.clear();

        conferir(
                "Alterar lista de entrada nao modifica a requisicao",
                inicial.getItens().equals(itens)
        );

        boolean listaProtegida = false;
        try {
            inicial.getItens().clear();
        } catch (UnsupportedOperationException esperada) {
            listaProtegida = true;
        }

        conferir("Lista recuperada permanece protegida", listaProtegida);

        Requisicao liberada = Requisicao.reconstituir(
                id, departamento, itens,
                "Provisionamento Liberado", valor("250"), null
        );

        conferir(
                "Pedido liberado e recuperado sem novo desconto",
                "Provisionamento Liberado".equals(liberada.getStatus())
                        && departamento.getSaldoDisponivel().equals(saldoAnterior)
        );

        conferir(
                "Repeticao reconhece liberacao mesmo com saldo atual menor",
                liberada.processarRoteamento() == ResultadoValidacao.JA_LIBERADA
                        && departamento.getSaldoDisponivel().equals(saldoAnterior)
        );

        for (ResultadoValidacao motivo : List.of(
                ResultadoValidacao.REVISAO_POR_SALDO_INSUFICIENTE,
                ResultadoValidacao.REVISAO_POR_RESERVA
        )) {
            Requisicao revisao = Requisicao.reconstituir(
                    id, departamento, itens,
                    "Revisão Pendente", valor("250"), motivo
            );

            conferir(
                    "Revisao preserva motivo historico: " + motivo,
                    "Revisão Pendente".equals(revisao.getStatus())
                            && revisao.getMotivoRevisao() == motivo
                            && departamento.getSaldoDisponivel().equals(saldoAnterior)
            );

            conferir(
                    "Revisao nao reinicia fluxo: " + motivo,
                    revisao.processarRoteamento()
                            == ResultadoValidacao.ESTADO_NAO_PERMITIDO
                            && departamento.getSaldoDisponivel().equals(saldoAnterior)
            );
        }

        rejeitar("Criacao explicita rejeita UUID nulo", () ->
                Requisicao.criarComId(null, departamento)
        );

        rejeitar("Criacao explicita rejeita departamento nulo", () ->
                Requisicao.criarComId(id, null)
        );

        Departamento semId = new Departamento(
                null, "Sem ID", valor("1000")
        );

        rejeitar("Criacao explicita rejeita departamento sem UUID", () ->
                Requisicao.criarComId(id, semId)
        );

        rejeitar("Reconstituicao rejeita UUID nulo", () ->
                Requisicao.reconstituir(
                        null, departamento, itens, "INICIAL", valor("250"), null
                )
        );

        rejeitar("Reconstituicao rejeita departamento nulo", () ->
                Requisicao.reconstituir(
                        id, null, itens, "INICIAL", valor("250"), null
                )
        );

        rejeitar("Reconstituicao rejeita departamento sem UUID", () ->
                Requisicao.reconstituir(
                        id, semId, itens, "INICIAL", valor("250"), null
                )
        );

        rejeitar("Reconstituicao rejeita lista nula", () ->
                Requisicao.reconstituir(
                        id, departamento, null, "INICIAL", valor("250"), null
                )
        );

        rejeitar("Reconstituicao rejeita lista vazia", () ->
                Requisicao.reconstituir(
                        id, departamento, List.of(), "INICIAL", valor("0"), null
                )
        );

        rejeitar("Reconstituicao rejeita item nulo", () ->
                Requisicao.reconstituir(
                        id, departamento, Arrays.asList(primeiro, null),
                        "INICIAL", valor("200"), null
                )
        );

        ItemRequisicao itemSemId = new ItemRequisicao(
                new RecursoCatalogo(null, "Sem ID", valor("100")), 1
        );

        rejeitar("Reconstituicao rejeita recurso sem UUID", () ->
                Requisicao.reconstituir(
                        id, departamento, List.of(itemSemId),
                        "INICIAL", valor("100"), null
                )
        );

        for (BigDecimal invalido : valoresInvalidos()) {
            rejeitar("Requisicao rejeita total: " + invalido, () ->
                    Requisicao.reconstituir(
                            id, departamento, itens, "INICIAL", invalido, null
                    )
            );
        }

        rejeitar("Requisicao rejeita total inconsistente", () ->
                Requisicao.reconstituir(
                        id, departamento, itens, "INICIAL", valor("251"), null
                )
        );

        for (String estado : Arrays.asList(null, "DESCONHECIDO", "Em Analise")) {
            rejeitar("Requisicao rejeita estado: " + estado, () ->
                    Requisicao.reconstituir(
                            id, departamento, itens, estado, valor("250"), null
                    )
            );
        }

        for (ResultadoValidacao motivo : Arrays.asList(
                null,
                ResultadoValidacao.LIBERADA,
                ResultadoValidacao.JA_LIBERADA,
                ResultadoValidacao.ESTADO_NAO_PERMITIDO
        )) {
            rejeitar("Revisao rejeita motivo: " + motivo, () ->
                    Requisicao.reconstituir(
                            id, departamento, itens,
                            "Revisão Pendente", valor("250"), motivo
                    )
            );
        }

        for (String estado : List.of("INICIAL", "Provisionamento Liberado")) {
            rejeitar("Estado rejeita motivo indevido: " + estado, () ->
                    Requisicao.reconstituir(
                            id, departamento, itens, estado, valor("250"),
                            ResultadoValidacao.REVISAO_POR_RESERVA
                    )
            );
        }

        conferir(
                "Reconstituicoes e rejeicoes preservam saldo e itens recebidos",
                departamento.getSaldoDisponivel().equals(saldoAnterior)
                        && primeiro.getPrecoUnitario().equals(valor("100.00"))
                        && primeiro.getCustoSubtotal().equals(valor("200.00"))
                        && segundo.getCustoSubtotal().equals(valor("50.00"))
                        && recurso.getCustoMensal().equals(valor("150.00"))
        );
    }

    private static List<BigDecimal> valoresInvalidos() {
        return Arrays.asList(null, valor("-0.01"), valor("0.001"));
    }

    private static BigDecimal valor(String texto) {
        return new BigDecimal(texto);
    }

    private static void rejeitar(String descricao, Runnable operacao) {
        try {
            operacao.run();
        } catch (IllegalArgumentException esperada) {
            conferir(
                    descricao,
                    esperada.getMessage() != null
                            && !esperada.getMessage().isBlank()
            );
            return;
        }

        throw new AssertionError("FALHOU: " + descricao);
    }

    private static void conferir(String descricao, boolean condicao) {
        if (!condicao) {
            throw new AssertionError("FALHOU: " + descricao);
        }

        verificacoes++;
        System.out.println("PASSOU: " + descricao);
    }
}