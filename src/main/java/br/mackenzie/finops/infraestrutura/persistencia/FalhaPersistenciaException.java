package br.mackenzie.finops.infraestrutura.persistencia;

import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;

/**
 * Falha de acesso ao banco ou inconsistência nos dados armazenados.
 * A mensagem deve ser segura para apresentação, sem credenciais.
 */
public final class FalhaPersistenciaException extends RuntimeException {
    private final boolean transitoria;

    public FalhaPersistenciaException(String mensagem) {
        this(mensagem, null);
    }

    FalhaPersistenciaException(String mensagem, SQLException causa) {
        super(mensagem);
        this.transitoria = indisponibilidade(causa);
    }

    static boolean indisponibilidade(Throwable falha) {
        return falha instanceof FalhaPersistenciaException persistencia
                ? persistencia.transitoria
                : falha instanceof SQLTransientException || falha instanceof SQLRecoverableException;
    }
}
