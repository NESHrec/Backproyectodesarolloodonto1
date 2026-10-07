ALTER TABLE bitacora_eventos ALTER COLUMN actor_personal_id DROP NOT NULL;
ALTER TABLE bitacora_eventos DROP CONSTRAINT bitacora_eventos_actor_personal_id_fkey;
-- actor_id/actor_rol are immutable snapshots. Keeping a live FK would either block
-- legitimate account retirement or require updating the append-only event.
ALTER TABLE bitacora_eventos ADD COLUMN actor_tipo VARCHAR(20);
ALTER TABLE bitacora_eventos ADD COLUMN actor_id VARCHAR(36);
ALTER TABLE bitacora_eventos ADD COLUMN actor_rol VARCHAR(20);

UPDATE bitacora_eventos b
SET actor_tipo = 'PERSONAL', actor_id = b.actor_personal_id, actor_rol = c.rol
FROM cuentas_personal c
WHERE c.id = b.actor_personal_id;

ALTER TABLE bitacora_eventos ALTER COLUMN actor_tipo SET NOT NULL;
ALTER TABLE bitacora_eventos ALTER COLUMN actor_id SET NOT NULL;
ALTER TABLE bitacora_eventos ALTER COLUMN actor_rol SET NOT NULL;
ALTER TABLE bitacora_eventos ADD CONSTRAINT ck_bitacora_actor_tipo CHECK (actor_tipo IN ('PERSONAL', 'PACIENTE'));

CREATE TABLE solicitudes_vinculacion_paciente (
    id VARCHAR(36) PRIMARY KEY,
    paciente_administrativo_id VARCHAR(36) NOT NULL REFERENCES pacientes_administrativos(paciente_id) ON UPDATE CASCADE,
    codigo_hash VARCHAR(64) NOT NULL,
    creado_por_personal_id VARCHAR(36) NOT NULL REFERENCES cuentas_personal(id),
    creado_en TIMESTAMPTZ NOT NULL,
    expira_en TIMESTAMPTZ NOT NULL,
    intentos SMALLINT NOT NULL DEFAULT 0,
    max_intentos SMALLINT NOT NULL,
    estado VARCHAR(20) NOT NULL,
    confirmado_por_cuenta_id VARCHAR(36) REFERENCES cuentas_paciente(id),
    confirmado_en TIMESTAMPTZ,
    revocado_en TIMESTAMPTZ,
    CONSTRAINT ck_vinculacion_estado CHECK (estado IN ('PENDIENTE','CONFIRMADA','EXPIRADA','REVOCADA','BLOQUEADA')),
    CONSTRAINT ck_vinculacion_intentos CHECK (intentos >= 0 AND max_intentos BETWEEN 1 AND 10),
    CONSTRAINT ck_vinculacion_expiracion CHECK (expira_en > creado_en)
);

CREATE UNIQUE INDEX uq_vinculacion_paciente_pendiente
    ON solicitudes_vinculacion_paciente (paciente_administrativo_id) WHERE estado = 'PENDIENTE';
CREATE INDEX idx_vinculacion_expira ON solicitudes_vinculacion_paciente (expira_en) WHERE estado = 'PENDIENTE';

ALTER TABLE bloques_disponibilidad ADD COLUMN retirado_en TIMESTAMPTZ;
ALTER TABLE bloques_disponibilidad ADD COLUMN retirado_por_personal_id VARCHAR(36) REFERENCES cuentas_personal(id);
CREATE INDEX idx_bloques_activos_medico_inicio ON bloques_disponibilidad (medico_id, inicio) WHERE retirado_en IS NULL;
