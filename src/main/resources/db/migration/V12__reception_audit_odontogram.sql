-- Propuesta local: expediente administrativo de recepción, bitácora acotada
-- y observaciones odontológicas inmutables. V1-V11 no se modifican.

CREATE TABLE pacientes_administrativos (
    paciente_id VARCHAR(36) PRIMARY KEY REFERENCES pacientes(id),
    nombre_completo VARCHAR(160) NOT NULL,
    telefono VARCHAR(40) NOT NULL,
    telefono_normalizado VARCHAR(20) NOT NULL,
    email_contacto VARCHAR(254),
    email_contacto_normalizado VARCHAR(254),
    creado_por_personal_id VARCHAR(36) NOT NULL REFERENCES cuentas_personal(id),
    creado_en TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_paciente_admin_nombre_no_vacio CHECK (length(trim(nombre_completo)) >= 3),
    CONSTRAINT ck_paciente_admin_telefono_no_vacio CHECK (length(trim(telefono_normalizado)) BETWEEN 7 AND 20),
    CONSTRAINT ck_paciente_admin_email_no_vacio CHECK (email_contacto_normalizado IS NULL OR length(trim(email_contacto_normalizado)) > 0)
);

CREATE UNIQUE INDEX uq_paciente_admin_telefono ON pacientes_administrativos (telefono_normalizado);
CREATE UNIQUE INDEX uq_paciente_admin_email ON pacientes_administrativos (email_contacto_normalizado)
    WHERE email_contacto_normalizado IS NOT NULL;
CREATE INDEX idx_paciente_admin_nombre ON pacientes_administrativos (nombre_completo);

CREATE TABLE bitacora_eventos (
    id VARCHAR(36) PRIMARY KEY,
    actor_personal_id VARCHAR(36) NOT NULL REFERENCES cuentas_personal(id),
    accion VARCHAR(80) NOT NULL,
    entidad_tipo VARCHAR(80) NOT NULL,
    entidad_id VARCHAR(36) NOT NULL,
    ocurrido_en TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_bitacora_accion_no_vacia CHECK (length(trim(accion)) > 0),
    CONSTRAINT ck_bitacora_entidad_tipo_no_vacia CHECK (length(trim(entidad_tipo)) > 0)
);

CREATE INDEX idx_bitacora_ocurrido_en ON bitacora_eventos (ocurrido_en DESC);
CREATE INDEX idx_bitacora_actor_ocurrido_en ON bitacora_eventos (actor_personal_id, ocurrido_en DESC);

CREATE TABLE observaciones_odontograma (
    id VARCHAR(36) PRIMARY KEY,
    paciente_id VARCHAR(36) NOT NULL REFERENCES pacientes(id),
    cita_id VARCHAR(36) NOT NULL REFERENCES citas(id),
    medico_id VARCHAR(36) NOT NULL REFERENCES medicos(id),
    autor_personal_id VARCHAR(36) NOT NULL REFERENCES cuentas_personal(id),
    pieza_dental SMALLINT NOT NULL,
    observacion VARCHAR(500) NOT NULL,
    registrada_en TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_odontograma_pieza_valida CHECK (pieza_dental IN (
        11,12,13,14,15,16,17,18,21,22,23,24,25,26,27,28,
        31,32,33,34,35,36,37,38,41,42,43,44,45,46,47,48
    )),
    CONSTRAINT ck_odontograma_observacion_no_vacia CHECK (length(trim(observacion)) BETWEEN 1 AND 500)
);

-- La identidad de la observación debe coincidir con la combinación protegida de la cita.
-- Así no es posible insertar una observación cruzando paciente o profesional.
ALTER TABLE observaciones_odontograma
    ADD CONSTRAINT fk_odontograma_cita_paciente_medico
        FOREIGN KEY (cita_id, paciente_id, medico_id)
        REFERENCES citas (id, paciente_id, medico_id);

CREATE INDEX idx_odontograma_paciente_registrada ON observaciones_odontograma (paciente_id, registrada_en DESC);
CREATE INDEX idx_odontograma_cita ON observaciones_odontograma (cita_id, registrada_en DESC);

CREATE TRIGGER trg_bitacora_eventos_inmutables
    BEFORE UPDATE OR DELETE ON bitacora_eventos
    FOR EACH ROW EXECUTE FUNCTION impedir_modificacion_registro_clinico();

CREATE TRIGGER trg_observaciones_odontograma_inmutables
    BEFORE UPDATE OR DELETE ON observaciones_odontograma
    FOR EACH ROW EXECUTE FUNCTION impedir_modificacion_registro_clinico();
