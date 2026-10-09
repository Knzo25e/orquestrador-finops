package br.mackenzie.finops.aplicacao;

import br.mackenzie.finops.dominio.entidades.Departamento;
import br.mackenzie.finops.dominio.entidades.ItemRequisicao;
import br.mackenzie.finops.dominio.entidades.RecursoCatalogo;
import br.mackenzie.finops.dominio.entidades.Requisicao;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class Main {
    public static void main(String[] args) {
        System.out.println(
                "--- Verificacoes da SPEC-002 com BigDecimal / Issue #37 ---"
        );

        Departamento ti = new Departamento(
                UUID.randomUUID(),
                "Engenharia e Tecnologia",
                new BigDecimal("50000.00")
        );

        RecursoCatalogo servidor = new RecursoCatalogo(
                UUID.randomUUID(),
                "Servidor Cloud AWS",
                new BigDecimal("1500.00")
        );

        ItemRequisicao item = new ItemRequisicao(servidor, 2);

        CriarRequisicaoUseCase casoDeUso = new CriarRequisicaoUseCase();
        Requisicao requisicao = casoDeUso.executar(ti, List.of(item));

        verificar(
                "Total inicial de duas unidades",
                new BigDecimal("3000.00")
                        .equals(requisicao.getCustoTotalProjetado())
        );

        verificar(
                "Status inicial preservado",
                "INICIAL".equals(requisicao.getStatus())
        );

        RecursoCatalogo armazenamento = new RecursoCatalogo(
                UUID.randomUUID(),
                "Armazenamento",
                new BigDecimal("200.00")
        );

        requisicao.adicionarItem(new ItemRequisicao(armazenamento, 3));

        verificar(
                "Total atualizado para 3600.00 ao adicionar outro item",
                new BigDecimal("3600.00")
                        .equals(requisicao.getCustoTotalProjetado())
        );

        BigDecimal totalRecalculado = requisicao.calcularTotalProjetado();

        verificar(
                "Recalculo preserva o total sem duplicar valores",
                new BigDecimal("3600.00").equals(totalRecalculado)
                        && new BigDecimal("3600.00")
                                .equals(requisicao.getCustoTotalProjetado())
        );

        verificar(
                "Adicionar itens nao altera o status INICIAL",
                "INICIAL".equals(requisicao.getStatus())
        );

        verificar(
                "Criacao preserva o departamento informado",
                requisicao.getDepartamento() == ti
        );

        verificarIllegalArgument(
                "Construtor rejeita departamento nulo",
                () -> new Requisicao(null)
        );

        verificarIllegalArgument(
                "Caso de uso rejeita departamento nulo",
                () -> casoDeUso.executar(null, List.of(item))
        );

        verificarIllegalArgument(
                "Caso de uso rejeita lista nula",
                () -> casoDeUso.executar(ti, null)
        );

        verificarIllegalArgument(
                "Caso de uso rejeita lista vazia",
                () -> casoDeUso.executar(ti, List.of())
        );

        System.out.println("Todos os 10 testes passaram.");
    }

    private static void verificar(String descricao, boolean condicao) {
        if (!condicao) {
            throw new IllegalStateException("FALHA: " + descricao);
        }

        System.out.println("PASSOU: " + descricao);
    }

    private static void verificarIllegalArgument(
            String descricao,
            Runnable acao
    ) {
        try {
            acao.run();
        } catch (IllegalArgumentException excecao) {
            System.out.println("PASSOU: " + descricao);
            return;
        }

        throw new IllegalStateException(
                "FALHA: " + descricao + " — esperava IllegalArgumentException."
        );
    }
}