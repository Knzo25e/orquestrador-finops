package br.mackenzie.finops.aplicacao;

import br.mackenzie.finops.aplicacao.excecoes.AcessoNegadoException;
import br.mackenzie.finops.aplicacao.portas.ConsultaCatalogoPersistente;
import br.mackenzie.finops.aplicacao.portas.ProvedorIdentidade;
import br.mackenzie.finops.aplicacao.seguranca.ContextoIdentidade;
import br.mackenzie.finops.aplicacao.seguranca.PerfilUsuario;
import br.mackenzie.finops.dominio.entidades.RecursoCatalogo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class VerificarListagemCatalogoAutorizada {

    private static int verificacoes;

    public static void main(String[] args) {
        AtomicInteger acessosBloqueados = new AtomicInteger();

        ConsultaCatalogoPersistente armazenamentoBloqueado = () -> {
            acessosBloqueados.incrementAndGet();
            throw new AssertionError(
                    "Uma tentativa bloqueada acessou o armazenamento."
            );
        };

        esperar(
                AcessoNegadoException.class,
                () -> new ListarCatalogoAutorizadoUseCase(
                        Optional::empty,
                        armazenamentoBloqueado
                ).executar(),
                "Identidade ausente bloqueia a consulta"
        );

        for (PerfilUsuario perfil : List.of(
                PerfilUsuario.DESENVOLVEDOR,
                PerfilUsuario.ARQUITETO_CLOUD
        )) {
            ContextoIdentidade inativa = identidade(perfil, false, null);

            esperar(
                    AcessoNegadoException.class,
                    () -> entrada(inativa, armazenamentoBloqueado).executar(),
                    perfil + " inativo não acessa o catálogo"
            );
        }

        esperar(
                IllegalStateException.class,
                () -> new ListarCatalogoAutorizadoUseCase(
                        () -> null,
                        armazenamentoBloqueado
                ).executar(),
                "Optional nulo do provedor interrompe a consulta"
        );

        RuntimeException falhaProvedor =
                new IllegalStateException("Falha controlada do provedor.");

        esperarMesmaFalha(
                falhaProvedor,
                () -> new ListarCatalogoAutorizadoUseCase(
                        () -> {
                            throw falhaProvedor;
                        },
                        armazenamentoBloqueado
                ).executar(),
                "Falha do provedor é propagada sem substituição"
        );

        conferir(
                acessosBloqueados.get() == 0,
                "Nenhuma tentativa bloqueada acessou o armazenamento"
        );

        ContextoIdentidade desenvolvedor = identidade(
                PerfilUsuario.DESENVOLVEDOR,
                true,
                UUID.randomUUID()
        );

        conferir(
                entrada(desenvolvedor, List::of).executar().isEmpty(),
                "Desenvolvedor ativo consulta catálogo vazio"
        );

        for (PerfilUsuario perfil : List.of(
                PerfilUsuario.DESENVOLVEDOR,
                PerfilUsuario.ARQUITETO_CLOUD
        )) {
            conferir(
                    entrada(
                            identidade(perfil, true, null),
                            List::of
                    ).executar().isEmpty(),
                    perfil + " ativo consulta sem vínculo departamental"
            );
        }

        // Esses UUIDs distinguem ordenação textual de UUID.compareTo().
        RecursoCatalogo primeiro = recurso(
                "00000000-0000-0000-0000-000000000001",
                "Primeiro",
                "0.10"
        );

        RecursoCatalogo segundo = recurso(
                "80000000-0000-0000-0000-000000000001",
                "Segundo",
                "12.50"
        );

        ArrayList<RecursoCatalogo> origem =
                new ArrayList<>(List.of(primeiro, segundo));

        AtomicInteger leiturasIdentidade = new AtomicInteger();
        AtomicInteger leiturasCatalogo = new AtomicInteger();

        ProvedorIdentidade provedor = () -> {
            leiturasIdentidade.incrementAndGet();
            return Optional.of(desenvolvedor);
        };

        List<RecursoCatalogo> recebidos =
                new ListarCatalogoAutorizadoUseCase(
                        provedor,
                        () -> {
                            leiturasCatalogo.incrementAndGet();
                            return origem;
                        }
                ).executar();

        conferir(
                leiturasIdentidade.get() == 1
                        && leiturasCatalogo.get() == 1,
                "Uma leitura da identidade e uma consulta por execução"
        );

        conferir(
                recebidos.size() == 2
                        && primeiro.getId().equals(recebidos.get(0).getId())
                        && segundo.getId().equals(recebidos.get(1).getId()),
                "Ordem textual canônica dos UUIDs é preservada"
        );

        conferir(
                "Primeiro".equals(recebidos.get(0).getNome())
                        && "Segundo".equals(recebidos.get(1).getNome())
                        && recebidos.get(0).getCustoMensal()
                                .compareTo(new BigDecimal("0.10")) == 0
                        && recebidos.get(1).getCustoMensal()
                                .compareTo(new BigDecimal("12.50")) == 0,
                "Nomes e custos mensais são preservados"
        );

        esperar(
                UnsupportedOperationException.class,
                () -> recebidos.add(primeiro),
                "Lista devolvida impede inclusão"
        );

        esperar(
                UnsupportedOperationException.class,
                () -> recebidos.remove(0),
                "Lista devolvida impede remoção"
        );

        esperar(
                UnsupportedOperationException.class,
                () -> recebidos.set(0, segundo),
                "Lista devolvida impede substituição"
        );

        origem.clear();

        conferir(
                recebidos.size() == 2,
                "Alterar a lista do adaptador não altera a lista devolvida"
        );

        esperar(
                IllegalStateException.class,
                () -> entrada(desenvolvedor, () -> null).executar(),
                "Lista nula do adaptador é rejeitada"
        );

        esperar(
                IllegalStateException.class,
                () -> entrada(
                        desenvolvedor,
                        () -> Arrays.asList(primeiro, null)
                ).executar(),
                "Elemento nulo é rejeitado sem devolver lista parcial"
        );

        esperar(
                IllegalStateException.class,
                () -> entrada(
                        desenvolvedor,
                        () -> List.of(segundo, primeiro)
                ).executar(),
                "Ordem incorreta é rejeitada"
        );

        RecursoCatalogo duplicado = recurso(
                primeiro.getId().toString(),
                "Outro objeto com o mesmo UUID",
                "1.00"
        );

        esperar(
                IllegalStateException.class,
                () -> entrada(
                        desenvolvedor,
                        () -> List.of(primeiro, duplicado)
                ).executar(),
                "UUID duplicado em objetos distintos é rejeitado"
        );

        RuntimeException falhaConsulta =
                new IllegalStateException("Falha controlada da consulta.");

        esperarMesmaFalha(
                falhaConsulta,
                () -> entrada(
                        desenvolvedor,
                        () -> {
                            throw falhaConsulta;
                        }
                ).executar(),
                "Falha da consulta não é convertida em catálogo vazio"
        );

        esperar(
                IllegalArgumentException.class,
                () -> new ListarCatalogoAutorizadoUseCase(
                        null,
                        List::of
                ),
                "Construtor rejeita provedor nulo"
        );

        esperar(
                IllegalArgumentException.class,
                () -> new ListarCatalogoAutorizadoUseCase(
                        Optional::empty,
                        null
                ),
                "Construtor rejeita consulta nula"
        );

        System.out.println(
                "Todas as " + verificacoes + " verificacoes passaram."
        );
    }

    private static ListarCatalogoAutorizadoUseCase entrada(
            ContextoIdentidade identidade,
            ConsultaCatalogoPersistente consulta
    ) {
        return new ListarCatalogoAutorizadoUseCase(
                () -> Optional.of(identidade),
                consulta
        );
    }

    private static ContextoIdentidade identidade(
            PerfilUsuario perfil,
            boolean ativo,
            UUID departamentoId
    ) {
        return new ContextoIdentidade(
                UUID.randomUUID(),
                perfil,
                ativo,
                departamentoId
        );
    }

    private static RecursoCatalogo recurso(
            String id,
            String nome,
            String custo
    ) {
        return new RecursoCatalogo(
                UUID.fromString(id),
                nome,
                new BigDecimal(custo)
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
                        "FALHOU: " + descricao
                                + " — exceção inesperada.",
                        falha
                );
            }

            conferir(true, descricao);
            return;
        }

        throw new IllegalStateException(
                "FALHOU: " + descricao + " — exceção não lançada."
        );
    }

    private static void esperarMesmaFalha(
            RuntimeException esperada,
            Runnable operacao,
            String descricao
    ) {
        try {
            operacao.run();
        } catch (RuntimeException recebida) {
            if (recebida != esperada) {
                throw new IllegalStateException(
                        "FALHOU: " + descricao
                                + " — falha original substituída.",
                        recebida
                );
            }

            conferir(true, descricao);
            return;
        }

        throw new IllegalStateException(
                "FALHOU: " + descricao + " — exceção não lançada."
        );
    }

    private static void conferir(boolean condicao, String descricao) {
        if (!condicao) {
            throw new IllegalStateException("FALHOU: " + descricao);
        }

        verificacoes++;
        System.out.println("PASSOU: " + descricao);
    }
}