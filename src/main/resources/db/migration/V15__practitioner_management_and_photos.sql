ALTER TABLE medicos
    ADD COLUMN activo boolean NOT NULL DEFAULT true,
    ADD COLUMN foto_clave varchar(120),
    ADD COLUMN foto_content_type varchar(40);

ALTER TABLE medicos ADD CONSTRAINT ck_medico_foto_completa CHECK (
    (foto_clave IS NULL AND foto_content_type IS NULL)
    OR (foto_clave IS NOT NULL AND foto_content_type IN ('image/jpeg', 'image/png'))
);

CREATE UNIQUE INDEX uq_medico_foto_clave ON medicos(foto_clave) WHERE foto_clave IS NOT NULL;
