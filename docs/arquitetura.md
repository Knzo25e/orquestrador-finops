# Documento de Arquitetura - Orquestrador FinOps

## 1. Visão Arquitetural
O sistema adota uma arquitetura em camadas (Layers) baseada nos princípios de Clean Architecture. O objetivo principal é garantir que as regras de negócio de aprovação de orçamento sejam isoladas da infraestrutura, frameworks (como Spring Boot) e interface de usuário.

## 2. Drivers Arquiteturais (DA)
* **DA-01. Consistência do Saldo:** (Origem: RB-02, RF-02). Na aprovação automática, a validação e o desconto devem preservar o saldo não negativo do Departamento, inclusive em cenários de concorrência. O tratamento financeiro da aprovação manual acima do saldo está pendente em OPEN-ARQ-01.
* **DA-02. Isolamento do Domínio:** (Origem: RB-02, RB-03). As regras de validação e roteamento da requisição precisam rodar de forma independente da UI e do banco de dados para garantir alta testabilidade.
* **DA-03. Intervenção Manual de Exceções:** (Origem: RF-03). O fluxo precisa de um mecanismo para pausar o estado de uma requisição para aprovação assíncrona do Arquiteto.
* **DA-04. Auditabilidade de Decisões Financeiras:** (Origem: RF-05). A exigência de um histórico imutável afeta diretamente a persistência. Não podemos apenas sobrescrever o status da requisição atualizando a mesma linha no banco; precisamos adotar uma tabela de log (append-only) para garantir a rastreabilidade das aprovações manuais do Arquiteto Cloud em caso de auditoria financeira.

## 3. Decisões Técnicas (DT) e ADRs
* **DT-01:** Utilização de Arquitetura em Camadas, isolando as entidades do framework (Responde a DA-02). -> *Requer ADR (Difícil reversão)*
* **DT-02:** Validação centralizada de limite no domínio antes de qualquer integração de API externa (Responde a DA-01). -> *Não requer ADR (Fácil reversão)*

---
### ADR-001: Adoção de Arquitetura em Camadas para Isolamento do Domínio
* **Status:** Aceito
* **Contexto:** Precisamos garantir que regras financeiras críticas (como a trava de orçamento do Departamento) não fiquem acopladas em controllers ou triggers de banco de dados.
* **Decisão:** Adotaremos uma arquitetura em camadas (Domínio, Aplicação, Infraestrutura). O domínio será codificado em classes puras, sem anotações externas, sendo o único responsável por alterar o status da `Requisicao`.
* **Alternativas Consideradas:** Padrão MVC tradicional com lógica no Controller. Rejeitado porque mistura o roteamento web com as regras de negócio, dificultando testes unitários sem levantar o contexto da aplicação.
* **Consequências:** 
  * **Positivas:** Regras do domínio rodam sem banco; independência da tecnologia de persistência.
  * **Negativas:** Maior número de interfaces e classes de mapeamento no início do projeto.

### ADR-002: MySQL e persistência inicial em memória

* **Status:** Decisão registrada no mapa de Specs; formalizada neste ADR.
* **Origem:** DECISÃO-02 do mapa de Specs.
* **Referências:** ADR 01, DA-01 e DA-04.
* **Contexto:** O projeto precisa armazenar dados para permitir continuidade entre sessões. Nas fases iniciais, a validação das regras do domínio deve permanecer independente da instalação e da disponibilidade de um banco de dados.
* **Decisão:** Utilizar MySQL como SGBD relacional do projeto. Nas fases iniciais, utilizar repositórios em memória, isolados por interfaces, preservando a separação entre domínio e infraestrutura.
* **Consequências:**
  * Os repositórios em memória permitirão verificar comportamentos sem conexão com o MySQL.
  * Os dados mantidos apenas em memória serão perdidos ao encerrar a aplicação; essa etapa não atende à exigência final de persistência entre sessões.
  * A integração com MySQL exigirá implementação e verificação próprias.
  * O uso de MySQL, por si só, não garante consistência do saldo em concorrência nem imutabilidade da auditoria; esses comportamentos precisarão ser implementados e verificados.
* **Alternativas:** O mapa anterior mencionava PostgreSQL e NoSQL. Não há justificativa comparativa registrada para a escolha do MySQL; este ADR não presume que essa avaliação tenha sido realizada.
* **Situação de implementação:** A decisão tecnológica não comprova que os repositórios em memória ou a integração com MySQL já estejam implementados.

### ADR-003: Java e Spring Boot

* **Status:** Decisão registrada no mapa de Specs; formalizada neste ADR.
* **Origem:** DECISÃO-01 do mapa de Specs.
* **Referências:** ADR 01 e DA-02.
* **Contexto:** O projeto possui entidades e um caso de uso implementados em Java. A evolução da aplicação deve preservar o isolamento das regras de domínio em relação ao framework.
* **Decisão:** Desenvolver o projeto em Java utilizando Spring Boot, mantendo as entidades de domínio independentes do framework, conforme a arquitetura em camadas já adotada.
* **Consequências:**
  * A configuração do Spring Boot deverá respeitar as dependências entre as camadas.
  * As regras do domínio deverão continuar verificáveis sem iniciar o framework ou conectar a um banco.
  * A integração do framework exigirá configuração de dependências e verificação própria.
* **Alternativas:** Não há avaliação comparativa de frameworks registrada nos materiais analisados.
* **Situação de implementação:** Java já é utilizado no código analisado. A adoção de Spring Boot está documentada, mas sua configuração não foi identificada nesse código.
* **Definições pendentes:** Padronizar a versão do Java, a versão do Spring Boot e a ferramenta de build antes da integração do framework.

### ADR-004: Adiamento da integração real com a AWS

* **Status:** Decisão registrada no mapa de Specs; formalizada neste ADR.
* **Origem:** DECISÃO-03 do mapa de Specs.
* **Referência:** ADR 01.
* **Contexto:** O mapa determina que as regras de negócio de FinOps sejam consolidadas antes da conexão com APIs reais de provedores de nuvem.
* **Decisão:** Postergar a integração real com a AWS e utilizar stubs, isto é, simuladores de resposta, na etapa que envolver comunicação com a infraestrutura.
* **Consequências:**
  * A validação inicial dos fluxos poderá ocorrer sem provisionamento real na AWS.
  * Os resultados simulados deverão ser identificados como simulação.
  * Testes com stubs não comprovarão o funcionamento da integração real com a AWS.
  * Uma integração real futura exigirá definição do contrato, configuração e testes próprios.
* **Alternativas:** A integração real desde as fases iniciais foi postergada conforme a decisão já registrada no mapa.
* **Situação de implementação:** A decisão está documentada; a implementação dos stubs não foi identificada no código analisado.


## 4. Questões Arquiteturais em Aberto

### OPEN-ARQ-01 — Efeito da aprovação manual sobre o saldo

**Referências:** RB-02, RB-03, RF-02, RF-03 e DA-01.

**Dúvida:** A RB-03 permite que o Arquiteto Cloud aprove uma requisição acima do saldo disponível mediante justificativa. A documentação ainda não define como essa aprovação afeta o saldo e o orçamento do Departamento.

**Decisão necessária:** Definir se a aprovação excepcional permite saldo negativo, exige ajuste prévio do orçamento ou utiliza outro tratamento financeiro explicitamente aprovado pela equipe.

**Status:** Pendente de decisão da equipe; consultar o professor se necessário.

**Impacto:** A definição deve anteceder a implementação do efeito financeiro da aprovação manual. As alternativas acima são possibilidades para discussão, não decisões tomadas.