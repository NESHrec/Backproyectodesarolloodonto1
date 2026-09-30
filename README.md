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

El acceso del personal usa un esquema Bearer opaco separado del de pacientes:

- `POST /api/v1/staff/auth/login` (público solo para iniciar sesión)
- `GET /api/v1/staff/auth/me` y `POST /api/v1/staff/auth/logout` (cualquier sesión de personal)
- `POST /api/v1/staff/accounts` (solo `ADMIN`; crea cuentas `RECEPCION` o `MEDICO`)
- `GET /api/v1/staff/agenda` (roles `ADMIN` y `RECEPCION`)
- `POST /api/v1/staff/agenda/{appointmentId}/arrival` (solo `RECEPCION`)

Vinculación médica (solo `ADMIN`):

- `GET /api/v1/staff/accounts?role=MEDICO`
- `PUT /api/v1/staff/accounts/{accountId}/practitioner` y `DELETE` del mismo recurso

Atención médica (solo `MEDICO` con profesional vinculado):

- `GET /api/v1/medico/citas` y `GET /api/v1/medico/citas/{appointmentId}`
- `GET /api/v1/medico/citas/{appointmentId}/expediente`
- `POST /api/v1/medico/citas/{appointmentId}/atencion`

La migración V9 añade `cuentas_personal.medico_id` con FK a `medicos`, una
restricción que solo permite vincular cuentas `MEDICO` y un índice único parcial
que impide asignar un profesional a dos cuentas activas. Las cuentas MEDICO
existentes quedan en `PENDIENTE_VINCULACION` hasta que un ADMIN las asigne; no se
infiere ninguna relación por nombre ni correo. Cada asignación o corrección se
registra en `historial_vinculacion_medico`.

El profesional de las operaciones clínicas se obtiene de la cuenta autenticada en
cada solicitud y el paciente de la cita persistida. Una cita de otro profesional
responde `404`. Solo se documentan citas `PENDIENTE` o `CONFIRMADA` cuya hora
programada ya comenzó y con llegada registrada por recepción (`409
APPOINTMENT_NOT_STARTED` o `ARRIVAL_NOT_REGISTERED` en caso contrario, sin efectos).
Las reglas se evalúan con la fila de la cita bloqueada y el reloj inyectable
`Clock`; la atención completa la cita, existe una por cita (`uq_atencion_cita` y bloqueo de la fila) y
las tablas `atenciones_clinicas` y `receta_items` rechazan cualquier `UPDATE`.

La agenda lee citas persistidas. Registrar llegada bloquea la fila de la cita,
valida que no esté cancelada o completada y guarda la hora y la cuenta de personal
que ejecutó la acción. Una segunda solicitud devuelve conflicto y no sobrescribe el
registro anterior.

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

La migración V8 crea las cuentas y sesiones separadas del personal y añade la
auditoría de llegada en `citas`. No modifica V1–V7 ni publica credenciales iniciales.

### Provisionamiento controlado del primer administrador

El primer administrador se provisiona una sola vez por un operador autorizado,
fuera del API. El procedimiento reproducible es el siguiente:

1. Selecciona la PostgreSQL de destino de forma explícita y carga la contraseña
   administrativa solo en la sesión local, sin escribirla en archivos del proyecto.
   `DB_HOST`, `DB_PORT`, `DB_NAME` y `DB_USER` pueden exportarse antes de copiar
   este bloque para apuntar a una base aislada; los valores por defecto son los de
   desarrollo local:

   ```bash
   export DB_HOST="${DB_HOST:-localhost}" DB_PORT="${DB_PORT:-5432}"
   export DB_NAME="${DB_NAME:-clinica_serena}" DB_USER="${DB_USER:-clinica_app}"
   read -r -s DB_PASSWORD
   export DB_PASSWORD
   export STAFF_ADMIN_ID="$(uuidgen | tr '[:upper:]' '[:lower:]')"
   export STAFF_ADMIN_EMAIL='admin-inicial@dominio-controlado.invalid'
   export STAFF_ADMIN_NAME='Administrador inicial'
   read -r -s STAFF_ADMIN_PASSWORD
   export STAFF_ADMIN_PASSWORD
   ```

2. Genera el hash BCrypt localmente con las dependencias del proyecto. La contraseña
   se lee desde la variable de proceso y nunca se imprime. El filtro conserva solo
   una línea que tenga el formato completo de BCrypt; no captura el prompt ni los
   mensajes de inicio de `jshell`:

   ```bash
   set -eu -o pipefail
   CP_FILE="$(mktemp /tmp/clinica-serena-classpath.XXXXXX)"
   trap 'rm -f "$CP_FILE"; unset DB_PASSWORD STAFF_ADMIN_PASSWORD STAFF_ADMIN_HASH' EXIT
   ./mvnw -q -DskipTests compile
   ./mvnw -q dependency:build-classpath -Dmdep.outputFile="$CP_FILE"
   STAFF_ADMIN_HASH="$(jshell --class-path "$(cat "$CP_FILE")" <<'EOF' |
   var encoderType = Class.forName("org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder");
   var encoder = encoderType.getDeclaredConstructor().newInstance();
   var hash = encoderType.getMethod("encode", CharSequence.class).invoke(encoder, System.getenv("STAFF_ADMIN_PASSWORD"));
   System.out.println(hash);
   /exit
   EOF
   awk '{ if (!found && match($0, /\$2[aby]\$[0-9][0-9]\$[A-Za-z0-9.\/]+/)) { print substr($0, RSTART, RLENGTH); found=1 } } END { if (!found) exit 1 }'
   )"
   test "${#STAFF_ADMIN_HASH}" -eq 60
   case "$STAFF_ADMIN_HASH" in
     \$2a\$[0-9][0-9]\$*|\$2b\$[0-9][0-9]\$*|\$2y\$[0-9][0-9]\$*) ;;
     *) echo 'jshell no produjo un hash BCrypt válido' >&2; exit 1 ;;
   esac
   ```

3. Ejecuta una inserción fail-closed. Los valores de `psql` se pasan con `-v`
   fuera del bloque SQL entrecomillado; no se interpolan variables del shell dentro
   del bloque. La transacción bloquea la tabla, comprueba si ya existe cualquier
   administrador y se detiene con código `3` sin sobrescribir ni duplicar una cuenta:

   ```bash
   PGPASSWORD="$DB_PASSWORD" psql \
     --host "$DB_HOST" --port "$DB_PORT" --username "$DB_USER" --dbname "$DB_NAME" \
     -v ON_ERROR_STOP=1 \
     -v admin_id="$STAFF_ADMIN_ID" \
     -v admin_email="$STAFF_ADMIN_EMAIL" \
     -v admin_name="$STAFF_ADMIN_NAME" \
     -v admin_hash="$STAFF_ADMIN_HASH" <<'SQL'
   BEGIN;
   LOCK TABLE cuentas_personal IN SHARE ROW EXCLUSIVE MODE;
   SELECT EXISTS (SELECT 1 FROM cuentas_personal WHERE rol = 'ADMIN') AS admin_exists \gset
   \if :admin_exists
   \echo 'ya existe una cuenta ADMIN; detener la provision'
   ROLLBACK;
   SELECT 1 / 0 AS admin_provision_abort;
   \endif
   INSERT INTO cuentas_personal
     (id, email_normalizado, nombre_completo, password_hash, rol, estado, creado_en, actualizada_en)
   VALUES
     (:'admin_id', lower(trim(:'admin_email')), :'admin_name', :'admin_hash', 'ADMIN', 'ACTIVA', now(), now());
   COMMIT;
   SQL
   ```

   Verifica únicamente el identificador, correo normalizado, rol y estado; no
   selecciones ni registres `password_hash` ni la contraseña. El endpoint
   `POST /api/v1/staff/accounts` rechaza la creación de otra cuenta `ADMIN`.
   Después de esta operación, elimina `STAFF_ADMIN_PASSWORD`, `STAFF_ADMIN_HASH` y
   `DB_PASSWORD` de la sesión (`unset ...`) y revoca cualquier sesión temporal de
   provisión. La contraseña inicial debe rotarse mediante el procedimiento operativo
   aprobado; sus valores no se guardan en Git, informes, logs ni capturas.

   En una base aislada de revisión se repite el mismo procedimiento con un nombre de
   base temporal, se comprueba que una segunda ejecución aborta sin insertar otra
   cuenta y al terminar se revoca la sesión y se elimina la cuenta temporal junto con
   la base aislada. En producción no se ejecuta automáticamente durante el arranque,
   no se deja un usuario por defecto y la eliminación de las credenciales temporales
   debe quedar confirmada por el operador.

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

Health, catálogo y login de pacientes/personal son públicos únicamente en sus rutas
de login. El resto requiere `Authorization: Bearer <token>` con el rol específico.
Sin sesión se responde `401 UNAUTHENTICATED`; con sesión válida y rol incorrecto,
`403 FORBIDDEN`.
Los tokens de pacientes y personal se almacenan como hashes en tablas separadas,
expiran y se revocan con logout. El backend permanece stateless y mantiene CSRF
deshabilitado porque no autentica con cookies; la protección CSRF del flujo web se
aplica en el BFF de Next.js, antes de reenviar el Bearer al backend.

No se añadieron usuarios fijos, cuentas de ejemplo ni permisos controlados por parámetros del cliente.
