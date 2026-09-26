CREATE TABLE pacientes (
    id VARCHAR(36) PRIMARY KEY,
    estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVO',
    creado_en TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_paciente_estado CHECK (estado IN ('ACTIVO', 'INACTIVO'))
);

CREATE TABLE cuentas_paciente (
    id VARCHAR(36) PRIMARY KEY,
    paciente_id VARCHAR(36) NOT NULL UNIQUE REFERENCES pacientes(id),
    email_normalizado VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVA',
    creado_en TIMESTAMPTZ NOT NULL,
    actualizada_en TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_cuenta_estado CHECK (estado IN ('ACTIVA', 'BLOQUEADA')),
    CONSTRAINT ck_cuenta_email_no_vacio CHECK (length(trim(email_normalizado)) > 0),
    CONSTRAINT ck_cuenta_password_hash_no_vacio CHECK (length(trim(password_hash)) > 0)
);

CREATE TABLE sesiones_paciente (
    id VARCHAR(36) PRIMARY KEY,
    cuenta_id VARCHAR(36) NOT NULL REFERENCES cuentas_paciente(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expira_en TIMESTAMPTZ NOT NULL,
    creada_en TIMESTAMPTZ NOT NULL,
    revocada_en TIMESTAMPTZ,
    CONSTRAINT ck_sesion_expiracion CHECK (expira_en > creada_en)
);

CREATE INDEX idx_sesiones_cuenta ON sesiones_paciente(cuenta_id);
CREATE INDEX idx_sesiones_expiracion ON sesiones_paciente(expira_en);
