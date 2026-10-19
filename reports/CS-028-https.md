# CS-028 — HTTPS, Caddy, redirección, HSTS y cookies seguras

## Alcance

- Repositorio: `Backproyectodesarolloodonto1`
- Rama de trabajo: `fix/CS-028-https`
- Commit base: `4dbe347c7face9cf02f892b3262331cf633a4bd7`
- Rama de integración de referencia: `security/integration`
- Producción: no modificada y no desplegada desde esta rama.

## Cambios realizados

- `src/main/resources/application.yml`
  - Añade `server.forward-headers-strategy` configurable mediante `SERVER_FORWARD_HEADERS_STRATEGY`, con valor predeterminado `framework`.
  - Esto permite que Spring interprete correctamente `X-Forwarded-Proto` y `X-Forwarded-Host` cuando la aplicación está detrás de Caddy.
- Copia de despliegue local `clinica-serena-vps-deploy-20261017/Caddyfile`
  - Configura `clinicaserena.fyi` como sitio HTTPS.
  - Caddy queda encargado del certificado y de la redirección HTTP a HTTPS.
  - Añade HSTS con `max-age=31536000; includeSubDomains`.
  - Añade encabezados defensivos en el borde y elimina `Server`.
  - Reenvía explícitamente `X-Forwarded-Proto` y `X-Forwarded-Host` al frontend.
- Copia de despliegue local `clinica-serena-vps-deploy-20261017/compose.yml`
  - Configura `SERVER_FORWARD_HEADERS_STRATEGY=framework` en backend.
  - Activa `ENABLE_HTTPS_UPGRADE=true` en build y runtime del frontend.
  - Mantiene backend y PostgreSQL sin publicación directa; solamente Caddy expone 80/443.
  - `AUTH_PUBLIC_WEB_URL` debe existir en el `.env` de la VPS con el valor público HTTPS, sin incluirlo en este informe.

## Pruebas ejecutadas

- `./mvnw -DskipTests compile -Dmaven.compiler.release=17`: correcto.
- `ClinicaSerenaApplicationTests` y `UnifiedAuthControllerIntegrationTest` con PostgreSQL aislado y Flyway hasta V16: 5/5 correctas, 0 fallos, 0 errores.
- En la ejecución completa iniciada con PostgreSQL aislado, 22 pruebas llegaron a completarse con 0 fallos y 0 errores antes de detenerla por duración; no se presenta como suite completa aprobada.
- `docker compose -f compose.yml config --quiet`: correcto.
- Gate adicional de autenticación: `PatientAuthControllerIntegrationTest` terminó 11/11 correctas usando un SMTP deliberadamente inaccesible; el backend respondió sin bloquearse y registró `MAIL_DELIVERY_FAILED` con `causeType=ConnectException`, sin exponer secretos.
- La validación ejecutable de Caddy no pudo realizarse porque Docker Desktop estaba pausado; debe repetirse `caddy validate` o `docker compose up` cuando Docker esté disponible.

## Riesgos y pendientes

- El certificado automático requiere que el DNS de `clinicaserena.fyi` apunte a `207.244.224.129` y que los puertos TCP 80 y 443 estén permitidos tanto en Contabo como en el firewall del sistema.
- Antes de producción hay que comprobar que `AUTH_PUBLIC_WEB_URL=https://clinicaserena.fyi` y que las cookies emitidas por el frontend llevan `Secure`, `HttpOnly` y `SameSite` según el flujo.
- HSTS con `includeSubDomains` solo debe publicarse cuando todos los subdominios que existan estén preparados para HTTPS.
- El build de la VPS todavía debe ejecutarse con Docker disponible; no se ha reiniciado ni modificado la producción.
- El proyecto declara Java 21; la compilación local se validó con el JDK 17 disponible usando `-Dmaven.compiler.release=17`. La validación final debe ejecutarse también con Java 21.

## Rollback

Antes de publicar, revisar y crear un commit propio de esta rama. Si el cambio se aprueba y luego debe revertirse, revertir ese commit, restaurar el Caddyfile/compose anterior y reconstruir los servicios. No hay migraciones de base de datos en este cambio.

## Secretos

No se añadieron, imprimieron ni documentaron contraseñas, tokens, API keys ni valores de `.env`.
