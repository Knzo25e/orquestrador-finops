package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.aplicacao.ValidarOrcamentoUseCase;
import br.mackenzie.finops.aplicacao.excecoes.ConfirmacaoValidacaoDesconhecidaException;
import br.mackenzie.finops.aplicacao.excecoes.FalhaValidacaoPersistenteException;
import br.mackenzie.finops.aplicacao.portas.ValidacaoRequisicaoPersistente;
import br.mackenzie.finops.aplicacao.resultados.ResultadoValidacaoPersistente;
import br.mackenzie.finops.dominio.entidades.Requisicao;
import br.mackenzie.finops.dominio.entidades.ResultadoValidacao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Coordena a validação financeira persistente.
 *
 * Cada execução utiliza uma conexão exclusiva.
 * Departamento e requisição permanecem bloqueados até o
 * encerramento da transação.
 */
public final class ValidacaoRequisicaoJdbc
        implements ValidacaoRequisicaoPersistente {

    private final FornecedorConexaoJdbc fornecedor;

    public ValidacaoRequisicaoJdbc(FabricaConexoes fabrica) {
        if (fabrica == null) {
            throw new IllegalArgumentException(
                    "A fábrica de conexões é obrigatória."
            );
        }

        this.fornecedor = fabrica::abrir;
    }

    // Permite fornecer conexões controladas nos testes da infraestrutura.
    ValidacaoRequisicaoJdbc(FornecedorConexaoJdbc fornecedor) {
        if (fornecedor == null) {
            throw new IllegalArgumentException(
                    "O fornecedor de conexões é obrigatório."
            );
        }

        this.fornecedor = fornecedor;
    }

    @Override
    public ResultadoValidacaoPersistente executar(UUID requisicaoId) {
        if (requisicaoId == null) {
            throw new IllegalArgumentException(
                    "O identificador da requisição é obrigatório."
            );
        }

        Connection conexao = null;
        ResultadoValidacaoPersistente resultado = null;
        RuntimeException falha = null;

        boolean transacaoEncerrada = false;
        boolean fechamentoFalhou = false;

        try {
            conexao = fornecedor.abrir();

            if (conexao == null) {
                throw new FalhaValidacaoPersistenteException();
            }

            conexao.setTransactionIsolation(
                    Connection.TRANSACTION_READ_COMMITTED
            );
            conexao.setAutoCommit(false);

            UUID departamentoId = new BloqueadorValidacaoJdbc(conexao)
                    .bloquear(requisicaoId);

            Requisicao requisicao = recuperar(
                    conexao,
                    requisicaoId,
                    departamentoId
            );

            BigDecimal saldoAnterior = requisicao
                    .getDepartamento()
                    .getSaldoDisponivel();

            ResultadoValidacao decisao =
                    new ValidarOrcamentoUseCase().executar(requisicao);

            ResultadoValidacaoPersistente candidato =
                    ResultadoValidacaoPersistente.concluida(
                            requisicao,
                            decisao
                    );

            if (exigeGravacao(decisao)) {
                new GravadorValidacaoJdbc(conexao).gravar(
                        requisicao,
                        decisao,
                        saldoAnterior
                );

                confirmar(conexao, requisicaoId);
            } else {
                // Encerra a leitura e libera os bloqueios.
                conexao.rollback();
            }

            transacaoEncerrada = true;
            resultado = candidato;
        } catch (SQLException | FalhaPersistenciaException excecao) {
            falha = new FalhaValidacaoPersistenteException();
        } catch (RuntimeException excecao) {
            if (excecao instanceof IllegalArgumentException
                    || excecao instanceof ConfirmacaoValidacaoDesconhecidaException
                    || excecao instanceof FalhaValidacaoPersistenteException) {
                falha = excecao;
            } else {
                falha = new FalhaValidacaoPersistenteException();
            }
        } finally {
            if (conexao != null) {
                if (!transacaoEncerrada) {
                    try {
                        conexao.rollback();
                    } catch (SQLException | RuntimeException excecao) {
                        if (falha != null) {
                            falha.addSuppressed(new IllegalStateException(
                                    "Não foi possível confirmar o rollback."
                            ));
                        }
                    }
                }

                try {
                    conexao.close();
                } catch (SQLException | RuntimeException excecao) {
                    fechamentoFalhou = true;

                    if (falha != null) {
                        falha.addSuppressed(new IllegalStateException(
                                "Não foi possível confirmar "
                                        + "o fechamento da conexão."
                        ));
                    }
                }
            }
        }

        if (falha != null) {
            throw falha;
        }

        if (resultado == null) {
            throw new FalhaValidacaoPersistenteException();
        }

        return fechamentoFalhou
                ? resultado.comFalhaNoFechamento()
                : resultado;
    }

    private static Requisicao recuperar(
            Connection conexao,
            UUID requisicaoId,
            UUID departamentoId
    ) {
        Requisicao requisicao = new RequisicaoRepositoryJdbc(conexao)
                .buscarPorId(requisicaoId)
                .orElseThrow(() -> new FalhaPersistenciaException(
                        "A requisição bloqueada não foi recuperada."
                ));

        if (!requisicaoId.equals(requisicao.getId())
                || requisicao.getDepartamento() == null
                || !departamentoId.equals(
                        requisicao.getDepartamento().getId()
                )) {
            throw new FalhaPersistenciaException(
                    "A requisição recuperada difere dos registros bloqueados."
            );
        }

        return requisicao;
    }

    private static boolean exigeGravacao(ResultadoValidacao resultado) {
        return switch (resultado) {
            case LIBERADA,
                    REVISAO_POR_SALDO_INSUFICIENTE,
                    REVISAO_POR_RESERVA -> true;

            case JA_LIBERADA,
                    ESTADO_NAO_PERMITIDO -> false;
        };
    }

    private static void confirmar(
            Connection conexao,
            UUID requisicaoId
    ) {
        try {
            conexao.commit();
        } catch (SQLException | RuntimeException excecao) {
            throw new ConfirmacaoValidacaoDesconhecidaException(
                    requisicaoId
            );
        }
    }
}