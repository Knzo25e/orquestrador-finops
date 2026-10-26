package br.mackenzie.finops.aplicacao;

import br.mackenzie.finops.aplicacao.portas.ProvedorIdentidade;
import br.mackenzie.finops.aplicacao.seguranca.ContextoIdentidade;
import br.mackenzie.finops.aplicacao.seguranca.PerfilUsuario;
import br.mackenzie.finops.aplicacao.seguranca.PoliticaAutorizacao;

import java.util.Optional;
import java.util.UUID;

public final class VerificarPoliticaAutorizacao {

    private static int verificacoes;

    public static void main(String[] args) {
        PoliticaAutorizacao politica = new PoliticaAutorizacao();

        UUID usuario = UUID.randomUUID();
        UUID colega = UUID.randomUUID();
        UUID departamento = UUID.randomUUID();
        UUID outroDepartamento = UUID.randomUUID();

        ContextoIdentidade desenvolvedor = new ContextoIdentidade(
                usuario, PerfilUsuario.DESENVOLVEDOR, true, departamento
        );

        ContextoIdentidade inativo = new ContextoIdentidade(
                usuario, PerfilUsuario.DESENVOLVEDOR, false, departamento
        );

        ContextoIdentidade semVinculo = new ContextoIdentidade(
                usuario, PerfilUsuario.DESENVOLVEDOR, true, null
        );

        ContextoIdentidade arquiteto = new ContextoIdentidade(
                colega, PerfilUsuario.ARQUITETO_CLOUD, true, null
        );

        ContextoIdentidade arquitetoInativo = new ContextoIdentidade(
                colega, PerfilUsuario.ARQUITETO_CLOUD, false, null
        );

        conferir(
                politica.podeConsultarCatalogo(desenvolvedor),
                "Desenvolvedor ativo consulta catalogo"
        );
        conferir(
                politica.podeConsultarCatalogo(arquiteto),
                "Arquiteto ativo consulta catalogo"
        );
        conferir(
                !politica.podeConsultarCatalogo(null),
                "Identidade ausente nao consulta catalogo"
        );
        conferir(
                !politica.podeConsultarCatalogo(inativo),
                "Desenvolvedor inativo nao consulta catalogo"
        );
        conferir(
                !politica.podeConsultarCatalogo(arquitetoInativo),
                "Arquiteto inativo nao consulta catalogo"
        );
        conferir(
                politica.podeConsultarCatalogo(semVinculo),
                "Consulta do catalogo nao exige vinculo departamental"
        );

        conferir(
                politica.podeCriarRequisicao(desenvolvedor, departamento),
                "Desenvolvedor cria no proprio departamento"
        );
        conferir(
                !politica.podeCriarRequisicao(
                        desenvolvedor, outroDepartamento
                ),
                "Desenvolvedor nao cria em outro departamento"
        );
        conferir(
                !politica.podeCriarRequisicao(desenvolvedor, null),
                "Criacao exige departamento solicitado"
        );
        conferir(
                !politica.podeCriarRequisicao(semVinculo, departamento),
                "Desenvolvedor sem vinculo nao cria"
        );
        conferir(
                !politica.podeCriarRequisicao(arquiteto, departamento),
                "Arquiteto nao cria"
        );
        conferir(
                !politica.podeCriarRequisicao(inativo, departamento),
                "Desenvolvedor inativo nao cria"
        );
        conferir(
                !politica.podeCriarRequisicao(null, departamento),
                "Identidade ausente nao cria"
        );

        conferir(
                politica.podeConsultarRequisicao(
                        desenvolvedor, usuario, departamento
                ),
                "Desenvolvedor consulta pedido proprio"
        );
        conferir(
                !politica.podeConsultarRequisicao(
                        desenvolvedor, colega, departamento
                ),
                "Desenvolvedor nao consulta pedido de colega"
        );
        conferir(
                !politica.podeConsultarRequisicao(
                        desenvolvedor, usuario, outroDepartamento
                ),
                "Autoria nao concede acesso a outro departamento"
        );
        conferir(
                !politica.podeConsultarRequisicao(
                        desenvolvedor, colega, outroDepartamento
                ),
                "Pedido de outro autor e departamento permanece inacessivel"
        );
        conferir(
                !politica.podeConsultarRequisicao(
                        desenvolvedor, null, departamento
                ),
                "Desenvolvedor nao consulta pedido sem autor"
        );
        conferir(
                !politica.podeConsultarRequisicao(
                        desenvolvedor, usuario, null
                ),
                "Pedido sem departamento nao concede acesso"
        );
        conferir(
                !politica.podeConsultarRequisicao(
                        semVinculo, usuario, departamento
                ),
                "Desenvolvedor sem vinculo nao consulta pedido"
        );
        conferir(
                !politica.podeConsultarRequisicao(
                        inativo, usuario, departamento
                ),
                "Desenvolvedor inativo nao consulta pedido"
        );
        conferir(
                !politica.podeConsultarRequisicao(
                        null, usuario, departamento
                ),
                "Identidade ausente nao consulta pedido"
        );
        conferir(
                politica.podeConsultarRequisicao(
                        arquiteto, usuario, departamento
                ),
                "Arquiteto consulta primeiro departamento"
        );
        conferir(
                politica.podeConsultarRequisicao(
                        arquiteto, usuario, outroDepartamento
                ),
                "Arquiteto consulta outro departamento"
        );
        conferir(
                politica.podeConsultarRequisicao(
                        arquiteto, null, departamento
                ),
                "Arquiteto consulta pedido sem autoria conhecida"
        );
        conferir(
                !politica.podeConsultarRequisicao(
                        arquitetoInativo, usuario, departamento
                ),
                "Arquiteto inativo nao consulta pedido"
        );
        conferir(
                !politica.podeConsultarRequisicao(
                        arquiteto, usuario, null
                ),
                "Arquiteto nao recebe permissao para pedido sem departamento"
        );

        ContextoIdentidade transferido = new ContextoIdentidade(
                usuario,
                PerfilUsuario.DESENVOLVEDOR,
                true,
                outroDepartamento
        );

        conferir(
                !politica.podeConsultarRequisicao(
                        transferido, usuario, departamento
                ),
                "Vinculo atual bloqueia consulta ao departamento anterior"
        );

        rejeitar(
                () -> new ContextoIdentidade(
                        null, PerfilUsuario.DESENVOLVEDOR, true, departamento
                ),
                "Contexto rejeita usuario sem identificador"
        );
        rejeitar(
                () -> new ContextoIdentidade(
                        usuario, null, true, departamento
                ),
                "Contexto rejeita perfil ausente"
        );
        rejeitar(
                () -> PerfilUsuario.valueOf("ADMINISTRADOR"),
                "Perfil desconhecido nao recebe permissao padrao"
        );

        ProvedorIdentidade identificado = () -> Optional.of(desenvolvedor);
        ProvedorIdentidade ausente = Optional::empty;

        conferir(
                identificado.obterIdentidadeAtual().orElseThrow()
                        .equals(desenvolvedor),
                "Provedor controlado retorna identidade esperada"
        );
        conferir(
                ausente.obterIdentidadeAtual().isEmpty(),
                "Provedor controlado representa identidade ausente"
        );

        conferir(
                desenvolvedor.usuarioId().equals(usuario)
                        && desenvolvedor.perfil()
                                == PerfilUsuario.DESENVOLVEDOR
                        && desenvolvedor.ativo()
                        && desenvolvedor.departamentoId().equals(departamento),
                "Decisoes preservam o contexto de identidade"
        );

        System.out.println(
                "Todas as " + verificacoes + " verificacoes passaram."
        );
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