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
  - Migración/pruebas: `V17__security_audit_client_ip.sql`, `BitacoraServiceClientIpTest.java`, `PatientAuthControllerIntegrationTest.java`, `UnifiedAuthControllerIntegrationTest.java`, `ReceptionPatientAndAuditIntegrationTest.java`, `StaffControllerIntegrationTest.java`.
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

La migración V17 pasó desde vacío por Maven y desde V16 mediante Flyway. En la actualización V16→V17 se conservó el evento sintético existente con IP nula; `client_ip` quedó nullable/45, la restricción admite `SISTEMA`, los índices previos permanecieron y un arranque posterior informó “No migration necessary”. La cobertura de IP IPv4/IPv6/ausente pasó 3/3.

La validación de base usó PostgreSQL 16 temporal en loopback, bases sintéticas separadas y credenciales solo en el proceso/contenedor. El contenedor, red y volumen temporales fueron eliminados y se verificó que no quedara ninguno.

## Staging, backups y rollback

No se identificó un endpoint/entorno staging autorizado para esta ejecución. Mailpit/correo QA, proveedor de pagos simulado, backups y smoke tests integrales en staging quedan **sin verificar**; producción no se utilizó. La migración es aditiva y no tiene down migration: rollback de aplicación debe dejar V17 intacta; revertir el esquema exige restaurar un backup previo aprobado o aplicar un forward-fix revisado. No se pudo verificar backup ni existe aprobación documentada de restauración.

Las dos pruebas BFF autenticadas deben ejecutarse con sesiones QA emitidas por el login autorizado o recibir aceptación formal del responsable. No se publican ramas mientras no se resuelva este bloqueo y se apruebe el rollback/staging.

## Dictamen

**BLOQUEADO** — suites individuales y combinada aprobadas, pero pruebas BFF autenticadas críticas sin sesión QA, staging/backups/servicios simulados no verificados y rollback operativo aún no aprobado. No publicar ramas, mergear ni desplegar.

## Addendum de readiness — 2026-10-19

### Pruebas BFF autenticadas de agenda

Las variables de proceso `STAFF_AGENDA_PATIENT_COOKIE` y `STAFF_AGENDA_MEDICO_COOKIE` no están configuradas. Al ejecutar la suite, las dos pruebas dependientes de esas sesiones fallaron en la validación previa de cookie requerida; no se llamaron como PASS ni se fabricaron sesiones, cookies o datos. Las otras 76 pruebas del conjunto frontend pasaron. Pendiente: responsable QA debe proporcionar un mecanismo seguro para que el proceso local obtenga sesiones de QA emitidas por el login autorizado; no pegar cookies/tokens en chat ni guardarlos en archivos. Hasta entonces siguen sin evidenciarse autorización de agenda por rol, 401/403 autenticados, cookies emitidas en vivo, ausencia de Bearer en navegador y limpieza de fixtures.

### Staging, correo, pagos y recuperación

La inspección documental disponible no identifica un destino staging vigente y autorizado, responsable operativo, SHAs desplegados, PostgreSQL aislada asignada, Mailpit/correo QA, simulador de pagos autorizado ni Caddyfile/Compose vigente para ese entorno. Los informes históricos de Caddy y una ruta de respaldo de una VPS no prueban que ese destino sea staging ni autorizan su uso. No se hicieron solicitudes de registro/correo, recuperación, pagos ni operaciones contra una VPS. Pendiente: release/infra debe entregar esos datos y autorizar el entorno; QA debe confirmar cuentas sintéticas y limpieza.

No se encontró evidencia de un procedimiento oficial aprobado para backup/restore y rollback de la migración V17. Propuesta **NO APROBADA** para que DBA/release owner la revise: tomar un `pg_dump` pre-migración de una base aislada; restaurarlo en otra base desechable y comprobar consistencia; aplicar V17 y probar el arranque; conservar imagen/configuración anterior. Si se revierte solo la aplicación, dejar V17 intacta; para problema de esquema, usar forward-fix revisado o restauración de backup con aprobación explícita. No ejecutar `flyway clean` ni restaurar datos reales.

### Auditoría de dependencias frontend

En la candidata frontend `4d9266c34351f64f80ae9b197aa6ee3a89f38e31`, `npm ci` inicial y `npm audit --json` encontraron 7 HIGH. `npm explain` identificó `sharp@0.35.4` desde Next 16.3.8, `source-map-js@1.2.1` por Tailwind/PostCSS/jsdom y `braces@3.0.3` por eslint-config-next → @next/eslint-plugin-next → fast-glob → micromatch. Se actualizaron puntualmente, dentro de rangos ya admitidos, sharp a 0.35.5 y source-map-js a 1.2.2 mediante `npm update sharp source-map-js`; no se ejecutó `npm audit fix` ni un `npm update` general. Reinstalación `npm ci` desde el lock resultante: PASS. El audit actual informa 0 Critical, 5 HIGH, todos atribuibles a `braces@3.0.3` (GHSA-vfj7-8cjw-p6xm, sin versión corregida publicada); la sugerencia automática de npm requiere bajar `eslint-config-next` a 14.2.35, cambio mayor incompatible que no se aplicó. `package.json` no cambió; `package-lock.json` es el único archivo frontend modificado.

Después de actualizar esas dependencias: 76/76 pruebas frontend ejecutables excluyendo las dos pruebas BFF con sesión ausente; lint PASS con una advertencia preexistente en `tests/browser-delivery-journey.mjs:80`; typecheck PASS; build PASS. La suite completa, incluidas las dos pruebas bloqueadas, reporta 76 PASS / 2 FAIL por ausencia de las cookies de sesión requeridas. `git diff --check` PASS.

### Estado Git antes de los commits de publicación

Frontend permanece en `integration/cs016-cs038-readiness`, HEAD `4d9266c34351f64f80ae9b197aa6ee3a89f38e31`; solo `package-lock.json` está modificado, sin staging. Backend permanece en `integration/cs016-cs038-readiness`, HEAD `7b225e412ef54b7431406f6b0827b123946b10d2`; este addendum es el único cambio local de esta continuación, sin staging. No hubo commit, push, merge, despliegue, uso de producción ni exposición de secretos.

## Revalidación previa a publicación para revisión — 2026-10-19

- Frontend `npm ci`: PASS, reproducible con el lock actualizado.
- Pruebas frontend excluyendo los dos casos que requieren sesión QA: 76/76 PASS.
- Suite frontend completa: 76 PASS / 2 FAIL al exigir `STAFF_AGENDA_PATIENT_COOKIE` y `STAFF_AGENDA_MEDICO_COOKIE`; las pruebas autenticadas no alcanzaron sus solicitudes y permanecen sin validar.
- Frontend lint: PASS, una advertencia preexistente en `tests/browser-delivery-journey.mjs:80`; typecheck: PASS; build: PASS.
- `npm audit --json`: 0 Critical, 5 HIGH por `braces@3.0.3`; no se aceptó el riesgo ni se ejecutó `npm audit fix`.
- Backend `mvnw test`: 131/131 PASS, 0 fallos/errores/omitidas. PostgreSQL 16.15 en contenedor temporal, base y usuario sintéticos, bind loopback; V17 aplicada desde esquema vacío. El contenedor fue eliminado después y no se usaron datos reales.
- `git diff --check`: PASS en ambos repositorios. Sin cambios staged ni rutas sin resolver.

No se ejecutaron: BFF autenticado de agenda; validación de staging; Caddy → Next/BFF → Spring; Mailpit/correo QA; pagos simulados; backup restaurable, restauración ni rollback operativo. Tampoco se ejecutaron flujos de registro, verificación/recuperación de correo o pagos contra proveedor alguno. Requieren la evidencia y autorizaciones de QA, Release/DevOps y DBA detalladas arriba.

La Scrum Master autorizó publicar estas candidatas únicamente para revisión de QA/DevOps. Esa autorización no implica aceptación del riesgo `braces`, aprobación de rollback, ni autorización de producción.

### Publicación para revisión QA/DevOps

- Backend: rama `integration/cs016-cs038-readiness`; commit de informe previo a la verificación post-push `375eda25ae7a39ebd82bfdca9a9db6a845835056`; rama publicada en origin con ese SHA al momento de la verificación.
- Frontend: rama `integration/cs016-cs038-readiness`; commit `fafb5a6c1e089e910c7c9dcc1fcc1fbd5a3a3d29`; rama publicada en origin con SHA idéntico.
- Archivos del cambio publicado: backend `reports/CS-016-CS-038-readiness.md`; frontend `package-lock.json`.
- Los hashes exactos de los dos commits de publicación se encuentran en el historial de esas ramas. Este addendum final registra el resultado post-push; su commit actualiza únicamente este informe.
- No se publicaron cambios a `main`, `desarrollo` ni `security/integration`; no hubo merge ni despliegue.

Los gates externos y el hallazgo `braces` permanecen abiertos. **Estado: `PUBLICADO PARA REVISIÓN QA/DEVOPS — NO APTO PARA PRODUCCIÓN`.** No hacer merge a main/desarrollo/security/integration ni desplegar producción hasta que se aporten y aprueben las evidencias pendientes.

## Revalidación QA/DevOps posterior a la publicación — 2026-10-19

Se revisaron las ramas publicadas en clones limpios:

- Backend: `integration/cs016-cs038-readiness` en `258c7cefc329a44231ca03c42ead26c982a87fdb`.
- Frontend: `integration/cs016-cs038-readiness` actualizado a `1ca8437` después de corregir el lockfile.
- `git diff --check`: PASS en ambos repositorios.

El `package-lock.json` publicado inicialmente no era reproducible con `npm ci`: faltaban `@emnapi/core@1.11.3` y `@emnapi/runtime@1.11.3`. Se regeneró únicamente el lockfile, se verificó la instalación limpia con npm 10.9.2 y se publicó el commit frontend `1ca8437`.

Tras la corrección del lockfile:

- `npm ci`: PASS.
- lint: PASS, con una advertencia preexistente en `tests/browser-delivery-journey.mjs:80`.
- typecheck: PASS.
- build: PASS.
- pruebas frontend: 76 PASS / 2 FAIL; las dos fallas siguen siendo las pruebas BFF autenticadas de agenda por ausencia de `STAFF_AGENDA_PATIENT_COOKIE` y `STAFF_AGENDA_MEDICO_COOKIE`.
- `npm audit`: 5 HIGH por `braces@3.0.3`, sin versión corregida publicada; el arreglo automático propone un downgrade mayor de `eslint-config-next` y no se aplicó.
- `npm audit --omit=dev`: 0 vulnerabilidades de runtime, pero el riesgo de herramientas de desarrollo permanece abierto y requiere mitigación o aceptación formal.

La validación backend no pudo repetirse en este entorno: solo está disponible Java 17 y el proyecto requiere Java 21; Docker Desktop está pausado. No se marcaron Maven, PostgreSQL aislado, Mailpit, Caddy, pagos, backup ni rollback como PASS.

**Dictamen actualizado: NO APTO PARA PRODUCCIÓN.** La corrección reproducible del lockfile quedó publicada, pero siguen abiertos los dos casos BFF autenticados, la suite backend con Java 21/PostgreSQL, staging integrado, correo, pagos, backup/rollback y la decisión formal sobre `braces`.

## Revalidación backend adicional — 2026-10-19

La suite backend se repitió en una base PostgreSQL 16.13 local, nueva y exclusiva para QA, usando Java 21.0.12.1 y credenciales de proceso. Las 17 migraciones se aplicaron desde esquema vacío hasta V17 y la ejecución final obtuvo **131/131 PASS**, 0 fallos, 0 errores y 0 omitidas. La base, el usuario y el servicio SMTP temporal fueron eliminados al terminar.

Para las pruebas que envían correo se utilizó un sink SMTP local temporal en `127.0.0.1:1026`; esto valida el comportamiento SMTP de las pruebas, pero **no sustituye la validación de Mailpit en staging**.

La corrección del lockfile frontend está publicada en `1ca8437` y el informe backend actualizado en `f72de2b`. La suite frontend continúa en **76 PASS / 2 FAIL** por ausencia de las dos sesiones QA requeridas para agenda. `npm audit` continúa en 5 HIGH de desarrollo por `braces@3.0.3`; `npm audit --omit=dev` reporta 0 vulnerabilidades de runtime.

Siguen sin estar aprobados ni ejecutados: Caddy → Next/BFF → Spring en staging autorizado, las dos pruebas BFF con sesiones QA reales, Mailpit de staging, pagos simulados, backup restaurable y rollback operativo. Por ello el dictamen permanece **NO APTO PARA PRODUCCIÓN**.
