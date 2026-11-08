package br.mackenzie.finops.infraestrutura.persistencia;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

final class CenarioIntervencoesMysql implements AutoCloseable {
    final CenarioUsuariosMysql usuarios = new CenarioUsuariosMysql();
    final UUID arquiteto = new UsuariosJdbc(usuarios.fabrica).buscarCredencial(usuarios.arquiteto).orElseThrow().id();
    final FabricaConexoes runtime = new FabricaConexoes(new ConfiguracaoBanco(
            System.getenv("FINOPS_DB_URL"), System.getenv("FINOPS_RUNTIME_DB_USER"),
            System.getenv("FINOPS_RUNTIME_DB_PASSWORD")));

    CenarioIntervencoesMysql() throws SQLException {}

    ComandoIntervencao comando(UUID operacao, UUID departamento) {
        return new ComandoIntervencao(operacao, arquiteto, "MUTACAO_TESTE", departamento, null,
                " Justificativa do cenário ", Map.of("valor", "1.00"));
    }

    IntervencaoAuditadaJdbc.Resultado executar(ComandoIntervencao comando) {
        return new IntervencaoAuditadaJdbc(runtime::abrir).executar(comando,
                conexao -> mutacao(conexao, comando.departamentoId()));
    }

    // Mutação sintética exclusiva dos testes. Não representa regra das Specs 008/009.
    static IntervencaoAuditadaJdbc.Decisao mutacao(Connection conexao, UUID departamento) throws SQLException {
        var antes = new DepartamentoRepositoryJdbc(conexao).buscarPorId(departamento).orElseThrow()
                .getSaldoDisponivel();
        CenarioUsuariosMysql.executar(conexao,
                "UPDATE departamento SET saldo_disponivel = saldo_disponivel - 1 WHERE id = ?", departamento.toString());
        return new IntervencaoAuditadaJdbc.Decisao(200, "MUTACAO_EFETIVADA",
                Map.of("saldoAnterior", antes.toPlainString(), "saldoPosterior", antes.subtract(java.math.BigDecimal.ONE).toPlainString()));
    }

    String saldo(UUID departamento) throws SQLException {
        try (var conexao = usuarios.abrir()) {
            return new DepartamentoRepositoryJdbc(conexao).buscarPorId(departamento).orElseThrow()
                    .getSaldoDisponivel().toPlainString();
        }
    }

    int quantidade() throws SQLException {
        try (var conexao = usuarios.abrir();
             var consulta = conexao.prepareStatement("SELECT COUNT(*) FROM intervencao WHERE departamento_id IN (?, ?)")) {
            consulta.setString(1, usuarios.departamento.toString());
            consulta.setString(2, usuarios.outroDepartamento.toString());
            try (var linha = consulta.executeQuery()) {
                linha.next();
                return linha.getInt(1);
            }
        }
    }

    @Override
    public void close() throws SQLException {
        try (var conexao = usuarios.abrir()) {
            CenarioUsuariosMysql.executar(conexao, "DELETE FROM intervencao WHERE departamento_id IN (?, ?)",
                    usuarios.departamento.toString(), usuarios.outroDepartamento.toString());
        }
        usuarios.close();
    }
}
