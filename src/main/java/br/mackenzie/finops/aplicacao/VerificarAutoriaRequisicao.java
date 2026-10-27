package br.mackenzie.finops.aplicacao;

import br.mackenzie.finops.dominio.entidades.Departamento;
import br.mackenzie.finops.dominio.entidades.ItemRequisicao;
import br.mackenzie.finops.dominio.entidades.RecursoCatalogo;
import br.mackenzie.finops.dominio.entidades.Requisicao;
import br.mackenzie.finops.dominio.entidades.ResultadoValidacao;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class VerificarAutoriaRequisicao {

    private static int verificacoes;

    public static void main(String[] args) {
        UUID autor = UUID.randomUUID();
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

        Requisicao pedido = Requisicao.criarComId(
                pedidoId, departamento, autor
        );

        conferir(
                autor.equals(pedido.getDesenvolvedorId()),
                "Criacao preserva autor"
        );
        conferir(
                pedidoId.equals(pedido.getId())
                        && pedido.getDepartamento() == departamento,
                "Criacao preserva identidade e departamento"
        );
        conferir(
                "INICIAL".equals(pedido.getStatus())
                        && igual(pedido.getCustoTotalProjetado(), "0.00"),
                "Criacao preserva estado e total inicial"
        );

        rejeitar(
                () -> Requisicao.criarComId(
                        UUID.randomUUID(), departamento, null
                ),
                "Criacao com autoria rejeita autor nulo"
        );

        pedido.adicionarItem(new ItemRequisicao(recurso, 2));

        conferir(
                autor.equals(pedido.getDesenvolvedorId())
                        && igual(pedido.getCustoTotalProjetado(), "25.00"),
                "Adicionar item preserva autor"
        );

        Requisicao recuperado = Requisicao.reconstituir(
                pedidoId,
                departamento,
                pedido.getItens(),
                pedido.getStatus(),
                pedido.getCustoTotalProjetado(),
                pedido.getMotivoRevisao(),
                autor
        );

        conferir(
                autor.equals(recuperado.getDesenvolvedorId())
                        && recuperado != pedido,
                "Reconstituicao preserva autor em nova instancia"
        );
        conferir(
                recuperado.getItens().size() == 1
                        && igual(recuperado.getCustoTotalProjetado(), "25.00"),
                "Reconstituicao preserva itens e total"
        );
        conferir(
                igual(departamento.getSaldoDisponivel(), "800.00"),
                "Criacao e reconstituicao preservam saldo"
        );

        conferir(
                recuperado.processarRoteamento() == ResultadoValidacao.LIBERADA
                        && autor.equals(recuperado.getDesenvolvedorId())
                        && igual(departamento.getSaldoDisponivel(), "775.00"),
                "Validacao financeira preserva autor e desconta uma vez"
        );

        Requisicao liberado = Requisicao.reconstituir(
                pedidoId,
                departamento,
                recuperado.getItens(),
                recuperado.getStatus(),
                recuperado.getCustoTotalProjetado(),
                recuperado.getMotivoRevisao(),
                autor
        );

        conferir(
                autor.equals(liberado.getDesenvolvedorId())
                        && "Provisionamento Liberado".equals(liberado.getStatus())
                        && igual(departamento.getSaldoDisponivel(), "775.00"),
                "Reconstituicao de liberado preserva autoria e saldo"
        );
        conferir(
                liberado.processarRoteamento()
                        == ResultadoValidacao.JA_LIBERADA
                        && autor.equals(liberado.getDesenvolvedorId())
                        && igual(departamento.getSaldoDisponivel(), "775.00"),
                "Repeticao financeira preserva autor sem novo desconto"
        );

        Requisicao antigo = Requisicao.reconstituir(
                UUID.randomUUID(),
                departamento,
                pedido.getItens(),
                "INICIAL",
                new BigDecimal("25.00"),
                null
        );

        conferir(
                antigo.getDesenvolvedorId() == null,
                "Reconstituicao antiga nao inventa autor"
        );

        Requisicao semAutor = Requisicao.reconstituir(
                UUID.randomUUID(),
                departamento,
                pedido.getItens(),
                "INICIAL",
                new BigDecimal("25.00"),
                null,
                null
        );

        conferir(
                semAutor.getDesenvolvedorId() == null,
                "Reconstituicao explicita aceita autoria desconhecida"
        );
        conferir(
                Requisicao.criarComId(UUID.randomUUID(), departamento)
                        .getDesenvolvedorId() == null
                        && new Requisicao(departamento)
                                .getDesenvolvedorId() == null,
                "Caminhos antigos preservam autoria desconhecida"
        );

        rejeitar(
                () -> Requisicao.reconstituir(
                        UUID.randomUUID(),
                        departamento,
                        pedido.getItens(),
                        "INICIAL",
                        new BigDecimal("99.00"),
                        null,
                        autor
                ),
                "Reconstituicao com autor continua rejeitando total incorreto"
        );
        rejeitar(
                () -> Requisicao.reconstituir(
                        UUID.randomUUID(),
                        departamento,
                        List.of(),
                        "INICIAL",
                        new BigDecimal("0.00"),
                        null,
                        autor
                ),
                "Reconstituicao com autor continua rejeitando lista vazia"
        );

        System.out.println(
                "Todas as " + verificacoes + " verificacoes passaram."
        );
    }

    private static boolean igual(BigDecimal valor, String esperado) {
        return valor != null
                && valor.compareTo(new BigDecimal(esperado)) == 0;
    }

    private static void conferir(boolean condicao, String descricao) {
        if (!condicao) {
            throw new IllegalStateException("FALHOU: " + descricao);
        }

        verificacoes++;
        System.out.println("PASSOU: " + descricao);
    }

    private static void rejeitar(Runnable operacao, String descricao) {
        try {
            operacao.run();
        } catch (IllegalArgumentException esperada) {
            conferir(true, descricao);
            return;
        }

        throw new IllegalStateException("FALHOU: " + descricao);
    }
}