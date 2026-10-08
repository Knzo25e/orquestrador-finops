# Spec 002: Criar Requisição de Recurso

## Identificação
**Objetivo:** Orquestrar a criação de uma nova requisição de recursos vinculada a um departamento do sistema FinOps.

## Rastreabilidade
* **Casos de Uso:** UC01 (Criar Requisição)
* **Requisitos:** RF01
* **Entidades de Domínio:** Departamento, Requisicao, ItemRequisicao, RecursoCatalogo

## Escopo
* **Incluído:** Instanciação da entidade Requisição, associação de itens do catálogo e definição do status inicial.
* **Fora do escopo:** Validação de limite de orçamento (pertence à Spec 003) e persistência definitiva em banco de dados físico.

## Comportamento
* **Pré-condições:** O departamento solicitante deve existir e ser válido.
* **Fluxo Principal:** O caso de uso recebe os dados da solicitação, delega a criação para as entidades de domínio, consolida o valor total e retorna a requisição estruturada.

## Invariantes
* Uma requisição nunca pode ser instanciada sem estar vinculada a um departamento.
* O status de uma requisição recém-criada deve ser obrigatoriamente `INICIAL`.

## Critérios de Aceitação
**Cenário 1: Criação de requisição com sucesso**
* **Dado** um departamento válido e uma lista contendo pelo menos um item de catálogo
* **Quando** o usuário solicita a criação da requisição
* **Então** o sistema gera a requisição com o status `INICIAL` e calcula o valor total corretamente com base nos itens.

**Cenário 2: Total atualizado após adicionar outro item**
* **Dado** uma requisição em status `INICIAL`, contendo duas unidades de um recurso de R$ 1.500,00, com total de R$ 3.000,00
* **Quando** são adicionadas três unidades de outro recurso de R$ 200,00
* **Então** o total passa a ser R$ 3.600,00 e o status permanece `INICIAL`.

**Cenário 3: Recálculo sem duplicação**
* **Dado** uma requisição cujo total dos itens é R$ 3.600,00
* **Quando** o cálculo do total é executado novamente sem alteração dos itens
* **Então** o valor retornado e o total armazenado permanecem R$ 3.600,00.

## Decisões e Pendências de Persistência

* **OPEN-01 — Resolvida quanto à escolha do SGBD:** MySQL foi definido como banco relacional do projeto, conforme o ADR-002 em [arquitetura.md](../arquitetura.md).
* **Estratégia inicial:** O ADR-002 prevê repositórios em memória, isolados por interfaces, nas fases iniciais de validação do domínio. Essa decisão não comprova que esses repositórios já estejam implementados.
* **Limite desta Spec:** A persistência definitiva em banco de dados continua fora do escopo da SPEC-002, conforme sua seção de escopo.
* **Trabalho posterior:** A implementação e a verificação da persistência entre sessões serão detalhadas na Spec 005 do [mapa de Specs](../mapa-de-specs.md).

## Registro de Verificação — Issue #26

**Data:** 08/10/2026  
**Forma de execução:** comando Run do VS Code, executando a classe `Main`.  
**Ambiente observado:** Eclipse Adoptium JDK 25.0.2.  
**Evidência:** saída da execução local compartilhada pelo responsável pela alteração.

Verificações executadas:
- Total inicial de duas unidades de R$ 1.500,00: R$ 3.000,00.
- Status inicial da requisição: `INICIAL`.
- Total após adicionar três unidades de R$ 200,00: R$ 3.600,00.
- Recálculo sem alteração dos itens: valor retornado e armazenado de R$ 3.600,00.
- Status após adicionar itens: `INICIAL`.

**Resultado:** as cinco verificações passaram.

**Limite da verificação:** execução de verificações programadas em `Main.java`, sem framework de testes. Não comprova validação de orçamento, persistência ou atendimento a todas as invariantes da Spec.

**Arquivos envolvidos:**
- `src/main/java/br/mackenzie/finops/dominio/entidades/Requisicao.java`
- `src/main/java/br/mackenzie/finops/aplicacao/Main.java`