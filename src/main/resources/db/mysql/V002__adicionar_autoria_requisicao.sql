-- SPEC-006 / Issue #74.
-- Aplicar uma unica vez em banco que ja recebeu V001.
-- Selecionar explicitamente o banco antes da execucao.
--
-- Registros existentes permanecem com desenvolvedor_id NULL.
-- Nao atribuir autores por suposicao.
--
-- Nao ha chave estrangeira de usuario nesta etapa:
-- o projeto ainda nao possui tabela de usuarios.
-- Esta coluna nao implementa autenticacao ou autorizacao.

ALTER TABLE requisicao
    ADD COLUMN desenvolvedor_id
        CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD INDEX idx_requisicao_autor_departamento
        (desenvolvedor_id, departamento_id);