# CS-001 / CS-002 — Rate limiting de login

Fecha de validación: 2026-10-19

Responsable de ejecución: Codex, bajo autorización del equipo Scrum/QA/DevOps

Rama en ambos repositorios: `fix/CS-001-002-rate-limit`

## Bases y commits recibidos

- Backend: base `4dbe347c7face9cf02f892b3262331cf633a4bd7`; commit recibido `777e0aad3199ba8d49b1cdc84e16bbaabff3b11b`.
- Frontend: base `ebf7fbe723416a45ad43c3357b2a1a9c18c2151a`; commit recibido `30b25d3d91e0f963aaeef34e4be94b7e217b93fb`.

## Problemas corregidos

1. Un login válido ya no elimina el contador por IP; solo limpia el contador de la cuenta autenticada.
2. El BFF ya no considera confiable una `X-Forwarded-For` solo por tener formato IPv4/IPv6.
3. Caddy elimina las cabeceras reenviadas aportadas por el cliente, establece valores propios y autentica el salto con `TRUSTED_PROXY_SHARED_SECRET`.
4. El BFF ignora los forwarded headers si el secreto no existe, tiene menos de 32 caracteres o no coincide mediante comparación de tiempo constante.
5. CS-028 queda aplicado en el BFF: cookies `Secure`, protocolo y host reenviados solo se aceptan del proxy autenticado.
6. Spring escucha en `127.0.0.1` por defecto y Tomcat limita forwarded headers mediante `TRUSTED_PROXY_CIDR`; en contenedores debe usarse una red privada sin puerto publicado.
7. Se actualizaron de forma dirigida `sharp` a 0.35.5 y `source-map-js` a 1.2.2 mediante `overrides`.

## Diff revisado

Backend:

- `LoginAttemptGuard.java`: conserva el estado por IP después de login exitoso.
- `application.yml`: estrategia native, headers Tomcat, bind loopback y allowlist configurable del BFF.
- `ForwardedLoginRateLimitIntegrationTest.java` y `LoginAttemptGuardTest.java`: bloqueo por IP/cuenta, dos IP, éxito intercalado y expiración.
- Informes de validación.

Frontend:

- `src/modules/auth/trusted-proxy.ts`: autenticación del salto de proxy.
- `src/modules/auth/session-security.ts`: CS-028 solo confía en protocolo/host autenticados.
- `src/app/api/session/login/route.ts`: solo reenvía IP de proxy autenticado.
- `tests/unified-login-route.test.ts`: IPv4, IPv6, cadena, formato inválido, cabecera artificial y cookies Secure.
- `deploy/Caddyfile` y `deploy/README.md`: frontera Caddy → BFF reproducible.
- `.env.example`, `package.json` y `package-lock.json`: variable de secreto y correcciones dirigidas.

## Evidencia local ejecutada

- PostgreSQL `postgres:16-alpine`, image ID `sha256:3c5c8892d184f738f4fe282d14ddaa613a38f00f4189d2d94725ebe6f2909ddb`, proyecto aislado `cs001002qa`, puerto 15432.
- Mailpit `axllent/mailpit:v1.27`, image ID `sha256:e22dce5b36f93c77082e204a3942fb6b283b7896e057458400a4c88344c3df68`, puertos 13025/20025.
- Caddy `caddy:2.10-alpine`, digest `sha256:4c6e91c6ed0e2fa03efd5b44747b625fec79bc9cd06ac5235a779726618e530d`.
- Suite Maven completa: **128/128 PASS**; 0 fallos, 0 errores, 0 omitidas.
- `ForwardedLoginRateLimitIntegrationTest`: **1/1 PASS** con Tomcat real.
- `LoginAttemptGuardTest`: **4/4 PASS**.
- BFF autenticación/roles/proxy/CS-028: **11/11 PASS**.
- Otros scripts frontend: **30/30 PASS**.
- ESLint: PASS, 0 errores y 1 advertencia preexistente en `tests/browser-delivery-journey.mjs`.
- Typecheck aislado: PASS. Una ejecución paralela con `next build` falló por carrera sobre `.next`; la repetición secuencial pasó.
- Build Next.js: PASS.
- `npm ci`: PASS con advertencias de motor porque Node 22.20.0 es inferior al mínimo 22.22.2 de dependencias de test.
- `caddy validate`: PASS para `deploy/Caddyfile`; advertencias no bloqueantes de formato/header_up.
- Backup/restore: `pg_dump -Fc`, restauración en `cs001002_restore`, Flyway v16 y 24 tablas verificadas: PASS.

## npm audit

Antes: 7 altas. Después: 5 altas, 0 críticas. Se eliminaron los hallazgos de `sharp` y `source-map-js`.

Persisten `braces <=3.0.3` y su cadena (`micromatch`, `fast-glob`, `@next/eslint-plugin-next`, `eslint-config-next`). Al 2026-10-19, `npm view braces version` devuelve 3.0.3: no existe versión corregida publicada. La recomendación automática de npm es bajar `eslint-config-next` de 16.x a 14.2.35, cambio mayor incompatible con Next 16; no se aplicó.

## Rate limit horizontal

`LoginAttemptGuard` conserva estado en memoria de una JVM. Un despliegue de varias réplicas no ofrece un límite global sin almacenamiento compartido. Decisión requerida antes de producción: implementar contador atómico compartido (por ejemplo Redis administrado), aplicar rate limit global en el perímetro o mantener una sola réplica documentada. Esta rama no introduce Redis ni una migración sin arquitectura/operación aprobadas.

## Pruebas pendientes de infraestructura

- Tráfico real del staging administrado cliente → Caddy → Next/BFF → Spring.
- Verificación externa de que solo Caddy tiene listener/ruta pública y que BFF/Spring no son alcanzables desde Internet.
- Configuración real de secretos, CIDR del BFF, certificados y firewall.
- Regresión manual en navegador de login, registro, verificación, recuperación, reservas, módulos administrativos, búsqueda, cobros y pagos simulados.
- Backup/restauración del staging y rollback del artefacto desplegado.
- Revisión de una eventual V17: este clon solo contiene migraciones hasta V16, por lo que no se puede validar aquí su ausencia de down migration.

Ningún punto de esta sección se marca PASS.

## Rollback

La base no debe retrocederse con una down migration destructiva. Conservar el backup verificado, desplegar el artefacto anterior y crear commits `git revert` de los commits de esta rama. Si existe una V17 en staging, restaurar sobre una instancia/base nueva desde backup validado y cambiar tráfico solo después de smoke tests; no modificar manualmente `flyway_schema_history`.

## Dictamen

**NO APTO PARA PRODUCCIÓN** hasta completar la validación del staging, la regresión manual y una decisión explícita para rate limit horizontal y los cinco hallazgos npm restantes. La publicación de la rama autoriza únicamente revisión QA/DevOps.
