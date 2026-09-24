# Clínica Serena — Backend

API backend de Clínica Serena, un gestor de expedientes y citas para clínicas
médicas y odontológicas. Sirve al frontend web (Next.js), a la app Android del
paciente, y persiste en PostgreSQL.

Este repositorio contiene únicamente la **base técnica** del backend y el
**contrato API oficial** (`docs/openapi.yaml`). La implementación funcional de
entidades, migraciones, servicios y endpoints de negocio se realiza en fases
siguientes sobre esta estructura.

## Stack

- Java 21
- Spring Boot 3.5.16 (Web, Validation, Data JPA, Security)
- Maven (con wrapper `./mvnw`)
- PostgreSQL 16 (vía Docker)
- Flyway (migraciones de base de datos)
- springdoc-openapi (Swagger UI, solo perfil `dev`)

## Arquitectura

Arquitectura por capas:

```
controller -> service -> repository -> PostgreSQL
```

```
src/main/java/com/clinicaserena/
  ClinicaSerenaApplication.java
  config/            Seguridad, CORS, etc.
  common/
    exception/       Manejo centralizado de errores (ApiError)
    response/        DTOs de respuesta compartidos
  health/
    controller/       GET /api/v1/health (implementado)
  catalogo/
    controller/       Especialidades, médicos, disponibilidad (fase siguiente)
    service/
    repository/
    entity/
    dto/
    mapper/
  auth/               Login/roles (fase siguiente)
  citas/              Gestión de citas (fase siguiente)
```

Reglas de la arquitectura:

- Los controllers reciben y responden **DTOs**, nunca entidades JPA.
- La lógica de negocio vive en los servicios.
- El acceso a datos vive en los repositorios (Spring Data JPA).
- Las entradas de usuario se validan con `@Valid` (Bean Validation).

## Contrato API

El contrato oficial —compartido entre backend, frontend web y app Android— es
[`docs/openapi.yaml`](docs/openapi.yaml). Define las rutas bajo `/api/v1`, los
esquemas de datos y qué endpoints son públicos vs. cuáles requieren rol de
paciente. Cualquier cambio de contrato debe empezar por ese archivo.

## Cómo correr el proyecto localmente

Servicios esperados en el entorno local:

| Servicio            | URL                      |
|---------------------|--------------------------|
| Frontend Next.js    | http://localhost:3000    |
| Backend Spring Boot | http://localhost:8080    |
| PostgreSQL (Docker) | localhost:5432           |

### 1. Variables de entorno

Copia `.env.example` a `.env` y ajusta los valores si es necesario. **Nunca**
subas el archivo `.env` real a Git (ya está en `.gitignore`).

```bash
cp .env.example .env
```

### 2. Levantar PostgreSQL

```bash
docker compose up -d
```

### 3. Ejecutar pruebas

```bash
./mvnw test
```

### 4. Ejecutar el backend

```bash
./mvnw spring-boot:run
```

Para habilitar Swagger UI (solo en desarrollo):

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Con el perfil `dev` activo, la documentación interactiva queda disponible en
`http://localhost:8080/swagger-ui.html`. Fuera de ese perfil, Swagger/OpenAPI
está deshabilitado.

### 5. Verificar

```bash
curl http://localhost:8080/api/v1/health
```

Respuesta esperada:

```json
{ "status": "UP", "service": "clinica-serena-api" }
```

## Seguridad (estado actual)

- `GET /api/v1/health` es el único endpoint público implementado.
- El resto de rutas quedan protegidas (`401` sin token) a la espera de la
  autenticación real, que se implementará en la fase siguiente con
  **BCrypt** (hash de contraseñas), **JWT o cookies de sesión** y **roles**.
- No hay login ni permisos "de mentira": mientras no exista autenticación
  real, las rutas protegidas simplemente rechazan el acceso.
- CORS está restringido a `http://localhost:3000` (nunca `*`).
- Los errores siguen un formato estándar (`ApiError`) que no expone detalles
  internos (stack traces, mensajes de SQL, etc.).

## Para continuar (siguiente fase)

Ver instrucciones detalladas en
[`docs/reports/2026-09-24-alejo-backend-base-contrato.md`](docs/reports/2026-09-24-alejo-backend-base-contrato.md).
