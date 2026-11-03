package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.aplicacao.ListarCatalogoAutorizadoUseCase;
import br.mackenzie.finops.aplicacao.seguranca.ContextoIdentidade;
import br.mackenzie.finops.aplicacao.seguranca.PerfilUsuario;
import br.mackenzie.finops.dominio.entidades.RecursoCatalogo;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class VerificarListagemCatalogoJdbc {

    private static int verificacoes;

    private record Dados(String nome, BigDecimal custo) {
    }

    public static void main(String[] args) throws Exception {
        FabricaConexoes fabrica = new FabricaConexoes(
                ConfiguracaoBanco.carregarDoAmbiente()
        );

        // Prefixos distintos exercitam a ordem textual dos UUIDs.
        String sufixo = UUID.randomUUID().toString().substring(1);
        UUID primeiroId = UUID.fromString("0" + sufixo);
        UUID segundoId = UUID.fromString("8" + sufixo);
        String marcador = "Catalogo Issue 84 " + UUID.randomUUID();

        System.out.println("Primeiro recurso: " + primeiroId);
        System.out.println("Segundo recurso: " + segundoId);
        System.out.println("Marcador: " + marcador);

        Map<UUID, Dados> anteriores = fotografia(fabrica);

        if (anteriores.containsKey(primeiroId)
                || anteriores.containsKey(segundoId)) {
            throw new IllegalStateException(
                    "Colisão de UUID: execute novamente sem alterar o banco."
            );
        }

        try {
            preparar(fabrica, primeiroId, segundoId, marcador);

            ConsultaCatalogoJdbc jdbc = new ConsultaCatalogoJdbc(
                    () -> abrirTeste(fabrica)
            );

            ContextoIdentidade desenvolvedor = new ContextoIdentidade(
                    UUID.randomUUID(),
                    PerfilUsuario.DESENVOLVEDOR,
                    true,
                    null
            );

            List<RecursoCatalogo> recursos =
                    new ListarCatalogoAutorizadoUseCase(
                            () -> Optional.of(desenvolvedor),
                            jdbc
                    ).executar();

            Map<UUID, Dados> esperado = new HashMap<>(anteriores);
            esperado.put(
                    primeiroId,
                    new Dados(marcador + " A", new BigDecimal("0.10"))
            );
            esperado.put(
                    segundoId,
                    new Dados(marcador + " B", new BigDecimal("12.50"))
            );

            conferir(
                    esperado.equals(dados(recursos)),
                    "Desenvolvedor sem vínculo recupera catálogo completo"
            );

            boolean ordenado = true;
            for (int indice = 1; indice < recursos.size(); indice++) {
                String anterior = recursos.get(indice - 1).getId().toString();
                String atual = recursos.get(indice).getId().toString();
                if (anterior.compareTo(atual) >= 0) {
                    ordenado = false;
                }
            }

            conferir(
                    ordenado,
                    "JDBC preserva ordem textual canônica dos UUIDs"
            );

            conferir(
                    posicao(recursos, primeiroId)
                            < posicao(recursos, segundoId),
                    "UUID iniciado por 0 aparece antes do iniciado por 8"
            );

            List<RecursoCatalogo> leituraDireta = jdbc.listar();

            boolean inclusaoBloqueada = false;
            try {
                leituraDireta.add(recursos.get(0));
            } catch (UnsupportedOperationException esperada) {
                inclusaoBloqueada = true;
            }

            conferir(
                    inclusaoBloqueada,
                    "Lista do próprio adaptador JDBC impede inclusão"
            );

            ContextoIdentidade arquiteto = new ContextoIdentidade(
                    UUID.randomUUID(),
                    PerfilUsuario.ARQUITETO_CLOUD,
                    true,
                    null
            );

            List<RecursoCatalogo> leituraArquiteto =
                    new ListarCatalogoAutorizadoUseCase(
                            () -> Optional.of(arquiteto),
                            jdbc
                    ).executar();

            conferir(
                    esperado.equals(dados(leituraArquiteto)),
                    "Arquiteto sem vínculo recupera o mesmo catálogo"
            );

            conferir(
                    esperado.equals(fotografia(fabrica)),
                    "Outra conexão confirma nomes e valores preservados"
            );
        } catch (Exception | AssertionError falha) {
            try {
                limpar(fabrica, primeiroId, segundoId, marcador);
            } catch (Exception falhaLimpeza) {
                falha.addSuppressed(falhaLimpeza);
            }
            throw falha;
        }

        limpar(fabrica, primeiroId, segundoId, marcador);

        Map<UUID, Dados> depois = fotografia(fabrica);

        conferir(
                !depois.containsKey(primeiroId)
                        && !depois.containsKey(segundoId),
                "Outra conexão confirma remoção dos recursos temporários"
        );

        conferir(
                anteriores.equals(depois),
                "Todos os recursos preexistentes permanecem preservados"
        );

        System.out.println(
                "Todas as " + verificacoes + " verificacoes passaram."
        );
    }

    private static void preparar(
            FabricaConexoes fabrica,
            UUID primeiroId,
            UUID segundoId,
            String marcador
    ) throws SQLException {
        try (Connection conexao = abrirTeste(fabrica)) {
            conexao.setAutoCommit(false);

            try {
                RecursoCatalogoRepositoryJdbc repositorio =
                        new RecursoCatalogoRepositoryJdbc(conexao);

                // Insere na ordem inversa à esperada na consulta.
                repositorio.adicionar(new RecursoCatalogo(
                        segundoId,
                        marcador + " B",
                        new BigDecimal("12.50")
                ));

                repositorio.adicionar(new RecursoCatalogo(
                        primeiroId,
                        marcador + " A",
                        new BigDecimal("0.10")
                ));

                conexao.commit();
            } catch (SQLException | RuntimeException falha) {
                try {
                    conexao.rollback();
                } catch (SQLException falhaRollback) {
                    falha.addSuppressed(falhaRollback);
                }
                throw falha;
            }
        }
    }

    private static Map<UUID, Dados> fotografia(
            FabricaConexoes fabrica
    ) throws SQLException {
        try (
                Connection conexao = abrirTeste(fabrica);
                PreparedStatement comando = conexao.prepareStatement(
                        "SELECT id, nome, custo_mensal FROM recurso_catalogo"
                );
                ResultSet resultado = comando.executeQuery()
        ) {
            Map<UUID, Dados> registros = new HashMap<>();

            while (resultado.next()) {
                UUID id = UUID.fromString(resultado.getString("id"));
                Dados anterior = registros.put(
                        id,
                        new Dados(
                                resultado.getString("nome"),
                                resultado.getBigDecimal("custo_mensal")
                                        .setScale(2)
                        )
                );

                if (anterior != null) {
                    throw new IllegalStateException("UUID duplicado.");
                }
            }

            return registros;
        }
    }

    private static Map<UUID, Dados> dados(
            List<RecursoCatalogo> recursos
    ) {
        Map<UUID, Dados> registros = new HashMap<>();

        for (RecursoCatalogo recurso : recursos) {
            Dados anterior = registros.put(
                    recurso.getId(),
                    new Dados(
                            recurso.getNome(),
                            recurso.getCustoMensal().setScale(2)
                    )
            );

            if (anterior != null) {
                throw new IllegalStateException(
                        "A consulta devolveu UUID duplicado."
                );
            }
        }

        return registros;
    }

    private static int posicao(
            List<RecursoCatalogo> recursos,
            UUID id
    ) {
        for (int indice = 0; indice < recursos.size(); indice++) {
            if (id.equals(recursos.get(indice).getId())) {
                return indice;
            }
        }

        throw new IllegalStateException("Recurso esperado não encontrado.");
    }

    private static void limpar(
            FabricaConexoes fabrica,
            UUID primeiroId,
            UUID segundoId,
            String marcador
    ) throws SQLException {
        try (
                Connection conexao = abrirTeste(fabrica);
                PreparedStatement comando = conexao.prepareStatement("""
                        DELETE FROM recurso_catalogo
                        WHERE (id = ? AND nome = ?)
                           OR (id = ? AND nome = ?)
                        """)
        ) {
            comando.setString(1, primeiroId.toString());
            comando.setString(2, marcador + " A");
            comando.setString(3, segundoId.toString());
            comando.setString(4, marcador + " B");
            comando.executeUpdate();
        }
    }

    private static Connection abrirTeste(
            FabricaConexoes fabrica
    ) throws SQLException {
        Connection conexao = fabrica.abrir();

        try {
            try (
                    Statement comando = conexao.createStatement();
                    ResultSet resultado = comando.executeQuery(
                            "SELECT DATABASE(), CURRENT_USER()"
                    )
            ) {
                if (!resultado.next()
                        || !"finops_test".equals(resultado.getString(1))
                        || !"finops_test_app@localhost".equals(
                                resultado.getString(2)
                        )) {
                    throw new IllegalStateException(
                            "Use finops_test com finops_test_app@localhost."
                    );
                }
            }

            conexao.setAutoCommit(true);
            return conexao;
        } catch (SQLException | RuntimeException falha) {
            try {
                conexao.close();
            } catch (SQLException fechamento) {
                falha.addSuppressed(fechamento);
            }
            throw falha;
        }
    }

    private static void conferir(boolean condicao, String descricao) {
        if (!condicao) {
            throw new IllegalStateException("FALHOU: " + descricao);
        }

        verificacoes++;
        System.out.println("PASSOU: " + descricao);
    }
}