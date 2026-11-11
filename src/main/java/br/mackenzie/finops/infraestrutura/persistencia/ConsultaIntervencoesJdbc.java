package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.aplicacao.auditoria.CursorIntervencao;
import br.mackenzie.finops.aplicacao.auditoria.EventoIntervencao;
import br.mackenzie.finops.aplicacao.excecoes.DepartamentoNaoEncontradoException;
import br.mackenzie.finops.aplicacao.excecoes.ServicoIndisponivelException;
import br.mackenzie.finops.aplicacao.portas.ConsultaIntervencoesPersistente;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ConsultaIntervencoesJdbc implements ConsultaIntervencoesPersistente {
    private final FornecedorConexaoJdbc fornecedor;

    public ConsultaIntervencoesJdbc(FabricaConexoes fabrica) {
        this(fabrica::abrir);
    }

    ConsultaIntervencoesJdbc(FornecedorConexaoJdbc fornecedor) {
        this.fornecedor = fornecedor;
    }

    @Override
    public List<EventoIntervencao> listarAutorizado(
            UUID usuario, UUID departamento, UUID requisicao, CursorIntervencao apos, int limite
    ) {
        if (departamento == null || limite < 1 || limite > 101) {
            throw new IllegalArgumentException("Filtro de histórico inválido.");
        }
        try (var conexao = fornecedor.abrir()) {
            conexao.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            conexao.setAutoCommit(false);
            try {
                AutorizacaoArquitetoJdbc.verificar(conexao, usuario);
                try (var consulta = conexao.prepareStatement("SELECT id FROM departamento WHERE id = ?")) {
                    consulta.setString(1, departamento.toString());
                    try (var linha = consulta.executeQuery()) {
                        if (!linha.next()) {
                            throw new DepartamentoNaoEncontradoException();
                        }
                    }
                }
                var pagina = listar(conexao, departamento, requisicao, apos, limite);
                conexao.rollback();
                return pagina;
            } catch (SQLException | RuntimeException falha) {
                try {
                    conexao.rollback();
                } catch (SQLException | RuntimeException rollback) {
                    falha.addSuppressed(new IllegalStateException("Não foi possível confirmar o rollback da consulta."));
                }
                throw falha;
            }
        } catch (SQLException falha) {
            if (FalhaPersistenciaException.indisponibilidade(falha)) {
                throw new ServicoIndisponivelException();
            }
            throw new FalhaPersistenciaException("Falha ao consultar intervenções.");
        }
    }

    private static List<EventoIntervencao> listar(
            Connection conexao, UUID departamento, UUID requisicao, CursorIntervencao apos, int limite
    ) throws SQLException {
        String sql = "SELECT * FROM intervencao WHERE departamento_id = ?";
        if (requisicao != null) {
            sql += " AND requisicao_id = ?";
        }
        if (apos != null) {
            sql += " AND (instante < ? OR (instante = ? AND operacao_id < ?))";
        }
        sql += " ORDER BY instante DESC, operacao_id DESC LIMIT ?";
        try (var consulta = conexao.prepareStatement(sql)) {
            int indice = 1;
            consulta.setString(indice++, departamento.toString());
            if (requisicao != null) {
                consulta.setString(indice++, requisicao.toString());
            }
            if (apos != null) {
                var instante = LocalDateTime.ofInstant(apos.instante(), ZoneOffset.UTC);
                consulta.setObject(indice++, instante);
                consulta.setObject(indice++, instante);
                consulta.setString(indice++, apos.operacaoId().toString());
            }
            consulta.setInt(indice, limite);
            var eventos = new ArrayList<EventoIntervencao>();
            try (var linha = consulta.executeQuery()) {
                while (linha.next()) {
                    eventos.add(RegistroIntervencaoJdbc.evento(linha));
                }
            }
            return List.copyOf(eventos);
        }
    }
}
