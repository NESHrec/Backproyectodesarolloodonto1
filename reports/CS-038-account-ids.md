# CS-038 — Protección de identificadores de cuentas

## Identificación

- Repositorio: backend Clínica Serena.
- Rama: `fix/CS-038-account-ids`.
- Commit base autorizado: `3c7c4fa15ae24857cc5b2c159efde6d2403934b2` (`origin/security/integration` vigente al 2026-10-19).
- Frontend coordinado: rama `fix/CS-038-account-ids`, base `76fbc47fb7aa4978e1ce7934849a542b8a11a8af`.
- Alcance: reducir exposición innecesaria en respuestas BFF sin cambiar DTO, endpoint ni contrato Spring.

## Vulnerabilidad corregida y revisión

El backend ya genera IDs aleatorios UUID v4 en la creación de cuentas (`UUID.randomUUID()`); no se alteraron cuentas existentes ni referencias históricas. Se añadió una aserción de integración para comprobar que una cuenta recién creada conserva esa propiedad.

En frontend, el ID interno se elimina de las identidades enviadas al navegador, de la respuesta de creación y de respuestas de vincular/desvincular que la UI no consume. El resumen administrativo ahora recibe únicamente totales agregados desde un BFF interno (`/api/staff/accounts/summary`), calculado en servidor usando el GET existente de Spring. El listado de cuentas aún incluye `accountId` en la vista ADMIN de vinculación porque esa operación existente necesita el identificador en la ruta; el backend mantiene la autorización ADMIN.

Se revisó `PatientClinicalRecordController` y su DTO sin cambiar el contrato Spring. El BFF de expediente del paciente proyecta la respuesta y omite `authorAccountId` de perfil, historial, atenciones y adendas, campos que la UI paciente no usa. La vista clínica de personal conserva sus datos de autor.

## Archivos modificados

### Backend

- `src/test/java/com/clinicaserena/staff/controller/StaffControllerIntegrationTest.java` (aserción UUID v4; sin cambios productivos backend).
- `reports/CS-038-account-ids.md`.

### Frontend coordinado

- `src/modules/auth/staff-session.ts`
- `src/modules/auth/staff-proxy.ts`
- `src/modules/atencion-medica/schemas.ts`
- `src/modules/admin/components/AdminAccountsSummaryClient.tsx`
- `src/modules/paciente-portal/components/PatientClinicalRecordClient.tsx`
- `src/app/api/staff/accounts/route.ts`
- `src/app/api/staff/accounts/[accountId]/practitioner/route.ts`
- `src/app/api/staff/accounts/summary/route.ts`
- `src/app/api/patient/clinical-record/route.ts`
- Pruebas de redacción de identificadores, login y resumen administrativo.

## Pruebas y resultados

- Suite Maven completa en PostgreSQL aislado: 125 pruebas, 0 fallos, 0 errores, 0 omitidas.
- Frontend determinista disponible sin cookies QA externas: 66/66 PASS.
- Pruebas dirigidas de redacción de IDs, resumen, sesión, login y rutas staff: 21/21 PASS.
- Pruebas de resumen administrativo actualizadas para el agregado sin identificadores: PASS.
- Lint: PASS, con una advertencia preexistente en `tests/browser-delivery-journey.mjs:80` (`staff` sin uso).
- Typecheck: PASS.
- Build Next.js de producción: PASS.
- `git diff --check`: PASS.
- Dos pruebas de agenda BFF requieren cookies de sesiones QA externas; no se inventaron credenciales/cookies y no se ejecutaron como prueba autenticada.

Las pruebas Maven usaron PostgreSQL 16.15 temporal y aislado, publicado solo en loopback, con base/usuario exclusivos. Se eliminó contenedor, red y volumen al terminar. No hubo acceso a producción ni a datos reales.

## Riesgos y compatibilidad

- No se modificaron `StaffAccountResponse`, `StaffIdentityResponse`, `ClinicalRecordResponse`, controladores ni endpoints Spring.
- El ID sigue siendo necesario en el listado de cuentas ADMIN empleado por la vinculación de profesionales; ocultarlo allí rompería las operaciones existentes. Ese listado permanece protegido por el rol ADMIN.
- El agregado BFF es una ruta interna de frontend y no crea un endpoint Spring.
- La proyección de paciente elimina solo IDs internos de autor no utilizados por su interfaz; el backend y consumidores médicos mantienen el DTO completo.
- La creación conserva UUID v4 y las referencias existentes; no hubo migración ni cambios de datos.
- No se expusieron secretos, contraseñas, tokens ni cookies.

## Rollback

Revertir los commits individuales de frontend/backend con `git revert <commit-CS-038>`. El backend solo tiene una aserción adicional de prueba; el rollback no requiere cambios de esquema ni recuperación de datos.

## Estado

- PostgreSQL aislado eliminado: sí.
- Secretos incluidos en código o informe: no.
- Push, merge y despliegue: no realizados.
