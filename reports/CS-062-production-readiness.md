# Evaluación de preparación — CS-062 — 2026-10-19

## Dictamen

**CS-062: APTO CONDICIONADO PARA INTEGRACIÓN.** La corrección puntual y sus pruebas esenciales pasaron en PostgreSQL QA, incluida la consulta de conteo real y la búsqueda por el formulario del frontend. Esto **no constituye una aprobación general de Clínica Serena para producción**. El despliegue global requiere resolver o aceptar formalmente los riesgos y las comprobaciones pendientes que se enumeran abajo.

## Bases y artefactos revisados

- Backend autorizado: rama `fix/CS-062-search-escaping`, base `4dbe347c7face9cf02f892b3262331cf633a4bd7`, commit publicado previo `c99c9135874201ce6bf5329dca68617479fbee74`; cambios locales de esta ampliación limitados al test `ReceptionPatientAndAuditIntegrationTest` y sus informes.
- Frontend de auditoría: detached `ebf7fbe723416a45ad43c3357b2a1a9c18c2151a`, sin cambios versionados.
- Remotos: repositorios esperados de NESHrec. No se modificaron `main`, `desarrollo`, `security/integration` ni se desplegó.
- Backend QA de pruebas: PostgreSQL 16 aislado en loopback, puerto 54330, base `clinica_serena_validation_tests_20261008`; identidad y variables `TEST_DB_*` comprobadas antes de Maven.
- Navegador QA: PostgreSQL aislado distinto, puerto 54331; Next/Spring temporales en 3004/8081; correo local capturado. Servicios previos en 3003/8080 y bases ajenas no se detuvieron ni utilizaron para fixtures de estos recorridos.

## Corrección y evidencia

La búsqueda de pacientes administrativos escapa `\\`, `%` y `_` en ese orden, declara `ESCAPE` en los predicados LIKE/ILIKE de contenido y conteo, conserva parámetros enlazados y valida el límite de 100 caracteres antes del escape. Los tests de integración cubren entrada ordinaria/parcial, comodines literales, combinaciones, barra invertida, vacío/espacios, acentos, comillas y texto con apariencia SQL.

La ampliación de conteo usa páginas completas de tamaño uno con totales esperados mayores que uno. Se verifican IDs de cada página, unión exacta sin duplicados, `totalElements`, `hasNext` y ausencia de registros que coincidirían solo como comodín. Esto fuerza a Spring Data a ejecutar el `countQuery`, evitando inferencia desde una página parcial.

| Comprobación | Resultado real | Código |
|---|---:|---:|
| Clase de integración `ReceptionPatientAndAuditIntegrationTest` en PostgreSQL QA | 6 tests, 0 fallos/errores/omitidos | 0 |
| Suite Maven completa en Java 21 / PostgreSQL QA | 127 tests, 0 fallos/errores/omitidos; BUILD SUCCESS | 0 |
| Siete suites frontend deterministas relevantes | 36/36 PASS | todos 0 |
| Lint | PASS; 0 errores, 1 warning preexistente de variable sin uso en `tests/browser-delivery-journey.mjs:80` | 0 |
| Typecheck / build | PASS / PASS | ambos 0 |
| Navegador real, Edge/Playwright, 1366×768 | 51 PASS, 0 FAIL, 6 rutas directas BLOCKED por redirección, 0 pendientes | runner completó |
| `npm ci` / `npm audit --json` | instalación PASS; auditoría reportó vulnerabilidades | ci 0 / audit 1 por hallazgos |
| `git diff --check` en cambios CS-062 | PASS | 0 |

La salida auditada reportó 7 nodos/paquetes afectados de severidad alta y 3 avisos únicos; no se afirma explotación. Detalle, rutas y advisories oficiales: `npm-audit-review.md`. No se ejecutó `npm audit fix` ni `npm update`.

## Límites y riesgos heredados

- La regresión visual no fue exhaustiva en todos los viewports; esta pasada de navegador registró 1366×768. Las rutas denegadas se clasificaron como redirecciones UI, no como respuestas HTTP 401/403 del endpoint.
- Los casos directos de acceso fuera de rol dieron seis redirecciones esperadas. Para ADMIN, el shell `/recepcion/cobros` abrió, pero sus dos peticiones de datos recibieron 403. No hubo lectura de datos demostrada. La autorización del shell frente al contrato de producto queda para revisión; no es un hallazgo confirmado de bypass ni se corrigió.
- Registro/verificación de correo y recuperación solo se inspeccionaron sin enviar; reserva final y disponibilidad elegible no se ejecutaron; cobros, pago parcial e idempotencia financiera no se ejercitaron en esta pasada. No se realizaron escrituras ni se alteraron fixtures preexistentes.
- Hay siete paquetes/nodos afectados por tres avisos altos npm. Dependencias, alcance de producción y condiciones de explotación no fueron remediados ni demostrados. La aprobación de despliegue debe considerar este riesgo con el equipo responsable.
- Suites automatizadas verdes no equivalen a validación manual integral de seguridad, privacidad, cobros, roles y despliegue.

## Esquema, datos y respaldo

CS-062 modifica lógica de consulta/validación y tests; no añade ni cambia migraciones, esquema, columnas, índices ni formato de datos persistentes. Las pruebas escribieron únicamente fixtures temporales aislados en PostgreSQL QA y verificaron su retiro. Por esta corrección no se requiere respaldo/restore de base de datos como paso de rollback. Esto **no sustituye** la política operativa habitual de respaldos antes de despliegues de la aplicación o por cambios independientes de datos/esquema.

## Rollback propuesto para una integración futura — no ejecutado

1. Identificar los SHA exactos integrados de CS-062 y confirmar que ningún cambio posterior depende del nuevo comportamiento.
2. En la rama de integración, abrir una reversión revisada que revierta en orden inverso los commits de CS-062; para el commit previo publicado, el SHA conocido es `c99c9135874201ce6bf5329dca68617479fbee74`. Si se integra la ampliación de conteo como commit separado, revertir primero ese seguimiento de tests/docs y luego el commit de implementación según el grafo real.
3. Ejecutar de nuevo pruebas de búsqueda de pacientes y suite backend sobre PostgreSQL QA. Verificar estado del repositorio y desplegar solo por el proceso normal aprobado.
4. No restaurar base de datos por este rollback, dado que no hay migración ni transformación persistente asociada a CS-062. Usar el procedimiento estándar de respaldos si otras modificaciones de la release lo requieren.

No se ejecutó revert, despliegue, commit ni push mientras se preparaba este dictamen.

## Condiciones pendientes para aprobación general del sistema

- Triage formal y plan de remediación/aceptación de los tres avisos npm con revisión de compatibilidad.
- Decidir y probar la política esperada de navegación del rol ADMIN a `/recepcion/cobros`, manteniendo el 403 observado en las lecturas BFF.
- Completar recorridos de registro/verificación, recuperación, disponibilidad/reserva, y pagos/idempotencia únicamente con fixtures sintéticos, correo local y proveedores de pago simulados.
- Validar entorno de despliegue real y la superficie de dependencias; esta evaluación no demuestra explotabilidad ni ausencia de ella.

Por tanto, el dictamen favorable se limita a integrar CS-062 bajo revisión normal; no se declara listo el sistema completo para producción.
