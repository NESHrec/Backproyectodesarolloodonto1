CREATE TABLE cuentas_personal (
    id VARCHAR(36) PRIMARY KEY,
    email_normalizado VARCHAR(254) NOT NULL UNIQUE,
    nombre_completo VARCHAR(160) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    rol VARCHAR(20) NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVA',
    creado_en TIMESTAMPTZ NOT NULL,
    actualizada_en TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_cuenta_personal_rol CHECK (rol IN ('ADMIN', 'RECEPCION', 'MEDICO')),
    CONSTRAINT ck_cuenta_personal_estado CHECK (estado IN ('ACTIVA', 'BLOQUEADA')),
    CONSTRAINT ck_cuenta_personal_email_no_vacio CHECK (length(trim(email_normalizado)) > 0),
    CONSTRAINT ck_cuenta_personal_nombre_no_vacio CHECK (length(trim(nombre_completo)) > 0),
    CONSTRAINT ck_cuenta_personal_hash_no_vacio CHECK (length(trim(password_hash)) > 0)
);

CREATE TABLE sesiones_personal (
    id VARCHAR(36) PRIMARY KEY,
    cuenta_id VARCHAR(36) NOT NULL REFERENCES cuentas_personal(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expira_en TIMESTAMPTZ NOT NULL,
    creada_en TIMESTAMPTZ NOT NULL,
    revocada_en TIMESTAMPTZ,
    CONSTRAINT ck_sesion_personal_expiracion CHECK (expira_en > creada_en)
);

CREATE INDEX idx_sesiones_personal_cuenta ON sesiones_personal(cuenta_id);
CREATE INDEX idx_sesiones_personal_expiracion ON sesiones_personal(expira_en);

ALTER TABLE citas
    ADD COLUMN llegada_en TIMESTAMPTZ,
    ADD COLUMN llegada_por_personal_id VARCHAR(36),
    ADD CONSTRAINT fk_citas_llegada_personal
        FOREIGN KEY (llegada_por_personal_id) REFERENCES cuentas_personal(id);

CREATE INDEX idx_citas_llegada ON citas(llegada_en);
