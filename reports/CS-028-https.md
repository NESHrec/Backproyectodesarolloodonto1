# CS-028 — HTTPS, Caddy, redirección, HSTS y cookies seguras

## Alcance

- Repositorio: `Backproyectodesarolloodonto1`
- Rama de trabajo: `fix/CS-028-https`
- Commit base: `4dbe347c7face9cf02f892b3262331cf633a4bd7`
- Rama de integración de referencia: `security/integration`
- Producción: desplegada en la VPS mediante la copia de despliegue validada.

## Cambios realizados

- `src/main/resources/application.yml`
  - Añade `server.forward-headers-strategy` configurable mediante `SERVER_FORWARD_HEADERS_STRATEGY`, con valor predeterminado `framework`.
  - Esto permite que Spring interprete correctamente `X-Forwarded-Proto` y `X-Forwarded-Host` cuando la aplicación está detrás de Caddy.
- Copia de despliegue local `clinica-serena-vps-deploy-20261017/Caddyfile`
  - Configura `clinicaserena.fyi` como sitio HTTPS.
  - Caddy queda encargado del certificado y de la redirección HTTP a HTTPS.
  - Añade HSTS con `max-age=31536000; includeSubDomains`.
  - Añade encabezados defensivos en el borde y elimina `Server` y `X-Powered-By`.
  - Reenvía explícitamente `X-Forwarded-Proto` y `X-Forwarded-Host` al frontend.
- Copia de despliegue local `clinica-serena-vps-deploy-20261017/compose.yml`
  - Configura `SERVER_FORWARD_HEADERS_STRATEGY=framework` en backend.
  - Activa `ENABLE_HTTPS_UPGRADE=true` en build y runtime del frontend.
  - Mantiene backend y PostgreSQL sin publicación directa; solamente Caddy expone 80/443.
  - `AUTH_PUBLIC_WEB_URL` debe existir en el `.env` de la VPS con el valor público HTTPS, sin incluirlo en este informe.

## Pruebas ejecutadas

- `./mvnw test` con Java 21, PostgreSQL aislado y SMTP deliberadamente inaccesible: 125/125 pruebas correctas, 0 fallos, 0 errores.
- `ClinicaSerenaApplicationTests` y `UnifiedAuthControllerIntegrationTest` con PostgreSQL aislado y Flyway hasta V16: 5/5 correctas, 0 fallos, 0 errores.
- `docker compose -f compose.yml config --quiet`: correcto.
- Gate adicional de autenticación: `PatientAuthControllerIntegrationTest` terminó 11/11 correctas usando un SMTP deliberadamente inaccesible; el backend respondió sin bloquearse y registró `MAIL_DELIVERY_FAILED` con `causeType=ConnectException`, sin exponer secretos.
- Caddy validó la configuración en la VPS y el despliegue reconstruyó backend y frontend correctamente.
- Smoke test público: HTTPS 200, HTTP redirige con 308, HSTS/CSP/encabezados defensivos presentes, `X-Powered-By` ausente y `/api/session/csrf` responde 200 con cookie `Secure` y `SameSite`.
- Prueba real de correo en producción: el registro de QA respondió `202`, el mensaje llegó a la bandeja Gmail de prueba y el enlace generado usa `https://clinicaserena.fyi/verificar-correo`; no se conservaron credenciales ni tokens.

## Riesgos y pendientes

- El certificado automático requiere que el DNS de `clinicaserena.fyi` apunte a `207.244.224.129` y que los puertos TCP 80 y 443 estén permitidos tanto en Contabo como en el firewall del sistema.
- Se comprobó en la VPS que `AUTH_PUBLIC_WEB_URL=https://clinicaserena.fyi`; las cookies verificadas en el flujo CSRF llevan `Secure` y `SameSite`.
- HSTS con `includeSubDomains` solo debe publicarse cuando todos los subdominios que existan estén preparados para HTTPS.
- El proyecto declara Java 21 y la suite completa se ejecutó con Java 21.
- Debe mantenerse monitoreo posterior al despliegue; el registro de QA se dejó sin verificar para no mantener una cuenta de prueba activa.

## Rollback

Si el cambio debe revertirse, restaurar el respaldo `/opt/clinica-serena/backups/pre-deploy-20261019081308-config.tgz`, restaurar el Caddyfile/compose anterior y reconstruir los servicios. No hay migraciones de base de datos en este cambio.

## Secretos

No se añadieron, imprimieron ni documentaron contraseñas, tokens, API keys ni valores de `.env`.
