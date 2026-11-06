package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.aplicacao.seguranca.PerfilUsuario;
import br.mackenzie.finops.dominio.entidades.Departamento;
import br.mackenzie.finops.dominio.entidades.RecursoCatalogo;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import org.springframework.security.crypto.factory.PasswordEncoderFactories;

final class CenarioUsuariosMysql implements AutoCloseable {
    final FabricaConexoes fabrica = new FabricaConexoes(ConfiguracaoBanco.carregarDoAmbiente());
    final UUID departamento = UUID.randomUUID();
    final UUID outroDepartamento = UUID.randomUUID();
    final UUID recurso = UUID.randomUUID();
    final String desenvolvedor = "dev-" + UUID.randomUUID();
    final String arquiteto = "arq-" + UUID.randomUUID();
    final String senha = UUID.randomUUID().toString();
    final UUID autor;

    CenarioUsuariosMysql() throws SQLException {
        var hash = PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(senha);
        try (var conexao = abrir()) {
            conexao.setAutoCommit(false);
            var departamentos = new DepartamentoRepositoryJdbc(conexao);
            departamentos.adicionar(new Departamento(departamento, desenvolvedor, new BigDecimal("1000.00")));
            departamentos.adicionar(new Departamento(outroDepartamento, arquiteto, new BigDecimal("1000.00")));
            new RecursoCatalogoRepositoryJdbc(conexao).adicionar(
                    new RecursoCatalogo(recurso, desenvolvedor, new BigDecimal("12.50"))
            );
            UsuariosJdbc.cadastrar(conexao, desenvolvedor, hash, PerfilUsuario.DESENVOLVEDOR, departamento);
            UsuariosJdbc.cadastrar(conexao, arquiteto, hash, PerfilUsuario.ARQUITETO_CLOUD, null);
            conexao.commit();
        }
        autor = new UsuariosJdbc(fabrica).buscarCredencial(desenvolvedor).orElseThrow().id();
    }

    Connection abrir() throws SQLException {
        var conexao = fabrica.abrir();
        try {
            if (!"finops_test".equals(conexao.getCatalog())) {
                throw new SQLException("O teste exige finops_test.");
            }
            return conexao;
        } catch (SQLException falha) {
            try (conexao) {
                throw falha;
            }
        }
    }

    static void executar(Connection conexao, String sql, String... parametros) throws SQLException {
        try (var comando = conexao.prepareStatement(sql)) {
            for (int i = 0; i < parametros.length; i++) {
                comando.setString(i + 1, parametros[i]);
            }
            comando.executeUpdate();
        }
    }

    @Override
    public void close() throws SQLException {
        try (var conexao = abrir()) {
            conexao.setAutoCommit(false);
            executar(conexao, """
                    DELETE FROM item_requisicao WHERE requisicao_id IN (
                        SELECT id FROM requisicao WHERE departamento_id IN (?, ?)
                    )
                    """, departamento.toString(), outroDepartamento.toString());
            executar(conexao, "DELETE FROM requisicao WHERE departamento_id IN (?, ?)",
                    departamento.toString(), outroDepartamento.toString());
            executar(conexao, "DELETE FROM usuario WHERE login IN (?, ?)", desenvolvedor, arquiteto);
            executar(conexao, "DELETE FROM recurso_catalogo WHERE id = ?", recurso.toString());
            executar(conexao, "DELETE FROM departamento WHERE id IN (?, ?)",
                    departamento.toString(), outroDepartamento.toString());
            conexao.commit();
        }
    }
}
