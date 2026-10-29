# SPEC-006 — Perfis, identidade e controle de acesso

## Identificação

- Status: base de identidade e persistência de autoria integradas; autorização dos fluxos persistentes implementada e verificada localmente na Issue #76, com identidade controlada e coordenação de vínculo no mesmo processo; integração desta entrega à main e autenticação de produção pendentes.
- Objetivo: identificar o usuário responsável pelas operações e controlar
  o acesso conforme seu perfil, sua autoria e seu vínculo departamental.
- Dependência: SPEC-001 — Domínio.
- Integrações: SPEC-002 — Criação, SPEC-003 — Validação,
  SPEC-005 — Persistência e futura SPEC-004 — API REST.

## Rastreabilidade

- RF-01: solicitação de recursos pelo desenvolvedor.
- RF-03: análise técnica pelo arquiteto.
- RF-04: gestão de orçamento pelo arquiteto.
- RF-05: identificação do responsável pelas ações manuais.
- RNF-02: isolamento entre departamentos.
- RB-03: separação entre aprovação técnica e autorização financeira.
- OPEN-ARQ-01: dois perfis no MVP, Desenvolvedor e Arquiteto Cloud.

## Escopo

Esta especificação define:

- identidade confiável do usuário;
- perfis e permissões;
- vínculo do desenvolvedor com um departamento;
- autoria das requisições;
- autorização de criação e consulta;
- comportamento diante de acesso negado.

A definição de uma permissão não implementa a operação correspondente.
Aprovação manual, ajustes orçamentários e autorização de uso da reserva
dependem de suas próprias especificações.

## Perfis

### Desenvolvedor

- Possui exatamente um departamento ativo para criar requisições.
- Consulta o catálogo.
- Cria requisições somente para seu departamento.
- Consulta somente requisições de sua autoria e do seu departamento atual.
- Não executa ações técnicas ou financeiras reservadas ao arquiteto.

### Arquiteto Cloud

- Consulta o catálogo.
- Consulta requisições de todos os departamentos.
- Pode exercer as ações de governança previstas nas respectivas
  especificações.
- Não cria requisições de infraestrutura.
- Não representa um terceiro perfil financeiro no MVP.

Aprovação técnica e autorização financeira permanecem ações distintas,
mesmo quando realizadas pelo mesmo arquiteto.

## Matriz de permissões

| Operação | Desenvolvedor | Arquiteto Cloud |
|---|---|---|
| Consultar catálogo | Permitido | Permitido |
| Criar requisição | Próprio departamento | Negado |
| Consultar requisição | Própria autoria e departamento atual | Todos os departamentos |
| Listar requisições | Própria autoria e departamento atual | Todos os departamentos |
| Aprovar ou rejeitar tecnicamente | Negado | Previsto em especificação própria |
| Ajustar orçamento | Negado | Previsto em especificação própria |
| Autorizar uso da reserva | Negado | Previsto em especificação própria |

Operações sem permissão expressamente definida devem ser negadas.

## Regras de acesso

### RA-01 — Identidade confiável

A identidade deve ser obtida por uma integração confiável de autenticação.

Informar um UUID, perfil ou departamento no conteúdo de uma solicitação
não autentica o usuário nem concede permissão.

A aplicação recebe um contexto de identidade estabelecido pela integração.
O mecanismo concreto de autenticação será definido antes da exposição
da API a usuários.

Adaptadores de teste podem fornecer identidades controladas, mas não
constituem autenticação de produção.

### RA-02 — Usuário habilitado

Operações protegidas exigem usuário identificado, ativo e com perfil
reconhecido.

Identidade ausente, usuário inativo ou perfil desconhecido devem impedir
a operação antes de qualquer alteração de negócio.

### RA-03 — Vínculo departamental

A criação exige um vínculo ativo do desenvolvedor com exatamente um
departamento.

O departamento utilizado deve ser determinado pelo vínculo confiável.
Caso a entrada também informe um departamento, ele deve corresponder
ao vínculo; divergências devem ser rejeitadas.

Ausência ou ambiguidade de vínculo impede a criação.

### RA-04 — Autoria

A autoria da requisição deve ser atribuída ao desenvolvedor identificado,
sem permitir que ele escolha outro autor na entrada.

Autor e departamento da requisição devem ser preservados na persistência
e não podem ser modificados por operações de consulta ou repetição.

### RA-05 — Consulta individual

O desenvolvedor somente pode consultar uma requisição quando:

- é seu autor; e
- o departamento da requisição corresponde ao seu vínculo atual.

O arquiteto pode consultar requisições de todos os departamentos.

Conhecer o UUID de uma requisição não concede acesso.

### RA-06 — Listagem

A listagem do desenvolvedor deve aplicar os mesmos critérios de acesso
da consulta individual.

Filtros, contagens e paginação não podem revelar requisições fora de
seu escopo autorizado.

### RA-07 — Acesso negado

Uma tentativa não autorizada não pode alterar:

- requisição, itens, estado ou motivo de revisão;
- saldo ou teto do departamento;
- autoria ou vínculos persistidos.

A resposta ao desenvolvedor não deve revelar conteúdo ou confirmar
a existência de uma requisição fora de seu escopo.

A representação HTTP dessas respostas será definida na SPEC-004.

### RA-08 — Repetição de criação

O controle de acesso também deve ser aplicado às tentativas repetidas.

Um UUID já utilizado não permite recuperar uma requisição de outro autor
ou departamento.

O reconhecimento de uma criação equivalente deve considerar a autoria
e o departamento, além dos critérios de equivalência já definidos na
SPEC-005.

Uma tentativa sem autorização não deve retornar o pedido existente.

### RA-09 — Separação entre acesso e decisão financeira

Permissão de acesso não substitui validação financeira.

Nenhum perfil pode liberar recursos ignorando saldo, reserva ou
consistência transacional.

A validação automática é uma operação interna do fluxo autorizado,
não uma permissão para o desenvolvedor escolher o resultado financeiro.

### RA-10 — Mudança de vínculo

A gestão de transferências departamentais não integra esta entrega.

Se o vínculo atual divergir do departamento de uma requisição anterior,
o desenvolvedor não terá acesso a ela por esta política.

A requisição mantém sua autoria e seu departamento originais.
O arquiteto mantém a consulta para governança.

### RA-11 — Dados anteriores à implementação

Requisições sem autoria confiável não devem receber um autor presumido.

Até existir uma estratégia explícita de migração, elas não serão
disponibilizadas para consulta por desenvolvedores.

Sua consulta pelo arquiteto permanece permitida.

## Diretrizes de implementação

- Centralizar a autorização na camada de aplicação, reutilizável pelos
  diferentes adaptadores de entrada.
- Manter regras financeiras no domínio.
- Manter detalhes de autenticação e persistência na infraestrutura.
- Não depender exclusivamente de verificações no controlador HTTP.
- Garantir coerência entre a autorização e a gravação, evitando que
  mudanças concorrentes de vínculo permitam uma criação indevida.
- Preservar os casos de uso existentes durante a integração, com
  regressão explícita dos comportamentos já implementados.
- Verificar o modelo Java e o esquema MySQL antes de definir a migração
  de autoria; a presença de um atributo na documentação não comprova
  sua implementação.

## Critérios de aceite

| ID | Cenário | Resultado esperado |
|---|---|---|
| C01 | Desenvolvedor ativo cria no próprio departamento | Criação permitida; autor vem da identidade confiável |
| C02 | Desenvolvedor informa outro departamento | Operação negada, sem gravação ou alteração de saldo |
| C03 | Entrada tenta informar outro autor | Autoria não é substituída; entrada incompatível é rejeitada |
| C04 | Desenvolvedor consulta pedido próprio do departamento atual | Consulta permitida |
| C05 | Desenvolvedor consulta pedido de colega do mesmo departamento | Acesso negado, sem exposição do pedido |
| C06 | Desenvolvedor consulta pedido de outro departamento | Acesso negado, sem exposição do pedido |
| C07 | Desenvolvedor lista pedidos | Somente pedidos próprios do departamento atual |
| C08 | Arquiteto consulta pedidos de departamentos distintos | Consulta permitida |
| C09 | Arquiteto tenta criar requisição | Operação negada |
| C10 | Identidade ausente, inativa ou com perfil desconhecido | Operação protegida negada |
| C11 | Desenvolvedor sem vínculo ou com vínculo ambíguo tenta criar | Operação negada |
| C12 | Desenvolvedor tenta ação exclusiva do arquiteto | Operação negada |
| C13 | Repetição autorizada de criação equivalente | Preserva autoria, departamento e comportamento idempotente |
| C14 | Outro desenvolvedor reutiliza UUID existente | Não recebe o pedido nem altera os dados |
| C15 | Consulta de pedido próprio de departamento anterior | Acesso negado ao desenvolvedor |
| C16 | Pedido sem autoria confiável | Não aparece nas consultas do desenvolvedor |
| C17 | Tentativa de acesso negado | Preserva pedido, itens, estado, saldo e teto |
| C18 | Acesso por adaptadores de entrada diferentes | Aplica a mesma política de autorização |
| C19 | Vínculo muda durante tentativa de criação | Não confirma criação baseada em autorização inválida |

## Fora de escopo

- Implementação de login, senhas, sessões ou tokens.
- Escolha de provedor de identidade.
- Cadastro administrativo e transferência de usuários.
- Interface do portal.
- Implementação das operações manuais de governança.
- Implementação do histórico imutável de auditoria.
- Provisionamento real na AWS.

Esses itens permanecem pendentes. A API não deve ser considerada segura
para uso real apenas pela existência desta especificação.

## Evidências

Há implementação parcial da política isolada, descrita na seção seguinte.
Os critérios de aceite dos fluxos integrados ainda não foram executados.

Os testes anteriores de domínio e JDBC não comprovam autenticação,
autoria ou autorização por usuário.

## Implementação inicial — Issue #72

A base de identidade e autorização foi implementada e verificada
localmente, com decisões isoladas para catálogo, criação e consulta.

Foram aprovadas 34 verificações da política e 282 verificações anteriores
sem banco, totalizando 316 nesta etapa.

A política ainda não está integrada aos casos de uso ou ao JDBC.
Autenticação real, persistência da autoria, listagem protegida e
autorização da repetição permanecem pendentes.

Evidências e limites:
[Issue #72](../evidencias/issue-72/base-identidade-autorizacao.md).

## Persistência da autoria — Issue #74

A autoria passou a ser preservada no domínio, na criação persistente,
na gravação JDBC e na recuperação por busca e listagem.

O identificador do desenvolvedor é imutável na entidade.
A criação explícita com autoria exige autor não nulo.
Os caminhos anteriores permanecem compatíveis com autoria desconhecida.

A migração V002 adiciona desenvolvedor_id, permitindo NULL, e um índice
por autor e departamento. Registros anteriores não recebem autor presumido.
A migração deve ser aplicada uma única vez, após V001.

A repetição de criação considera a autoria, além dos critérios anteriores.
Autores diferentes ou divergência entre autoria conhecida e ausente
produzem conflito. Duas autorias ausentes preservam a compatibilidade.

As verificações locais totalizaram 540 aprovações:
339 sem banco e 201 JDBC, incluindo 44 novas nesta entrega.
Um cenário preparado antes da migração preservou seus dados e permaneceu
sem autoria após V002. Sua limpeza foi confirmada.

### Limites deste recorte

Persistir autoria não autentica o usuário nem comprova autorização.

A política da Issue #72 ainda não está integrada aos casos de uso.
Busca e listagem preservam autoria, mas ainda não restringem o acesso
conforme o usuário. A comparação de autoria na repetição também não
substitui a autorização do solicitante.

A integração da identidade confiável, a proteção dos fluxos e a
coerência diante de mudanças concorrentes de vínculo permanecem pendentes.

As pendências descritas na seção da Issue #72 representam a situação
daquela entrega; a persistência de autoria foi tratada neste recorte.

Evidências e limites:
[Issue #74](../evidencias/issue-74/autoria-requisicoes.md).

## Autorização dos fluxos persistentes — Issue #76

A criação coordenada obtém a identidade por contrato confiável e deriva
dela o autor e o departamento. A entrada não recebe esses identificadores
como escolhas do solicitante.

Identidade ausente, inativa, sem vínculo exigido ou falha na obtenção
impede a operação. O Arquiteto Cloud não pode criar requisições.

Consulta e listagem do Desenvolvedor utilizam filtros JDBC por autoria
e departamento, aplicados antes da reconstituição dos pedidos.
Na consulta por UUID, pedidos fora do escopo e inexistentes retornam
ausência.

O Arquiteto Cloud pode consultar pedidos de departamentos distintos,
inclusive aqueles sem autoria conhecida.

A repetição de criação verifica o acesso ao pedido armazenado antes
de comparar seu conteúdo. A conferência também ocorre na nova transação
de reconciliação após disputa pelo UUID.

### Coordenação da identidade

CriarRequisicaoCoordenadaUseCase exige uma execução coordenada que
abrange autorização, operação transacional e eventual reconciliação.

Uma fonte controlada demonstrou que a alteração do vínculo aguarda
o commit da criação em andamento. Operações posteriores utilizam
o novo vínculo, sem transferir a autoria ou o departamento dos pedidos
anteriores.

A implementação utilizada nos testes coordena somente o mesmo processo.
Uma integração real depende de um provedor que mantenha essa garantia
com todos os escritores da identidade, inclusive entre processos
quando aplicável.

As consultas usam a identidade obtida no início da chamada; não
receberam a mesma coordenação da criação.

### Entradas e compatibilidade

A entrada intermediária CriarRequisicaoAutorizadaUseCase é interna
ao pacote da aplicação.

CriarRequisicaoPersistenteUseCase permanece disponível para
compatibilidade interna e verificadores anteriores. Não deve ser
exposto por adaptadores de usuário.

Adaptadores de usuário devem utilizar CriarRequisicaoCoordenadaUseCase
para criação, ConsultarRequisicaoAutorizadaUseCase para busca e
ListarRequisicoesAutorizadasUseCase para listagem.

Dois adaptadores controlados, tipado e textual, foram verificados
delegando aos mesmos casos de uso, sem duplicar regras de autorização.
Não foram implementados API REST ou portal.

### Evidências e limites

Foram aprovadas 648 verificações distintas: 415 sem banco e 233 JDBC,
incluindo 117 novas. Limpeza dos cenários JDBC confirmada.

As nove verificações de VerificarAutoriaJdbc da Issue #74 não foram
reexecutadas, pois dependem do cenário anterior à migração já removido.

Autenticação de produção, provedor real e coordenação distribuída
não foram implementados. Os caminhos internos de compatibilidade
continuam exigindo delimitação na composição dos futuros adaptadores.

As pendências registradas nas seções anteriores descrevem o estado
das respectivas entregas. Esta implementação não representa
atendimento integral à SPEC-006.

Evidências:
[Issue #76](../evidencias/issue-76/autorizacao-requisicoes.md).