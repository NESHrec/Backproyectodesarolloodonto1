# CS-001 / CS-002 — Production readiness

Fecha: 2026-10-19

Dictamen: **NO APTO PARA PRODUCCIÓN**

Uso de las ramas: revisión QA/DevOps únicamente; no autoriza merge ni despliegue.

## Matriz de gates

| Gate | Estado | Evidencia |
|---|---|---|
| SHAs/base y alcance del diff | PASS | Ramas fix verificadas; sin cambios en main, desarrollo o security/integration. |
| Suite Maven con PostgreSQL aislado | PASS | 128/128, PostgreSQL 16.15 local aislado. |
| Guard por cuenta/IP, expiración y éxito intercalado | PASS | Unidad 4/4 e integración Tomcat 1/1. |
| BFF: IPv4, IPv6, cadena, malformada y cabecera artificial | PASS automatizado | BFF 11/11; forwarded headers se ignoran sin secreto del proxy. |
| CS-028 cookies HTTPS | PASS automatizado | Cookie Secure solo con protocolo HTTPS de proxy autenticado. |
| Caddyfile | PASS sintáctico | `caddy validate` con imagen oficial 2.10-alpine. |
| Caddy real del staging sobrescribe cabeceras | NO EJECUTADO | No existe URL, acceso ni configuración del staging en los clones. |
| BFF/Spring inaccesibles desde Internet | NO EJECUTADO | Requiere escaneo/listeners/firewall del staging administrado. |
| Regresión manual completa | NO EJECUTADO | Sin endpoint/credenciales/datos QA administrados. |
| npm audit | FAIL/BLOQUEADO | 5 altas transitivas; `braces` no tiene release corregida. |
| Rate limit horizontal global | RIESGO ABIERTO | Estado en memoria por JVM; requiere decisión arquitectónica explícita. |
| Backup/restore local | PASS | Dump custom restaurado; Flyway v16 y 24 tablas. |
| Backup/rollback staging/V17 | NO EJECUTADO | El clon llega hasta V16; falta infraestructura/evidencia del staging. |

## Controles implementados

- Caddy reemplaza, no conserva, forwarded headers del cliente.
- Caddy inyecta un secreto privado; el BFF lo compara en tiempo constante y exige al menos 32 caracteres.
- IP, host y protocolo falsificados se ignoran sin autenticación del proxy.
- Spring se enlaza a loopback por defecto y Tomcat acepta forwarded headers solo desde `TRUSTED_PROXY_CIDR`.
- En contenedores, BFF y Spring deben estar en redes internas sin `ports`; solo Caddy puede publicar 80/443.

Estos controles son seguros por diseño/configuración versionada, pero su aplicación en el staging real continúa sin evidencia.

## Pruebas funcionales

Automatizadas PASS: roles PACIENTE/ADMIN/RECEPCION/MEDICO, autenticación, registro/verificación/recuperación backend, citas/disponibilidad, módulos administrativos, pacientes, cobros y pagos simulados incluidos en las 128 pruebas Maven y 41 frontend.

Manuales NO EJECUTADAS: todos los recorridos anteriores en navegador y contra el staging administrado. No se sustituyen por las pruebas automatizadas.

## Dependencias

- Corregidas: `sharp 0.35.5`, `source-map-js 1.2.2`.
- Pendientes: cinco altas derivadas de `braces 3.0.3`; no existe versión superior publicada. No se aplicó el downgrade mayor sugerido por npm.
- Entorno: actualizar Node de 22.20.0 a 22.22.2 o superior compatible antes de homologación.

## Riesgos residuales y decisiones

1. Elegir Redis/limitación perimetral/una réplica para consistencia horizontal.
2. Aceptar temporalmente o mitigar la cadena `braces` de tooling; no procesa entrada clínica en runtime, pero permanece como gate de supply chain.
3. Aprovisionar el mismo secreto en Caddy/BFF y rotarlo; nunca exponerlo al navegador.
4. Configurar `TRUSTED_PROXY_CIDR` con la IP/CIDR exacta del BFF, no una red amplia.
5. Probar acceso directo desde una red externa y una interna del staging.
6. Confirmar migraciones reales del staging; aquí no existe V17.

## Plan de rollback

1. Conservar imagen/artefacto y configuración anteriores, más dump restaurable.
2. Revertir los commits de esta rama mediante `git revert`; no usar `reset --hard` ni reescribir historial compartido.
3. Desplegar primero en QA, validar salud, autenticación, cookies y pagos simulados.
4. Para migraciones sin down migration, restaurar backup en una base/instancia nueva y cambiar tráfico; no alterar la historia Flyway manualmente.

## Condiciones para cambiar el dictamen

El dictamen solo puede cambiar cuando el equipo aporte evidencia verificable del staging para Caddy, listeners/firewall, regresión manual, backup/rollback y decisión horizontal, y cuando el gate de dependencias sea resuelto o aceptado formalmente.

## Addendum de revalidación local — 2026-10-19

Se corrigió el contrato de autorización paciente/personal y se publicaron los commits backend `72d7a9b6c14dcaa75c8dd108f0a0c2f0f854ec30` y frontend `d2e5da781bfadbaa3e5b34c4cb1aa99e87bac900`. La revalidación obtuvo BFF de agenda **2/2 PASS**, frontend **77/77 PASS**, Maven **128/128 PASS** en una base PostgreSQL Docker nueva, Caddy validado, registro con correo recibido por Mailpit y backup/restore local PASS.

Esta corrida sí comprobó el flujo técnico en contenedores locales, pero no fue el staging administrado. Permanecen como gates de producción: acceso externo y firewall del staging, certificados y secretos reales, regresión manual completa, rollback del artefacto oficial, rate limit horizontal, decisión formal sobre `braces@3.0.3` y homologación de Node compatible. Por lo tanto, el dictamen no cambia: **NO APTO PARA PRODUCCIÓN**.
