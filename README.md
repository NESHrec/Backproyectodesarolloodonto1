# Clínica Serena — Backend

API backend para el gestor de expedientes y citas de Clínica Serena. El servicio usa Java 21, Spring Boot, PostgreSQL y migraciones Flyway.

## Estado funcional

Están implementados y son públicos:

- `GET /api/v1/health`
- `GET /api/v1/especialidades`
- `GET /api/v1/medicos`
- `GET /api/v1/medicos?specialtyId={id}`
- `GET /api/v1/medicos/{medicoId}/disponibilidad`
- `POST /api/v1/auth/login`

Están implementados y protegidos para pacientes autenticados:

- `GET /api/v1/auth/me`
- `POST /api/v1/auth/logout`
- `POST /api/v1/citas`
- `GET /api/v1/pacientes/me/citas`

La autenticación de pacientes usa Bearer opaco de vida limitada: el login devuelve el token una sola vez, la base de datos conserva solo su hash y `logout` revoca la sesión. La persistencia de citas y la reserva transaccional de bloques están expuestas por controlador; el `patientId` se obtiene desde `PatientPrincipal`, no desde JSON, rutas ni query strings. La base de datos impide más de una cita no cancelada por bloque y el servicio bloquea la fila de disponibilidad durante la reserva. Una cancelación futura conserva el historial y libera el bloque para una nueva reserva.

## Stack

- Java 21
- Spring Boot 3.5.16 (Web, Validation, Data JPA, Security)
- Maven Wrapper
- PostgreSQL 16
- Flyway
- springdoc-openapi, disponible solo con el perfil `dev`

## Arquitectura

```text
controller -> service -> repository -> PostgreSQL
```

Los controladores usan DTOs, la lógica de negocio vive en servicios y el acceso a datos se realiza mediante repositorios Spring Data JPA. `docs/openapi.yaml` es la fuente de verdad del contrato HTTP.

## Ejecución local

1. Copia `.env.example` a `.env` y ajusta las credenciales.
2. Levanta PostgreSQL:

   ```bash
   docker compose up -d
   ```

3. Ejecuta las pruebas con JDK 21:

   ```bash
   ./mvnw test
   ```

4. Inicia el backend:

   ```bash
   ./mvnw spring-boot:run
   ```

El backend usa el puerto `8080` y PostgreSQL el `5432` de forma predeterminada. Ambos pueden configurarse con las variables documentadas en `.env.example`.

Registro, verificacion y recuperacion usan SMTP (`SMTP_HOST`, `SMTP_PORT`) y
`AUTH_PUBLIC_WEB_URL`; los enlaces nunca se construyen desde `Host`. Mailpit queda
por defecto en `http://localhost:8025`. V7 normaliza correos con `lower(trim(...))`;
si dos cuentas historicas colisionan, aborta sin borrar ni fusionar identidades. Un
responsable debe verificar identidad e historicos, asignar correos unicos confirmados
y reintentar Flyway. El limite publico usa el hash de la identidad normalizada, no la
IP compartida por el BFF.
Los enlaces usan el fragmento `#token=`, que no forma parte de la solicitud HTTP
inicial. Un reenvio limitado invalida enlaces de verificacion anteriores y nunca
cambia anonimamente la contrasena.

Para Swagger UI en desarrollo:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## Seguridad

Health, catálogo y login son públicos. El resto requiere `Authorization: Bearer <token>` de un paciente activo. El backend permanece stateless y mantiene CSRF deshabilitado porque no autentica con cookies; la protección CSRF del flujo web se aplica en el BFF de Next.js, antes de reenviar el Bearer al backend.

No se añadieron usuarios fijos, cuentas de ejemplo ni permisos controlados por parámetros del cliente.
