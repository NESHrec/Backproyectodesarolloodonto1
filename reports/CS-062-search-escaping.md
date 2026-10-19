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

## Ampliación de validación de `countQuery` — 2026-10-19

Se amplió `ReceptionPatientAndAuditIntegrationTest` sin cambiar la implementación del servicio ni el repositorio. Se añadió un segundo fixture que contiene `%_\`, además de candidatos que solo resultarían coincidentes si `%` o `_` se interpretasen como comodines. Los resultados esperados se derivan de los nombres e IDs insertados por el test:

| Consulta literal | IDs esperados | Total esperado |
|---|---|---:|
| `%` | `b6200000-0000-0000-0000-000000000001`, `b6200000-0000-0000-0000-000000000004`, `b6200000-0000-0000-0000-000000000008` | 3 |
| `_` | `b6200000-0000-0000-0000-000000000002`, `b6200000-0000-0000-0000-000000000004`, `b6200000-0000-0000-0000-000000000008` | 3 |
| `%_\` | `b6200000-0000-0000-0000-000000000004`, `b6200000-0000-0000-0000-000000000008` | 2 |

Para las tres consultas se solicitó `limit=1` con `page=0..N-1`. Cada página devuelve exactamente un elemento (página completa), se valida su ID, `totalElements`, `hasNext`, el tamaño y la unión exacta de IDs sin duplicados. Como el contenido de cada página llena el tamaño solicitado mientras el total esperado es mayor que uno, Spring Data no puede inferir el total por una página parcial: la paginación hace ejecutar el `countQuery` en cada solicitud. Los parámetros `page` y `limit` coinciden con el contrato real de `GET /api/v1/staff/patients`. La consulta principal y `countQuery` continúan teniendo los mismos cuatro predicados y cláusulas `ESCAPE '\\'`.

La primera ejecución de la clase ampliada dio 5 PASS / 1 FAIL por una expectativa de fixture incompleta: el registro `...0008`, creado para `%_\\`, también contiene una barra invertida y por eso debía incluirse en esa búsqueda literal. Se corrigió únicamente la expectativa del test para reflejar los datos insertados; no se alteró la implementación. La repetición quedó en 6/6 PASS.

| Comando | Resultado |
|---|---|
| `.\mvnw.cmd -Dtest=ReceptionPatientAndAuditIntegrationTest test` (repetición final) | 6 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS, exit 0 |
| `.\mvnw.cmd test` | 127 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS, exit 0 |
| `git diff --check` | PASS, exit 0 |

Las dos ejecuciones Maven usaron Java 21.0.12.1 y el PostgreSQL QA de pruebas ya identificado en este informe: contenedor PostgreSQL 16 aislado, `127.0.0.1:54330`, base `clinica_serena_validation_tests_20261008`. La identidad efectiva fue consultada antes de correrlas; `TEST_DB_*` se estableció solo para cada proceso Maven. Flyway confirmó versión de esquema 16, con 16 migraciones validadas y ninguna pendiente. No se usaron la base de navegador ni la habitual.

Esta ampliación valida específicamente la consulta de conteo y no modifica las limitaciones generales de regresión descritas arriba. El backend quedó en el commit publicado `c99c9135874201ce6bf5329dca68617479fbee74` con cambios locales no publicados en el test y este informe; esta ampliación no se ha comiteado ni publicado.

## Revisión final de regresión y estado de publicación — 2026-10-19

Se verificó nuevamente el worktree backend `fix/CS-062-search-escaping`, HEAD inicial `c99c9135874201ce6bf5329dca68617479fbee74`, remoto NESHrec correcto, base ancestro `4dbe347c7face9cf02f892b3262331cf633a4bd7`, staging vacío y únicamente los cambios locales esperados del test de integración y este informe. El frontend permaneció detached en `ebf7fbe723416a45ad43c3357b2a1a9c18c2151a`, sin cambios versionados.

El recorrido de navegador se ejecutó mediante formularios visibles en Edge/Playwright con cuentas sintéticas existentes, Next en `127.0.0.1:3004` y Spring en `127.0.0.1:8081`, conectado a PostgreSQL de navegador aislado en `127.0.0.1:54331` y correo de captura local. Los servicios temporales fueron identificados por su worktree y configuración, distintos de los servicios preexistentes, que no se detuvieron ni reconfiguraron. El registro sanitizado quedó fuera de Git en `runtime/cs062-browser-regression-20261019/browser-regression-sanitized.json`.

Resultado de la última pasada: 51 PASS, 0 FAIL, 6 casos de apertura directa marcados BLOCKED porque el middleware redirigió a la vista autorizada, 0 pendientes; los seis bloqueos están emparejados con comprobaciones de denegación/redirección PASS. Login y logout reales pasaron para RECEPCION, ADMIN, MEDICO y PACIENTE. Se revisaron rutas públicas, de recepción, administración, médico y paciente a 1366×768; no se observó overflow horizontal. No se expuso Bearer en navegador. Dos respuestas 403 al consultar datos de cobros desde la sesión ADMIN fueron respuestas esperadas de autorización; la UI de `/recepcion/cobros` es accesible como shell, pero la lectura de datos fue denegada. Esto no demuestra acceso a datos ni se clasifica aquí como vulnerabilidad; queda como punto de política/UX para revisión independiente. Los 180 requests cancelados se clasificaron como `navigation-abort` por navegación rápida de Playwright, no como errores de red del servicio. Hubo 2 mensajes de consola asociados a las denegaciones 403 previstas y 0 errores inesperados.

El recorrido de registro/recuperación abrió los formularios sin enviarlos; no se alteraron cuentas ni se generaron correos. La reserva se inspeccionó sin confirmar y no se crearon citas; cobros/pagos no se ejecutaron. Correo, disponibilidad/reserva completa y operaciones financieras quedan NO EJECUTADOS en navegador. Las suites automáticas frontend no sustituyen estos recorridos manuales.

La ejecución final de la clase de integración fue 6/6 y la suite Maven completa 127/127, con Java 21 y PostgreSQL de pruebas aislado. Las suites deterministas frontend fueron 36/36; lint, typecheck y build finalizaron con código 0. Lint dejó un warning preexistente documentado en `tests/browser-delivery-journey.mjs`; no hubo errores de lint.

La auditoría npm actual se ejecutó con `npm ci` y `npm audit --json` sin cambiar manifiestos ni lockfile. npm terminó audit con código 1 por vulnerabilidades detectadas: 7 paquetes afectados, todos de severidad alta, vinculados a 3 avisos distintos. El JSON sanitizado y los detalles se encuentran fuera de Git; el análisis se documenta en `reports/npm-audit-review.md`. No se ejecutaron `npm audit fix` ni `npm update`.

## Dictamen acotado

CS-062, incluidas las consultas de contenido y conteo, la validación de longitud, los casos literales especiales y el formulario BFF→Spring, tiene evidencia aprobatoria en QA PostgreSQL y navegador. No se modificaron esquema, migraciones ni datos persistentes de aplicación; el rollback de código no requiere restauración de base por CS-062. La aplicación completa no queda aprobada para producción por esta tarea: los avisos npm y recorridos generales no ejecutados permanecen pendientes, conforme al informe `reports/CS-062-production-readiness.md`.
