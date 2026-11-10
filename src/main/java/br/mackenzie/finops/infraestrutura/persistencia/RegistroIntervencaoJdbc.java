package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.aplicacao.auditoria.EventoIntervencao;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

final class RegistroIntervencaoJdbc {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, String>> MAPA = new TypeReference<>() {};

    private RegistroIntervencaoJdbc() {}

    record Recibo(ComandoIntervencao comando, EventoIntervencao evento, int statusHttp) {}

    static Optional<Recibo> buscar(Connection conexao, UUID id) throws SQLException {
        try (var consulta = conexao.prepareStatement("SELECT * FROM intervencao WHERE operacao_id = ?")) {
            consulta.setString(1, id.toString());
            try (var linha = consulta.executeQuery()) {
                if (!linha.next()) {
                    return Optional.empty();
                }
                var evento = evento(linha);
                var comando = new ComandoIntervencao(evento.operacaoId(), evento.responsavelId(), evento.acao(),
                        evento.departamentoId(), evento.requisicaoId(), evento.justificativa(),
                        JSON.readValue(linha.getString("parametros"), MAPA));
                return Optional.of(new Recibo(comando, evento, linha.getInt("status_http")));
            }
        }
    }

    static Recibo inserir(Connection conexao, ComandoIntervencao comando, IntervencaoAuditadaJdbc.Decisao decisao)
            throws SQLException {
        String sql = """
                INSERT INTO intervencao (operacao_id, responsavel_id, departamento_id, requisicao_id,
                    acao, instante, justificativa, parametros, resultado, status_http, dados)
                VALUES (?, ?, ?, ?, ?, UTC_TIMESTAMP(6), ?, ?, ?, ?, ?)
                """;
        try (var insercao = conexao.prepareStatement(sql)) {
            insercao.setString(1, comando.operacaoId().toString());
            insercao.setString(2, comando.responsavelId().toString());
            insercao.setString(3, comando.departamentoId().toString());
            insercao.setString(4, Objects.toString(comando.requisicaoId(), null));
            insercao.setString(5, comando.acao());
            insercao.setString(6, comando.justificativa());
            insercao.setString(7, JSON.writeValueAsString(comando.parametros()));
            insercao.setString(8, decisao.resultado());
            insercao.setInt(9, decisao.statusHttp());
            insercao.setString(10, JSON.writeValueAsString(decisao.dados()));
            insercao.executeUpdate();
        }
        return buscar(conexao, comando.operacaoId()).orElseThrow();
    }

    static EventoIntervencao evento(ResultSet linha) throws SQLException {
        if (linha.getInt("versao") != 1) {
            throw new FalhaPersistenciaException("Versão de recibo não suportada.");
        }
        String pedido = linha.getString("requisicao_id");
        return new EventoIntervencao(UUID.fromString(linha.getString("operacao_id")),
                UUID.fromString(linha.getString("responsavel_id")), linha.getString("acao"),
                linha.getObject("instante", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                linha.getString("justificativa"), UUID.fromString(linha.getString("departamento_id")),
                pedido == null ? null : UUID.fromString(pedido), linha.getString("resultado"),
                JSON.readValue(linha.getString("dados"), MAPA));
    }
}
