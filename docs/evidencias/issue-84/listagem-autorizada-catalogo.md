# Issue #84 — Listagem autorizada do catálogo

## Situação

Implementação e verificações locais realizadas na branch
feat/84-listagem-autorizada-catalogo.

Revisão final, commit, PR e integração à main pendentes.

## Implementação

- Porta ConsultaCatalogoPersistente na aplicação.
- Caso de uso ListarCatalogoAutorizadoUseCase.
- Autorização pela política compartilhada antes da consulta.
- Desenvolvedor e Arquiteto ativos podem consultar sem vínculo departamental.
- Identidade ausente ou inativa impede acesso à porta de leitura.
- Falha do provedor ou resultado inválido interrompe a operação.
- Adaptador ConsultaCatalogoJdbc com conexão exclusiva por chamada.
- Ordenação pela representação textual canônica dos UUIDs.
- Lista estruturalmente não modificável.
- Validação dos dados recuperados e rejeição de resultados inconsistentes.
- Falhas não são convertidas em catálogo vazio.

## Ambiente e execução

- Execução local em 30/10/2026.
- Windows e PowerShell.
- Maven Wrapper, compilação com release 25.
- Compilação de 104 arquivos Java aprovada.
- MySQL: finops_test.
- Usuário de teste: finops_test_app@localhost.
- Nenhuma migração adicionada ou reaplicada nesta etapa.

Os verificadores foram executados explicitamente pelos métodos main.
A compilação Maven não representa execução dos testes.

## Resultados

| Verificador | Ambiente | Verificações |
|---|---|---:|
| VerificarListagemCatalogoAutorizada | Sem banco | 23 |
| VerificarRecursosConsultaCatalogoJdbc | JDBC controlado, sem banco | 18 |
| VerificarListagemCatalogoJdbc | MySQL | 8 |
| VerificarPoliticaAutorizacao | Sem banco, regressão | 34 |
| **Total** | | **83** |

Total: 49 verificações novas e 34 de regressão.
São 75 verificações sem banco e oito com MySQL.

As 648 verificações registradas na Issue #76 não foram
reexecutadas integralmente nem somadas a esta entrega.

## Autorização e contrato

Foram verificados bloqueio antes do armazenamento, propagação
das falhas, acesso dos dois perfis ativos sem vínculo,
catálogo vazio e preservação de nomes e valores.

Também foram verificadas a ordem textual dos UUIDs,
a rejeição de duplicatas e elementos nulos, a proteção estrutural
da lista e sua independência em relação à lista do adaptador.

A identidade é obtida no início da chamada.
Este fluxo não coordena alterações concorrentes da identidade.

## Recursos JDBC

Conexões controladas verificaram chamadas de fechamento
de resultado, comando e conexão no sucesso e nas falhas
de configuração, preparação, execução, leitura e fechamento.

Quando close lança uma exceção, o teste comprova a tentativa
de fechamento dos recursos adquiridos, não o encerramento
efetivo de um recurso real.

Conexão nula e falha de abertura foram rejeitadas.

## Integração MySQL e limpeza

O teste adicionou dois recursos temporários com UUIDs iniciados
por 0 e 8, inseridos na ordem inversa à esperada na consulta.

Foram verificados:

- Catálogo completo para Desenvolvedor e Arquiteto sem vínculo.
- Ordem textual canônica dos UUIDs.
- Lista do adaptador JDBC protegida contra inclusão.
- Nomes e valores preservados, conferidos por outra conexão.
- Remoção dos dois recursos temporários.
- Preservação de todos os recursos preexistentes.

A limpeza utilizou os UUIDs e os nomes com marcador exclusivo
da execução. Não foi utilizada exclusão geral do catálogo.

O teste pressupõe ausência de outras alterações concorrentes
no catálogo durante a comparação com o conteúdo inicial.

## Limites

- Nenhum endpoint HTTP, login, sessão ou integração Spring Boot.
- Identidades controladas não representam autenticação de produção.
- Não foi executada a regressão completa do projeto.
- Catálogo vazio foi verificado sem banco; não foi necessário
  esvaziar o catálogo real.
- Dados corrompidos no MySQL não foram injetados nesta execução.
- A lista é estruturalmente protegida; seus elementos não são
  declarados imutáveis.
- A consulta retorna o catálogo completo; limites operacionais
  e eventual paginação permanecem acompanhados em OPEN-004-05.
- Não foram executados testes de desempenho ou carga.

## Referências

- Issue #84.
- Issue #81.
- [SPEC-004](../../casos-de-uso/SPEC-004-interface-aplicacao.md).
- [SPEC-006](../../casos-de-uso/SPEC-006-perfis-acesso.md).