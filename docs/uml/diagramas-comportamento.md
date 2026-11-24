# Diagramas Comportamentais e de Casos de Uso (Texto Versionado)

Artefatos de modelagem versionados em texto (sintaxe **Mermaid**) complementando os diagramas visuais em `docs/uml/` (`dominio-v1.png`, `RF-02-estados.png` e `RF-02-sequencia.png`), conforme diretriz de rastreabilidade do projeto.

## 1. Diagrama de Casos de Uso por Perfil (`SPEC-006`)

```mermaid
flowchart LR
    DEV([Desenvolvedor])
    ARQ([Arquiteto FinOps])

    subgraph Sistema [Orquestrador FinOps]
        UC1(Consultar Catálogo Pré-Aprovado)
        UC2(Submeter Requisição de Infraestrutura)
        UC3(Consultar Próprias Requisições)
        UC4(Validar Orçamento Automaticamente)
        UC5(Consultar Visão Global de Departamentos)
        UC6(Ajustar Teto Orçamentário)
        UC7(Liberar ou Rejeitar Requisição Retida)
        UC8(Consultar Trilha de Auditoria Imutável)
    end

    DEV --> UC1
    DEV --> UC2
    DEV --> UC3
    UC2 -.->|include| UC4

    ARQ --> UC1
    ARQ --> UC5
    ARQ --> UC6
    ARQ --> UC7
    ARQ --> UC8
```

## 2. Máquina de Estados da Requisição (`EstadoRequisicao`)

```mermaid
stateDiagram-v2
    [*] --> RASCUNHO_SUBMETIDO: Desenvolvedor cria requisição
    RASCUNHO_SUBMETIDO --> APROVADA_AUTOMATICAMENTE: Custo <= Saldo disponível
    RASCUNHO_SUBMETIDO --> RETIDA_ORCAMENTO: Custo > Saldo disponível
    RETIDA_ORCAMENTO --> APROVADA_MANUALMENTE: Arquiteto libera com justificativa
    RETIDA_ORCAMENTO --> REJEITADA: Arquiteto rejeita com justificativa
    APROVADA_AUTOMATICAMENTE --> [*]
    APROVADA_MANUALMENTE --> [*]
    REJEITADA --> [*]
```

## 3. Sequência de Submissão e Validação Transacional (`SPEC-002` / `SPEC-003`)

```mermaid
sequenceDiagram
    actor Dev as Desenvolvedor
    participant HTTP as RequisicaoController
    participant Auth as CriarRequisicaoAutorizadaUseCase
    participant Dom as Motor Financeiro (BigDecimal)
    participant DB as MySQL (JDBC Transacional)

    Dev->>HTTP: POST /api/requisicoes (itens, departamento)
    HTTP->>Auth: Executa com ContextoIdentidade (DESENVOLVEDOR)
    Auth->>DB: Inicia transação (autoCommit = false)
    DB-->>Dom: Carrega teto e saldo atual do departamento
    Dom->>Dom: Calcula custo total exato (BigDecimal)
    alt Custo dentro do saldo disponível
        Dom-->>DB: Grava requisição APROVADA + abate saldo
        DB-->>Auth: COMMIT
    else Estouro de orçamento
        Dom-->>DB: Grava requisição RETIDA (sem abater saldo)
        DB-->>Auth: COMMIT
    end
    Auth-->>HTTP: ResultadoCriacaoRequisicao
    HTTP-->>Dev: HTTP 201 Created (estado e custo calculado)
```
