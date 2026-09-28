-- Abort safely when historical V5 values collide after trim/lower. Operators must
-- resolve each identity manually; this migration never deletes or merges history.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM cuentas_paciente
        GROUP BY lower(trim(email_normalizado))
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'V7_EMAIL_NORMALIZATION_CONFLICT: resolve duplicated normalized emails before retrying';
    END IF;
END $$;

UPDATE cuentas_paciente SET email_normalizado = lower(trim(email_normalizado));

ALTER TABLE cuentas_paciente
    ADD COLUMN email_verificado_en TIMESTAMPTZ,
    ADD COLUMN nombre_completo VARCHAR(160),
    ADD CONSTRAINT ck_cuenta_email_normalizado
        CHECK (email_normalizado = lower(trim(email_normalizado)));

-- Existing accounts predate verification and retain their published login behavior.
UPDATE cuentas_paciente SET email_verificado_en = creado_en;

CREATE UNIQUE INDEX uq_cuentas_paciente_email_normalizado
    ON cuentas_paciente (lower(trim(email_normalizado)));

CREATE TABLE tokens_cuenta_paciente (
    id VARCHAR(36) PRIMARY KEY,
    cuenta_id VARCHAR(36) NOT NULL REFERENCES cuentas_paciente(id) ON DELETE CASCADE,
    tipo VARCHAR(24) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expira_en TIMESTAMPTZ NOT NULL,
    creado_en TIMESTAMPTZ NOT NULL,
    usado_en TIMESTAMPTZ,
    CONSTRAINT ck_token_tipo CHECK (tipo IN ('VERIFICACION_EMAIL', 'RECUPERACION_PASSWORD')),
    CONSTRAINT ck_token_expiracion CHECK (expira_en > creado_en)
);

CREATE INDEX idx_tokens_cuenta_tipo ON tokens_cuenta_paciente(cuenta_id, tipo);
CREATE INDEX idx_tokens_expiracion ON tokens_cuenta_paciente(expira_en);
CREATE UNIQUE INDEX uq_token_activo_cuenta_tipo
    ON tokens_cuenta_paciente(cuenta_id, tipo)
    WHERE usado_en IS NULL;
