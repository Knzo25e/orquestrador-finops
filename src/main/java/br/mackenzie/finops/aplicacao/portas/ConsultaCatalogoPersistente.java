package br.mackenzie.finops.aplicacao.portas;

import br.mackenzie.finops.dominio.entidades.RecursoCatalogo;

import java.util.List;

/**
 * Leitura persistente do catálogo.
 *
 * A implementação controla os recursos necessários à consulta.
 * A aplicação verifica a identidade e a autorização antes da chamada.
 *
 * Este contrato não autentica usuários e não altera recursos.
 */
public interface ConsultaCatalogoPersistente {

    /**
     * Recupera todos os recursos em ordem crescente pela representação
     * textual canônica dos UUIDs.
     *
     * Retorna uma lista não modificável, sem elementos nulos.
     * Catálogo sem recursos retorna uma lista vazia.
     *
     * Identificadores, nomes e custos mensais devem ser preservados.
     * Dados inconsistentes ou falhas de leitura interrompem a operação,
     * sem devolver lista parcial nem representar a falha como lista vazia.
     *
     * A proteção da lista é estrutural: não implica que seus elementos
     * sejam imutáveis.
     *
     * @return recursos recuperados do armazenamento
     */
    List<RecursoCatalogo> listar();
}