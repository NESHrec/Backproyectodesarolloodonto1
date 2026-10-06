-- Ampliaciones clínicas aditivas. Las filas anteriores permanecen intactas.

ALTER TABLE observaciones_odontograma
    ADD COLUMN superficie VARCHAR(12);

ALTER TABLE observaciones_odontograma
    DROP CONSTRAINT ck_odontograma_pieza_valida,
    ADD CONSTRAINT ck_odontograma_pieza_valida CHECK (pieza_dental IN (
        11,12,13,14,15,16,17,18,21,22,23,24,25,26,27,28,
        31,32,33,34,35,36,37,38,41,42,43,44,45,46,47,48,
        51,52,53,54,55,61,62,63,64,65,71,72,73,74,75,81,82,83,84,85
    )),
    ADD CONSTRAINT ck_odontograma_superficie_valida CHECK (superficie IS NULL OR superficie IN (
        'MESIAL','DISTAL','VESTIBULAR','LINGUAL','PALATINA','OCLUSAL','INCISAL'
    ));

CREATE INDEX idx_odontograma_pieza_superficie
    ON observaciones_odontograma (paciente_id, pieza_dental, superficie, registrada_en DESC);

-- Cada edición del perfil clínico crea una versión completa; nunca se reemplaza la anterior.
CREATE TABLE versiones_perfil_clinico (
    id VARCHAR(36) PRIMARY KEY,
    secuencia BIGSERIAL NOT NULL UNIQUE,
    expediente_id VARCHAR(36) NOT NULL REFERENCES expedientes_clinicos(id),
    paciente_id VARCHAR(36) NOT NULL REFERENCES pacientes(id),
    alergias VARCHAR(2000),
    condiciones_relevantes VARCHAR(2000),
    medicamentos_actuales VARCHAR(2000),
    antecedentes_odontologicos VARCHAR(2000),
    autor_personal_id VARCHAR(36) NOT NULL REFERENCES cuentas_personal(id),
    registrada_en TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_perfil_al_menos_un_dato CHECK (
        alergias IS NOT NULL OR condiciones_relevantes IS NOT NULL OR
        medicamentos_actuales IS NOT NULL OR antecedentes_odontologicos IS NOT NULL
    )
);
CREATE INDEX idx_perfil_clinico_historial
    ON versiones_perfil_clinico (paciente_id, registrada_en DESC, id DESC);

CREATE TABLE adendas_atencion (
    id VARCHAR(36) PRIMARY KEY,
    atencion_id VARCHAR(36) NOT NULL REFERENCES atenciones_clinicas(id),
    texto VARCHAR(2000) NOT NULL,
    motivo VARCHAR(500) NOT NULL,
    autor_personal_id VARCHAR(36) NOT NULL REFERENCES cuentas_personal(id),
    registrada_en TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_adenda_texto CHECK (length(trim(texto)) BETWEEN 3 AND 2000),
    CONSTRAINT ck_adenda_motivo CHECK (length(trim(motivo)) BETWEEN 3 AND 500)
);
CREATE INDEX idx_adendas_atencion_historial
    ON adendas_atencion (atencion_id, registrada_en ASC, id ASC);

CREATE TRIGGER trg_perfil_clinico_inmutable
    BEFORE UPDATE OR DELETE ON versiones_perfil_clinico
    FOR EACH ROW EXECUTE FUNCTION impedir_modificacion_registro_clinico();

CREATE TRIGGER trg_adendas_atencion_inmutables
    BEFORE UPDATE OR DELETE ON adendas_atencion
    FOR EACH ROW EXECUTE FUNCTION impedir_modificacion_registro_clinico();

-- V9 impedía UPDATE, pero la conservación clínica también exige impedir DELETE.
DROP TRIGGER trg_atenciones_clinicas_inmutables ON atenciones_clinicas;
CREATE TRIGGER trg_atenciones_clinicas_inmutables
    BEFORE UPDATE OR DELETE ON atenciones_clinicas
    FOR EACH ROW EXECUTE FUNCTION impedir_modificacion_registro_clinico();

DROP TRIGGER trg_receta_items_inmutables ON receta_items;
CREATE TRIGGER trg_receta_items_inmutables
    BEFORE UPDATE OR DELETE ON receta_items
    FOR EACH ROW EXECUTE FUNCTION impedir_modificacion_registro_clinico();
