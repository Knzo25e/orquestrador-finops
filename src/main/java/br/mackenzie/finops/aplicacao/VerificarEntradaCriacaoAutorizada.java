package br.mackenzie.finops.aplicacao;

import br.mackenzie.finops.aplicacao.entradas.ItemSolicitado;
import br.mackenzie.finops.aplicacao.entradas.SolicitacaoCriacaoRequisicao;
import br.mackenzie.finops.aplicacao.excecoes.AcessoNegadoException;
import br.mackenzie.finops.aplicacao.portas.CriacaoRequisicaoAutorizadaPersistente;
import br.mackenzie.finops.aplicacao.portas.ProvedorIdentidade;
import br.mackenzie.finops.aplicacao.resultados.ResultadoCriacaoRequisicao;
import br.mackenzie.finops.aplicacao.seguranca.ContextoIdentidade;
import br.mackenzie.finops.aplicacao.seguranca.PerfilUsuario;
import br.mackenzie.finops.dominio.entidades.Departamento;
import br.mackenzie.finops.dominio.entidades.ItemRequisicao;
import br.mackenzie.finops.dominio.entidades.RecursoCatalogo;
import br.mackenzie.finops.dominio.entidades.Requisicao;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class VerificarEntradaCriacaoAutorizada {

    private static int verificacoes;

    public static void main(String[] args) {
        UUID usuarioId = UUID.randomUUID();
        UUID departamentoId = UUID.randomUUID();
        UUID pedidoId = UUID.randomUUID();
        UUID recursoId = UUID.randomUUID();

        List<ItemSolicitado> itens = new ArrayList<>(
                List.of(new ItemSolicitado(recursoId, 2))
        );

        CriacaoProibida proibida = new CriacaoProibida();

        negar(
                Optional::empty, proibida, pedidoId, itens,
                "Identidade ausente bloqueia criacao"
        );

        negar(
                identidade(
                        usuarioId, departamentoId,
                        PerfilUsuario.DESENVOLVEDOR, false
                ),
                proibida, pedidoId, itens,
                "Desenvolvedor inativo bloqueia criacao"
        );

        negar(
                identidade(
                        usuarioId, null,
                        PerfilUsuario.DESENVOLVEDOR, true
                ),
                proibida, pedidoId, itens,
                "Desenvolvedor sem vinculo bloqueia criacao"
        );

        negar(
                identidade(
                        usuarioId, departamentoId,
                        PerfilUsuario.ARQUITETO_CLOUD, true
                ),
                proibida, pedidoId, itens,
                "Arquiteto ativo nao cria requisicao"
        );

        negar(
                identidade(
                        usuarioId, departamentoId,
                        PerfilUsuario.ARQUITETO_CLOUD, false
                ),
                proibida, pedidoId, itens,
                "Arquiteto inativo nao cria requisicao"
        );

        rejeitar(
                IllegalStateException.class,
                () -> new CriarRequisicaoAutorizadaUseCase(
                        () -> null, proibida
                ).executar(pedidoId, itens),
                "Optional nulo do provedor interrompe criacao"
        );

        IllegalStateException falhaProvedor =
                new IllegalStateException("Falha controlada do provedor.");

        try {
            new CriarRequisicaoAutorizadaUseCase(
                    () -> {
                        throw falhaProvedor;
                    },
                    proibida
            ).executar(pedidoId, itens);

            throw new AssertionError("A falha do provedor deveria ser propagada.");
        } catch (IllegalStateException falha) {
            conferir(
                    falha == falhaProvedor,
                    "Falha do provedor e propagada sem persistencia"
            );
        }

        ProvedorIdentidade desenvolvedor = identidade(
                usuarioId, departamentoId,
                PerfilUsuario.DESENVOLVEDOR, true
        );

        var entradaBloqueada = new CriarRequisicaoAutorizadaUseCase(
                desenvolvedor, proibida
        );

        rejeitar(
                IllegalArgumentException.class,
                () -> entradaBloqueada.executar(null, itens),
                "UUID nulo e rejeitado antes da persistencia"
        );

        rejeitar(
                IllegalArgumentException.class,
                () -> entradaBloqueada.executar(pedidoId, null),
                "Lista nula e rejeitada antes da persistencia"
        );

        rejeitar(
                IllegalArgumentException.class,
                () -> entradaBloqueada.executar(pedidoId, List.of()),
                "Lista vazia e rejeitada antes da persistencia"
        );

        conferir(
                proibida.chamadas == 0,
                "Nenhuma entrada bloqueada chamou a persistencia"
        );

        ContextoIdentidade contexto = desenvolvedor
                .obterIdentidadeAtual().orElseThrow();

        Departamento departamento = Departamento.reconstituir(
                departamentoId,
                "Departamento do teste",
                new BigDecimal("1000.00"),
                new BigDecimal("800.00")
        );

        Requisicao pedido = Requisicao.criarComId(
                pedidoId, departamento, usuarioId
        );
        pedido.adicionarItem(new ItemRequisicao(
                new RecursoCatalogo(
                        recursoId,
                        "Recurso do teste",
                        new BigDecimal("12.50")
                ),
                2
        ));

        ResultadoCriacaoRequisicao esperado =
                ResultadoCriacaoRequisicao.criada(pedido);

        CapturaCriacao captura = new CapturaCriacao(esperado);
        int[] consultasIdentidade = {0};

        var entrada = new CriarRequisicaoAutorizadaUseCase(
                () -> {
                    consultasIdentidade[0]++;
                    return Optional.of(contexto);
                },
                captura
        );

        ResultadoCriacaoRequisicao recebido =
                entrada.executar(pedidoId, itens);

        conferir(
                consultasIdentidade[0] == 1
                        && captura.chamadas == 1
                        && captura.identidade == contexto,
                "Uma identidade obtida e encaminhada a operacao transacional"
        );

        conferir(
                pedidoId.equals(captura.solicitacao.requisicaoId())
                        && usuarioId.equals(
                                captura.solicitacao.desenvolvedorId()
                        )
                        && departamentoId.equals(
                                captura.solicitacao.departamentoId()
                        ),
                "Autor e departamento sao derivados da identidade"
        );

        conferir(
                captura.solicitacao.itens().size() == 1
                        && recursoId.equals(
                                captura.solicitacao.itens().get(0).recursoId()
                        )
                        && captura.solicitacao.itens().get(0).quantidade() == 2,
                "UUID do recurso e quantidade sao encaminhados"
        );

        itens.clear();

        conferir(
                captura.solicitacao.itens().size() == 1,
                "Alterar lista original nao altera solicitacao encaminhada"
        );

        conferir(
                recebido == esperado
                        && departamento.getSaldoDisponivel()
                                .compareTo(new BigDecimal("800.00")) == 0,
                "Entrada preserva resultado transacional e saldo"
        );

        System.out.println(
                "Todas as " + verificacoes + " verificacoes passaram."
        );
    }

    private static ProvedorIdentidade identidade(
            UUID usuario,
            UUID departamento,
            PerfilUsuario perfil,
            boolean ativo
    ) {
        ContextoIdentidade contexto = new ContextoIdentidade(
                usuario, perfil, ativo, departamento
        );
        return () -> Optional.of(contexto);
    }

    private static void negar(
            ProvedorIdentidade identidades,
            CriacaoRequisicaoAutorizadaPersistente criacao,
            UUID pedidoId,
            List<ItemSolicitado> itens,
            String descricao
    ) {
        rejeitar(
                AcessoNegadoException.class,
                () -> new CriarRequisicaoAutorizadaUseCase(
                        identidades, criacao
                ).executar(pedidoId, itens),
                descricao
        );
    }

    private static void rejeitar(
            Class<? extends RuntimeException> tipo,
            Runnable operacao,
            String descricao
    ) {
        try {
            operacao.run();
        } catch (RuntimeException falha) {
            if (!tipo.isInstance(falha)) {
                throw new IllegalStateException(
                        "FALHOU: excecao inesperada em " + descricao,
                        falha
                );
            }

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

    private static final class CriacaoProibida
            implements CriacaoRequisicaoAutorizadaPersistente {

        private int chamadas;

        @Override
        public ResultadoCriacaoRequisicao executar(
                SolicitacaoCriacaoRequisicao solicitacao,
                ContextoIdentidade identidade
        ) {
            chamadas++;
            throw new AssertionError(
                    "Uma entrada bloqueada chamou a persistencia."
            );
        }
    }

    private static final class CapturaCriacao
            implements CriacaoRequisicaoAutorizadaPersistente {

        private final ResultadoCriacaoRequisicao resultado;
        private SolicitacaoCriacaoRequisicao solicitacao;
        private ContextoIdentidade identidade;
        private int chamadas;

        private CapturaCriacao(ResultadoCriacaoRequisicao resultado) {
            this.resultado = resultado;
        }

        @Override
        public ResultadoCriacaoRequisicao executar(
                SolicitacaoCriacaoRequisicao solicitacao,
                ContextoIdentidade identidade
        ) {
            chamadas++;
            this.solicitacao = solicitacao;
            this.identidade = identidade;
            return resultado;
        }
    }
}