# Documento de Arquitetura - Orquestrador FinOps

## 1. Visão Arquitetural
O sistema adota uma arquitetura em camadas (Layers) baseada nos princípios de Clean Architecture. O objetivo principal é garantir que as regras de negócio de aprovação de orçamento sejam isoladas da infraestrutura, frameworks (como Spring Boot) e interface de usuário.

## 2. Drivers Arquiteturais (DA)
* **DA-01. Consistência Financeira:** (Origem: RF-02, RF-04, RB-02, RB-03, RB-04 e RB-05). A decisão de liberação deve considerar saldo, teto e reserva vigentes de forma consistente com o comprometimento e a mudança de estado, inclusive em concorrência. Deve impedir saldo negativo, comprometimento duplicado e aprovação automática que atinja ou consuma a reserva. Alterações de teto devem preservar os compromissos existentes e rejeitar reduções abaixo desse valor.
* **DA-02. Isolamento do Domínio:** (Origem: RB-02, RB-03, RB-04 e RB-05). As regras de saldo, reserva, alteração de teto e transição de estados devem permanecer independentes da interface, do framework e do banco de dados.
* **DA-03. Separação entre Aprovação Técnica e Liberação:** (Origem: RF-03, RF-04 e RB-03). A aprovação técnica não compromete saldo nem autoriza provisionamento. O fluxo deve distinguir espera por ajuste quando faltar saldo e espera por autorização financeira quando a liberação utilizar a reserva. Toda liberação exige revalidação das condições financeiras.
* **DA-04. Auditabilidade das Intervenções:** (Origem: RF-05). Aprovações técnicas, rejeições, ajustes orçamentários e autorizações para utilização da reserva devem produzir registros imutáveis com ação, responsável, data, justificativa e departamento ou requisição afetada. Ajustes devem registrar teto anterior, novo teto e valor do ajuste. Autorizações de uso da reserva devem identificar a requisição e os valores de custo, saldo, teto e percentual considerados. A tabela de log append-only prevista deve preservar esse histórico.

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

### OPEN-ARQ-01 — Aprovação Técnica e Liberação Financeira

**Referências:** Issues #31 e #35; RF-02, RF-03, RF-04, RF-05; RB-02, RB-03, RB-04 e RB-05.

**Status:** Decisão inicial registrada em 08/10/2026 e ampliada em 09/10/2026. A equipe aprovou a regra de reserva conforme confirmação do responsável pelo projeto. O detalhamento documental permanece em revisão; implementação pendente.

#### Histórico — Issue #31

A decisão inicial tratava de pedidos acima do saldo disponível: aprovação técnica sem comprometimento, seguida de ajuste orçamentário autorizado e revalidação antes da liberação.

Foram mantidos dois perfis no MVP: Desenvolvedor e Arquiteto Cloud. O Arquiteto Cloud acumula análise técnica e gestão orçamentária, executadas como ações separadas e auditadas.

A Issue #35 amplia essa decisão para contemplar pedidos que possuem saldo suficiente, mas atingem ou consomem a reserva. A exigência de ajuste deixa de se aplicar indistintamente a todos os pedidos aprovados tecnicamente.

#### Decisão vigente — Issue #35

A reserva inicial corresponde a 10% do orçamento mensal do departamento, não a 10% do saldo restante.

A liberação automática exige que o custo caiba no saldo e que o saldo após o pedido fique estritamente acima da reserva.

Pedidos que atinjam ou consumam a reserva seguem para Revisão Pendente, sem desconto, mesmo quando houver saldo suficiente. Pedidos acima do saldo também seguem para revisão, com motivo distinto de insuficiência de saldo.

A aprovação técnica não altera o orçamento, não compromete saldo e não autoriza provisionamento.

Para pedidos aprovados tecnicamente:

- Quando faltar saldo, a liberação depende de ajuste orçamentário autorizado.
- Quando a liberação atingir ou consumir a reserva vigente, exige autorização financeira explícita para utilização da reserva.
- As duas condições podem coexistir: um ajuste que forneça saldo suficiente não dispensa autorização para utilização da reserva, caso ela ainda seja necessária.
- Toda liberação revalida as condições vigentes e respeita a RB-05.
- A autorização para utilização da reserva nunca permite saldo negativo.

O aumento do teto não é obrigatório quando já existe saldo suficiente e o impedimento é apenas a utilização da reserva.

Os estados e as transições em revisão estão descritos na seção 3.3 de [spec.md](spec.md). As pendências de detalhamento do fluxo manual são acompanhadas em OPEN-003-05 da [SPEC-003](casos-de-uso/SPEC-003-validar-orcamento.md).

#### Regra complementar — OPEN-MAPA-01

Alterar o teto preserva o valor comprometido e recalcula o saldo disponível. Reduções abaixo do valor comprometido são rejeitadas, mantendo os valores anteriores e explicando o motivo, conforme RB-04.

Como a reserva depende do teto, sua avaliação deve considerar o orçamento mensal vigente.

#### Justificativa e limitações do MVP

Manter dois perfis reduz a complexidade do projeto acadêmico. O Arquiteto Cloud executa aprovação técnica, autorização para utilização da reserva e ajuste de teto como ações separadas e auditadas.

Não há segregação dessas responsabilidades entre pessoas distintas no MVP. Um perfil Financeiro separado permanece fora do escopo atual.

A reserva é uma referência de governança para aprovação automática, não um bloqueio absoluto à utilização do saldo.

#### Pendências e implementação

- A configuração do percentual, suas permissões, limites, abrangência e auditoria permanecem em OPEN-003-04.
- A representação monetária e o arredondamento permanecem em OPEN-003-01.
- O mecanismo e o alcance da consistência concorrente permanecem em OPEN-003-02.
- Os casos manuais ainda não definidos permanecem em OPEN-003-05.

Este registro documenta decisões e pendências. Não comprova implementação da reserva, das autorizações, dos estados, do ajuste de teto, da auditoria ou da revalidação.
