package br.mackenzie.finops.aplicacao;

import br.mackenzie.finops.dominio.entidades.Departamento;
import br.mackenzie.finops.dominio.entidades.ItemRequisicao;
import br.mackenzie.finops.dominio.entidades.RecursoCatalogo;
import br.mackenzie.finops.dominio.entidades.Requisicao;
import br.mackenzie.finops.infraestrutura.repositorios.memoria.DepartamentoRepositoryMemoria;
import br.mackenzie.finops.infraestrutura.repositorios.memoria.RecursoCatalogoRepositoryMemoria;
import br.mackenzie.finops.infraestrutura.repositorios.memoria.RequisicaoRepositoryMemoria;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class VerificarRepositoriosMemoria {

    private static int verificacoes;

    public static void main(String[] args) {
        System.out.println("--- Repositorios em memoria / Issue #49 ---");

        verificarCadastroEConsultas();
        verificarEntradasInvalidas();
        verificarVinculos();
        verificarReferenciasEContextos();

        System.out.println(
                "Todas as " + verificacoes + " verificacoes passaram."
        );
    }

    private static void verificarCadastroEConsultas() {
        Contexto contexto = new Contexto();
        Requisicao pedido = criarPedido(contexto.departamento, contexto.recurso);
        contexto.requisicoes.adicionar(pedido);

        verificar("Departamento preserva identidade",
                contexto.departamentos.buscarPorId(contexto.departamento.getId())
                        .orElseThrow() == contexto.departamento);

        verificar("Recurso preserva identidade",
                contexto.recursos.buscarPorId(contexto.recurso.getId())
                        .orElseThrow() == contexto.recurso);

        verificar("Requisicao preserva identidade",
                contexto.requisicoes.buscarPorId(pedido.getId())
                        .orElseThrow() == pedido);

        verificar("Consultas inexistentes retornam ausencia",
                contexto.departamentos.buscarPorId(UUID.randomUUID()).isEmpty()
                        && contexto.recursos.buscarPorId(UUID.randomUUID()).isEmpty()
                        && contexto.requisicoes.buscarPorId(UUID.randomUUID()).isEmpty());

        Departamento outro = departamento(UUID.randomUUID());
        contexto.departamentos.adicionar(outro);
        Requisicao outroPedido = criarPedido(outro, contexto.recurso);
        contexto.requisicoes.adicionar(outroPedido);

        List<Requisicao> lista = contexto.requisicoes
                .listarPorDepartamento(contexto.departamento.getId());

        verificar("Listagem distingue departamentos",
                lista.size() == 1 && lista.get(0) == pedido
                        && contexto.requisicoes.listarPorDepartamento(outro.getId())
                        .equals(List.of(outroPedido)));

        verificar("Departamento inexistente retorna lista vazia",
                contexto.requisicoes.listarPorDepartamento(UUID.randomUUID())
                        .isEmpty());

        esperarExcecao("Lista rejeita inclusao", UnsupportedOperationException.class,
                () -> lista.add(outroPedido));

        esperarExcecao("Lista rejeita remocao", UnsupportedOperationException.class,
                () -> lista.remove(0));

        esperarExcecao("Lista rejeita substituicao", UnsupportedOperationException.class,
                () -> lista.set(0, outroPedido));

        Requisicao novo = criarPedido(contexto.departamento, contexto.recurso);
        contexto.requisicoes.adicionar(novo);

        verificar("Lista anterior preserva seus elementos",
                lista.size() == 1
                        && contexto.requisicoes
                        .listarPorDepartamento(contexto.departamento.getId()).size() == 2);

        verificar("Cadastro e consulta preservam estado, total e saldo",
                "INICIAL".equals(pedido.getStatus())
                        && pedido.getMotivoRevisao() == null
                        && pedido.getCustoTotalProjetado()
                        .compareTo(new BigDecimal("100.00")) == 0
                        && contexto.departamento.getSaldoDisponivel()
                        .compareTo(new BigDecimal("1000.00")) == 0);

        esperarArgumento("Departamento duplicado e rejeitado",
                () -> contexto.departamentos.adicionar(
                        departamento(contexto.departamento.getId())));

        esperarArgumento("Recurso duplicado e rejeitado",
                () -> contexto.recursos.adicionar(
                        recurso(contexto.recurso.getId())));

        esperarArgumento("Mesma instancia de departamento e rejeitada",
                () -> contexto.departamentos.adicionar(contexto.departamento));

        esperarArgumento("Mesma instancia de recurso e rejeitada",
                () -> contexto.recursos.adicionar(contexto.recurso));

        esperarArgumento("Requisicao duplicada e rejeitada",
                () -> contexto.requisicoes.adicionar(pedido));

        verificar("Duplicatas preservam registros anteriores",
                contexto.departamentos.buscarPorId(contexto.departamento.getId())
                        .orElseThrow() == contexto.departamento
                        && contexto.recursos.buscarPorId(contexto.recurso.getId())
                        .orElseThrow() == contexto.recurso
                        && contexto.requisicoes.buscarPorId(pedido.getId())
                        .orElseThrow() == pedido);
    }

    private static void verificarEntradasInvalidas() {
        Contexto contexto = new Contexto();

        esperarArgumento("Departamento nulo",
                () -> contexto.departamentos.adicionar(null));
        esperarArgumento("Recurso nulo",
                () -> contexto.recursos.adicionar(null));
        esperarArgumento("Requisicao nula",
                () -> contexto.requisicoes.adicionar(null));

        esperarArgumento("Departamento com UUID nulo",
                () -> contexto.departamentos.adicionar(departamento(null)));
        esperarArgumento("Recurso com UUID nulo",
                () -> contexto.recursos.adicionar(recurso(null)));

        esperarArgumento("Consulta de departamento com UUID nulo",
                () -> contexto.departamentos.buscarPorId(null));
        esperarArgumento("Consulta de recurso com UUID nulo",
                () -> contexto.recursos.buscarPorId(null));
        esperarArgumento("Consulta de requisicao com UUID nulo",
                () -> contexto.requisicoes.buscarPorId(null));
        esperarArgumento("Listagem com UUID nulo",
                () -> contexto.requisicoes.listarPorDepartamento(null));

        esperarArgumento("Construtor rejeita repositorio de departamentos nulo",
                () -> new RequisicaoRepositoryMemoria(null, contexto.recursos));
        esperarArgumento("Construtor rejeita repositorio de recursos nulo",
                () -> new RequisicaoRepositoryMemoria(contexto.departamentos, null));
    }

    private static void verificarVinculos() {
        Contexto contexto = new Contexto();

        rejeitarCadastro(contexto, "Departamento nao cadastrado",
                criarPedido(departamento(UUID.randomUUID()), contexto.recurso));

        rejeitarCadastro(contexto, "Recurso nao cadastrado",
                criarPedido(contexto.departamento, recurso(UUID.randomUUID())));

        rejeitarCadastro(contexto, "Outra instancia do mesmo departamento",
                criarPedido(departamento(contexto.departamento.getId()),
                        contexto.recurso));

        rejeitarCadastro(contexto, "Outra instancia do mesmo recurso",
                criarPedido(contexto.departamento,
                        recurso(contexto.recurso.getId())));

        rejeitarCadastro(contexto, "Requisicao vazia",
                new Requisicao(contexto.departamento));

        Requisicao liberada = criarPedido(contexto.departamento, contexto.recurso);
        liberada.processarRoteamento();
        rejeitarCadastro(contexto, "Requisicao liberada", liberada);

        Requisicao revisao = new Requisicao(contexto.departamento);
        revisao.adicionarItem(new ItemRequisicao(contexto.recurso, 9));
        revisao.processarRoteamento();
        rejeitarCadastro(contexto, "Requisicao em revisao", revisao);
    }

    private static void verificarReferenciasEContextos() {
        Contexto contexto = new Contexto();
        Requisicao pedido = criarPedido(contexto.departamento, contexto.recurso);
        contexto.requisicoes.adicionar(pedido);

        pedido.adicionarItem(new ItemRequisicao(contexto.recurso, 2));

        verificar("Alteracao permitida fica visivel na consulta",
                contexto.requisicoes.buscarPorId(pedido.getId()).orElseThrow()
                        .getCustoTotalProjetado()
                        .compareTo(new BigDecimal("300.00")) == 0);

        Contexto outro = new Contexto();

        verificar("Contextos independentes nao compartilham registros",
                outro.departamentos.buscarPorId(contexto.departamento.getId()).isEmpty()
                        && outro.recursos.buscarPorId(contexto.recurso.getId()).isEmpty()
                        && outro.requisicoes.buscarPorId(pedido.getId()).isEmpty()
                        && outro.requisicoes
                        .listarPorDepartamento(contexto.departamento.getId()).isEmpty());
    }

    private static void rejeitarCadastro(
            Contexto contexto, String descricao, Requisicao pedido
    ) {
        BigDecimal saldoAnterior = pedido.getDepartamento().getSaldoDisponivel();
        BigDecimal totalAnterior = pedido.getCustoTotalProjetado();
        String estadoAnterior = pedido.getStatus();
        List<ItemRequisicao> itensAnteriores = pedido.getItens();
        var motivoAnterior = pedido.getMotivoRevisao();

        esperarArgumento(descricao,
                () -> contexto.requisicoes.adicionar(pedido));

        verificar(descricao + ": sem inclusao ou alteracao",
                contexto.requisicoes.buscarPorId(pedido.getId()).isEmpty()
                        && saldoAnterior.equals(
                        pedido.getDepartamento().getSaldoDisponivel())
                        && totalAnterior.equals(pedido.getCustoTotalProjetado())
                        && estadoAnterior.equals(pedido.getStatus())
                        && itensAnteriores.equals(pedido.getItens())
                        && motivoAnterior == pedido.getMotivoRevisao());
    }

    private static Departamento departamento(UUID id) {
        return new Departamento(id, "Departamento", new BigDecimal("1000.00"));
    }

    private static RecursoCatalogo recurso(UUID id) {
        return new RecursoCatalogo(id, "Recurso", new BigDecimal("100.00"));
    }

    private static Requisicao criarPedido(
            Departamento departamento, RecursoCatalogo recurso
    ) {
        Requisicao pedido = new Requisicao(departamento);
        pedido.adicionarItem(new ItemRequisicao(recurso, 1));
        return pedido;
    }

    private static void verificar(String descricao, boolean condicao) {
        if (!condicao) {
            throw new IllegalStateException("FALHA: " + descricao);
        }
        verificacoes++;
        System.out.println("PASSOU: " + descricao);
    }

    private static void esperarArgumento(String descricao, Runnable acao) {
        esperarExcecao(descricao, IllegalArgumentException.class, acao);
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

            if (tipo == IllegalArgumentException.class
                    && (excecao.getMessage() == null
                    || excecao.getMessage().isBlank())) {
                throw new IllegalStateException(
                        "FALHA: " + descricao + " — mensagem ausente.",
                        excecao
                );
            }

            verificacoes++;
            System.out.println("PASSOU: " + descricao);
            return;
        }

        throw new IllegalStateException(
                "FALHA: " + descricao + " — excecao esperada nao ocorreu."
        );
    }

    private static class Contexto {
        final DepartamentoRepositoryMemoria departamentos =
                new DepartamentoRepositoryMemoria();
        final RecursoCatalogoRepositoryMemoria recursos =
                new RecursoCatalogoRepositoryMemoria();
        final RequisicaoRepositoryMemoria requisicoes =
                new RequisicaoRepositoryMemoria(departamentos, recursos);

        final Departamento departamento = departamento(UUID.randomUUID());
        final RecursoCatalogo recurso = recurso(UUID.randomUUID());

        Contexto() {
            departamentos.adicionar(departamento);
            recursos.adicionar(recurso);
        }
    }
}