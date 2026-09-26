# Clínica Serena — Backend

API backend para el gestor de expedientes y citas de Clínica Serena. El servicio usa Java 21, Spring Boot, PostgreSQL y migraciones Flyway.

## Estado funcional

Están implementados y son públicos:

- `GET /api/v1/health`
- `GET /api/v1/especialidades`
- `GET /api/v1/medicos`
- `GET /api/v1/medicos?specialtyId={id}`
- `GET /api/v1/medicos/{medicoId}/disponibilidad`

La persistencia de citas y la reserva transaccional de bloques están implementadas en la capa de servicio. La base de datos impide más de una cita no cancelada por bloque y el servicio bloquea la fila de disponibilidad durante la reserva. Una cancelación futura conserva el historial y libera el bloque para una nueva reserva.

`POST /api/v1/citas` y `GET /api/v1/pacientes/me/citas` permanecen protegidos y todavía no tienen controlador funcional. Aunque OpenAPI define su contrato objetivo, falta autenticación real para obtener `patientId` desde una identidad confiable. No se acepta `patientId` desde el navegador ni se usa un paciente fijo.

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

Para Swagger UI en desarrollo:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## Seguridad

Solo los `GET` de health y catálogo enumerados arriba son públicos. El resto requiere autenticación. Aún no hay login, JWT/cookie ni roles funcionales; las rutas privadas responden `401` y no se han añadido credenciales provisionales, usuarios fijos ni permisos controlados por parámetros del cliente.
