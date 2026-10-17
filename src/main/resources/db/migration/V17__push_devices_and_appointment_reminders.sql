-- Recordatorios push de citas (Fase D1). No modifica V1-V16.
--
-- dispositivos_push_paciente: token de notificaciones de cada teléfono de un paciente. El token se
-- necesita para enviar; nunca se devuelve por la API, ni se registra en logs ni en la bitácora. La
-- unicidad y las búsquedas usan su SHA-256 (token_hash). sesion_paciente_id es la sesión que lo
-- registró: al cerrar esa sesión se retira el dispositivo; el vencimiento de la sesión no lo retira.
CREATE TABLE dispositivos_push_paciente (
    id                 VARCHAR(36) PRIMARY KEY,
    cuenta_paciente_id VARCHAR(36) NOT NULL REFERENCES cuentas_paciente(id) ON DELETE CASCADE,
    sesion_paciente_id VARCHAR(36) REFERENCES sesiones_paciente(id) ON DELETE SET NULL,
    token_hash         VARCHAR(64) NOT NULL,
    token              TEXT NOT NULL,
    plataforma         VARCHAR(10) NOT NULL,
    registrado_en      TIMESTAMPTZ NOT NULL,
    actualizado_en     TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_dispositivo_push_token UNIQUE (token_hash),
    CONSTRAINT ck_dispositivo_push_plataforma CHECK (plataforma IN ('ANDROID')),
    CONSTRAINT ck_dispositivo_push_token_largo CHECK (char_length(token) BETWEEN 20 AND 4096),
    CONSTRAINT ck_dispositivo_push_token_hash CHECK (token_hash ~ '^[0-9a-f]{64}$')
);

CREATE INDEX idx_dispositivos_push_cuenta ON dispositivos_push_paciente (cuenta_paciente_id);
CREATE INDEX idx_dispositivos_push_sesion ON dispositivos_push_paciente (sesion_paciente_id);

-- recordatorios_cita: un registro por cita y tipo. La restricción única es la garantía final contra
-- duplicados (varias pasadas, reinicios o varias instancias): quien inserta la fila EN_PROCESO es el
-- único que envía. actualizado_en es la base del umbral para retomar un EN_PROCESO abandonado.
-- ENVIADO queda reservado para un proveedor real; el modo de prueba solo produce SIMULADO.
CREATE TABLE recordatorios_cita (
    id                      VARCHAR(36) PRIMARY KEY,
    cita_id                 VARCHAR(36) NOT NULL REFERENCES citas(id),
    tipo                    VARCHAR(20) NOT NULL,
    estado                  VARCHAR(20) NOT NULL,
    programada_en           TIMESTAMPTZ NOT NULL,
    dispositivos_intentados SMALLINT NOT NULL DEFAULT 0,
    dispositivos_fallidos   SMALLINT NOT NULL DEFAULT 0,
    intentos                SMALLINT NOT NULL DEFAULT 1,
    error_codigo            VARCHAR(40),
    creado_en               TIMESTAMPTZ NOT NULL,
    actualizado_en          TIMESTAMPTZ NOT NULL,
    procesado_en            TIMESTAMPTZ,
    CONSTRAINT uq_recordatorio_cita_tipo UNIQUE (cita_id, tipo),
    CONSTRAINT ck_recordatorio_tipo CHECK (tipo IN ('CITA_24H')),
    CONSTRAINT ck_recordatorio_estado CHECK (estado IN
        ('EN_PROCESO', 'SIMULADO', 'ENVIADO', 'SIN_DISPOSITIVO', 'OMITIDO', 'FALLIDO')),
    CONSTRAINT ck_recordatorio_intentos CHECK (intentos BETWEEN 1 AND 10),
    CONSTRAINT ck_recordatorio_dispositivos CHECK (
        dispositivos_intentados >= 0 AND dispositivos_fallidos >= 0
        AND dispositivos_fallidos <= dispositivos_intentados)
);

CREATE INDEX idx_recordatorios_pendientes ON recordatorios_cita (actualizado_en)
    WHERE estado IN ('EN_PROCESO', 'FALLIDO');

-- Búsqueda de la ventana de 24 h por estado y hora programada.
CREATE INDEX idx_citas_estado_programada ON citas (estado, programada_en);
