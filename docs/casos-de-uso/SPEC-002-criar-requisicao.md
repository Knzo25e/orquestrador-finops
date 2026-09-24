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

## Questões em Aberto
* **OPEN-01:** O mecanismo de persistência (SGBD relacional ou NoSQL) ainda não foi definido pela arquitetura. A implementação atual utilizará repositórios em memória ou interfaces (Ports) para isolar essa decisão.