package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.aplicacao.portas.ConsultaCatalogoPersistente;
import br.mackenzie.finops.dominio.entidades.RecursoCatalogo;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Consulta o catálogo com conexão exclusiva por chamada.
 *
 * Não autentica usuários nem decide permissões.
 * A autorização deve ocorrer no caso de uso da aplicação.
 */
public final class ConsultaCatalogoJdbc
        implements ConsultaCatalogoPersistente {

    private static final String CONSULTA = """
            SELECT id, nome, custo_mensal
            FROM recurso_catalogo
            ORDER BY BINARY id
            """;

    private final FornecedorConexaoJdbc fornecedor;

    public ConsultaCatalogoJdbc(FabricaConexoes fabrica) {
        if (fabrica == null) {
            throw new IllegalArgumentException(
                    "A fábrica de conexões é obrigatória."
            );
        }

        this.fornecedor = fabrica::abrir;
    }

    // Permite fornecer conexões controladas nos verificadores.
    ConsultaCatalogoJdbc(FornecedorConexaoJdbc fornecedor) {
        if (fornecedor == null) {
            throw new IllegalArgumentException(
                    "O fornecedor de conexões é obrigatório."
            );
        }

        this.fornecedor = fornecedor;
    }

    @Override
    public List<RecursoCatalogo> listar() {
        try (Connection conexao = fornecedor.abrir()) {
            if (conexao == null) {
                throw new FalhaPersistenciaException(
                        "Não foi possível abrir a conexão de consulta."
                );
            }

            conexao.setTransactionIsolation(
                    Connection.TRANSACTION_READ_COMMITTED
            );
            conexao.setAutoCommit(true);

            try (
                    PreparedStatement comando =
                            conexao.prepareStatement(CONSULTA);
                    ResultSet resultado = comando.executeQuery()
            ) {
                List<RecursoCatalogo> recursos = new ArrayList<>();
                String identificadorAnterior = null;

                while (resultado.next()) {
                    RecursoCatalogo recurso = reconstituir(resultado);
                    String identificadorAtual = recurso.getId().toString();

                    if (identificadorAnterior != null
                            && identificadorAnterior.compareTo(
                                    identificadorAtual
                            ) >= 0) {
                        throw inconsistencia();
                    }

                    recursos.add(recurso);
                    identificadorAnterior = identificadorAtual;
                }

                return List.copyOf(recursos);
            }
        } catch (SQLException excecao) {
            throw new FalhaPersistenciaException(
                    "Falha ao abrir, configurar, consultar ou fechar "
                            + "os recursos da consulta do catálogo."
            );
        }
    }

    private static RecursoCatalogo reconstituir(
            ResultSet resultado
    ) throws SQLException {
        try {
            UUID id = ValidacaoCadastroJdbc.interpretarId(
                    resultado.getString("id")
            );

            String nome = ValidacaoCadastroJdbc.validarNome(
                    resultado.getString("nome")
            );

            BigDecimal custo = ValidacaoCadastroJdbc.validarValorMonetario(
                    resultado.getBigDecimal("custo_mensal"),
                    "O custo mensal"
            );

            return new RecursoCatalogo(id, nome, custo);
        } catch (IllegalArgumentException excecao) {
            throw inconsistencia();
        }
    }

    private static FalhaPersistenciaException inconsistencia() {
        return new FalhaPersistenciaException(
                "O catálogo armazenado contém dados inconsistentes."
        );
    }
}