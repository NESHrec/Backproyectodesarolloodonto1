-- Vinculación controlada entre una cuenta de personal MEDICO y un profesional del
-- catálogo. Las cuentas MEDICO existentes quedan sin vinculación (medico_id NULL)
-- hasta que un ADMIN las asigne explícitamente; no se infiere por nombre ni correo.
ALTER TABLE cuentas_personal
    ADD COLUMN medico_id VARCHAR(36),
    ADD COLUMN medico_vinculado_en TIMESTAMPTZ,
    ADD COLUMN medico_vinculado_por VARCHAR(36),
    ADD CONSTRAINT fk_cuenta_personal_medico
        FOREIGN KEY (medico_id) REFERENCES medicos(id),
    ADD CONSTRAINT fk_cuenta_personal_medico_vinculado_por
        FOREIGN KEY (medico_vinculado_por) REFERENCES cuentas_personal(id),
    ADD CONSTRAINT ck_cuenta_personal_medico_solo_rol_medico
        CHECK (medico_id IS NULL OR rol = 'MEDICO'),
    ADD CONSTRAINT ck_cuenta_personal_medico_auditoria
        CHECK ((medico_id IS NULL AND medico_vinculado_en IS NULL AND medico_vinculado_por IS NULL)
            OR (medico_id IS NOT NULL AND medico_vinculado_en IS NOT NULL AND medico_vinculado_por IS NOT NULL));

-- Un profesional no puede quedar asignado a dos cuentas activas, incluso ante
-- solicitudes concurrentes: la base de datos rechaza la segunda asignación.
CREATE UNIQUE INDEX uq_cuenta_personal_medico_activa
    ON cuentas_personal (medico_id)
    WHERE medico_id IS NOT NULL AND estado = 'ACTIVA';

-- Historial de asignaciones y correcciones; solo se inserta.
CREATE TABLE historial_vinculacion_medico (
    id VARCHAR(36) PRIMARY KEY,
    cuenta_personal_id VARCHAR(36) NOT NULL REFERENCES cuentas_personal(id),
    medico_anterior_id VARCHAR(36) REFERENCES medicos(id),
    medico_nuevo_id VARCHAR(36) REFERENCES medicos(id),
    realizado_por VARCHAR(36) NOT NULL REFERENCES cuentas_personal(id),
    realizado_en TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_historial_vinculacion_cambio
        CHECK (medico_anterior_id IS DISTINCT FROM medico_nuevo_id)
);

CREATE INDEX idx_historial_vinculacion_cuenta
    ON historial_vinculacion_medico (cuenta_personal_id, realizado_en);

-- Permite que las atenciones referencien la combinación persistida de cita,
-- paciente y profesional. No altera ni valida filas históricas de citas.
ALTER TABLE citas
    ADD CONSTRAINT uq_citas_id_paciente_medico UNIQUE (id, paciente_id, medico_id);

CREATE TABLE expedientes_clinicos (
    id VARCHAR(36) PRIMARY KEY,
    paciente_id VARCHAR(36) NOT NULL UNIQUE REFERENCES pacientes(id),
    creado_en TIMESTAMPTZ NOT NULL,
    creado_por_personal_id VARCHAR(36) NOT NULL REFERENCES cuentas_personal(id)
);

CREATE TABLE atenciones_clinicas (
    id VARCHAR(36) PRIMARY KEY,
    cita_id VARCHAR(36) NOT NULL,
    expediente_id VARCHAR(36) NOT NULL REFERENCES expedientes_clinicos(id),
    paciente_id VARCHAR(36) NOT NULL REFERENCES pacientes(id),
    medico_id VARCHAR(36) NOT NULL REFERENCES medicos(id),
    autor_personal_id VARCHAR(36) NOT NULL REFERENCES cuentas_personal(id),
    motivo_consulta VARCHAR(1000) NOT NULL,
    hallazgos VARCHAR(2000),
    diagnostico VARCHAR(1000) NOT NULL,
    plan_tratamiento VARCHAR(2000),
    registrada_en TIMESTAMPTZ NOT NULL,
    -- Una sola atención por cita: el segundo envío concurrente falla aquí.
    CONSTRAINT uq_atencion_cita UNIQUE (cita_id),
    CONSTRAINT fk_atencion_cita_paciente_medico
        FOREIGN KEY (cita_id, paciente_id, medico_id)
        REFERENCES citas (id, paciente_id, medico_id),
    CONSTRAINT ck_atencion_motivo_no_vacio CHECK (length(trim(motivo_consulta)) >= 3),
    CONSTRAINT ck_atencion_diagnostico_no_vacio CHECK (length(trim(diagnostico)) >= 3)
);

CREATE INDEX idx_atenciones_expediente ON atenciones_clinicas (expediente_id, registrada_en);
CREATE INDEX idx_atenciones_medico ON atenciones_clinicas (medico_id, registrada_en);

CREATE TABLE receta_items (
    id VARCHAR(36) PRIMARY KEY,
    atencion_id VARCHAR(36) NOT NULL REFERENCES atenciones_clinicas(id),
    orden SMALLINT NOT NULL,
    medicamento VARCHAR(160) NOT NULL,
    dosis VARCHAR(80) NOT NULL,
    frecuencia VARCHAR(80) NOT NULL,
    duracion VARCHAR(80) NOT NULL,
    indicaciones VARCHAR(500),
    CONSTRAINT uq_receta_item_orden UNIQUE (atencion_id, orden),
    CONSTRAINT ck_receta_item_orden CHECK (orden BETWEEN 1 AND 10),
    CONSTRAINT ck_receta_item_medicamento_no_vacio CHECK (length(trim(medicamento)) >= 2),
    CONSTRAINT ck_receta_item_dosis_no_vacia CHECK (length(trim(dosis)) >= 1),
    CONSTRAINT ck_receta_item_frecuencia_no_vacia CHECK (length(trim(frecuencia)) >= 1),
    CONSTRAINT ck_receta_item_duracion_no_vacia CHECK (length(trim(duracion)) >= 1)
);

-- Los registros clínicos guardados no se sobrescriben: cualquier UPDATE se rechaza.
CREATE FUNCTION impedir_modificacion_registro_clinico() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'CLINICAL_RECORD_IMMUTABLE: % no admite modificaciones', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_atenciones_clinicas_inmutables
    BEFORE UPDATE ON atenciones_clinicas
    FOR EACH ROW EXECUTE FUNCTION impedir_modificacion_registro_clinico();

CREATE TRIGGER trg_receta_items_inmutables
    BEFORE UPDATE ON receta_items
    FOR EACH ROW EXECUTE FUNCTION impedir_modificacion_registro_clinico();

CREATE TRIGGER trg_historial_vinculacion_inmutable
    BEFORE UPDATE ON historial_vinculacion_medico
    FOR EACH ROW EXECUTE FUNCTION impedir_modificacion_registro_clinico();
