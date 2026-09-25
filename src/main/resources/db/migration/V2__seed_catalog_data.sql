INSERT INTO especialidades (id, nombre, descripcion) VALUES
    ('10000000-0000-0000-0000-000000000001', 'Odontología general', 'Prevención, diagnóstico y tratamiento odontológico integral.'),
    ('10000000-0000-0000-0000-000000000002', 'Ortodoncia', 'Diagnóstico y corrección de la posición dental y maxilar.'),
    ('10000000-0000-0000-0000-000000000003', 'Pediatría', 'Atención médica preventiva y curativa para niñas y niños.'),
    ('10000000-0000-0000-0000-000000000004', 'Medicina general', 'Evaluación primaria, prevención y seguimiento de la salud.');

INSERT INTO medicos (id, nombre_completo, especialidad_id, numero_colegiado) VALUES
    ('20000000-0000-0000-0000-000000000001', 'Dra. Elena Morales', '10000000-0000-0000-0000-000000000001', 'COL-FICT-OD-1042'),
    ('20000000-0000-0000-0000-000000000002', 'Dr. Mateo Rivera', '10000000-0000-0000-0000-000000000002', 'COL-FICT-OR-2087'),
    ('20000000-0000-0000-0000-000000000003', 'Dra. Lucía Herrera', '10000000-0000-0000-0000-000000000003', 'COL-FICT-PE-3154'),
    ('20000000-0000-0000-0000-000000000004', 'Dr. Gabriel Soto', '10000000-0000-0000-0000-000000000004', 'COL-FICT-MG-4219');

INSERT INTO bloques_disponibilidad (id, medico_id, inicio, fin, disponible) VALUES
    ('30000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', date_trunc('day', CURRENT_TIMESTAMP) + INTERVAL '2 days 15 hours', date_trunc('day', CURRENT_TIMESTAMP) + INTERVAL '2 days 15 hours 30 minutes', TRUE),
    ('30000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000001', date_trunc('day', CURRENT_TIMESTAMP) + INTERVAL '2 days 16 hours', date_trunc('day', CURRENT_TIMESTAMP) + INTERVAL '2 days 16 hours 30 minutes', TRUE),
    ('30000000-0000-0000-0000-000000000003', '20000000-0000-0000-0000-000000000002', date_trunc('day', CURRENT_TIMESTAMP) + INTERVAL '3 days 14 hours', date_trunc('day', CURRENT_TIMESTAMP) + INTERVAL '3 days 14 hours 45 minutes', TRUE),
    ('30000000-0000-0000-0000-000000000004', '20000000-0000-0000-0000-000000000002', date_trunc('day', CURRENT_TIMESTAMP) + INTERVAL '5 days 17 hours', date_trunc('day', CURRENT_TIMESTAMP) + INTERVAL '5 days 17 hours 45 minutes', TRUE),
    ('30000000-0000-0000-0000-000000000005', '20000000-0000-0000-0000-000000000003', date_trunc('day', CURRENT_TIMESTAMP) + INTERVAL '4 days 15 hours', date_trunc('day', CURRENT_TIMESTAMP) + INTERVAL '4 days 15 hours 30 minutes', TRUE),
    ('30000000-0000-0000-0000-000000000006', '20000000-0000-0000-0000-000000000003', date_trunc('day', CURRENT_TIMESTAMP) + INTERVAL '6 days 16 hours', date_trunc('day', CURRENT_TIMESTAMP) + INTERVAL '6 days 16 hours 30 minutes', TRUE),
    ('30000000-0000-0000-0000-000000000007', '20000000-0000-0000-0000-000000000004', date_trunc('day', CURRENT_TIMESTAMP) + INTERVAL '3 days 20 hours', date_trunc('day', CURRENT_TIMESTAMP) + INTERVAL '3 days 20 hours 30 minutes', TRUE),
    ('30000000-0000-0000-0000-000000000008', '20000000-0000-0000-0000-000000000004', date_trunc('day', CURRENT_TIMESTAMP) + INTERVAL '7 days 14 hours', date_trunc('day', CURRENT_TIMESTAMP) + INTERVAL '7 days 14 hours 30 minutes', TRUE),
    ('30000000-0000-0000-0000-000000000009', '20000000-0000-0000-0000-000000000001', date_trunc('day', CURRENT_TIMESTAMP) + INTERVAL '3 days 15 hours', date_trunc('day', CURRENT_TIMESTAMP) + INTERVAL '3 days 15 hours 30 minutes', FALSE);
