# CS-062 — Escape de comodines en búsqueda de pacientes

## Alcance y revisiones

- Responsable: Junior.
- Rama de implementación: `fix/CS-062-search-escaping`.
- Base backend: `4dbe347c7face9cf02f892b3262331cf633a4bd7` (`origin/security/integration`).
- Base de comprobación frontend: `ebf7fbe723416a45ad43c3357b2a1a9c18c2151a` (worktree detached, sin cambios de código).
- Repositorios remotos: `NESHrec/Backproyectodesarolloodonto1` y `NESHrec/frontproyectodesarollo1`.
- Alcance: escape de LIKE/ILIKE en la búsqueda administrativa de pacientes, límite explícito de entrada y cobertura de integración y formulario real.
- No se modificaron otros dominios ni se corrigieron otras vulnerabilidades.

## Implementación

`PacienteAdministrativoService.list` valida el texto original antes de normalizarlo: más de 100 puntos de código produce HTTP 400 y el código de error existente para esta validación (`PATIENT_SEARCH_TOO_LONG`), sin truncamiento. Después mantiene la normalización previa (nulo/vacío/trim) y escapa, en este orden, barra invertida, `%` y `_`. La consulta sigue parametrizada.

Los ocho predicados LIKE/ILIKE afectados (consulta de resultados y `countQuery`) declaran explícitamente `ESCAPE`. El literal presente en el text block Java contiene dos barras para que el SQL que recibe PostgreSQL especifique una sola barra como carácter de escape. Esto se confirmó mediante la ejecución de integración en PostgreSQL: la búsqueda de `%` y `_` devolvió solo registros que contienen esos caracteres literalmente; el escape SQL vacío de la primera forma ensayada fue detectado por la propia prueba y corregido antes de la validación final.

La paginación, orden, permisos, filtros, búsqueda parcial ordinaria y llamadas existentes no se alteraron.

## Archivos

- `src/main/java/com/clinicaserena/recepcion/repository/PacienteAdministrativoRepository.java`
- `src/main/java/com/clinicaserena/recepcion/service/PacienteAdministrativoService.java`
- `src/test/java/com/clinicaserena/recepcion/controller/ReceptionPatientAndAuditIntegrationTest.java`
- `reports/CS-062-search-escaping.md`
- `reports/security-regression-checklist.md`

## Entorno y comandos

Backend: OpenJDK 21.0.12; Maven Wrapper; PostgreSQL 16 aislado para pruebas, accesible solo en loopback en el puerto 54330, base `clinica_serena_validation_tests_20261008`. Antes de Maven se comprobó mediante `psql` la identidad efectiva (`current_database`, `current_user`, versión PostgreSQL) y la configuración `TEST_DB_*`. Este servicio era distinto de la base de navegador y de la habitual. La autenticación de ese contenedor QA era `trust`; `TEST_DB_PASSWORD` se proporcionó solo al proceso Maven como valor no utilizado por el método de autenticación. No se guardaron valores sensibles en este informe.

Comandos ejecutados en el worktree backend:

| Comando | Resultado |
|---|---|
| `.\mvnw.cmd -Dtest=ReceptionPatientAndAuditIntegrationTest test` (baseline) | PASS, 4 tests, 0 failures, 0 errors; exit 0 |
| `.\mvnw.cmd -Dtest=ReceptionPatientAndAuditIntegrationTest test` (con cambios) | PASS, 6 tests, 0 failures, 0 errors, 0 skipped; exit 0 |
| `.\mvnw.cmd test` | PASS, 127 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS, exit 0 |

Una ejecución completa inicial reportó 127 tests con un error de configuración: faltaba `TEST_DB_PASSWORD`, exigido por una prueba de migración aunque el contenedor QA utiliza autenticación `trust`. Se verificó la identidad de la misma base aislada y se repitió la suite pasando esa variable únicamente al proceso. La repetición completa pasó; no se modificaron pruebas para ocultar el fallo.

Frontend aislado en el SHA indicado; `npm.cmd ci` terminó con exit 0 sin cambios de `package.json` ni lockfile. Suites deterministas: `test:billing-client` (6), `test:auth-bff` (6), `test:staff-routes` (7), `test:audit-findings` (4), `test:clinical-expansions` (5), `test:patient-link` (4), `test:practitioner-routes` (4): total 36/36, comandos con exit 0. `npm.cmd run lint`: exit 0, un warning preexistente de variable `staff` sin uso en `tests/browser-delivery-journey.mjs:80`; cero errores. `npm.cmd run typecheck`: exit 0. `npm.cmd run build`: exit 0.

La instalación informó siete vulnerabilidades de nivel alto en dependencias. No se evaluó su explotabilidad y no se ejecutaron `npm audit fix` ni `npm update`; quedan pendientes de evaluación separada.

## Casos de CS-062

| Caso | Resultado | Evidencia |
|---|---|---|
| Búsqueda normal y coincidencia parcial | PASS | Integración PostgreSQL y formulario real; la búsqueda parcial devolvió los dos fixtures esperados |
| `%` literal | PASS | Solo coincidieron los fixtures sintéticos que contienen `%` |
| `_` literal | PASS | Solo coincidieron los fixtures sintéticos que contienen `_` |
| Barra invertida literal | PASS | Solo coincidieron los fixtures sintéticos que contienen `\` |
| Combinación `%_\` | PASS | Solo coincidió el fixture con la combinación literal |
| Límite de 100 caracteres | PASS | Búsqueda de 100 puntos de código encontró el fixture sintético |
| Entrada de 101 caracteres | PASS | HTTP 400 y error `PATIENT_SEARCH_TOO_LONG`; sin truncamiento |
| Vacío y espacios | PASS | Conservaron la semántica previa de búsqueda sin filtro |
| Caracteres acentuados | PASS | Coincidencia sintética `árbol ñandú` |
| Comillas y texto de apariencia SQL | PASS | Se trató como texto literal y devolvió únicamente su fixture |
| Codificación desde UI/BFF | PASS | Formulario real; `%` llegó codificado como `%25`, `_` conservó su codificación URL no reservada y `\` como `%5C`; Spring devolvió resultados literales |
| Sesión al salir | PASS | Login de recepción mediante formulario real; logout y posterior navegación protegida redirigieron a acceso |

El recorrido de navegador usó la cuenta sintética privada ya existente, sin exponer sus credenciales. Los ocho registros QA temporales se eliminaron al terminar; se verificó que quedaban cero filas en ambas tablas de fixtures. No se alteraron los datos de navegación ajenos a esos IDs.

## Limitaciones y clasificación

- Los tests Maven de integración utilizaron PostgreSQL real de QA, no H2 ni mocks para la corrección.
- La comprobación de UI se limitó al login/logout de RECEPCIÓN y a búsqueda de pacientes por el formulario/BFF. No se realizó recorrido manual en navegador de registro y verificación de correo, recuperación de contraseña, reserva, vistas completas de ADMIN/MÉDICO/PACIENTE ni operaciones de cobro. No se crearon citas ni se ejecutaron pagos. Esos recorridos quedan `NO EJECUTADOS` como navegador; los resultados de suites automatizadas no se presentan como prueba manual de esas pantallas.
- No se evaluó explotabilidad de los siete avisos npm.
- Sin otros fallos de código identificados en las comprobaciones ejecutadas. El error inicial de la suite total se clasificó como configuración de variable TEST_DB, resuelto en el proceso de prueba; no fue un error de código.

## Estado de publicación

Este informe registra la validación previa a la publicación. La rama autorizada es únicamente `fix/CS-062-search-escaping`. Estado de commit y push se comunica en la entrega al Scrum; no se afirma aprobación integral de seguridad fuera del alcance CS-062.
