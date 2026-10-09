# Documento de Arquitetura - Orquestrador FinOps

## 1. Visão Arquitetural
O sistema adota uma arquitetura em camadas (Layers) baseada nos princípios de Clean Architecture. O objetivo principal é garantir que as regras de negócio de aprovação de orçamento sejam isoladas da infraestrutura, frameworks (como Spring Boot) e interface de usuário.

## 2. Drivers Arquiteturais (DA)
* **DA-01. Consistência do Saldo:** (Origem: RF-02, RF-04, RB-02, RB-03, RB-04 e RB-05). Toda liberação deve verificar o saldo e comprometer o valor de forma consistente, inclusive em concorrência, impedindo saldo negativo e comprometimento duplicado da mesma requisição. Alterações de teto devem preservar os compromissos existentes e rejeitar reduções abaixo desse valor.
* **DA-02. Isolamento do Domínio:** (Origem: RB-02, RB-03, RB-04 e RB-05). As regras de validação, alteração de teto e transição de estados precisam permanecer independentes da interface, do framework e do banco de dados.
* **DA-03. Separação entre Aprovação Técnica e Liberação:** (Origem: RF-03, RF-04 e RB-03). A aprovação técnica encaminha a requisição para AGUARDANDO_AJUSTE_ORCAMENTARIO sem comprometer saldo. O fluxo deve permitir espera por ajuste autorizado e posterior revalidação antes da liberação.
* **DA-04. Auditabilidade das Intervenções:** (Origem: RF-05). Aprovações técnicas, rejeições e ajustes orçamentários devem produzir registros imutáveis com ação, responsável, data, justificativa e departamento ou requisição afetada. Ajustes devem registrar teto anterior, novo teto e valor do ajuste. A tabela de log append-only prevista deve preservar o histórico, em vez de apenas sobrescrever o estado atual.

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


## 4. Registro de Resolução de Questões

### OPEN-ARQ-01 — Efeito da aprovação manual sobre o saldo

**Referências:** Issue #31; RF-02, RF-03, RF-04, RF-05; RB-02, RB-03, RB-04 e RB-05.

**Status:** Decisão definida em 08/10/2026; implementação pendente.

**Questão original:** Como tratar a aprovação de uma solicitação cujo custo excede o saldo disponível, preservando a consistência financeira?

**Decisão:** Manter dois perfis no MVP: Desenvolvedor e Arquiteto Cloud. O Arquiteto Cloud acumula a análise técnica e a gestão orçamentária, executadas como ações separadas.

A aprovação técnica não altera o orçamento, não compromete saldo e não autoriza provisionamento. A requisição passa para AGUARDANDO_AJUSTE_ORCAMENTARIO.

A liberação depende de ajuste orçamentário autorizado pelo Arquiteto Cloud, devidamente auditado, seguido de revalidação do saldo. Se o saldo continuar insuficiente, a requisição permanece aguardando. Se suficiente, a liberação deve respeitar a operação consistente e a proteção contra comprometimento duplicado da RB-05.

**Regra complementar — OPEN-MAPA-01:** Alterar o teto preserva o valor comprometido e recalcula o saldo disponível. Reduções abaixo do valor comprometido são rejeitadas, mantendo os valores anteriores e explicando o motivo, conforme RB-04.

**Justificativa do recorte:** Manter os dois perfis existentes reduz a complexidade do MVP acadêmico, preservando a distinção entre aprovação técnica e autorização de orçamento.

**Limitação assumida:** A mesma pessoa pode aprovar tecnicamente uma solicitação e autorizar o ajuste do orçamento. Não há segregação dessas responsabilidades entre pessoas distintas no MVP. Um perfil Financeiro separado é uma possibilidade futura, fora do escopo atual.

**Situação de implementação:** Este registro documenta a decisão. Não comprova implementação do novo estado, do ajuste de teto, da auditoria ou da revalidação.
