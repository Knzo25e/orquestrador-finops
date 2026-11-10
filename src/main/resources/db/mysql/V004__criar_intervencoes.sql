-- SPEC-007. Aplicar depois de V003 com a conta de migração.
CREATE TABLE intervencao (
    operacao_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    responsavel_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    departamento_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    requisicao_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    acao VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    instante DATETIME(6) NOT NULL,
    justificativa VARCHAR(500) NOT NULL,
    parametros JSON NOT NULL,
    resultado VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status_http SMALLINT NOT NULL,
    dados JSON NOT NULL,
    versao SMALLINT NOT NULL DEFAULT 1,
    PRIMARY KEY (operacao_id),
    INDEX idx_intervencao_pagina (departamento_id, instante DESC, operacao_id DESC),
    INDEX idx_intervencao_requisicao (departamento_id, requisicao_id, instante DESC, operacao_id DESC),
    CONSTRAINT ck_intervencao_status CHECK (status_http BETWEEN 200 AND 499),
    CONSTRAINT ck_intervencao_versao CHECK (versao = 1),
    CONSTRAINT fk_intervencao_usuario FOREIGN KEY (responsavel_id) REFERENCES usuario (id),
    CONSTRAINT fk_intervencao_departamento FOREIGN KEY (departamento_id) REFERENCES departamento (id),
    CONSTRAINT fk_intervencao_requisicao FOREIGN KEY (requisicao_id) REFERENCES requisicao (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- A conta de execução recebe SELECT e INSERT nesta tabela, sem privilégios
-- globais/de schema de escrita ou DDL que permitam alterar/remover o histórico.
-- Migração, administração e limpeza da base de teste usam conta separada.
