# Candidata combinada Erick + Fercho — readiness

Fecha de revisión: 2026-10-19  
Alcance: CS-001/002, CS-016, CS-038, CS-062 y correcciones de integración local.

## Candidata revisada

La revisión se hizo en clones temporales limpios, sin modificar `main`, `desarrollo` ni
`security/integration`:

- Backend combinado local: `e6d8c491c535a0096542ddee077d1fbd18d67b6b`.
- Frontend combinado local, con la corrección final: `a4a37769aeb058b822b77a44f12066f0032f5dda`.
- Base backend de integración: `3c7c4fa15ae24857cc5b2c159efde6d2403934b2`.
- Base frontend de integración: `76fbc47fb7aa4978e1ce7934849a542b8a11a8af`.

Los commits de Erick se integraron únicamente en la candidata local para probar la
interacción con CS-016/038/062. No se publicó esta candidata combinada.

## Correcciones realizadas durante esta revisión

1. El test de cookies Secure declaraba un proxy autenticado, pero no enviaba el secreto
   compartido. Se corrigió el fixture para que la expectativa corresponda al contrato
   real: los forwarded headers solo son confiables con el secreto del proxy.
2. `/recepcion/cobros` quedó protegido explícitamente para `RECEPCION`. Una cuenta
   `ADMIN` no puede abrir el shell ni ver datos de cobros; sus llamadas API responden
   `403`. Esta es la política de mínimo privilegio aplicada a la candidata.

## Evidencia ejecutada

- Maven combinado: exit 0; 134 casos contabilizados, 0 fallos, 0 errores y 28 omitidos
  por condiciones propias de pruebas/entorno. La repetición final, con el backend de
  pruebas aislado y sin el servidor Spring compartiendo la base, quedó en 134 casos,
  0 fallos, 0 errores y 31 omitidos.
- Frontend determinista combinado: 73/73 PASS.
- `npm ci --ignore-scripts`: PASS; auditoría deja 5 HIGH heredados relacionados con
  `braces@3.0.3` y su cadena.
- ESLint: 0 errores y una advertencia preexistente en
  `tests/browser-delivery-journey.mjs`.
- Node 22.23.3 LTS: `npm ci`, lint, typecheck, build y la suite frontend completa
  pasaron. Node 25.9.0 queda fuera del rango soportado y no debe usarse para el
  release.
- Typecheck: PASS.
- Build Next.js: PASS.
- BFF de agenda con sesiones sintéticas emitidas por el login local: 2/2 PASS;
  PACIENTE devuelve 401 y MÉDICO sin permiso conserva 403, sin convertirlos en 503.
- Flujo local Caddy → Next/BFF → Spring: login de paciente PASS, respuesta con
  `Via: 1.1 Caddy` y sin Bearer expuesto al navegador.
- Falsificación de `X-Forwarded-Proto/Host` desde el cliente: Caddy reemplaza los
  valores y la cookie no recibe `Secure` cuando el salto real es HTTP.
- Registro local a través de Caddy/BFF/Spring: 202 PASS y mensaje de verificación
  recibido en Mailpit.
- Política ADMIN/cobros: Spring 403 y BFF 403 verificados con una sesión ADMIN.
- Pagos: la prueba de integración de reloj controlado/idempotencia pasó dentro de la
  suite Maven combinada.
- Backup/restauración local PostgreSQL: PASS; restauración a base separada con
  Flyway V17 y 24 tablas públicas verificadas.
- `git diff --check`: PASS en ambos clones.

## Bloqueos de producción que siguen vigentes

La evidencia anterior es local y aislada; no equivale a staging productivo. La
candidata no debe desplegarse todavía porque faltan:

- Staging oficial identificado, responsable, SHAs desplegados y autorización escrita.
- Caddy oficial con certificados, firewall, CIDR, secreto y prueba externa de que
  BFF/Spring no son públicos.
- Mailpit/correo QA y simulador de pagos del entorno autorizado.
- Backup restaurable y rollback operativo del entorno autorizado. V17 no tiene
  `down migration`; un rollback de aplicación debe conservar V17.
- Decisión formal sobre los 5 HIGH de `braces@3.0.3`, con responsable, justificación,
  alcance y fecha de revalidación. No se aplicó un downgrade incompatible.
- Decisión arquitectónica para el rate limit entre varias JVM: Redis/almacenamiento
  compartido, límite en perímetro o despliegue de una sola réplica documentada.

## Dictamen

**APTO PARA REVISIÓN TÉCNICA E INTEGRACIÓN CONTROLADA. NO APTO PARA PRODUCCIÓN.**

La candidata combinada fue publicada en su rama de integración para revisión; no se
hizo merge a ramas protegidas ni despliegue. Para cambiar el dictamen se deben cerrar
los gates oficiales anteriores y repetir las pruebas combinadas en el staging
autorizado.
