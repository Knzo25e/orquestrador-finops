# Issue #72 — Base de identidade e autorização

## Objetivo

Registrar a implementação e a verificação local da base de identidade
e autorização prevista na SPEC-006.

## Componentes criados

- PerfilUsuario: representa Desenvolvedor e Arquiteto Cloud.
- ContextoIdentidade: representa usuário, perfil, situação ativa e departamento.
- ProvedorIdentidade: define o contrato para obter a identidade atual.
- PoliticaAutorizacao: decide acesso ao catálogo, criação e consulta.
- VerificarPoliticaAutorizacao: executa verificações sem banco.

## Ambiente

- Data da execução: 26/10/2026.
- Windows e PowerShell.
- JDK Eclipse Adoptium 25.0.2.
- Maven Wrapper do projeto.
- Compilação de 72 arquivos Java com release 25.
- Resultado da compilação: BUILD SUCCESS.

## Execução

Compilação realizada com:

    .\mvnw.cmd compile

Verificador executado com o java.exe do JDK configurado em JAVA_HOME,
classpath target/classes e classe principal:

    br.mackenzie.finops.aplicacao.VerificarPoliticaAutorizacao

Os verificadores foram executados diretamente por seus métodos main.
Não foram executados pelo Maven Surefire.

## Resultados

| Programa | Verificações aprovadas |
|---|---:|
| Main | 10 |
| VerificarValoresMonetarios | 34 |
| VerificarValidacaoOrcamentaria | 25 |
| VerificarConcorrenciaOrcamentaria | 6 |
| VerificarRepositoriosMemoria | 44 |
| VerificarConcorrenciaRepositorios | 9 |
| VerificarIntegracaoRepositorios | 39 |
| VerificarConcorrenciaIntegracao | 9 |
| VerificarReconstituicaoEntidades | 64 |
| VerificarConfiguracaoBanco | 14 |
| VerificarValidacaoCadastroJdbc | 28 |
| VerificarPoliticaAutorizacao | 34 |
| **Total desta etapa** | **316** |

Os 11 programas anteriores concluíram sem erro: 282 verificações.
O novo verificador concluiu sem erro: 34 verificações.

Nos testes concorrentes, a contagem considera as verificações
individuais, não somente a quantidade de cenários.

## Cobertura da política

- Consulta do catálogo por usuários ativos dos dois perfis.
- Criação pelo desenvolvedor somente no próprio departamento.
- Bloqueio de criação pelo arquiteto.
- Consulta do desenvolvedor limitada à autoria e departamento atual.
- Consulta do arquiteto em departamentos distintos.
- Bloqueio de acesso com identidade ausente ou usuário inativo.
- Bloqueio de criação e consulta de pedidos pelo desenvolvedor sem vínculo.
- Bloqueio de consulta pelo desenvolvedor a pedido sem autoria.
- Consulta pelo arquiteto a pedido sem autoria conhecida.
- Bloqueio de consulta a pedido sem departamento.
- Restrição de consulta após mudança do vínculo representado no contexto.
- Rejeição de contexto sem identificador ou perfil.
- Rejeição de nome de perfil desconhecido por PerfilUsuario.valueOf.
- Provedores controlados com identidade presente ou ausente.
- Preservação do contexto após as decisões.

## Limites

A política retorna decisões booleanas, mas ainda não é chamada pelos
casos de uso existentes. Esses fluxos ainda não estão protegidos por ela.

Construir ContextoIdentidade não autentica um usuário.

ProvedorIdentidade possui contrato e exemplos controlados nos testes.
Não existe adaptador real de autenticação nesta entrega.

O teste de perfil desconhecido verifica a conversão do enum.
Não comprova tratamento de credenciais em uma integração real.

A rejeição de vínculos ambíguos é obrigação contratual da futura
integração. Não foi testada com uma fonte real de identidades.

A mudança de vínculo foi representada por outro contexto.
Não houve alteração concorrente de vínculo durante uma transação.

Permanecem pendentes:
- persistência e migração da autoria;
- integração da política com os casos de uso;
- consultas e listagens filtradas no armazenamento;
- autorização da repetição de criação;
- ações manuais técnicas ou financeiras;
- respostas HTTP para acesso negado;
- autenticação de produção.

A política não recebe entidades financeiras nem executa gravações.
A preservação transacional de pedidos e saldo diante de acesso negado
deverá ser verificada na futura integração.

As verificações JDBC anteriores não foram reexecutadas nesta etapa.
O total histórico de 462 verificações não representa uma execução
completa nesta entrega.

## Situação

Base implementada e verificada localmente.
Revisão final, integração e conclusão da Issue #72 ainda pendentes.

Os resultados não comprovam atendimento integral à SPEC-006.