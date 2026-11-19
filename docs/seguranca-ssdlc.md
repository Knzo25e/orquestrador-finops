# Considerações de Segurança e SSDLC — Orquestrador FinOps

Este documento consolida os controles de segurança incorporados desde a modelagem até a verificação do sistema (Secure Software Development Lifecycle), atendendo aos requisitos da `SPEC-006` e `SPEC-007`.

## 1. Autenticação e Gestão de Sessão
- Toda requisição protegida passa pelo filtro `SegurancaHttp`, que resolve a identidade autenticada via `ProvedorIdentidade` e constrói o `ContextoIdentidade` antes de acionar qualquer caso de uso.
- Sessões inválidas ou ausentes são bloqueadas antes de atingir a camada de domínio.

## 2. Controle de Acesso Baseado em Perfil (RBAC) e Autoria
- **Separação de Perfis (`PerfilUsuario`):** O sistema distingue estritamente os perfis `DESENVOLVEDOR` e `ARQUITETO`.
- **Autorização no Backend (`PoliticaAutorizacao`):** A restrição não depende apenas da interface HTML (`desenvolvedor.html` vs. `arquiteto.html`). Casos de uso decorados (`*AutorizadaUseCase`) validam o perfil no servidor e lançam `AcessoNegadoException` diante de chamadas fora do escopo.
- **Isolamento de Dados por Autoria:** Desenvolvedores consultam e acompanham apenas suas próprias requisições (`VerificarAutoriaRequisicao`), enquanto apenas Arquitetos possuem visão global, permissão para ajustar tetos (`AjustarTetoUseCase`) e permissão para intervir manualmente (`IntervirRequisicaoUseCase`).

## 3. Integridade de Dados, Concorrência e Precisão Financeira
- **Prevenção de SQL Injection:** Todas as consultas e gravações nos adaptadores `*Jdbc` utilizam `PreparedStatement` parametrizado.
- **Precisão Monetária:** Valores financeiros e quantidades são tratados com `BigDecimal` (`VerificarValoresMonetarios`), eliminando vulnerabilidades de arredondamento de ponto flutuante.
- **Proteção contra Duplicidade e Race Conditions:** Identificadores duplicados são rejeitados (`RequisicaoDuplicadaException`) e o saldo departamental é protegido por isolamento transacional no MySQL.

## 4. Rastreabilidade e Não-Repúdio
- Toda decisão manual exige identificação do autor autenticado e justificativa obrigatória, sendo gravada em trilha de auditoria imutável (`IntervencaoAuditadaJdbc`).

## 5. Verificação Automatizada de Segurança
- As regras de autorização, isolamento de autoria e bloqueio de consultas indevidas são verificadas continuamente por suítes automatizadas (`VerificarPoliticaAutorizacao`, `VerificarAutoriaRequisicao`, `AutorizacaoCriacaoHttpTest` e `SessaoMysqlIT`).
