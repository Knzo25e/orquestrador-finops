# Estratégia de Testes e Evidências de Qualidade

Este documento formaliza a pirâmide de verificação adotada no **Orquestrador FinOps** e consolida as evidências de qualidade produzidas ao longo das entregas, conforme rastreabilidade com `docs/spec.md` e `docs/rastreabilidade-requisitos.md`.

## 1. Camadas da Estratégia de Testes

| Camada | Escopo Verificado | Localização no Repositório | Execução |
| :--- | :--- | :--- | :--- |
| **Testes Unitários de Domínio** | Regras financeiras com `BigDecimal`, transições da máquina de estados (`EstadoRequisicao`), ajuste de teto e fluxo de intervenção manual. | `src/test/java/br/mackenzie/finops/dominio/entidades/` (`AjusteOrcamentarioTest`, `FluxoManualTest`) | Em memória (sem dependência externa) |
| **Testes de Contrato HTTP** | Parsing de payloads, códigos de status HTTP, tratamento de erros (`TratamentoErrosHttp`) e isolamento de autorização nos controllers. | `src/test/java/br/mackenzie/finops/infraestrutura/http/` (`CatalogoHttpTest`, `RequisicaoHttpTest`, `OperacoesRequisicaoHttpTest`, `IntervencoesHttpTest`) | Em memória / mocks de porta |
| **Testes de Integração (`*MysqlIT`)** | Persistência JDBC transacional, migrações `V001`–`V005`, atomicidade de `commit`/`rollback`, concorrência orçamentária e auditoria imutável. | `src/test/java/br/mackenzie/finops/infraestrutura/persistencia/` (`*MysqlIT.java`) | Banco relacional MySQL |
| **Verificações de Interface (Portal)** | Cálculo de estimativa visual, paginação e operações ponta a ponta nas telas de Desenvolvedor e Arquiteto. | `scripts/testar-*.mjs` | Execução local contra `FinopsServidor` |
| **Benchmark de Desempenho (`RNF-01`)** | Latência da validação orçamentária sob carga concorrente no MySQL. | `MedirDesempenhoOrcamentario.java` e `scripts/medir-rnf01.py` | Banco relacional MySQL |

## 2. Cenários Críticos Cobertos
1. **Precisão Monetária:** Garantia de ausência de erro de ponto flutuante em somatórios de itens e abatimento de saldo departamental (`VerificarValoresMonetarios`).
2. **Concorrência Orçamentária:** Submissão simultânea de requisições concorrendo pelo mesmo saldo departamental, assegurando que apenas pedidos dentro do teto efetivo sejam aprovados automaticamente (`VerificarConcorrenciaOrcamentaria`, `ValidacaoAutorizadaMysqlIT`).
3. **Segregação de Perfis e Autoria:** Bloqueio de acesso cruzado entre desenvolvedores e restrição de ajuste de teto e intervenção manual exclusivamente ao perfil `ARQUITETO` (`AutorizacaoCriacaoHttpTest`, `SessaoMysqlIT`).
4. **Imutabilidade da Auditoria:** Verificação de persistência *append-only* e leitura consistente via paginação por cursor (`AuditoriaMysqlIT`).

## 3. Índice de Evidências de Qualidade Versionadas
- **Homologação Ponta a Ponta do Portal:** `docs/evidencias/issue-118/homologacao-e2e-portal.md`
- **Desempenho e Requisito Não Funcional (`RNF-01`):** `docs/evidencias/rnf-01-mysql.md`
- **Auditoria Imutável e Fluxo Manual:** `docs/evidencias/issue-101/auditoria-imutavel.md`, `docs/evidencias/issue-105/ajuste-teto.md`, `docs/evidencias/issue-107/fluxo-manual.md`
- **Persistência Transacional e Autoria JDBC:** `docs/evidencias/issue-59/` a `docs/evidencias/issue-84/`
- **Evidências Visuais das Interfaces:** `docs/evidencias/dashboard-arquiteto/`, `docs/evidencias/dashboard-desenvolvedor/` e `docs/evidencias/portal-navegacao/`
