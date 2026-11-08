package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.aplicacao.auditoria.EventoIntervencao;
import br.mackenzie.finops.aplicacao.excecoes.ConfirmacaoIntervencaoDesconhecidaException;
import br.mackenzie.finops.aplicacao.excecoes.ConflitoOperacaoException;
import br.mackenzie.finops.aplicacao.excecoes.DepartamentoNaoEncontradoException;
import br.mackenzie.finops.aplicacao.excecoes.RequisicaoNaoEncontradaException;
import br.mackenzie.finops.aplicacao.excecoes.ServicoIndisponivelException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

/**
 * Base interna da SPEC-007. O adaptador de cada intervenção deve delegar aqui.
 * A ação recebe alvos bloqueados e não pode encerrar a transação, abrir outra
 * conexão nem produzir efeitos externos. Regras de negócio pertencem ao domínio.
 */
final class IntervencaoAuditadaJdbc {
    private final FornecedorConexaoJdbc fornecedor;

    IntervencaoAuditadaJdbc(FornecedorConexaoJdbc fornecedor) {
        this.fornecedor = fornecedor;
    }

    @FunctionalInterface
    interface Acao {
        Decisao executar(Connection conexao) throws SQLException;
    }

    record Decisao(int statusHttp, String resultado, Map<String, String> dados) {
        Decisao {
            if ((statusHttp < 200 || statusHttp > 299) && statusHttp != 409) {
                throw new IllegalArgumentException("A decisão deve ser um resultado de negócio.");
            }
            if (resultado == null || !resultado.matches("[A-Z][A-Z_]{0,63}")) {
                throw new IllegalArgumentException("Resultado inválido.");
            }
            dados = Map.copyOf(dados);
        }
    }

    record Resultado(EventoIntervencao evento, int statusHttp, boolean falhaNoFechamento) {}

    Resultado executar(ComandoIntervencao comando, Acao acao) {
        try {
            return tentar(comando, acao, false);
        } catch (ColisaoConcorrente colisao) {
            // Apenas uma reconciliação de leitura. Nunca repetir a ação automaticamente.
            return tentar(comando, acao, true);
        }
    }

    private Resultado tentar(ComandoIntervencao comando, Acao acao, boolean reconciliar) {
        Connection conexao = null;
        RegistroIntervencaoJdbc.Recibo recibo = null;
        RuntimeException falha = null;
        boolean encerrada = false;
        boolean fechamentoFalhou = false;
        try {
            conexao = fornecedor.abrir();
            conexao.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            conexao.setAutoCommit(false);
            AutorizacaoIntervencaoJdbc.verificar(conexao, comando.responsavelId());
            bloquearAlvos(conexao, comando);
            recibo = RegistroIntervencaoJdbc.buscar(conexao, comando.operacaoId()).orElse(null);
            if (recibo != null) {
                if (!recibo.comando().equals(comando)) {
                    throw new ConflitoOperacaoException();
                }
                conexao.rollback();
            } else {
                if (reconciliar) {
                    throw new ConfirmacaoIntervencaoDesconhecidaException(comando.operacaoId());
                }
                var decisao = acao.executar(conexao);
                try {
                    recibo = RegistroIntervencaoJdbc.inserir(conexao, comando, decisao);
                } catch (SQLException insercao) {
                    if (insercao.getErrorCode() == 1062) {
                        throw new ColisaoConcorrente();
                    }
                    throw insercao;
                }
                try {
                    conexao.commit();
                } catch (SQLException | RuntimeException commit) {
                    throw new ConfirmacaoIntervencaoDesconhecidaException(comando.operacaoId());
                }
            }
            encerrada = true;
        } catch (SQLException erro) {
            falha = FalhaPersistenciaException.indisponibilidade(erro)
                    ? new ServicoIndisponivelException() : new FalhaPersistenciaException("Falha na intervenção.");
        } catch (RuntimeException erro) {
            falha = erro;
        } finally {
            if (conexao != null) {
                if (!encerrada) {
                    try {
                        conexao.rollback();
                    } catch (SQLException | RuntimeException rollback) {
                        // Rollback não confirmado impede reconciliar automaticamente uma colisão.
                        if (!(falha instanceof ConfirmacaoIntervencaoDesconhecidaException)) {
                            falha = new FalhaPersistenciaException("Não foi possível confirmar o rollback.");
                        }
                    }
                }
                try {
                    conexao.close();
                } catch (SQLException | RuntimeException fechamento) {
                    fechamentoFalhou = true;
                    if (falha instanceof ColisaoConcorrente) {
                        falha = new FalhaPersistenciaException("Não foi possível confirmar o fechamento.");
                    }
                }
            }
        }
        if (falha != null) {
            throw falha;
        }
        return new Resultado(recibo.evento(), recibo.statusHttp(), fechamentoFalhou);
    }

    private static void bloquearAlvos(Connection conexao, ComandoIntervencao comando) throws SQLException {
        try (var consulta = conexao.prepareStatement("SELECT id FROM departamento WHERE id = ? FOR UPDATE")) {
            consulta.setString(1, comando.departamentoId().toString());
            try (var linha = consulta.executeQuery()) {
                if (!linha.next()) {
                    throw new DepartamentoNaoEncontradoException();
                }
            }
        }
        if (comando.requisicaoId() != null) {
            try (var consulta = conexao.prepareStatement(
                    "SELECT departamento_id FROM requisicao WHERE id = ? FOR UPDATE")) {
                consulta.setString(1, comando.requisicaoId().toString());
                try (var linha = consulta.executeQuery()) {
                    if (!linha.next() || !comando.departamentoId().toString().equals(linha.getString(1))) {
                        throw new RequisicaoNaoEncontradaException();
                    }
                }
            }
        }
    }

    private static final class ColisaoConcorrente extends RuntimeException {}
}
