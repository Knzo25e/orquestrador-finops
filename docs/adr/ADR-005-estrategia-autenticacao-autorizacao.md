# ADR-005: Estratégia de Autenticação por Sessão, TLS Local e Autorização por Perfil

## Contexto
O Orquestrador FinOps possui dois perfis distintos (`DESENVOLVEDOR` e `ARQUITETO`) com segregação estrita de responsabilidades (`SPEC-006`): desenvolvedores só podem criar e visualizar suas próprias requisições, enquanto arquitetos possuem visão global, ajustam tetos orçamentários e intervêm manualmente em pedidos retidos. Como o projeto adota Java puro sem Spring Security (`ADR-002`), era necessário definir um mecanismo de autenticação, transporte seguro e controle de acesso que protegesse os endpoints no backend sem depender de frameworks externos.

## Decisão
Adotar autenticação baseada em sessão HTTP gerenciada no servidor (`SegurancaHttp` e `SessaoController`) combinada com transporte TLS local (`finops-servidor.properties`, porta `8443`, keystore `PKCS12`) e autorização explícita na camada de aplicação:
1. **Proteção de Cookie de Sessão:** Configuração de cookies com flags `HttpOnly`, `Secure` e `SameSite=Lax`, com expiração explícita (`30m`), mitigando roubo de sessão via XSS e ataques CSRF.
2. **Autorização Desacoplada da Interface:** Ocultar botões no HTML (`desenvolvedor.html` e `arquiteto.html`) não substitui a validação no servidor; todos os casos de uso sensíveis são protegidos por decoradores (`*AutorizadaUseCase`) apoiados em `PoliticaAutorizacao`, lançando `AcessoNegadoException` (`HTTP 403`) em tentativas não autorizadas.
3. **Isolamento por Autoria:** O vínculo entre a requisição e o identificador do usuário autenticado é gravado na criação (`V002__adicionar_autoria_requisicao.sql`) e filtrado diretamente nas consultas JDBC do perfil `DESENVOLVEDOR`.

## Consequências
- **Positivas:** Controle total e auditável sobre o fluxo de identidade e permissões; ausência de dependência de bibliotecas externas de segurança; bloqueio efetivo de escalonamento horizontal (um desenvolvedor acessar pedidos de outro) e vertical (desenvolvedor aprovar o próprio pedido).
- **Negativas e Limitações:** O gerenciamento de sessão em memória do processo exige sticky session caso o servidor venha a escalar horizontalmente no futuro, e o certificado TLS local autoassinado requer aceite manual no navegador durante demonstrações acadêmicas.
