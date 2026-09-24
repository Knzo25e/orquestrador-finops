package br.mackenzie.finops.aplicacao;

import br.mackenzie.finops.dominio.entidades.Departamento;
import br.mackenzie.finops.dominio.entidades.ItemRequisicao;
import br.mackenzie.finops.dominio.entidades.Requisicao;

import java.util.List;

public class CriarRequisicaoUseCase {

    // A Spec 002 exige que o caso de uso orquestre a criação recebendo os dados
    public Requisicao executar(Departamento departamento, List<ItemRequisicao> itensSolicitados) {
        
        // Verificação de Invariante/Pré-condição: Departamento deve ser válido e existir itens
        if (departamento == null) {
            throw new IllegalArgumentException("Uma requisição não pode ser criada sem um departamento solicitante.");
        }
        if (itensSolicitados == null || itensSolicitados.isEmpty()) {
            throw new IllegalArgumentException("A requisição deve conter pelo menos um item de catálogo.");
        }

        // Instancia a entidade (a própria entidade deve garantir o status INICIAL em seu construtor)
        Requisicao novaRequisicao = new Requisicao(departamento);

        // Associa os itens à requisição e consolida o valor total
        for (ItemRequisicao item : itensSolicitados) {
            novaRequisicao.adicionarItem(item);
        }

        // OPEN-01: O salvamento no banco de dados (Repository) entraria aqui futuramente.
        
        return novaRequisicao;
    }
}