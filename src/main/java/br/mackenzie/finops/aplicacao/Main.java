package br.mackenzie.finops.aplicacao;

import br.mackenzie.finops.dominio.entidades.Departamento;
import br.mackenzie.finops.dominio.entidades.ItemRequisicao;
import br.mackenzie.finops.dominio.entidades.RecursoCatalogo;
import br.mackenzie.finops.dominio.entidades.Requisicao;

import java.util.List;
import java.util.UUID;

public class Main {
    public static void main(String[] args) {
        System.out.println("--- Teste de Aceite: Spec 002 ---");
        
        // 1. Preparar o cenário (Dado)
        Departamento ti = new Departamento(UUID.randomUUID(), "Engenharia e Tecnologia", 50000.0);
        RecursoCatalogo servidor = new RecursoCatalogo(UUID.randomUUID(), "Servidor Cloud AWS", 1500.0);
        ItemRequisicao item = new ItemRequisicao(servidor, 2);
        
        // 2. Executar a ação orquestrada pelo Caso de Uso (Quando)
        CriarRequisicaoUseCase casoDeUso = new CriarRequisicaoUseCase();
        Requisicao novaReq = casoDeUso.executar(ti, List.of(item));
        
        // 3. Verificar o resultado (Então)
        System.out.println("Departamento Solicitante: " + novaReq.getDepartamento().getNome());
        System.out.println("Quantidade de Itens: " + novaReq.getItens().size());
        System.out.println("Status da Requisição: " + novaReq.getStatus());
        
        if ("INICIAL".equals(novaReq.getStatus())) {
            System.out.println("✅ SUCESSO: A invariante da Spec 002 foi respeitada!");
        } else {
            System.out.println("❌ FALHA: Status incorreto.");
        }
    }
}