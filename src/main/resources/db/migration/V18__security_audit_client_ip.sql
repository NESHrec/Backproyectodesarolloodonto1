ALTER TABLE bitacora_eventos
    ADD COLUMN client_ip VARCHAR(45);

ALTER TABLE bitacora_eventos
    DROP CONSTRAINT ck_bitacora_actor_tipo;

ALTER TABLE bitacora_eventos
    ADD CONSTRAINT ck_bitacora_actor_tipo
        CHECK (actor_tipo IN ('PERSONAL', 'PACIENTE', 'SISTEMA'));

COMMENT ON COLUMN bitacora_eventos.client_ip IS
    'Resolved client IP for security events; historical values remain null.';
