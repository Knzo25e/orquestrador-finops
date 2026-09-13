# Documento de Arquitetura - Orquestrador FinOps

## 1. Visão Arquitetural
O sistema adota uma arquitetura em camadas (Layers) baseada nos princípios de Clean Architecture. O objetivo principal é garantir que as regras de negócio de aprovação de orçamento sejam isoladas da infraestrutura, frameworks (como Spring Boot) e interface de usuário.

## 2. Drivers Arquiteturais (DA)
* **DA-01. Consistência Forte de Saldo:** (Origem: RN-02, RF-02). O saldo disponível do Departamento não pode ficar negativo em cenários de concorrência.
* **DA-02. Isolamento do Domínio:** (Origem: RN-02, RN-03). As regras de validação e roteamento da requisição precisam rodar de forma independente da UI e do banco de dados para garantir alta testabilidade.
* **DA-03. Intervenção Manual de Exceções:** (Origem: RF-03). O fluxo precisa de um mecanismo para pausar o estado de uma requisição para aprovação assíncrona do Arquiteto.
* **DA-04. Auditabilidade de Decisões Financeiras:** (Origem: RF-05). A exigência de um histórico imutável afeta diretamente a persistência. Não podemos apenas sobrescrever o status da requisição atualizando a mesma linha no banco; precisamos adotar uma tabela de log (append-only) para garantir a rastreabilidade das aprovações manuais do Arquiteto Cloud em caso de auditoria financeira.

## 3. Decisões Técnicas (DT) e ADRs
* **DT-01:** Utilização de Arquitetura em Camadas, isolando as entidades do framework (Responde a DA-02). -> *Requer ADR (Difícil reversão)*
* **DT-02:** Validação centralizada de limite no domínio antes de qualquer integração de API externa (Responde a DA-01). -> *Não requer ADR (Fácil reversão)*

---

### ADR 01: Adoção de Arquitetura em Camadas para Isolamento do Domínio
* **Status:** Aceito
* **Contexto:** Precisamos garantir que regras financeiras críticas (como a trava de orçamento do Departamento) não fiquem acopladas em controllers ou triggers de banco de dados.
* **Decisão:** Adotaremos uma arquitetura em camadas (Domínio, Aplicação, Infraestrutura). O domínio será codificado em classes puras, sem anotações externas, sendo o único responsável por alterar o status da `Requisicao`.
* **Alternativas Consideradas:** Padrão MVC tradicional com lógica no Controller. Rejeitado porque mistura o roteamento web com as regras de negócio, dificultando testes unitários sem levantar o contexto da aplicação.
* **Consequências:** 
  * **Positivas:** Regras do domínio rodam sem banco; independência da tecnologia de persistência.
  * **Negativas:** Maior número de interfaces e classes de mapeamento no início do projeto.