package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.aplicacao.entradas.SolicitacaoAjusteTeto;
import br.mackenzie.finops.aplicacao.portas.AjusteTetoAutorizadoPersistente;
import br.mackenzie.finops.aplicacao.resultados.ResultadoAjusteTeto;
import br.mackenzie.finops.dominio.entidades.ResultadoAjusteOrcamentario.Situacao;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

public final class AjusteOrcamentarioJdbc implements AjusteTetoAutorizadoPersistente {
    private final IntervencaoAuditadaJdbc coordenador;

    public AjusteOrcamentarioJdbc(FabricaConexoes fabrica) {
        this(fabrica::abrir);
    }

    AjusteOrcamentarioJdbc(FornecedorConexaoJdbc fornecedor) {
        this.coordenador = new IntervencaoAuditadaJdbc(fornecedor);
    }

    @Override
    public ResultadoAjusteTeto executarAutorizado(UUID usuario, UUID departamento, SolicitacaoAjusteTeto pedido) {
        var comando = new ComandoIntervencao(pedido.operacaoId(), usuario, "AJUSTAR_TETO", departamento, null,
                pedido.justificativa(), Map.of("novoTeto", pedido.novoTeto()));
        var resultado = coordenador.executar(comando,
                conexao -> ajustar(conexao, departamento, pedido));
        return new ResultadoAjusteTeto(resultado.evento().operacaoId(), resultado.evento().resultado(),
                resultado.evento().dados(), resultado.statusHttp(), resultado.falhaNoFechamento());
    }

    private static IntervencaoAuditadaJdbc.Decisao ajustar(
            Connection conexao, UUID departamentoId, SolicitacaoAjusteTeto pedido
    ) throws SQLException {
        var departamento = new DepartamentoRepositoryJdbc(conexao).buscarPorId(departamentoId)
                .orElseThrow(() -> new FalhaPersistenciaException("Departamento bloqueado não recuperado."));
        var decisao = departamento.ajustarTeto(pedido.valorNovoTeto());
        if (decisao.situacao() == Situacao.TETO_AJUSTADO) {
            try (var atualizacao = conexao.prepareStatement(
                    "UPDATE departamento SET teto_mensal = ?, saldo_disponivel = ? WHERE id = ?")) {
                atualizacao.setBigDecimal(1, decisao.tetoNovo());
                atualizacao.setBigDecimal(2, decisao.saldoNovo());
                atualizacao.setString(3, departamentoId.toString());
                if (atualizacao.executeUpdate() != 1) {
                    throw new FalhaPersistenciaException("O ajuste não afetou exatamente um departamento.");
                }
            }
        }
        var dados = Map.of("departamentoId", departamentoId.toString(),
                "tetoAnterior", decisao.tetoAnterior().toPlainString(), "tetoNovo", decisao.tetoNovo().toPlainString(),
                "variacaoTeto", decisao.variacaoTeto().toPlainString(), "saldoAnterior", decisao.saldoAnterior().toPlainString(),
                "saldoNovo", decisao.saldoNovo().toPlainString(), "comprometido", decisao.comprometido().toPlainString(),
                "tetoSolicitado", pedido.novoTeto());
        int status = decisao.situacao() == Situacao.TETO_ABAIXO_DO_COMPROMETIDO ? 409 : 200;
        return new IntervencaoAuditadaJdbc.Decisao(status, decisao.situacao().name(), dados);
    }
}
