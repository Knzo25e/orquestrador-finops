# Matriz de Rastreabilidade — Especificações, Modelagem e Testes

Mapeamento entre as especificações funcionais e não funcionais, os artefatos UML, os casos de uso de aplicação e as suítes de verificação automatizada do projeto.

| ID | Escopo Funcional | Artefato UML / Documento | Caso de Uso / Camada | Cobertura de Testes |
| :--- | :--- | :--- | :--- | :--- |
| **SPEC-002** | Criação de requisição com validação de entrada e autoria | `dominio-v1.png` | `CriarRequisicaoUseCase` | `PedidoCriacaoHttpTest`, `CriacaoHttpMysqlIT` |
| **SPEC-003** | Cálculo de custo e validação contra teto departamental | `RF-02-sequencia.png`, `UC-02-validacao-orcamento.md` | `ValidarOrcamentoUseCase` | `ValidacaoAutorizadaMysqlIT`, `VerificarValidacaoOrcamentaria` |
| **SPEC-004** | Contratos de entrada/saída e tratamento de erros HTTP | `contratos-submissao-consulta.md` | `OperacoesRequisicaoController`, `TratamentoErrosHttp` | `OperacoesRequisicaoHttpTest`, `RequisicaoHttpTest` |
| **SPEC-005** | Persistência transacional e reconstituição de entidades | `arquitetura.md` | `CriacaoRequisicaoJdbc`, `ValidacaoRequisicaoJdbc` | `CriacaoIdentidadeMysqlIT`, `MigracaoEstadosMysqlIT` |
| **SPEC-006** | Autenticação de sessão e controle de acesso por perfil | `modelo-dominio.md` | `PoliticaAutorizacao`, `SegurancaHttp` | `SessaoMysqlIT`, `AutorizacaoCriacaoHttpTest` |
| **SPEC-007** | Registro imutável e consulta paginada de auditoria | `arquitetura.md` | `ListarIntervencoesUseCase`, `CursorIntervencao` | `AuditoriaMysqlIT`, `IntervencoesHttpTest` |
| **SPEC-008** | Reajuste de teto orçamentário de departamento | `dominio-v1.png` | `AjustarTetoUseCase`, `AjusteOrcamentarioController` | `AjusteOrcamentarioTest`, `AjusteOrcamentarioMysqlIT` |
| **SPEC-009** | Revisão e decisão manual de requisições retidas | `RF-02-estados.png` | `IntervirRequisicaoUseCase`, `DecisaoManual` | `FluxoManualTest`, `FluxoManualMysqlIT` |
| **RNF-01** | Desempenho da validação orçamentária com MySQL | `rnf-01-mysql.md` | `MedirDesempenhoOrcamentario` | `scripts/medir-rnf01.py` |
