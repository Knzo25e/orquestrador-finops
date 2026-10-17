-- SPEC-005, etapa B / Issue #59.
-- Executar uma unica vez em cada banco vazio do projeto.
-- Selecionar explicitamente finops_test ou finops_dev antes da execucao.
-- Este arquivo nao seleciona banco nem exclui estruturas existentes.

CREATE TABLE departamento (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    nome VARCHAR(255) NOT NULL,
    teto_mensal DECIMAL(19,2) NOT NULL,
    saldo_disponivel DECIMAL(19,2) NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT ck_departamento_teto
        CHECK (teto_mensal >= 0),

    CONSTRAINT ck_departamento_saldo
        CHECK (
            saldo_disponivel >= 0
            AND saldo_disponivel <= teto_mensal
        )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;


CREATE TABLE recurso_catalogo (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    nome VARCHAR(255) NOT NULL,
    custo_mensal DECIMAL(19,2) NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT ck_recurso_custo
        CHECK (custo_mensal >= 0)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;


CREATE TABLE requisicao (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    departamento_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    estado VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    custo_total DECIMAL(19,2) NOT NULL,
    motivo_revisao VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,

    PRIMARY KEY (id),

    INDEX idx_requisicao_departamento (departamento_id),

    CONSTRAINT fk_requisicao_departamento
        FOREIGN KEY (departamento_id)
        REFERENCES departamento (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT ck_requisicao_total
        CHECK (custo_total >= 0),

    CONSTRAINT ck_requisicao_estado
        CHECK (
            estado IN (
                'INICIAL',
                'REVISAO_PENDENTE',
                'PROVISIONAMENTO_LIBERADO'
            )
        ),

    CONSTRAINT ck_requisicao_motivo
        CHECK (
            (
                estado = 'REVISAO_PENDENTE'
                AND motivo_revisao IS NOT NULL
                AND motivo_revisao IN (
                    'SALDO_INSUFICIENTE',
                    'RESERVA'
                )
            )
            OR
            (
                estado IN ('INICIAL', 'PROVISIONAMENTO_LIBERADO')
                AND motivo_revisao IS NULL
            )
        )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;


CREATE TABLE item_requisicao (
    requisicao_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ordem INT NOT NULL,
    recurso_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    preco_unitario DECIMAL(19,2) NOT NULL,
    quantidade INT NOT NULL,
    subtotal DECIMAL(19,2) NOT NULL,

    PRIMARY KEY (requisicao_id, ordem),

    INDEX idx_item_recurso (recurso_id),

    CONSTRAINT fk_item_requisicao
        FOREIGN KEY (requisicao_id)
        REFERENCES requisicao (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT fk_item_recurso
        FOREIGN KEY (recurso_id)
        REFERENCES recurso_catalogo (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT ck_item_ordem
        CHECK (ordem > 0),

    CONSTRAINT ck_item_quantidade
        CHECK (quantidade > 0),

    CONSTRAINT ck_item_preco
        CHECK (preco_unitario >= 0),

    CONSTRAINT ck_item_subtotal
        CHECK (
            subtotal >= 0
            AND subtotal = preco_unitario * quantidade
        )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;