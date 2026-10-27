package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.aplicacao.entradas.ItemSolicitado;
import br.mackenzie.finops.aplicacao.entradas.SolicitacaoCriacaoRequisicao;
import br.mackenzie.finops.aplicacao.excecoes.ConflitoIdentificadorException;
import br.mackenzie.finops.dominio.entidades.Departamento;
import br.mackenzie.finops.dominio.entidades.ItemRequisicao;
import br.mackenzie.finops.dominio.entidades.RecursoCatalogo;
import br.mackenzie.finops.dominio.entidades.Requisicao;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class VerificarComparacaoAutoria {

    private static int verificacoes;

    public static void main(String[] args) {
        UUID autor = UUID.randomUUID();
        UUID outroAutor = UUID.randomUUID();
        UUID pedidoId = UUID.randomUUID();

        Departamento departamento = Departamento.reconstituir(
                UUID.randomUUID(),
                "Departamento do teste",
                new BigDecimal("1000.00"),
                new BigDecimal("800.00")
        );

        RecursoCatalogo recurso = new RecursoCatalogo(
                UUID.randomUUID(),
                "Recurso do teste",
                new BigDecimal("12.50")
        );

        Requisicao comAutor = Requisicao.criarComId(
                pedidoId, departamento, autor
        );
        comAutor.adicionarItem(new ItemRequisicao(recurso, 2));

        Requisicao semAutor = Requisicao.criarComId(
                pedidoId, departamento
        );
        semAutor.adicionarItem(new ItemRequisicao(recurso, 2));

        List<ItemSolicitado> itens = List.of(
                new ItemSolicitado(recurso.getId(), 2)
        );

        SolicitacaoCriacaoRequisicao mesmoAutor =
                SolicitacaoCriacaoRequisicao.comAutoria(
                        pedidoId, departamento.getId(), itens, autor
                );

        SolicitacaoCriacaoRequisicao autorDiferente =
                SolicitacaoCriacaoRequisicao.comAutoria(
                        pedidoId, departamento.getId(), itens, outroAutor
                );

        SolicitacaoCriacaoRequisicao autoriaAusente =
                new SolicitacaoCriacaoRequisicao(
                        pedidoId, departamento.getId(), itens
                );

        ComparadorSolicitacaoRequisicao.conferir(mesmoAutor, comAutor);
        conferir(true, "Mesmo autor permite repeticao equivalente");

        exigirConflito(
                () -> ComparadorSolicitacaoRequisicao.conferir(
                        autorDiferente, comAutor
                ),
                "Outro autor produz conflito"
        );

        exigirConflito(
                () -> ComparadorSolicitacaoRequisicao.conferir(
                        mesmoAutor, semAutor
                ),
                "Autor informado nao assume pedido antigo sem autor"
        );

        exigirConflito(
                () -> ComparadorSolicitacaoRequisicao.conferir(
                        autoriaAusente, comAutor
                ),
                "Autoria ausente nao equivale a pedido com autor"
        );

        ComparadorSolicitacaoRequisicao.conferir(autoriaAusente, semAutor);
        conferir(true, "Duas autorias ausentes preservam compatibilidade");

        conferir(
                autor.equals(comAutor.getDesenvolvedorId())
                        && semAutor.getDesenvolvedorId() == null,
                "Comparacoes preservam autoria dos pedidos"
        );

        conferir(
                "INICIAL".equals(comAutor.getStatus())
                        && "INICIAL".equals(semAutor.getStatus())
                        && comAutor.getItens().size() == 1
                        && semAutor.getItens().size() == 1
                        && comAutor.getCustoTotalProjetado()
                                .compareTo(new BigDecimal("25.00")) == 0
                        && semAutor.getCustoTotalProjetado()
                                .compareTo(new BigDecimal("25.00")) == 0
                        && departamento.getSaldoDisponivel()
                                .compareTo(new BigDecimal("800.00")) == 0,
                "Comparacoes preservam estado, itens, total e saldo"
        );

        System.out.println(
                "Todas as " + verificacoes + " verificacoes passaram."
        );
    }

    private static void exigirConflito(
            Runnable operacao,
            String descricao
    ) {
        try {
            operacao.run();
        } catch (ConflitoIdentificadorException esperada) {
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