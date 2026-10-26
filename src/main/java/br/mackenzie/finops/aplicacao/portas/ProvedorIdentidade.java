package br.mackenzie.finops.aplicacao.portas;

import br.mackenzie.finops.aplicacao.seguranca.ContextoIdentidade;

import java.util.Optional;

/**
 * Contrato para obter a identidade da execucao atual.
 *
 * A implementacao deve usar uma fonte confiavel e rejeitar perfis
 * desconhecidos ou vinculos ambiguos, sem escolher valores padrao.
 *
 * Falhas na obtencao da identidade devem impedir a operacao protegida.
 * Implementacoes nao devem compartilhar identidade mutavel entre
 * execucoes de usuarios diferentes.
 */
@FunctionalInterface
public interface ProvedorIdentidade {

    /**
     * Retorna a identidade atual ou Optional.empty() quando ausente.
     * Nunca retorna null.
     *
     * O resultado nao substitui a verificacao de autorizacao.
     */
    Optional<ContextoIdentidade> obterIdentidadeAtual();
}