package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.aplicacao.excecoes.AcessoNegadoException;
import br.mackenzie.finops.aplicacao.seguranca.PoliticaAutorizacao;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

final class AutorizacaoArquitetoJdbc {
    private AutorizacaoArquitetoJdbc() {}

    static void verificar(Connection conexao, UUID usuario) throws SQLException {
        if (usuario == null || !new PoliticaAutorizacao().podeAcessarIntervencoes(
                UsuariosJdbc.identidadeBloqueada(conexao, usuario).orElseThrow(AcessoNegadoException::new))) {
            throw new AcessoNegadoException();
        }
    }
}
