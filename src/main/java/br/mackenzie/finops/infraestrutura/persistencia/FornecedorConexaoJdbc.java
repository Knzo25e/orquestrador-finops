package br.mackenzie.finops.infraestrutura.persistencia;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Fornece uma nova conexão exclusiva para cada chamada.
 * A operação que solicita a conexão assume seu fechamento.
 */
@FunctionalInterface
interface FornecedorConexaoJdbc {

    Connection abrir() throws SQLException;
}