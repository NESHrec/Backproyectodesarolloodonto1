# Informe — Base de backend y contrato API (Clínica Serena)

- **Fecha:** 2026-09-24
- **Responsable:** Alejo
- **Repositorio:** `NESHrec/Backproyectodesarolloodonto1`
- **Rama:** `main` (sin commits — el repositorio remoto estaba completamente vacío al iniciar)

## 1. Objetivo de la tarea

Crear la base técnica del backend de Clínica Serena (proyecto Spring Boot con
arquitectura por capas) y el contrato API oficial (`docs/openapi.yaml`) que
compartirán el backend, el frontend Next.js y la app Android del paciente.
No se implementó lógica de negocio (pacientes, pagos, recetas, expedientes,
autenticación real): eso corresponde a Erick en la fase siguiente.

## 2. Estructura creada

```
com.clinicaserena/
  ClinicaSerenaApplication.java
  config/
    SecurityConfig.java
  common/
    exception/
      ApiException.java
      GlobalExceptionHandler.java
    response/
      ApiError.java
  health/
    HealthResponse.java
    controller/
      HealthController.java
  catalogo/
    controller/ service/ repository/ entity/ dto/ mapper/   (solo package-info.java, vacíos)
  auth/        (solo package-info.java, vacío)
  citas/       (solo package-info.java, vacío)
```

Los paquetes `catalogo/*`, `auth` y `citas` se dejaron vacíos (con
`package-info.java` documentando su propósito) para que Erick los llene con
entidades, migraciones, servicios y endpoints reales sin decisiones de
estructura pendientes.

## 3. Dependencias usadas

- Java 21 (ver sección 10 sobre el JDK usado para compilar)
- Spring Boot **3.5.16** (última versión estable de la línea 3.x disponible en
  Maven Central; ver sección 10 — Spring Initializr ya no genera proyectos
  3.x)
- Maven (con Maven Wrapper `./mvnw`, generado con `maven-wrapper-plugin:3.3.2`,
  Maven 3.9.9)
- `spring-boot-starter-web`
- `spring-boot-starter-validation`
- `spring-boot-starter-data-jpa`
- `spring-boot-starter-security`
- `org.postgresql:postgresql` (runtime)
- `org.flywaydb:flyway-core` + `org.flywaydb:flyway-database-postgresql`
  (Flyway 10+ requiere el módulo de dialecto separado)
- `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.9.1` (Swagger UI, solo
  perfil `dev`)
- `spring-boot-starter-test` y `spring-security-test` (test)

## 4. Archivos creados o modificados

Todo el repositorio estaba vacío, por lo que todos los archivos son nuevos:

- `pom.xml`
- `.mvn/wrapper/maven-wrapper.properties`, `mvnw`, `mvnw.cmd`
- `src/main/java/com/clinicaserena/**` (ver estructura arriba)
- `src/main/resources/application.yml`
- `src/main/resources/application-dev.yml`
- `src/main/resources/db/migration/.gitkeep` (carpeta lista para las
  migraciones Flyway de Erick)
- `src/test/java/com/clinicaserena/ClinicaSerenaApplicationTests.java`
- `src/test/java/com/clinicaserena/health/controller/HealthControllerTest.java`
- `docker-compose.yml`
- `.env.example`
- `.gitignore`
- `README.md`
- `docs/openapi.yaml`
- `docs/reports/2026-09-24-alejo-backend-base-contrato.md` (este informe)

No se modificó ni se borró ningún archivo existente porque no existía ninguno.

## 5. Configuración local creada

- `docker-compose.yml`: un servicio `postgres` (imagen `postgres:16-alpine`),
  puerto configurable vía `DB_PORT` (por defecto `5432`), volumen nombrado
  persistente y healthcheck con `pg_isready`.
- `.env.example` con exactamente las claves pedidas:
  ```
  DB_NAME=clinica_serena
  DB_USER=clinica_app
  DB_PASSWORD=change-me
  DB_PORT=5432
  ```
- `.gitignore` excluye `.env`, `target/`, artefactos de IDE y `.DS_Store`.
- Servicios esperados en local:
  - Frontend Next.js → `http://localhost:3000`
  - Backend Spring Boot → `http://localhost:8080`
  - PostgreSQL (Docker) → `localhost:5432`

No se creó ni se subió ningún `.env` real con credenciales.

## 6. Contenido y propósito de `docs/openapi.yaml`

Contrato OpenAPI 3.0.3, base de rutas `/api/v1`, que define:

- **Endpoints (9 en total)**: `GET /health`, `GET /especialidades`,
  `GET /medicos`, `GET /medicos/{medicoId}/disponibilidad`,
  `POST /auth/login`, `POST /citas`, `GET /pacientes/me/citas`,
  `GET /pacientes/me/recetas`, `GET /pacientes/me/chequeos`.
- **Esquemas**: `Specialty`, `Practitioner`, `AvailabilitySlot`,
  `Appointment`, `Prescription`, `PrescriptionItem`, `Checkup`,
  `LoginRequest`, `LoginResponse`, `ApiError`, más el enum
  `AppointmentStatus` (`PENDIENTE`, `CONFIRMADA`, `CANCELADA`,
  `COMPLETADA`).
- **Convenciones aplicadas**: IDs como `string`; fechas en ISO 8601 con nota
  de que la UI las muestra en `America/Guatemala`; montos como enteros
  `amountCents` (en `Appointment` y `Checkup`); formato de error estándar
  igual al implementado en `GlobalExceptionHandler`.
- **Público vs. protegido**: cada operación declara `security: []` (pública)
  o `security: [bearerAuth: []]` (requiere rol paciente), y la descripción de
  `info` aclara que la autenticación real es fase 2.

Este archivo es la fuente de verdad de contrato; su implementación funcional
(controllers/servicios reales para catálogo, citas, recetas, chequeos) queda
pendiente para Erick.

## 7. Endpoint funcional implementado

`GET /api/v1/health` — implementado y probado en vivo:

```json
{ "status": "UP", "service": "clinica-serena-api" }
```

Es el único endpoint con lógica real; el resto del contrato está solo
documentado en `docs/openapi.yaml`.

## 8. Medidas de seguridad aplicadas

- `SecurityConfig` (Spring Security): `/api/v1/health` y las rutas de
  Swagger/OpenAPI son públicas; **todo lo demás requiere autenticación**
  (`anyRequest().authenticated()`), aunque todavía no exista un mecanismo de
  login real — es decir, las rutas quedan protegidas por defecto, no
  abiertas, a la espera de JWT/cookies + roles.
- No se implementó ningún login falso ni permisos "solo visuales".
- CORS restringido explícitamente a `http://localhost:3000` (nunca `*`).
- CSRF deshabilitado de forma consciente (API stateless, sin sesiones de
  navegador) y sesión configurada como `STATELESS`.
- `GlobalExceptionHandler` centraliza los errores y responde siempre con
  `ApiError`, sin exponer stack traces, mensajes de SQL ni detalles internos.
- Swagger/OpenAPI (`springdoc`) deshabilitado por defecto
  (`application.yml`) y habilitado solo bajo el perfil `dev`
  (`application-dev.yml`).
- `.env` real excluido de Git vía `.gitignore` (verificado con
  `git check-ignore -v .env`).
- No se usó SQL concatenado (no hay acceso a datos todavía; los repositorios
  quedan vacíos para que Erick use Spring Data JPA).
- No se manejan datos reales de pacientes en ningún archivo.

## 9. Resultado exacto de cada validación

| Validación | Resultado |
|---|---|
| `git ls-remote` / estado inicial del repo | Repositorio remoto completamente vacío, sin ramas con commits (confirmado antes de esta tarea). |
| `./mvnw -q -DskipTests compile` | **BUILD SUCCESS**, sin errores ni warnings de compilación. |
| `docker compose up -d` (puerto 5432 por defecto) | **Falló**: `ports are not available: ... bind: address already in use`. Causa: hay un PostgreSQL 17 nativo del sistema (`/Library/PostgreSQL/17`) ya escuchando en `5432` en esta máquina, ajeno a este proyecto. |
| `docker compose up -d` (con `DB_PORT=5433` solo como variable de entorno temporal, sin tocar `.env.example` ni `docker-compose.yml`) | Contenedor `clinica-serena-postgres` creado y saludable (`healthy`) en ~10s. |
| `./mvnw test` (con `DB_PORT=5433` exportado) | **BUILD SUCCESS** — `Tests run: 2, Failures: 0, Errors: 0`. Incluye carga completa del contexto Spring (`ClinicaSerenaApplicationTests`) y prueba del endpoint de salud con `MockMvc` (`HealthControllerTest`), Flyway creó `flyway_schema_history` sin migraciones (esperado, sin entidades aún). |
| `./mvnw spring-boot:run` (con `DB_PORT=5433`) | Arrancó correctamente en `http://localhost:8080`. |
| `curl http://localhost:8080/api/v1/health` | `200 OK` → `{"status":"UP","service":"clinica-serena-api"}` |
| Limpieza posterior | Proceso de la app detenido y `docker compose down` ejecutado; el entorno quedó igual que antes de la validación. |

## 10. Bloqueos, pendientes o decisiones técnicas

1. **Puerto 5432 ocupado en esta máquina**: un PostgreSQL 17 nativo (no
   Docker) ya escucha en `5432`. Para la validación usé `DB_PORT=5433` solo
   como variable de entorno de la sesión (no lo escribí en `.env.example` ni
   en `docker-compose.yml`, que siguen usando `5432` por defecto como pide la
   tarea). **Quien continúe en una máquina con este mismo conflicto** deberá
   ajustar su `.env` local (no el `.env.example`) a un puerto libre, o
   detener su Postgres nativo antes de levantar el `docker-compose.yml` del
   proyecto.
2. **Spring Initializr ya no genera proyectos Spring Boot 3.x**: al intentar
   generar el scaffolding con `start.spring.io`, la API respondió
   `Invalid Spring Boot version, Spring Boot compatibility range is >=4.0.0`
   (el generador web ya solo soporta Boot 4.x). Decisión tomada: construí el
   `pom.xml` a mano fijando **Spring Boot 3.5.16**, que sigue publicado y
   soportado en Maven Central, para cumplir el requisito explícito de la
   tarea ("Spring Boot 3"). Si el equipo prefiere adoptar Spring Boot 4.x en
   su lugar, es una decisión que debe tomar el Scrum Master/equipo, no algo
   que decidí unilateralmente más allá de este punto.
3. **JDK usado para compilar**: el equipo pidió Java 21, pero esta máquina
   tiene instalados JDK 23, 11 y 8 (no 21). En vez de instalar un JDK nuevo
   sin permiso, configuré `pom.xml` con `<java.version>21</java.version>`
   (que Spring Boot traduce a `--release 21` en el compilador), de modo que
   el proyecto **compila a bytecode Java 21** aunque se use el JDK 23 ya
   instalado para ejecutar Maven. Esto es compatible con el requisito y no
   requirió instalar nada. Si el equipo prefiere un JDK 21 instalado
   explícitamente, es una instalación que debe autorizarse aparte.
4. **Docker Desktop no estaba corriendo**: estaba instalado pero el daemon
   estaba apagado. Lo inicié (`open -a Docker`, sin instalar nada) para poder
   ejecutar las validaciones; quedó corriendo al finalizar (no lo cerré,
   igual que estaba disponible antes, solo no arrancado).
5. **Pendiente para fases siguientes** (fuera de este alcance a propósito):
   entidades JPA, migraciones Flyway reales, servicios y endpoints
   funcionales de catálogo/citas/pacientes, autenticación real
   (BCrypt + JWT/cookies + roles).

## 11. Instrucciones exactas para que Erick continúe

1. `git pull origin main` (una vez que el Scrum Master apruebe y se haga el
   commit/push de esta base).
2. Revisar el contrato en `docs/openapi.yaml` antes de programar cualquier
   endpoint — es la fuente de verdad compartida con frontend y Android.
3. Copiar `.env.example` a `.env` y ajustar `DB_PORT` si su máquina ya tiene
   un PostgreSQL ocupando `5432` (ver punto 10.1).
4. `docker compose up -d` para levantar PostgreSQL.
5. Implementar, dentro de los paquetes ya creados (`catalogo/entity`,
   `catalogo/repository`, `catalogo/service`, `catalogo/controller`,
   `catalogo/dto`, `catalogo/mapper`, `auth`, `citas`):
   - Entidades JPA (no exponerlas nunca directamente en los controllers).
   - Migraciones Flyway en `src/main/resources/db/migration/`
     (`V1__...sql`, `V2__...sql`, etc. — la carpeta ya existe).
   - Servicios con la lógica de negocio.
   - Controllers que reciban/respondan **DTOs**, validados con `@Valid`,
     siguiendo exactamente las rutas y esquemas de `docs/openapi.yaml`.
   - Autenticación real (BCrypt + JWT o cookies + roles) en `auth/`, y
     actualizar `SecurityConfig` para usarla en vez de
     `anyRequest().authenticated()` "a ciegas".
6. Ejecutar `./mvnw test` y `./mvnw spring-boot:run` antes de reportar avance.
7. Crear su propio informe en `docs/reports/` siguiendo este mismo formato.

## 12. Confirmación de que no se hizo commit ni push

Confirmado: no se ejecutó `git add`, `git commit` ni `git push` en ningún
momento de esta tarea. `git status` al finalizar muestra únicamente archivos
**untracked** (no agregados al índice). El repositorio queda en el mismo
estado de rama `main` sin commits en que se encontró, únicamente con estos
archivos nuevos en el árbol de trabajo, a la espera de revisión del Scrum
Master antes de cualquier commit/push.

## 13. Corrección post-revisión (aprobada por el Scrum Master)

- **Hallazgo**: el conteo de endpoints se mencionó como "8" en un resumen
  fuera de los archivos del proyecto. El contrato `docs/openapi.yaml` siempre
  definió **9 endpoints** (`/health`, `/especialidades`, `/medicos`,
  `/medicos/{medicoId}/disponibilidad`, `/auth/login`, `/citas`,
  `/pacientes/me/citas`, `/pacientes/me/recetas`, `/pacientes/me/chequeos`),
  verificado contando las claves `paths` del archivo.
- **Corrección aplicada**: se agregó una línea explícita en
  `docs/openapi.yaml` (`info.description`) indicando "Define 9 endpoints
  iniciales..."; y en este informe (sección 6) se aclaró "(9 en total)" junto
  al listado de endpoints.
- Esta es la única corrección solicitada antes de aprobar el commit inicial
  a la rama `desarrollo`.
