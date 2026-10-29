package br.mackenzie.finops.aplicacao.portas;

import br.mackenzie.finops.aplicacao.entradas.SolicitacaoCriacaoRequisicao;
import br.mackenzie.finops.aplicacao.excecoes.AcessoNegadoException;
import br.mackenzie.finops.aplicacao.resultados.ResultadoCriacaoRequisicao;
import br.mackenzie.finops.aplicacao.seguranca.ContextoIdentidade;

/**
 * Contrato transacional de criação com autorização.
 *
 * A identidade deve ser obtida pela aplicação por meio de um
 * ProvedorIdentidade confiável, nunca recebida diretamente do cliente.
 *
 * Não realiza autenticação.
 */
public interface CriacaoRequisicaoAutorizadaPersistente {

    /**
     * Cria uma requisição ou reconhece uma repetição autorizada.
     *
     * Exige desenvolvedor ativo, com vínculo departamental.
     * O autor e o departamento da solicitação devem corresponder
     * ao usuário e ao departamento da identidade recebida.
     *
     * Quando o UUID já existir, a implementação deve conferir
     * a autorização sobre o autor e o departamento armazenados
     * antes de comparar o conteúdo ou devolver o pedido.
     *
     * Essa conferência também é obrigatória na reconciliação
     * após uma tentativa concorrente de inclusão.
     *
     * Pedido fora do escopo produz uma negativa genérica,
     * sem expor autor, departamento, itens, estado ou valores.
     * Divergências de conteúdo somente podem produzir conflito
     * depois de autorizado o acesso ao pedido existente.
     *
     * Preserva as garantias transacionais da criação:
     * CRIADA somente após commit confirmado, repetição sem
     * reiniciar o pedido e nenhuma alteração financeira.
     *
     * O contexto recebido representa uma fotografia da identidade.
     * Por si só, não garante que o vínculo permaneça válido diante
     * de alterações concorrentes até o commit. Essa coordenação
     * exige uma solução adicional com a fonte de identidade.
     *
     * @throws AcessoNegadoException quando a identidade ou o acesso
     *         ao pedido não permitir a operação
     * @throws IllegalArgumentException quando a solicitação for nula
     *         ou possuir dados inválidos
     */
    ResultadoCriacaoRequisicao executar(
            SolicitacaoCriacaoRequisicao solicitacao,
            ContextoIdentidade identidade
    );
}