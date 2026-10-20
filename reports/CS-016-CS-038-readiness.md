# CS-016 / CS-038 — Readiness para integración y validación

## Autorización de base vigente

Las referencias remotas fueron actualizadas con `git fetch --all --prune` y verificadas en los remotos configurados.

- Backend remoto autorizado para esta candidata: `origin/security/integration` en `3c7c4fa15ae24857cc5b2c159efde6d2403934b2`.
- Frontend remoto autorizado para esta candidata: `origin/security/integration` en `76fbc47fb7aa4978e1ce7934849a542b8a11a8af`.
- Las bases anteriores de trabajo eran backend `4dbe347c7face9cf02f892b3262331cf633a4bd7` y frontend `ebf7fbe723416a45ad43c3357b2a1a9c18c2151a`; no son la punta vigente.
- La revisión del historial confirma que CS-022 (frontend security headers) y CS-028 (backend HTTPS forwarding) ya son ancestros de las bases actuales; no se reaplican.
- CS-062 no es ancestro de backend `security/integration`; la rama remota observada es `fix/CS-062-search-escaping`, SHA `c70764436c8725771c99af43099cf2af249816dd` (cambio funcional `c99c913`). Su compatibilidad y alcance quedan sujetos a revisión en esta candidata.

Los worktrees de readiness se crearon nuevos desde estas referencias exactas. Las copias antiguas con cambios locales no se utilizaron ni modificaron.

## Cambios candidatos y resultados

- Commits individuales locales sobre las bases vigentes:
  - CS-016 backend: `b10cd21` (`fix/security): audit events on current security base`.
  - CS-016 frontend: `b30a9f8` (`feat(admin): render security audit events`).
  - CS-038 backend: `3887e16` (`test(security): verify UUID account identifiers`).
  - CS-038 frontend: `d50c346` (`fix(security): minimize account identifier exposure`).
- Integración candidata: worktrees backend y frontend en `integration/cs016-cs038-readiness`, derivados de las bases listadas arriba. Los commits de cierre de estas worktrees se informan en la entrega de revisión; no se publicaron.
- Inventario backend candidato:
  - Auditoría: `AuditEventResponse.java`, `BitacoraEvento.java`, `BitacoraService.java`, `PatientAuthController.java`, `PatientAuthService.java`, `UnifiedAuthService.java`, `GlobalExceptionHandler.java`, `SecurityConfig.java`, `StaffController.java`, `StaffAuthService.java`.
  - Búsqueda CS-062: `PacienteAdministrativoRepository.java`, `PacienteAdministrativoService.java`.
  - Migración/pruebas: `V18__security_audit_client_ip.sql`, `BitacoraServiceClientIpTest.java`, `PatientAuthControllerIntegrationTest.java`, `UnifiedAuthControllerIntegrationTest.java`, `ReceptionPatientAndAuditIntegrationTest.java`, `StaffControllerIntegrationTest.java`.
  - Informes: `CS-016-audit-events.md`, `CS-038-account-ids.md`, este readiness report.
- Inventario frontend candidato:
  - BFF: `src/app/api/patient/clinical-record/route.ts`, `src/app/api/staff/accounts/route.ts`, `src/app/api/staff/accounts/summary/route.ts`, `src/app/api/staff/accounts/[accountId]/practitioner/route.ts`.
  - Sesión/proyecciones/UI: `staff-session.ts`, `staff-proxy.ts`, `schemas.ts` de atención médica y usuarios, `AdminAccountsSummaryClient.tsx`, `AdminAuditClient.tsx`, `PatientClinicalRecordClient.tsx`.
  - Pruebas: `dashboard-summaries.test.tsx`, `staff-account-id.test.ts`, `unified-login-route.test.ts`.
- CS-016 backend: base `3c7c4fa15ae24857cc5b2c159efde6d2403934b2`, suite Maven 129/129 PASS.
- CS-016 frontend: base `76fbc47fb7aa4978e1ce7934849a542b8a11a8af`, deterministas 62/62 PASS, bitácora 4/4 PASS, lint/typecheck/build PASS.
- CS-038 backend: base `3c7c4fa15ae24857cc5b2c159efde6d2403934b2`, suite Maven 125/125 PASS; producción backend sin cambios, solo aserción UUID v4 e informe.
- CS-038 frontend: base `76fbc47fb7aa4978e1ce7934849a542b8a11a8af`, deterministas 66/66 PASS, dirigidas 21/21 PASS, lint/typecheck/build PASS.
- Candidata combinada backend CS-016 + CS-038 + CS-062: Maven 131/131 PASS en PostgreSQL aislado desde base vacía.
- Candidata combinada frontend: deterministas 66/66 PASS, lint/typecheck/build PASS.
- ESLint emite una advertencia preexistente (`tests/browser-delivery-journey.mjs:80`, variable `staff` no usada); cero errores.
- La primera ejecución Maven en paralelo falló por límite de conexiones del PostgreSQL aislado (`SQLSTATE 53300`); las tres suites se repitieron secuencialmente en bases nuevas y pasaron con los conteos indicados.
- `npm ci` reportó 7 advisories HIGH heredados; no se ejecutó `npm audit fix` ni se actualizaron dependencias.
- Prueba BFF autenticada de agenda no ejecutable: faltan `STAFF_AGENDA_PATIENT_COOKIE` y `STAFF_AGENDA_MEDICO_COOKIE`. Estado **BLOQUEADA**, no PASS. No se generaron sesiones ni credenciales.

CS-062 fue integrado en la candidata backend únicamente para sus dos clases de código y pruebas: `PacienteAdministrativoRepository`, `PacienteAdministrativoService` y `ReceptionPatientAndAuditIntegrationTest`, desde la rama `fix/CS-062-search-escaping` (punta `c70764436c8725771c99af43099cf2af249816dd`, implementación `c99c913`). Sus informes históricos ajenos no se copiaron. CS-022 y CS-028 ya eran ancestros de las bases vigentes y no se reaplicaron.

La migración V18 pasó desde vacío por Maven y sobre una base que ya contiene V17 (`push devices and appointment reminders`), evitando colisión de versiones. Se conserva el evento sintético existente con IP nula; `client_ip` quedó nullable/45, la restricción admite `SISTEMA`, los índices previos permanecieron y un arranque posterior informó “No migration necessary”. La cobertura de IP IPv4/IPv6/ausente pasó 3/3.

La validación de base usó PostgreSQL 16 temporal en loopback, bases sintéticas separadas y credenciales solo en el proceso/contenedor. El contenedor, red y volumen temporales fueron eliminados y se verificó que no quedara ninguno.

## Staging, backups y rollback

La migración es aditiva y no tiene down migration: rollback de aplicación debe dejar V17/V18 intactas; revertir el esquema exige restaurar un backup previo aprobado o aplicar un forward-fix revisado.

Las dos pruebas BFF autenticadas deben ejecutarse con sesiones QA emitidas por el login autorizado o recibir aceptación formal del responsable. No se publican ramas mientras no se resuelva este bloqueo y se apruebe el rollback/staging.

## Dictamen

**BLOQUEADO** — suites individuales y combinada aprobadas, pero pruebas BFF autenticadas críticas sin sesión QA, staging/backups/servicios simulados no verificados y rollback operativo aún no aprobado. No publicar ramas, mergear ni desplegar.
