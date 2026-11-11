package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.aplicacao.excecoes.ServicoIndisponivelException;
import br.mackenzie.finops.aplicacao.portas.ConsultaDepartamentosPersistente;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ConsultaDepartamentosJdbc implements ConsultaDepartamentosPersistente {
    private final FabricaConexoes fabrica;

    public ConsultaDepartamentosJdbc(FabricaConexoes fabrica) {
        this.fabrica = fabrica;
    }

    @Override
    public List<Resumo> listarAutorizado(UUID usuario, UUID apos, int limite) {
        try (var conexao = fabrica.abrir()) {
            conexao.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            conexao.setAutoCommit(false);
            AutorizacaoArquitetoJdbc.verificar(conexao, usuario);
            var departamentos = listar(conexao, apos, limite);
            conexao.rollback();
            return departamentos;
        } catch (SQLException falha) {
            if (FalhaPersistenciaException.indisponibilidade(falha)) {
                throw new ServicoIndisponivelException();
            }
            throw new FalhaPersistenciaException("Falha ao consultar departamentos.");
        }
    }

    private static List<Resumo> listar(Connection conexao, UUID apos, int limite) throws SQLException {
        String sql = "SELECT id, nome, teto_mensal, saldo_disponivel FROM departamento"
                + (apos == null ? "" : " WHERE id > ?")
                + " ORDER BY id LIMIT ?";
        try (var consulta = conexao.prepareStatement(sql)) {
            int indice = 1;
            if (apos != null) {
                consulta.setString(indice++, apos.toString());
            }
            consulta.setInt(indice, limite);
            try (var linhas = consulta.executeQuery()) {
                var departamentos = new ArrayList<Resumo>();
                while (linhas.next()) {
                    departamentos.add(new Resumo(UUID.fromString(linhas.getString("id")), linhas.getString("nome"),
                            linhas.getBigDecimal("teto_mensal"), linhas.getBigDecimal("saldo_disponivel")));
                }
                return List.copyOf(departamentos);
            }
        }
    }
}
