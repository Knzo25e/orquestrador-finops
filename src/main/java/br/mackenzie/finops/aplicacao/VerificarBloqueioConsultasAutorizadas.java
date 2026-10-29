package br.mackenzie.finops.aplicacao;

import br.mackenzie.finops.aplicacao.excecoes.AcessoNegadoException;
import br.mackenzie.finops.aplicacao.portas.ConsultaRequisicaoPersistente;
import br.mackenzie.finops.aplicacao.portas.ProvedorIdentidade;
import br.mackenzie.finops.aplicacao.seguranca.ContextoIdentidade;
import br.mackenzie.finops.aplicacao.seguranca.PerfilUsuario;
import br.mackenzie.finops.dominio.entidades.Requisicao;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class VerificarBloqueioConsultasAutorizadas {

    private static int verificacoes;

    public static void main(String[] args) {
        UUID usuarioId = UUID.randomUUID();
        UUID departamentoId = UUID.randomUUID();
        UUID outroDepartamentoId = UUID.randomUUID();
        UUID pedidoId = UUID.randomUUID();

        ConsultaProibida armazenamento = new ConsultaProibida();

        verificarIdentidadeNegada(
                Optional::empty,
                armazenamento,
                pedidoId,
                departamentoId,
                "Identidade ausente"
        );

        verificarIdentidadeNegada(
                () -> Optional.of(new ContextoIdentidade(
                        usuarioId,
                        PerfilUsuario.DESENVOLVEDOR,
                        false,
                        departamentoId
                )),
                armazenamento,
                pedidoId,
                departamentoId,
                "Desenvolvedor inativo"
        );

        verificarIdentidadeNegada(
                () -> Optional.of(new ContextoIdentidade(
                        usuarioId,
                        PerfilUsuario.ARQUITETO_CLOUD,
                        false,
                        null
                )),
                armazenamento,
                pedidoId,
                departamentoId,
                "Arquiteto inativo"
        );

        verificarIdentidadeNegada(
                () -> Optional.of(new ContextoIdentidade(
                        usuarioId,
                        PerfilUsuario.DESENVOLVEDOR,
                        true,
                        null
                )),
                armazenamento,
                pedidoId,
                departamentoId,
                "Desenvolvedor sem vinculo"
        );

        ProvedorIdentidade desenvolvedor = () -> Optional.of(
                new ContextoIdentidade(
                        usuarioId,
                        PerfilUsuario.DESENVOLVEDOR,
                        true,
                        departamentoId
                )
        );

        esperar(
                AcessoNegadoException.class,
                () -> new ListarRequisicoesAutorizadasUseCase(
                        desenvolvedor, armazenamento
                ).executar(outroDepartamentoId),
                "Listagem de outro departamento e negada antes da consulta"
        );

        esperar(
                IllegalArgumentException.class,
                () -> new ConsultarRequisicaoAutorizadaUseCase(
                        desenvolvedor, armazenamento
                ).executar(null),
                "Consulta rejeita UUID nulo antes do armazenamento"
        );

        esperar(
                IllegalArgumentException.class,
                () -> new ListarRequisicoesAutorizadasUseCase(
                        desenvolvedor, armazenamento
                ).executar(null),
                "Listagem rejeita departamento nulo antes do armazenamento"
        );

        ProvedorIdentidade retornoInvalido = () -> null;

        esperar(
                IllegalStateException.class,
                () -> new ConsultarRequisicaoAutorizadaUseCase(
                        retornoInvalido, armazenamento
                ).executar(pedidoId),
                "Consulta interrompe quando provedor retorna null"
        );

        esperar(
                IllegalStateException.class,
                () -> new ListarRequisicoesAutorizadasUseCase(
                        retornoInvalido, armazenamento
                ).executar(departamentoId),
                "Listagem interrompe quando provedor retorna null"
        );

        RuntimeException falhaOriginal = new IllegalStateException(
                "Falha controlada do provedor"
        );

        ProvedorIdentidade provedorComFalha = () -> {
            throw falhaOriginal;
        };

        esperarMesmaFalha(
                falhaOriginal,
                () -> new ConsultarRequisicaoAutorizadaUseCase(
                        provedorComFalha, armazenamento
                ).executar(pedidoId),
                "Consulta propaga falha do provedor sem acessar armazenamento"
        );

        esperarMesmaFalha(
                falhaOriginal,
                () -> new ListarRequisicoesAutorizadasUseCase(
                        provedorComFalha, armazenamento
                ).executar(departamentoId),
                "Listagem propaga falha do provedor sem acessar armazenamento"
        );

        conferir(
                armazenamento.chamadas == 0,
                "Nenhuma tentativa bloqueada acessou o armazenamento"
        );

        System.out.println(
                "Todas as " + verificacoes + " verificacoes passaram."
        );
    }

    private static void verificarIdentidadeNegada(
            ProvedorIdentidade provedor,
            ConsultaRequisicaoPersistente armazenamento,
            UUID pedidoId,
            UUID departamentoId,
            String descricao
    ) {
        esperar(
                AcessoNegadoException.class,
                () -> new ConsultarRequisicaoAutorizadaUseCase(
                        provedor, armazenamento
                ).executar(pedidoId),
                descricao + ": consulta negada"
        );

        esperar(
                AcessoNegadoException.class,
                () -> new ListarRequisicoesAutorizadasUseCase(
                        provedor, armazenamento
                ).executar(departamentoId),
                descricao + ": listagem negada"
        );
    }

    private static void esperar(
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

        throw new IllegalStateException(
                "FALHOU: operacao permitida em " + descricao
        );
    }

    private static void esperarMesmaFalha(
            RuntimeException esperada,
            Runnable operacao,
            String descricao
    ) {
        try {
            operacao.run();
        } catch (RuntimeException encontrada) {
            conferir(encontrada == esperada, descricao);
            return;
        }

        throw new IllegalStateException(
                "FALHOU: falha nao propagada em " + descricao
        );
    }

    private static void conferir(boolean condicao, String descricao) {
        if (!condicao) {
            throw new IllegalStateException("FALHOU: " + descricao);
        }

        verificacoes++;
        System.out.println("PASSOU: " + descricao);
    }

    private static final class ConsultaProibida
            implements ConsultaRequisicaoPersistente {

        private int chamadas;

        private void registrarAcessoIndevido() {
            chamadas++;
            throw new AssertionError(
                    "O armazenamento nao deveria ter sido consultado."
            );
        }

        @Override
        public Optional<Requisicao> buscarPorIdEAutorEDepartamento(
                UUID requisicaoId,
                UUID autorId,
                UUID departamentoId
        ) {
            registrarAcessoIndevido();
            return Optional.empty();
        }

        @Override
        public List<Requisicao> listarPorAutorEDepartamento(
                UUID autorId,
                UUID departamentoId
        ) {
            registrarAcessoIndevido();
            return List.of();
        }

        @Override
        public Optional<Requisicao> buscarPorId(UUID requisicaoId) {
            registrarAcessoIndevido();
            return Optional.empty();
        }

        @Override
        public List<Requisicao> listarPorDepartamento(UUID departamentoId) {
            registrarAcessoIndevido();
            return List.of();
        }
    }
}