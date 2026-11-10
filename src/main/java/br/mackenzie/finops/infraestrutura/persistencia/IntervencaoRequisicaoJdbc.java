package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.aplicacao.entradas.SolicitacaoIntervencaoRequisicao;
import br.mackenzie.finops.aplicacao.portas.IntervencaoRequisicaoPersistente;
import br.mackenzie.finops.aplicacao.resultados.FotografiaRequisicao;
import br.mackenzie.finops.aplicacao.resultados.ResultadoIntervencaoRequisicao;
import br.mackenzie.finops.dominio.entidades.EstadoRequisicao;
import br.mackenzie.finops.dominio.entidades.ResultadoIntervencaoManual;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;

public final class IntervencaoRequisicaoJdbc implements IntervencaoRequisicaoPersistente {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final IntervencaoAuditadaJdbc coordenador;

    public IntervencaoRequisicaoJdbc(FabricaConexoes fabrica) {
        this(fabrica::abrir);
    }

    IntervencaoRequisicaoJdbc(FornecedorConexaoJdbc fornecedor) {
        coordenador = new IntervencaoAuditadaJdbc(fornecedor);
    }

    @Override
    public ResultadoIntervencaoRequisicao executarAutorizado(UUID usuario, UUID requisicao, SolicitacaoIntervencaoRequisicao pedido) {
        String acao = pedido.decisao().tecnica() ? "REVISAO_TECNICA" : "LIBERACAO_FINANCEIRA";
        var resultado = coordenador.executarParaRequisicao(usuario, pedido.operacaoId(), requisicao, acao,
                pedido.justificativa(), Map.of("decisao", pedido.decisao().name()),
                conexao -> decidir(conexao, requisicao, pedido));
        var dados = resultado.evento().dados();
        if (!"1".equals(dados.get("versaoFotografia"))) {
            throw new FalhaPersistenciaException("Versão de fotografia não suportada.");
        }
        var fotografia = JSON.readValue(dados.get("requisicaoJson"), FotografiaRequisicao.class);
        return new ResultadoIntervencaoRequisicao(resultado.evento().operacaoId(), resultado.evento().resultado(),
                fotografia, resultado.statusHttp(), resultado.falhaNoFechamento());
    }

    private static IntervencaoAuditadaJdbc.Decisao decidir(
            Connection conexao, UUID requisicaoId, SolicitacaoIntervencaoRequisicao pedido
    ) throws SQLException {
        var requisicao = new RequisicaoRepositoryJdbc(conexao).buscarPorId(requisicaoId)
                .orElseThrow(() -> new FalhaPersistenciaException("Requisição bloqueada não recuperada."));
        var departamento = requisicao.getDepartamento();
        var saldoAnterior = departamento.getSaldoDisponivel();
        var estadoAnterior = EstadoRequisicao.deStatus(requisicao.getStatus()).name();
        var motivoOriginal = FotografiaRequisicao.motivoPublico(requisicao.getMotivoRevisao());
        var resultado = pedido.decisao().tecnica()
                ? requisicao.revisarTecnicamente(pedido.decisao()) : requisicao.liberarFinanceiramente(pedido.decisao());
        if (saldoAnterior.compareTo(departamento.getSaldoDisponivel()) != 0) {
            try (var atualizacao = conexao.prepareStatement(
                    "UPDATE departamento SET saldo_disponivel = ? WHERE id = ? AND saldo_disponivel = ?")) {
                atualizacao.setBigDecimal(1, departamento.getSaldoDisponivel());
                atualizacao.setString(2, departamento.getId().toString());
                atualizacao.setBigDecimal(3, saldoAnterior);
                conferir(atualizacao.executeUpdate());
            }
        }
        String estadoNovo = EstadoRequisicao.deStatus(requisicao.getStatus()).name();
        if (!estadoAnterior.equals(estadoNovo)) {
            try (var atualizacao = conexao.prepareStatement("""
                    UPDATE requisicao SET estado = ?, motivo_revisao = NULL
                    WHERE id = ? AND departamento_id = ? AND estado = ?
                    """)) {
                atualizacao.setString(1, estadoNovo);
                atualizacao.setString(2, requisicaoId.toString());
                atualizacao.setString(3, departamento.getId().toString());
                atualizacao.setString(4, estadoAnterior);
                conferir(atualizacao.executeUpdate());
            }
        }
        var dados = new LinkedHashMap<String, String>();
        dados.put("estadoAnterior", estadoAnterior);
        dados.put("estadoNovo", estadoNovo);
        dados.put("motivoOriginal", Objects.toString(motivoOriginal, ""));
        dados.put("custoHistorico", requisicao.getCustoTotalProjetado().toPlainString());
        dados.put("saldoAnterior", saldoAnterior.toPlainString());
        dados.put("saldoNovo", departamento.getSaldoDisponivel().toPlainString());
        dados.put("tetoMensal", departamento.getOrcamentoMensal().toPlainString());
        dados.put("percentualReserva", departamento.getPercentualReserva().toPlainString());
        dados.put("valorReserva", departamento.calcularReserva().toPlainString());
        dados.put("versaoFotografia", "1");
        dados.put("requisicaoJson", JSON.writeValueAsString(FotografiaRequisicao.de(requisicao)));
        return new IntervencaoAuditadaJdbc.Decisao(resultado == ResultadoIntervencaoManual.ESTADO_NAO_PERMITIDO ? 409 : 200,
                resultado.name(), dados);
    }

    private static void conferir(int atualizadas) {
        if (atualizadas != 1) {
            throw new FalhaPersistenciaException("A intervenção não atualizou exatamente um registro.");
        }
    }
}
