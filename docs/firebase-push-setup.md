# Configuración de Firebase Cloud Messaging

El backend envía FCM con Firebase Admin SDK y Application Default Credentials. No se requiere
guardar una clave dentro del repositorio ni habilitar el proveedor durante el desarrollo.

## Proyecto y aplicación Android

1. Crear el proyecto Firebase del equipo y registrar la aplicación Android con `applicationId`
   `com.clinicaserena.app` (verificado en `app/build.gradle.kts`). El build QA usa el sufijo `.qa`;
   Alejandro debe confirmar si Firebase requiere registrar también esa variante.
2. Alejandro integra el SDK cliente de Firebase, solicita permiso de notificaciones en Android,
   obtiene/renueva el token FCM y llama a los endpoints de registro y retiro documentados en
   `docs/openapi.yaml`.
3. En Firebase/Google Cloud, habilitar Firebase Cloud Messaging API para el proyecto.

## Credenciales de backend

Crear una cuenta de servicio con permisos mínimos para enviar FCM y entregar la clave JSON mediante
un gestor de secretos o archivo montado fuera del repositorio. En una VPS no-Google, establecer
`GOOGLE_APPLICATION_CREDENTIALS` a la ruta del archivo JSON. No copiar la clave a Git, Docker image,
artefactos, chat, ni al cliente Android. Si una clave se expone, revocarla inmediatamente.

## Activación gradual

Mantener inicialmente:

```dotenv
CLINICA_PUSH_PROVIDER=NONE
CLINICA_PUSH_REMINDERS_ENABLED=false
```

Después de instalar credenciales y completar las pruebas Android de extremo a extremo, activar en un
entorno de prueba:

```dotenv
CLINICA_PUSH_PROVIDER=FIREBASE
CLINICA_PUSH_REMINDERS_ENABLED=false
GOOGLE_APPLICATION_CREDENTIALS=/run/secrets/firebase-service-account.json
```

Los recordatorios automáticos permanecen apagados hasta validar entrega, tokens caducados, retiro del
dispositivo y ausencia de datos clínicos en el contenido. Las notificaciones push actuales son solo
recordatorios 24 horas antes de una cita elegible; el backend no implementa aún una API de envío
instantáneo para eventos arbitrarios.
