CREATE TABLE citas (
    id VARCHAR(36) PRIMARY KEY,
    paciente_id VARCHAR(36) NOT NULL,
    bloque_id VARCHAR(36) NOT NULL REFERENCES bloques_disponibilidad(id),
    medico_id VARCHAR(36) NOT NULL REFERENCES medicos(id),
    especialidad_id VARCHAR(36) NOT NULL REFERENCES especialidades(id),
    programada_en TIMESTAMPTZ NOT NULL,
    estado VARCHAR(20) NOT NULL,
    notas VARCHAR(1000),
    monto_centavos BIGINT,
    creada_en TIMESTAMPTZ NOT NULL,
    actualizada_en TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_cita_bloque UNIQUE (bloque_id),
    CONSTRAINT ck_cita_estado CHECK (estado IN ('PENDIENTE', 'CONFIRMADA', 'CANCELADA', 'COMPLETADA')),
    CONSTRAINT ck_cita_monto CHECK (monto_centavos IS NULL OR monto_centavos >= 0)
);

CREATE INDEX idx_citas_paciente_programada ON citas(paciente_id, programada_en);
CREATE INDEX idx_citas_medico_programada ON citas(medico_id, programada_en);
