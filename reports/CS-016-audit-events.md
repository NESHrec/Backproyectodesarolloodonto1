# CS-016 — Auditoría de eventos

## Identificación

- Repositorio: backend Clínica Serena.
- Rama: `fix/CS-016-audit-events`.
- Commit base autorizado: `3c7c4fa15ae24857cc5b2c159efde6d2403934b2` (`origin/security/integration` vigente al 2026-10-19).
- Frontend coordinado: rama `fix/CS-016-audit-events`, base `76fbc47fb7aa4978e1ce7934849a542b8a11a8af`.
- Alcance: auditoría transversal de autenticación y respuestas de seguridad; no cambia reglas clínicas, pagos, roles ni autorización.

## Vulnerabilidad corregida

La bitácora no registraba intentos de inicio de sesión, bloqueos, logout ni respuestas HTTP 401/403/500. Tampoco guardaba IP de origen. Se añadió una migración aditiva con `client_ip` nullable y se amplió el tipo de actor para representar eventos anónimos como `SISTEMA`, sin reescribir registros históricos.

Se registran inicios de sesión de personal y paciente (incluido login unificado), fallos y bloqueos sin guardar el correo introducido, logout, denegaciones y errores internos. Los eventos incluyen actor, rol, acción, entidad genérica, IP resuelta y fecha UTC. Para errores HTTP se guarda el método, no la ruta concreta ni parámetros que pudieran contener identificadores clínicos. La bitácora sigue siendo consultable solo por ADMIN mediante `GET /api/v1/staff/audit-events`.

La IP usa la dirección resuelta por Servlet/Spring Forwarded Headers. El proxy de borde debe ser de confianza y eliminar/reemplazar cabeceras `Forwarded`/`X-Forwarded-*` aportadas directamente por clientes; la aplicación no persiste una cabecera arbitraria.

## Archivos modificados

### Backend

- `src/main/resources/db/migration/V17__security_audit_client_ip.sql`
- `src/main/resources/application.yml`
- `src/main/java/com/clinicaserena/auditoria/dto/AuditEventResponse.java`
- `src/main/java/com/clinicaserena/auditoria/entity/BitacoraEvento.java`
- `src/main/java/com/clinicaserena/auditoria/service/BitacoraService.java`
- `src/main/java/com/clinicaserena/auth/controller/PatientAuthController.java`
- `src/main/java/com/clinicaserena/auth/service/PatientAuthService.java`
- `src/main/java/com/clinicaserena/auth/service/UnifiedAuthService.java`
- `src/main/java/com/clinicaserena/common/exception/GlobalExceptionHandler.java`
- `src/main/java/com/clinicaserena/config/SecurityConfig.java`
- `src/main/java/com/clinicaserena/staff/controller/StaffController.java`
- `src/main/java/com/clinicaserena/staff/service/StaffAuthService.java`
- Pruebas de integración de autenticación de paciente, login unificado y personal.
- Este informe.

### Frontend coordinado

- `src/modules/usuarios-accesos/schemas.ts`
- `src/modules/admin/components/AdminAuditClient.tsx`

## Pruebas y resultados

- Base Maven antes de cambios: 125 pruebas, 0 fallos/errores/omitidas.
- Suite Maven final: 126 pruebas, 0 fallos, 0 errores, 0 omitidas.
- Pruebas finales específicas (personal, paciente, login unificado e IP): 25/25 PASS.
- Pruebas unitarias de IP IPv4, IPv6 y dirección ausente: 3/3 PASS.
- Frontend determinista disponible sin cookies QA externas: 66/66 PASS; prueba específica de bitácora: 4/4 PASS.
- Lint frontend: PASS, con una advertencia preexistente en `tests/browser-delivery-journey.mjs:80` (`staff` sin uso).
- Typecheck: PASS.
- Build Next.js de producción: PASS.
- `git diff --check`: PASS.
- Migración V17: PASS desde base vacía (suite combinada), desde V16 con conservación de un evento sintético existente y sin IP, y en repetición sin migraciones pendientes. Columna nullable de 45 caracteres, restricción `SISTEMA` y ambos índices auditados.
- Las dos pruebas de `staff-agenda-bff.test.ts` requieren cookies de sesiones QA entregadas fuera del código. No se inventaron cookies; esas dos pruebas de red no formaron parte de la suite determinista final.

Las pruebas Maven usaron PostgreSQL 16.15 temporal, aislado, publicado únicamente en loopback, con identidad exclusiva para esta corrida. La migración quedó aplicada a esa base temporal; al finalizar se eliminaron contenedor, red y volumen. No se usó una base compartida ni productiva.

## Compatibilidad y riesgos

- La migración agrega una columna nullable y permite `SISTEMA`; los registros anteriores conservan `client_ip = NULL`.
- La respuesta de auditoría agrega `clientIp`. El frontend coordinado acepta `SISTEMA` y muestra la IP; desplegar el consumidor actualizado junto con el backend evita que validadores antiguos rechacen ese nuevo valor de actor.
- La base vigente ya configura `forward-headers-strategy` como `framework` con valor predeterminado `framework`; se conservó su variable de despliegue existente en vez de reemplazarla por el valor constante del cambio original.
- Los errores de persistencia de auditoría no reemplazan la respuesta original de autenticación/seguridad. Si la base de auditoría no está disponible, el evento puede no persistirse.
- El proxy debe estar configurado como frontera confiable para que Forwarded Headers no sea falsificable por clientes externos.
- No se guardan contraseñas, tokens, cookies, cuerpos, secretos ni datos clínicos en los nuevos eventos.

## Rollback

Revertir el commit de esta rama individual con `git revert <commit-CS-016>`. La migración es aditiva; no borrar la columna, reducir la restricción ni eliminar registros ya escritos. Si se requiere retirar el esquema, hacerlo mediante una migración posterior revisada y después de confirmar que no hay consumidores ni datos que dependan de él.

## Estado

- PostgreSQL aislado eliminado: sí.
- Secretos incluidos en código o informe: no.
- Push, merge y despliegue: no realizados.
