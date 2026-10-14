package br.mackenzie.finops.aplicacao;

import br.mackenzie.finops.aplicacao.entradas.ItemSolicitado;
import br.mackenzie.finops.aplicacao.portas.RecursoCatalogoRepository;
import br.mackenzie.finops.dominio.entidades.Departamento;
import br.mackenzie.finops.dominio.entidades.ItemRequisicao;
import br.mackenzie.finops.dominio.entidades.RecursoCatalogo;
import br.mackenzie.finops.dominio.entidades.Requisicao;
import br.mackenzie.finops.dominio.entidades.ResultadoValidacao;
import br.mackenzie.finops.infraestrutura.repositorios.memoria.DepartamentoRepositoryMemoria;
import br.mackenzie.finops.infraestrutura.repositorios.memoria.RecursoCatalogoRepositoryMemoria;
import br.mackenzie.finops.infraestrutura.repositorios.memoria.RequisicaoRepositoryMemoria;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class VerificarIntegracaoRepositorios {

    private static int verificacoes;

    public static void main(String[] args) {
        System.out.println("--- Integracao em memoria / Issue #53 ---");

        verificarCriacao();
        verificarEntradasInvalidas();
        verificarRoteamento();
        verificarVinculoPosterior();
        verificarFalhaDeConsulta();

        System.out.println(
                "Todas as " + verificacoes + " verificacoes passaram."
        );
    }

    private static void verificarCriacao() {
        Contexto c = new Contexto();

        Requisicao pedido = c.criar.executar(
                c.departamento.getId(),
                List.of(
                        new ItemSolicitado(c.recurso.getId(), 2),
                        new ItemSolicitado(c.recurso.getId(), 1)
                )
        );

        verificar("Criacao cadastra a mesma instancia",
                c.requisicoes.buscarPorId(pedido.getId()).orElseThrow()
                        == pedido);

        verificar("Criacao preserva departamento e recursos cadastrados",
                pedido.getDepartamento() == c.departamento
                        && pedido.getItens().size() == 2
                        && pedido.getItens().stream().allMatch(
                        item -> item.getRecurso() == c.recurso));

        verificar("Recursos repetidos permanecem em itens separados",
                pedido.getItens().get(0).getQuantidade() == 2
                        && pedido.getItens().get(1).getQuantidade() == 1
                        && pedido.getItens().get(0).getCustoSubtotal()
                        .compareTo(new BigDecimal("200.00")) == 0
                        && pedido.getItens().get(1).getCustoSubtotal()
                        .compareTo(new BigDecimal("100.00")) == 0);

        verificar("Criacao calcula total e preserva estado e saldo",
                pedido.getCustoTotalProjetado()
                        .compareTo(new BigDecimal("300.00")) == 0
                        && "INICIAL".equals(pedido.getStatus())
                        && pedido.getMotivoRevisao() == null
                        && saldoIgual(c, "1000.00"));

        Requisicao outro = c.criar(3);

        verificar("Chamadas distintas criam pedidos distintos",
                !pedido.getId().equals(outro.getId())
                        && c.requisicoes.listarPorDepartamento(
                        c.departamento.getId()).size() == 2);
    }

    private static void verificarEntradasInvalidas() {
        Contexto c = new Contexto();

        esperarArgumento("Entrada rejeita recurso nulo",
                () -> new ItemSolicitado(null, 1));
        esperarArgumento("Entrada rejeita quantidade nula",
                () -> new ItemSolicitado(c.recurso.getId(), null));
        esperarArgumento("Entrada rejeita quantidade zero",
                () -> new ItemSolicitado(c.recurso.getId(), 0));
        esperarArgumento("Entrada rejeita quantidade negativa",
                () -> new ItemSolicitado(c.recurso.getId(), -1));

        esperarArgumento("Criacao rejeita departamento nulo",
                () -> c.criar.executar(null, c.entradas(1)));
        esperarArgumento("Criacao rejeita lista nula",
                () -> c.criar.executar(c.departamento.getId(), null));
        esperarArgumento("Criacao rejeita lista vazia",
                () -> c.criar.executar(
                        c.departamento.getId(), List.of()));
        esperarArgumento("Criacao rejeita item nulo",
                () -> c.criar.executar(
                        c.departamento.getId(),
                        Arrays.asList(
                                new ItemSolicitado(c.recurso.getId(), 1),
                                null
                        )
                ));

        esperarArgumento("Criacao rejeita departamento inexistente",
                () -> c.criar.executar(UUID.randomUUID(), c.entradas(1)));

        esperarArgumento("Recurso inexistente apos item valido impede cadastro",
                () -> c.criar.executar(
                        c.departamento.getId(),
                        List.of(
                                new ItemSolicitado(c.recurso.getId(), 1),
                                new ItemSolicitado(UUID.randomUUID(), 1)
                        )
                ));

        esperarArgumento("Validacao rejeita identificador nulo",
                () -> c.validar.executar(null));
        esperarArgumento("Validacao rejeita requisicao inexistente",
                () -> c.validar.executar(UUID.randomUUID()));

        esperarArgumento("Criacao rejeita repositorio de departamentos nulo",
                () -> new CriarRequisicaoArmazenadaUseCase(
                        null, c.recursos, c.requisicoes));
        esperarArgumento("Criacao rejeita repositorio de recursos nulo",
                () -> new CriarRequisicaoArmazenadaUseCase(
                        c.departamentos, null, c.requisicoes));
        esperarArgumento("Criacao rejeita repositorio de requisicoes nulo",
                () -> new CriarRequisicaoArmazenadaUseCase(
                        c.departamentos, c.recursos, null));

        esperarArgumento("Validacao rejeita repositorio de departamentos nulo",
                () -> new ValidarRequisicaoArmazenadaUseCase(
                        null, c.recursos, c.requisicoes));
        esperarArgumento("Validacao rejeita repositorio de recursos nulo",
                () -> new ValidarRequisicaoArmazenadaUseCase(
                        c.departamentos, null, c.requisicoes));
        esperarArgumento("Validacao rejeita repositorio de requisicoes nulo",
                () -> new ValidarRequisicaoArmazenadaUseCase(
                        c.departamentos, c.recursos, null));

        verificar("Rejeicoes preservam cadastro vazio e saldo",
                c.requisicoes.listarPorDepartamento(
                        c.departamento.getId()).isEmpty()
                        && saldoIgual(c, "1000.00"));
    }

    private static void verificarRoteamento() {
        Contexto liberacao = new Contexto();
        Requisicao pedido = liberacao.criar(2);

        verificar("Validacao integrada libera pedido",
                liberacao.validar.executar(pedido.getId())
                        == ResultadoValidacao.LIBERADA);

        Requisicao consultado = liberacao.requisicoes
                .buscarPorId(pedido.getId()).orElseThrow();

        verificar("Consulta observa liberacao e saldo comprometido",
                "Provisionamento Liberado".equals(consultado.getStatus())
                        && consultado.getMotivoRevisao() == null
                        && saldoIgual(liberacao, "800.00"));

        verificar("Repeticao reconhece liberacao sem novo desconto",
                liberacao.validar.executar(pedido.getId())
                        == ResultadoValidacao.JA_LIBERADA
                        && saldoIgual(liberacao, "800.00"));

        Contexto reserva = new Contexto();
        Requisicao pedidoReserva = reserva.criar(9);

        verificar("Saldo projetado na reserva encaminha para revisao",
                reserva.validar.executar(pedidoReserva.getId())
                        == ResultadoValidacao.REVISAO_POR_RESERVA);

        verificar("Revisao por reserva preserva saldo e registra motivo",
                "Revisão Pendente".equals(pedidoReserva.getStatus())
                        && pedidoReserva.getMotivoRevisao()
                        == ResultadoValidacao.REVISAO_POR_RESERVA
                        && saldoIgual(reserva, "1000.00"));

        verificar("Pedido em revisao nao retorna ao fluxo automatico",
                reserva.validar.executar(pedidoReserva.getId())
                        == ResultadoValidacao.ESTADO_NAO_PERMITIDO
                        && "Revisão Pendente".equals(pedidoReserva.getStatus())
                        && pedidoReserva.getMotivoRevisao()
                        == ResultadoValidacao.REVISAO_POR_RESERVA
                        && saldoIgual(reserva, "1000.00"));

        Contexto insuficiencia = new Contexto();
        Requisicao pedidoInsuficiente = insuficiencia.criar(11);

        verificar("Custo superior ao saldo encaminha por insuficiencia",
                insuficiencia.validar.executar(pedidoInsuficiente.getId())
                        == ResultadoValidacao.REVISAO_POR_SALDO_INSUFICIENTE);

        verificar("Revisao por insuficiencia preserva saldo e motivo",
                "Revisão Pendente".equals(pedidoInsuficiente.getStatus())
                        && pedidoInsuficiente.getMotivoRevisao()
                        == ResultadoValidacao.REVISAO_POR_SALDO_INSUFICIENTE
                        && saldoIgual(insuficiencia, "1000.00"));
    }

    private static void verificarVinculoPosterior() {
        Contexto c = new Contexto();
        Requisicao pedido = c.criar(1);

        RecursoCatalogo naoCadastrado = new RecursoCatalogo(
                UUID.randomUUID(), "Externo", new BigDecimal("50.00")
        );

        pedido.adicionarItem(new ItemRequisicao(naoCadastrado, 1));

        esperarArgumento("Recurso acrescentado sem cadastro bloqueia validacao",
                () -> c.validar.executar(pedido.getId()));

        verificar("Vinculo invalido preserva pedido e saldo",
                "INICIAL".equals(pedido.getStatus())
                        && pedido.getMotivoRevisao() == null
                        && pedido.getItens().size() == 2
                        && pedido.getCustoTotalProjetado()
                        .compareTo(new BigDecimal("150.00")) == 0
                        && saldoIgual(c, "1000.00"));

        Contexto outro = new Contexto();
        Requisicao outroPedido = outro.criar(1);

        RecursoCatalogo outraInstancia = new RecursoCatalogo(
                outro.recurso.getId(), "Outra instancia",
                new BigDecimal("100.00")
        );

        outroPedido.adicionarItem(new ItemRequisicao(outraInstancia, 1));

        esperarArgumento("Mesmo UUID com outra instancia bloqueia validacao",
                () -> outro.validar.executar(outroPedido.getId()));

        verificar("Instancia divergente preserva estado e saldo",
                "INICIAL".equals(outroPedido.getStatus())
                        && outroPedido.getMotivoRevisao() == null
                        && outroPedido.getCustoTotalProjetado()
                        .compareTo(new BigDecimal("200.00")) == 0
                        && saldoIgual(outro, "1000.00"));
    }

    private static void verificarFalhaDeConsulta() {
        Contexto c = new Contexto();
        Requisicao pedido = c.criar(2);

        IllegalStateException falha =
                new IllegalStateException("Falha controlada na consulta.");

        RecursoCatalogoRepository recursosComFalha =
                new RecursoCatalogoRepository() {
                    @Override
                    public void adicionar(RecursoCatalogo recurso) {
                        throw new UnsupportedOperationException(
                                "Cadastro indisponivel neste dublê."
                        );
                    }

                    @Override
                    public Optional<RecursoCatalogo> buscarPorId(UUID id) {
                        throw falha;
                    }
                };

        ValidarRequisicaoArmazenadaUseCase validarComFalha =
                new ValidarRequisicaoArmazenadaUseCase(
                        c.departamentos, recursosComFalha, c.requisicoes
                );

        boolean propagouMesmaFalha = false;

        try {
            validarComFalha.executar(pedido.getId());
        } catch (IllegalStateException excecao) {
            propagouMesmaFalha = excecao == falha;
        }

        verificar("Falha de consulta e propagada",
                propagouMesmaFalha);

        Requisicao consultado = c.requisicoes
                .buscarPorId(pedido.getId()).orElseThrow();

        verificar("Falha anterior a mutacao preserva registros",
                consultado == pedido
                        && "INICIAL".equals(consultado.getStatus())
                        && consultado.getMotivoRevisao() == null
                        && consultado.getCustoTotalProjetado()
                        .compareTo(new BigDecimal("200.00")) == 0
                        && saldoIgual(c, "1000.00"));

        verificar("Validacao normal funciona apos falha anterior a mutacao",
                c.validar.executar(pedido.getId())
                        == ResultadoValidacao.LIBERADA
                        && saldoIgual(c, "800.00"));
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

    private static void esperarArgumento(String descricao, Runnable acao) {
        try {
            acao.run();
        } catch (IllegalArgumentException excecao) {
            verificar(descricao,
                    excecao.getMessage() != null
                            && !excecao.getMessage().isBlank());
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

        final Departamento departamento = new Departamento(
                UUID.randomUUID(), "Departamento",
                new BigDecimal("1000.00")
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
        }

        List<ItemSolicitado> entradas(int quantidade) {
            return List.of(new ItemSolicitado(recurso.getId(), quantidade));
        }

        Requisicao criar(int quantidade) {
            return criar.executar(departamento.getId(), entradas(quantidade));
        }
    }
}