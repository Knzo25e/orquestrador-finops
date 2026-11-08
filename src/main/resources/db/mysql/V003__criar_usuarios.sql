-- Aplicar depois de V002. As contas são cadastradas separadamente.
CREATE TABLE usuario (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    login VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    senha_hash VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    perfil VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ativo BOOLEAN NOT NULL,
    departamento_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usuario_login (login),
    CONSTRAINT ck_usuario_perfil CHECK (perfil IN ('DESENVOLVEDOR', 'ARQUITETO_CLOUD')),
    CONSTRAINT ck_usuario_ativo CHECK (ativo IN (0, 1)),
    CONSTRAINT fk_usuario_departamento FOREIGN KEY (departamento_id) REFERENCES departamento (id)
) ENGINE = InnoDB;
