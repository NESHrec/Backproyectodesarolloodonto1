ALTER TABLE citas DROP CONSTRAINT uq_cita_bloque;

CREATE UNIQUE INDEX uq_cita_bloque_activa
    ON citas (bloque_id)
    WHERE estado <> 'CANCELADA';
