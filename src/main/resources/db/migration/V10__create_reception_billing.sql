ALTER TABLE citas
    ADD COLUMN moneda VARCHAR(3) NOT NULL DEFAULT 'GTQ',
    ADD CONSTRAINT ck_cita_moneda CHECK (moneda = upper(moneda) AND length(trim(moneda)) = 3);

CREATE TABLE cargos_citas_auditoria (
    id VARCHAR(36) PRIMARY KEY,
    cita_id VARCHAR(36) NOT NULL REFERENCES citas(id),
    monto_anterior_centavos BIGINT,
    monto_nuevo_centavos BIGINT NOT NULL,
    moneda_anterior VARCHAR(3),
    moneda_nueva VARCHAR(3) NOT NULL,
    fijado_por_personal_id VARCHAR(36) NOT NULL REFERENCES cuentas_personal(id),
    fijado_en TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_cargo_cita_monto_nuevo CHECK (monto_nuevo_centavos > 0),
    CONSTRAINT ck_cargo_cita_moneda_nueva CHECK (moneda_nueva = upper(moneda_nueva) AND length(trim(moneda_nueva)) = 3)
);

CREATE INDEX idx_cargos_citas_auditoria_cita
    ON cargos_citas_auditoria(cita_id, fijado_en);

CREATE TABLE pagos_citas (
    id VARCHAR(36) PRIMARY KEY,
    cita_id VARCHAR(36) NOT NULL REFERENCES citas(id),
    registrado_por_personal_id VARCHAR(36) NOT NULL REFERENCES cuentas_personal(id),
    monto_centavos BIGINT NOT NULL,
    moneda VARCHAR(3) NOT NULL,
    metodo VARCHAR(20) NOT NULL,
    referencia VARCHAR(120),
    idempotency_key VARCHAR(80) NOT NULL,
    registrado_en TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_pago_cita_monto CHECK (monto_centavos > 0),
    CONSTRAINT ck_pago_cita_moneda CHECK (moneda = upper(moneda) AND length(trim(moneda)) = 3),
    CONSTRAINT ck_pago_cita_metodo CHECK (metodo IN ('EFECTIVO', 'TRANSFERENCIA', 'OTRO')),
    CONSTRAINT uq_pago_cita_idempotency UNIQUE (cita_id, idempotency_key)
);

CREATE INDEX idx_pagos_citas_cita
    ON pagos_citas(cita_id, registrado_en);

CREATE FUNCTION impedir_modificacion_cobro_recepcion() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'BILLING_RECORD_IMMUTABLE: % no admite modificaciones', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_pagos_citas_inmutables
    BEFORE UPDATE OR DELETE ON pagos_citas
    FOR EACH ROW EXECUTE FUNCTION impedir_modificacion_cobro_recepcion();

CREATE TRIGGER trg_cargos_citas_auditoria_inmutables
    BEFORE UPDATE OR DELETE ON cargos_citas_auditoria
    FOR EACH ROW EXECUTE FUNCTION impedir_modificacion_cobro_recepcion();
