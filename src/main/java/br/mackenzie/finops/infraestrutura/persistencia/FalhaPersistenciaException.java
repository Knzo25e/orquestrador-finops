package br.mackenzie.finops.infraestrutura.persistencia;

/**
 * Falha de acesso ao banco ou inconsistência nos dados armazenados.
 * A mensagem deve ser segura para apresentação, sem credenciais.
 */
public final class FalhaPersistenciaException extends RuntimeException {

    public FalhaPersistenciaException(String mensagem) {
        super(mensagem);
    }
}