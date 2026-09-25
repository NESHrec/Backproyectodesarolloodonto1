CREATE TABLE especialidades (
    id VARCHAR(36) PRIMARY KEY,
    nombre VARCHAR(120) NOT NULL UNIQUE,
    descripcion VARCHAR(500) NOT NULL
);

CREATE TABLE medicos (
    id VARCHAR(36) PRIMARY KEY,
    nombre_completo VARCHAR(160) NOT NULL,
    especialidad_id VARCHAR(36) NOT NULL REFERENCES especialidades(id),
    numero_colegiado VARCHAR(50) NOT NULL UNIQUE
);

CREATE INDEX idx_medicos_especialidad ON medicos(especialidad_id);

CREATE TABLE bloques_disponibilidad (
    id VARCHAR(36) PRIMARY KEY,
    medico_id VARCHAR(36) NOT NULL REFERENCES medicos(id) ON DELETE CASCADE,
    inicio TIMESTAMPTZ NOT NULL,
    fin TIMESTAMPTZ NOT NULL,
    disponible BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_bloque_intervalo_valido CHECK (fin > inicio),
    CONSTRAINT uq_bloque_medico_inicio UNIQUE (medico_id, inicio)
);

CREATE INDEX idx_disponibilidad_consulta
    ON bloques_disponibilidad(medico_id, disponible, inicio);
