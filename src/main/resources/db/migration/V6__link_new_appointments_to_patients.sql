-- Existing appointments may predate patient accounts. NOT VALID preserves those
-- historical rows while enforcing that every new appointment belongs to a patient.
ALTER TABLE citas
    ADD CONSTRAINT fk_citas_paciente
    FOREIGN KEY (paciente_id) REFERENCES pacientes(id) NOT VALID;
