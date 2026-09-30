CREATE TABLE intenciones_pago_recepcion (
    cuenta_recepcion_id VARCHAR(36) PRIMARY KEY REFERENCES cuentas_personal(id),
    cita_id VARCHAR(36) NOT NULL REFERENCES citas(id),
    monto_centavos BIGINT NOT NULL,
    metodo VARCHAR(20) NOT NULL,
    referencia VARCHAR(120),
    idempotency_key VARCHAR(80) NOT NULL,
    estado VARCHAR(20) NOT NULL,
    creada_en TIMESTAMPTZ NOT NULL,
    completada_en TIMESTAMPTZ,
    CONSTRAINT ck_intencion_pago_monto CHECK (monto_centavos > 0),
    CONSTRAINT ck_intencion_pago_metodo CHECK (metodo IN ('EFECTIVO', 'TRANSFERENCIA', 'OTRO')),
    CONSTRAINT ck_intencion_pago_estado CHECK (estado IN ('PREPARADA', 'COMPLETADA')),
    CONSTRAINT ck_intencion_pago_completada CHECK (
        (estado = 'PREPARADA' AND completada_en IS NULL)
        OR (estado = 'COMPLETADA' AND completada_en IS NOT NULL)
    ),
    CONSTRAINT uq_intencion_pago_clave UNIQUE (cita_id, idempotency_key)
);

CREATE INDEX idx_intenciones_pago_cita
    ON intenciones_pago_recepcion(cita_id);
