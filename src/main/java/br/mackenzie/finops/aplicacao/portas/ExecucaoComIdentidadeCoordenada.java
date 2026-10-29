package br.mackenzie.finops.aplicacao.portas;

import br.mackenzie.finops.aplicacao.seguranca.ContextoIdentidade;

import java.util.function.Function;

/**
 * Executa uma operação mantendo válida a identidade utilizada
 * na autorização durante toda a execução da função recebida.
 *
 * A identidade deve vir de uma fonte confiável.
 * Este contrato não implementa autenticação.
 */
public interface ExecucaoComIdentidadeCoordenada {

    /**
     * Obtém a identidade dentro da coordenação e executa a operação.
     *
     * A coordenação deve abranger os atributos usados na autorização:
     * identificador do usuário, perfil, situação ativa e departamento.
     *
     * Uma alteração incompatível desses atributos não pode tornar
     * inválida a autorização enquanto a operação estiver em andamento.
     *
     * A função deve executar de forma síncrona. Para uma criação
     * persistente, sua execução deve incluir a transação, a confirmação
     * e eventual reconciliação. Não deve retornar antes desse trabalho
     * nem transferi-lo para uma tarefa em segundo plano.
     *
     * Identidade ausente ou falha ao estabelecer a coordenação impede
     * a chamada da função. A identidade fornecida nunca é nula.
     * A política da aplicação continua responsável por decidir
     * se o perfil, a situação e o vínculo permitem a operação.
     *
     * A coordenação deve ser liberada também quando a função falhar.
     * Uma falha não autoriza repetir automaticamente a função, pois
     * uma operação persistente pode ter sido confirmada.
     *
     * Todos os caminhos que alteram a identidade devem participar
     * do mesmo protocolo de coordenação. Um bloqueio local só atende
     * ao contrato quando a fonte e seus escritores estão limitados
     * ao mesmo processo.
     *
     * @param operacao trabalho executado sob a identidade coordenada
     * @return resultado produzido pela operação
     */
    <T> T executar(
            Function<ContextoIdentidade, T> operacao
    );
}