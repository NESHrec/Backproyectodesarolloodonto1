-- Fail without changing historical rows if case/whitespace normalization collides.
-- Resolve any reported identifiers operationally before retrying this migration.
CREATE UNIQUE INDEX uq_medico_numero_colegiado_normalizado
    ON medicos (upper(regexp_replace(btrim(numero_colegiado), '[[:space:]]+', ' ', 'g')));
