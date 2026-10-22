package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.aplicacao.entradas.ItemSolicitado;
import br.mackenzie.finops.aplicacao.entradas.SolicitacaoCriacaoRequisicao;
import br.mackenzie.finops.aplicacao.excecoes.ConfirmacaoCriacaoDesconhecidaException;
import br.mackenzie.finops.aplicacao.excecoes.FalhaCriacaoPersistenteException;
import br.mackenzie.finops.aplicacao.portas.CriacaoRequisicaoPersistente;
import br.mackenzie.finops.aplicacao.resultados.ResultadoCriacaoRequisicao;
import br.mackenzie.finops.dominio.entidades.Departamento;
import br.mackenzie.finops.dominio.entidades.ItemRequisicao;
import br.mackenzie.finops.dominio.entidades.RecursoCatalogo;
import br.mackenzie.finops.dominio.entidades.Requisicao;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Coordena a criação persistente e a repetição por UUID.
 * Cada tentativa utiliza uma conexão exclusiva.
 */
public final class CriacaoRequisicaoJdbc
        implements CriacaoRequisicaoPersistente {

    private final FornecedorConexaoJdbc fornecedor;

    public CriacaoRequisicaoJdbc(FabricaConexoes fabrica) {
        if (fabrica == null) {
            throw new IllegalArgumentException(
                    "A fábrica de conexões é obrigatória."
            );
        }

        this.fornecedor = fabrica::abrir;
    }

    // Permite fornecer conexões controladas nos testes da infraestrutura.
    CriacaoRequisicaoJdbc(FornecedorConexaoJdbc fornecedor) {
        if (fornecedor == null) {
            throw new IllegalArgumentException(
                    "O fornecedor de conexões é obrigatório."
            );
        }

        this.fornecedor = fornecedor;
    }

    @Override
    public ResultadoCriacaoRequisicao executar(
            SolicitacaoCriacaoRequisicao solicitacao
    ) {
        if (solicitacao == null) {
            throw new IllegalArgumentException(
                    "A solicitação é obrigatória."
            );
        }

        try {
            return executarTentativa(solicitacao, false);
        } catch (RequisicaoDuplicadaException duplicata) {
            // A tentativa anterior já encerrou sua transação e conexão.
            // A conferência usa uma nova visão dos dados confirmados.
            return executarTentativa(solicitacao, true);
        }
    }

    private ResultadoCriacaoRequisicao executarTentativa(
            SolicitacaoCriacaoRequisicao solicitacao,
            boolean somenteConferir
    ) {
        Connection conexao = null;
        ResultadoCriacaoRequisicao resultado = null;
        RuntimeException falha = null;

        boolean transacaoEncerrada = false;
        boolean rollbackFalhou = false;
        boolean fechamentoFalhou = false;

        try {
            conexao = fornecedor.abrir();

            if (conexao == null) {
                throw new FalhaCriacaoPersistenteException();
            }

            conexao.setTransactionIsolation(
                    Connection.TRANSACTION_REPEATABLE_READ
            );
            conexao.setAutoCommit(false);

            RequisicaoRepositoryJdbc requisicoes =
                    new RequisicaoRepositoryJdbc(conexao);

            Optional<Requisicao> encontrada = requisicoes.buscarPorId(
                    solicitacao.requisicaoId()
            );

            if (encontrada.isPresent()) {
                Requisicao existente = encontrada.get();

                ComparadorSolicitacaoRequisicao.conferir(
                        solicitacao, existente
                );

                ResultadoCriacaoRequisicao repeticao =
                        ResultadoCriacaoRequisicao.jaExistente(existente);

                // Encerra a transação que apenas consultou dados.
                conexao.rollback();
                transacaoEncerrada = true;
                resultado = repeticao;
            } else {
                if (somenteConferir) {
                    throw new FalhaCriacaoPersistenteException();
                }

                Requisicao nova = construir(conexao, solicitacao);
                requisicoes.adicionar(nova);

                ResultadoCriacaoRequisicao criacao =
                        ResultadoCriacaoRequisicao.criada(nova);

                confirmar(conexao, solicitacao.requisicaoId());
                transacaoEncerrada = true;
                resultado = criacao;
            }
        } catch (SQLException | FalhaPersistenciaException excecao) {
            falha = new FalhaCriacaoPersistenteException();
        } catch (RuntimeException excecao) {
            if (excecao instanceof IllegalArgumentException
                    || excecao instanceof ConfirmacaoCriacaoDesconhecidaException
                    || excecao instanceof FalhaCriacaoPersistenteException) {
                falha = excecao;
            } else {
                falha = new FalhaCriacaoPersistenteException();
            }
        } finally {
            if (conexao != null) {
                if (!transacaoEncerrada) {
                    try {
                        conexao.rollback();
                    } catch (SQLException | RuntimeException excecao) {
                        rollbackFalhou = true;

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
                                "Não foi possível confirmar o fechamento da conexão."
                        ));
                    }
                }
            }
        }

        if (falha != null) {
            if (falha instanceof RequisicaoDuplicadaException
                    && (rollbackFalhou || fechamentoFalhou)) {
                // Não inicia a reconciliação automática se não conseguiu
                // encerrar os recursos da tentativa que perdeu a inclusão.
                FalhaCriacaoPersistenteException encerramento =
                        new FalhaCriacaoPersistenteException();
                encerramento.addSuppressed(falha);
                throw encerramento;
            }

            throw falha;
        }

        if (resultado == null) {
            throw new FalhaCriacaoPersistenteException();
        }

        return fechamentoFalhou
                ? resultado.comFalhaNoFechamento()
                : resultado;
    }

    private static Requisicao construir(
            Connection conexao,
            SolicitacaoCriacaoRequisicao solicitacao
    ) {
        DepartamentoRepositoryJdbc departamentos =
                new DepartamentoRepositoryJdbc(conexao);
        RecursoCatalogoRepositoryJdbc recursos =
                new RecursoCatalogoRepositoryJdbc(conexao);

        Departamento departamento = departamentos
                .buscarPorId(solicitacao.departamentoId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "O departamento solicitado não está cadastrado."
                ));

        Requisicao requisicao = Requisicao.criarComId(
                solicitacao.requisicaoId(), departamento
        );

        Map<UUID, RecursoCatalogo> recursosConsultados = new HashMap<>();

        for (ItemSolicitado item : solicitacao.itens()) {
            RecursoCatalogo recurso = recursosConsultados.get(
                    item.recursoId()
            );

            if (recurso == null) {
                recurso = recursos.buscarPorId(item.recursoId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Um recurso solicitado não está cadastrado."
                        ));

                recursosConsultados.put(item.recursoId(), recurso);
            }

            requisicao.adicionarItem(
                    new ItemRequisicao(recurso, item.quantidade())
            );
        }

        return requisicao;
    }

    private static void confirmar(
            Connection conexao,
            UUID requisicaoId
    ) {
        try {
            conexao.commit();
        } catch (SQLException | RuntimeException excecao) {
            throw new ConfirmacaoCriacaoDesconhecidaException(
                    requisicaoId
            );
        }
    }
}