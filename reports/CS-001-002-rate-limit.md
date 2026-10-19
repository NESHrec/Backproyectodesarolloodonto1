# CS-001 / CS-002 — Rate Limiting Login

## Responsable

Erik

## Rama

`fix/CS-001-002-rate-limit`

## SHA base frontend

`ebf7fbe723416a45ad43c3357b2a1a9c18c2151a`

## SHA base backend

`4dbe347c7face9cf02f892b3262331cf633a4bd7`

## Causa

El limitador mantenía contadores separados por cuenta y por dirección, pero `recordSuccess` eliminaba ambos. Un acceso correcto podía reiniciar el contador por IP y permitir nuevos intentos. Además, el BFF no propagaba la IP entregada por Caddy, por lo que Spring observaba la dirección compartida del BFF.

## Archivos modificados

Backend:

- `src/main/java/com/clinicaserena/auth/security/LoginAttemptGuard.java`
- `src/main/resources/application.yml`
- `src/test/java/com/clinicaserena/auth/security/LoginAttemptGuardTest.java`
- `src/test/java/com/clinicaserena/auth/controller/ForwardedLoginRateLimitIntegrationTest.java`
- `reports/CS-001-002-rate-limit.md`

Frontend:

- `src/app/api/session/login/route.ts`
- `tests/unified-login-route.test.ts`

## Cambio en LoginAttemptGuard

Un login exitoso elimina solamente el estado de la cuenta autenticada. El contador por IP permanece activo hasta que vence la ventana o el cooldown configurado. No se cambiaron umbrales, ventana, cooldown ni capacidad.

## Tratamiento de IP real

Spring Boot usa `server.forward-headers-strategy: native`. Tomcat interpreta `X-Forwarded-For` mediante su soporte nativo y solo acepta forwarded headers cuando el salto inmediato pertenece a sus proxies internos confiables. Sin proxy, `getRemoteAddr()` conserva la dirección de la conexión directa.

## BFF / proxy

El BFF de login toma exclusivamente la primera dirección de la cadena `X-Forwarded-For`, valida que sea IPv4 o IPv6 y la envía a Spring. Una cabecera malformada se omite. La topología autorizada es cliente → Caddy → Next/BFF → Spring; Caddy y el BFF deben permanecer como únicos puntos públicos, y Spring debe permanecer detrás de ellos.

No se reenvían cookies, CSRF ni tokens mediante esta cabecera. El Bearer continúa confinado al BFF.

## Límites y ventanas preservados

- Cuenta: 5 fallos.
- IP: 20 fallos.
- Ventana: 60 segundos.
- Cooldown: 60 segundos.
- Capacidad: 10,000 entradas.

## Pruebas ejecutadas

- `LoginAttemptGuardTest`: 4/4 PASS.
- Autenticación dirigida con PostgreSQL aislado: 19/19 PASS.
- Tomcat real y forwarded headers: 1/1 PASS.
- Suite completa Maven: 128/128 PASS.
- BFF de autenticación: 8/8 PASS.
- Resto de pruebas frontend disponibles: 30/30 PASS.
- ESLint: PASS, con una advertencia preexistente fuera del alcance.
- TypeScript: PASS.
- Build de producción Next.js: PASS.

## Resultados

- El contador por cuenta se limpia después de un login válido.
- El contador por IP no se limpia después de un login válido.
- Cambiar de correo no evade el límite por IP.
- Dos IP distintas conservan estados independientes.
- Cuenta e IP se desbloquean al vencer ventana/cooldown.
- PACIENTE, ADMIN, RECEPCION y MEDICO conservan sus flujos de autenticación.
- El BFF no expone el Bearer al navegador.

## Prueba detrás de proxy

`ForwardedLoginRateLimitIntegrationTest` inicia Tomcat en un puerto aleatorio y envía solicitudes HTTP reales desde loopback, que representa el proxy interno confiable. Verifica solicitud directa sin proxy, dos IP reenviadas independientes, bloqueo por IP y que un login correcto intercalado no reinicia ese contador. Las pruebas del BFF verifican además el reenvío de la primera IP válida y el rechazo de valores malformados.

## Roles existentes

Los flujos unificados y separados de paciente y personal siguen disponibles. La suite cubrió los roles PACIENTE, ADMIN, RECEPCION y MEDICO sin cambiar contratos ni autorizaciones.

## Posibles conflictos con CS-028

CS-028 también necesita forwarded headers para interpretar `X-Forwarded-Proto` y emitir cookies `Secure`. Esta corrección configura también el nombre de `protocol-header` en Tomcat, pero no habilita HTTPS, HSTS, certificados ni cambios de cookies. La futura corrección CS-028 debe conservar la estrategia nativa y la frontera de proxies confiables.

## Riesgos

- Next/BFF no debe exponerse directamente a Internet: confía en la cadena saneada que recibe del proxy frontal autorizado.
- Spring no debe publicarse directamente: la configuración presupone BFF en loopback o red privada confiable.
- El estado del limitador sigue siendo local a cada instancia; un despliegue horizontal requeriría almacenamiento compartido para un límite global.

## Rollback

Revertir los commits de esta rama con `git revert <sha-del-commit>` en frontend y backend. No usar `reset --hard`. El rollback restaura el comportamiento anterior sin reescribir historial.

## Secretos

No se registraron ni versionaron secretos, tokens, cookies, CSRF, archivos `.env`, datos clínicos ni credenciales reales. Las pruebas utilizaron exclusivamente credenciales sintéticas y una PostgreSQL aislada.
