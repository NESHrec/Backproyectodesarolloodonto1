# Lista de comprobación de regresión — CS-062

## Contexto

- Backend revisado desde `4dbe347c7face9cf02f892b3262331cf633a4bd7`, rama individual `fix/CS-062-search-escaping`.
- Frontend revisado detached en `ebf7fbe723416a45ad43c3357b2a1a9c18c2151a`; no se modificó.
- Pruebas backend: PostgreSQL 16 de QA aislado para Maven. Comprobada la identidad efectiva antes de ejecutar.
- Pruebas de formulario: Next y Spring temporales en puertos loopback separados, conectados a la base sintética de navegador. Los servicios temporales se detuvieron después; servicios preexistentes no se tocaron.

## Resultados ejecutados

| Área | Comprobación | Resultado |
|---|---|---|
| CS-062 | LIKE/ILIKE literal para `%`, `_`, `\` y combinaciones | PASS, integración PostgreSQL |
| CS-062 | Texto parcial, acentos, comillas/texto con apariencia SQL | PASS, integración PostgreSQL |
| CS-062 | 100 caracteres, rechazo de 101, vacío y espacios | PASS, integración PostgreSQL |
| Integración backend | Suite completa | PASS, 127 tests; 0 failures, 0 errors, 0 skipped |
| Frontend determinista | Siete suites pertinentes: cobros, auth/BFF, staff routes, auditoría, expansiones clínicas, patient link, practitioner routes | PASS, 36/36 |
| Frontend | lint | PASS, exit 0; un warning de variable no usada en test de navegador existente |
| Frontend | typecheck | PASS, exit 0 |
| Frontend | build | PASS, exit 0 |
| Sesión/navegador | Login de RECEPCIÓN por formulario, consulta por UI→BFF→Spring, logout y denegación posterior | PASS |
| Datos sintéticos | Fixtures temporales de búsqueda | Eliminados al finalizar; cero filas de esos IDs verificadas |

## Matriz de regresión y límites

| Área | Estado | Alcance de evidencia |
|---|---|---|
| Registro y verificación de correo | NO EJECUTADO en navegador | No se afirmó recorrido manual ni se expusieron enlaces o tokens |
| Recuperación de contraseña | NO EJECUTADO en navegador | No se cambió configuración de correo ni controles |
| Acceso de recepción a su búsqueda de pacientes | PASS | Login real por formulario y búsqueda real vía BFF/Spring |
| Autorización de otras rutas/roles | NO EJECUTADO en navegador | Las suites automatizadas corrieron, pero no se presenta como prueba manual de todas las vistas |
| Reservas/disponibilidad | NO EJECUTADO en navegador | No se creó ninguna reserva |
| Módulos administrativos completos | NO EJECUTADO en navegador | Sin operaciones administrativas en UI |
| Cobros/pagos/idempotencia en navegador | NO EJECUTADO | Sin escrituras financieras |
| Seguridad distinta de CS-062 | FUERA DE ALCANCE | No se inició auditoría amplia ni scanner externo |
| HTTP, permisos, CSRF y contratos relacionados | Suites deterministas PASS; comprobación manual amplia NO EJECUTADA | Resultado limitado a suites enumeradas en el reporte CS-062 |

## Incidencias

- Primera ejecución Maven completa: un error de configuración porque faltaba `TEST_DB_PASSWORD` para un test de migración; la base usada ya tenía autenticación QA `trust`. Se verificó otra vez el destino y se repitió el comando con la variable solo en el proceso. Resultado final: 127/127 sin fallos ni errores.
- `npm ci` informó siete vulnerabilidades altas. No se evaluó su explotabilidad; quedan pendientes. No se cambió ni actualizó dependencia alguna.
- `lint` dejó un warning preexistente en `tests/browser-delivery-journey.mjs`; no impidió el código de salida 0.
- No se detectó un fallo nuevo fuera de la corrección dentro de las comprobaciones realizadas. Las áreas no ejecutadas arriba siguen siendo pendientes, no aprobadas.

## Dictamen

CS-062 y sus pruebas esenciales quedaron validadas en PostgreSQL y en el formulario real de búsqueda. La validación no equivale a una aprobación general de seguridad ni cubre manualmente todos los roles, correo, reservas o pagos. Ningún control de seguridad, configuración productiva o backend ajeno a CS-062 fue modificado.

## Actualización de regresión controlada — 2026-10-19

### Entorno y alcance realmente ejecutado

- Worktree backend: `fix/CS-062-search-escaping`, inicial en `c99c9135874201ce6bf5329dca68617479fbee74`, basado en `4dbe347c7face9cf02f892b3262331cf633a4bd7`.
- Worktree frontend: detached `ebf7fbe723416a45ad43c3357b2a1a9c18c2151a`, sin cambios versionados.
- Edge/Playwright, Next `127.0.0.1:3004`, Spring `127.0.0.1:8081`, PostgreSQL navegador aislado `127.0.0.1:54331`; cuentas sintéticas privadas ya existentes; correo capturado localmente. No se registraron contraseñas, cookies, tokens ni cabeceras.
- Evidencia estructurada sanitizada, fuera del repositorio: `runtime/cs062-browser-regression-20261019/browser-regression-sanitized.json`.

### Matriz navegador real

| Rol/ruta o acción | Resultado | Evidencia / límite |
|---|---|---|
| Público: `/`, `/clinica`, `/especialidades`, `/medicos`, `/reservar`, `/iniciar-sesion`, `/registro`, `/recuperar-contrasena` | PASS | HTTP 200, encabezado principal presente, viewport 1366×768, sin overflow |
| Login visible y sesión RECEPCION | PASS | Formulario real, BFF/CSRF, aterrizaje en `/recepcion` |
| RECEPCION: resumen, agenda, pacientes, cobros | PASS | Rutas renderizadas; búsqueda CS-062 tiene cobertura real previa documentada arriba |
| RECEPCION: admin/bitácora y médico/agenda | PASS denegación | Middleware devolvió a `/recepcion`; no se confunde la navegación UI con HTTP 401/403 del API |
| Login visible y sesión ADMIN; `/admin`, usuarios, roles, bitácora | PASS | Formulario real y páginas cargadas |
| ADMIN: médico/agenda | PASS denegación | Middleware devolvió a `/admin` |
| ADMIN: shell `/recepcion/cobros` | PASS carga / PENDIENTE política | La ruta shell abrió, pero las dos solicitudes de datos recibieron 403. No se observó acceso a datos. Política de acceso a shell queda para revisión; no se corrigió |
| Login visible y sesión MEDICO; `/medico`, agenda | PASS | Formulario real y rutas cargadas |
| MEDICO: admin/roles | PASS denegación | Middleware devolvió a `/medico` |
| Login visible y sesión PACIENTE; `/paciente`, citas y `/reservar` | PASS | Formulario real y páginas cargadas |
| PACIENTE: recepción/pacientes y admin/bitácora | PASS denegación | Middleware devolvió a inicio de sesión |
| Logout y visita posterior a ruta protegida, cuatro roles | PASS | Formulario real; logout HTTP 200; ruta protegida redirige a login |
| Consola, Network y Bearer | PASS acotado | 2 mensajes de consola por 403 deliberados; 0 inesperados; requests fallidos solo `navigation-abort`; Bearer no visible en navegador |

El resumen de ejecución fue 51 PASS, 0 FAIL, 6 rutas directas BLOCKED por redirección (con denegación esperada verificada por separado), 0 pendientes. 180 requests cancelados por navegación rápida se clasificaron como abortos de navegación, no como fallas del backend. Todos los resultados visuales corresponden a 1366×768; no se afirma revisión en otros viewports.

### Acciones no ejecutadas

- Registro, verificación de correo y recuperación: formularios abiertos, no enviados; no se alteraron usuarios ni se generaron mensajes.
- Reserva final, escritura de cita, cargo, pago o idempotencia financiera: NO EJECUTADOS en esta pasada; no se hicieron escrituras. No se forzó fixture.
- Auditoría ofensiva, pruebas de carga y scanner externo: no ejecutados.
- Los resultados automatizados se mantienen diferenciados de la revisión de navegador.

### Pruebas finales

- Backend PostgreSQL: clase `ReceptionPatientAndAuditIntegrationTest`, 6/6; suite completa, 127/127; ambas con Java 21 y base Maven aislada; códigos 0.
- Frontend determinista: siete suites, 36/36; lint exit 0 (un warning preexistente), typecheck exit 0, build exit 0.
- `npm audit --json`: se ejecutó correctamente; exit 1 corresponde a vulnerabilidades reportadas, no a fallo de red/herramienta. Conteo y cadenas de dependencia en `reports/npm-audit-review.md`.

La ampliación de pruebas del countQuery confirma los mismos predicados de escape mediante páginas completas, que fuerzan conteo real; los detalles y límites están en `reports/CS-062-search-escaping.md`. No se corrigieron otros hallazgos ni controles.
