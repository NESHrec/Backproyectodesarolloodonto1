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
