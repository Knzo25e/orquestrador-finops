-- SPEC-009. Aplicar uma vez, depois de V004, com a conta de migração.
-- DDL MySQL confirma implicitamente. Faça backup antes da migração do banco real.
-- Os valores existentes são preservados. O maior nome aprovado tem 33 caracteres.
ALTER TABLE requisicao
    DROP CHECK ck_requisicao_estado,
    DROP CHECK ck_requisicao_motivo,
    MODIFY COLUMN estado VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ADD CONSTRAINT ck_requisicao_estado CHECK (estado IN (
        'INICIAL', 'REVISAO_PENDENTE', 'PROVISIONAMENTO_LIBERADO',
        'AGUARDANDO_AJUSTE_ORCAMENTARIO', 'AGUARDANDO_AUTORIZACAO_FINANCEIRA',
        'AGUARDANDO_LIBERACAO_FINANCEIRA', 'REJEITADO'
    )),
    ADD CONSTRAINT ck_requisicao_motivo CHECK (
        (estado = 'REVISAO_PENDENTE' AND motivo_revisao IS NOT NULL
            AND motivo_revisao IN ('SALDO_INSUFICIENTE', 'RESERVA'))
        OR (estado <> 'REVISAO_PENDENTE' AND motivo_revisao IS NULL)
    );
