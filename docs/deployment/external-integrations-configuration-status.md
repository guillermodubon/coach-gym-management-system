# Estado de integraciones externas de Coach Gym

## Gmail API, Stripe Sandbox y Supabase

- **Proyecto:** Coach Gym Management System
- **Estado del backend:** terminado para el alcance aprobado
- **Estado de estas integraciones:** configuración local parcial completada
- **Validación total pendiente:** después de terminar el frontend y antes del despliegue definitivo
- **Fecha de consolidación:** 3 de octubre de 2026
- **Próxima etapa principal:** desarrollo del frontend

> Este documento no contiene credenciales reales. Los secretos permanecen exclusivamente en `.env.local` durante el desarrollo y, en el futuro, en las variables privadas del servicio de backend en Render.

---

# 1. Resumen ejecutivo

Se avanzó en la configuración de tres servicios externos:

1. **Gmail API**, para correos transaccionales e identidad.
2. **Stripe Sandbox**, para pagos simulados sin dinero real.
3. **Supabase**, para PostgreSQL y almacenamiento privado.

Estado general:

```text
Gmail API
├── Configuración local: completada
├── Envío real: probado
├── Recepción en correo personal: probada
├── Flujos backend: probados hasta la fase 10
└── Publicación OAuth y prueba desplegada: pendientes

Stripe Sandbox
├── Cuenta y sandbox: configurados
├── Variables locales: configuradas
├── Webhook local y wiring: revisados
├── Backend y pruebas automatizadas: corregidos y validados
└── Checkout completo desde la aplicación: pendiente del frontend

Supabase PostgreSQL
├── Proyecto: creado
├── Conexión remota: funcionando
├── Perfil Spring supabase: funcionando
├── Flyway y esquema gym: funcionando
└── Administración desde Dashboard: disponible

Supabase Storage
├── Adaptador backend: preparado
├── Credenciales: configuradas localmente
├── Bucket privado: configurado
└── Pruebas de fotos, PDF y PNG: pendientes del frontend
```

---

# 2. Reglas de seguridad comunes

## 2.1 Ubicación de credenciales

Las credenciales reales deben existir únicamente en:

```text
Desarrollo local:
.env.local

Despliegue futuro:
Render → Backend service → Environment
```

Nunca deben guardarse en:

```text
Java
application.yml
application-supabase.yml
README.md
docs/
.env.example
migraciones Flyway
base de datos
frontend
variables VITE_*
GitHub
capturas públicas
mensajes de commit
```

## 2.2 Archivo local

El archivo utilizado es:

```text
.env.local
```

Se confirmó que Git lo ignora mediante:

```bash
git check-ignore -v .env.local
```

La regla actual es equivalente a:

```gitignore
.env.*
```

## 2.3 Carga en Git Bash

Spring Boot no carga automáticamente `.env.local`. Al abrir una terminal nueva se debe ejecutar:

```bash
set -a
source .env.local
set +a
```

No se deben imprimir secretos con `echo`.

## 2.4 Archivo de ejemplo

`.env.example` puede versionarse únicamente con placeholders inequívocos.

Ejemplo permitido:

```dotenv
GMAIL_CLIENT_SECRET=REPLACE_WITH_GOOGLE_OAUTH_CLIENT_SECRET
STRIPE_SECRET_KEY=REPLACE_WITH_STRIPE_TEST_SECRET_KEY
SUPABASE_SECRET_KEY=REPLACE_WITH_SUPABASE_SECRET_KEY
```

---

# 3. Gmail API

## 3.1 Objetivo

Gmail API se utiliza como único transporte real de correo del backend.

Usos contemplados:

- invitaciones de empleados;
- recuperación de contraseña;
- avisos de seguridad;
- comprobantes de pago PDF;
- credenciales de acceso PNG;
- correos transaccionales persistentes cuando corresponde.

No se utiliza:

- SMTP;
- Mailpit como transporte real;
- Resend;
- contraseña de aplicación;
- lectura de la bandeja de entrada;
- correo masivo o marketing.

## 3.2 Cuenta Gmail

Se creó una cuenta Gmail gratuita y dedicada para Coach Gym.

Modelo acordado:

```text
Cuenta personal del propietario
→ administra el proyecto de Google Cloud y recuperación

Cuenta Gmail dedicada de Coach Gym
→ autoriza Gmail API y envía los mensajes
```

La cuenta técnica:

- tiene un nombre visible de Coach Gym;
- utiliza contraseña exclusiva;
- debe mantener verificación en dos pasos;
- no se usa como cuenta de usuario de Coach Gym;
- debe coincidir con el remitente configurado en el backend.

## 3.3 Google Cloud y OAuth

Se configuró:

```text
Proyecto de Google Cloud
Gmail API habilitada
Google Auth Platform
Audience: External
Publishing status: Testing
Cuenta técnica agregada como Test user
Scope: https://www.googleapis.com/auth/gmail.send
OAuth Client tipo Web application
Redirect URI de OAuth Playground
```

Redirect URI utilizado:

```text
https://developers.google.com/oauthplayground
```

Se obtuvieron:

```text
OAuth Client ID
OAuth Client Secret
Refresh Token
```

El refresh token pertenece a la cuenta Gmail dedicada de Coach Gym.

## 3.4 Variables configuradas

Variables exactas del backend:

```dotenv
EMAIL_ENABLED=true

GMAIL_API_BASE_URL=https://gmail.googleapis.com
GOOGLE_OAUTH_TOKEN_URL=https://oauth2.googleapis.com/token

EMAIL_FROM_ADDRESS=REPLACE_WITH_DEDICATED_GMAIL_SENDER_ADDRESS
EMAIL_FROM_NAME="Coach Gym Demo"
GMAIL_SENDER_ADDRESS=REPLACE_WITH_DEDICATED_GMAIL_SENDER_ADDRESS

GMAIL_CLIENT_ID=REPLACE_WITH_GOOGLE_OAUTH_CLIENT_ID
GMAIL_CLIENT_SECRET=REPLACE_WITH_GOOGLE_OAUTH_CLIENT_SECRET
GMAIL_REFRESH_TOKEN=REPLACE_WITH_OAUTH_PLAYGROUND_REFRESH_TOKEN

GMAIL_LIVE_TESTS_ENABLED=true
GMAIL_LIVE_TEST_RECIPIENT=REPLACE_WITH_ALLOWLISTED_TEST_RECIPIENT
GMAIL_LIVE_TEST_RECIPIENT_ALLOWLIST=REPLACE_WITH_ALLOWLISTED_TEST_RECIPIENT
```

Relaciones obligatorias:

```text
EMAIL_FROM_ADDRESS
=
GMAIL_SENDER_ADDRESS
=
cuenta Gmail que autorizó el refresh token
```

Para una prueba con un destinatario:

```text
GMAIL_LIVE_TEST_RECIPIENT
=
GMAIL_LIVE_TEST_RECIPIENT_ALLOWLIST
```

## 3.5 Pruebas completadas

Se completó la configuración y validación hasta la fase 10.

Se comprobó:

- configuración OAuth;
- intercambio de refresh token por access token;
- envío mediante Gmail API;
- recepción real en una cuenta personal;
- mensaje visible en la carpeta Enviados de la cuenta Coach Gym;
- prueba live separada de las pruebas normales;
- funcionamiento offline del quality gate;
- flujos backend relacionados con correo dentro del alcance probado;
- uso de variables de entorno;
- ausencia de secretos en el repositorio.

Comando utilizado para la prueba real:

```bash
./gradlew gmailIntegrationTest
```

Los mensajes llegaron al correo personal, inicialmente en Spam. Esto no invalida la conexión técnica. El despliegue en Render no garantiza por sí mismo la llegada a la bandeja principal.

## 3.6 Estado actual recomendado

Mientras no se prueben correos reales:

```dotenv
EMAIL_ENABLED=false
GMAIL_LIVE_TESTS_ENABLED=false
```

Para una prueba controlada local:

```dotenv
EMAIL_ENABLED=true
GMAIL_LIVE_TESTS_ENABLED=true
```

Después de cambiar `.env.local`:

```bash
set -a
source .env.local
set +a
```

## 3.7 Pendientes hasta finalizar el frontend

- crear homepage pública de Coach Gym;
- crear página pública de privacidad;
- crear página pública de términos;
- configurar URLs públicas de invitación y recuperación;
- completar Branding en Google Cloud;
- cambiar OAuth de `Testing` a `In production`;
- generar un refresh token nuevo y definitivo;
- actualizar las credenciales en Render;
- configurar las URLs reales del frontend en Vercel;
- repetir pruebas de invitación;
- repetir pruebas de recuperación;
- probar comprobantes PDF desplegados;
- probar credenciales PNG desplegadas;
- comprobar enlaces HTTPS públicos;
- revisar Enviados, Bandeja, Spam y Promociones;
- confirmar clasificación y entregabilidad con distintos proveedores controlados.

## 3.8 Nota sobre el refresh token actual

Mientras la aplicación OAuth permanezca como `External + Testing`, el refresh token de prueba puede expirar. Si aparece:

```text
invalid_grant
```

se debe:

1. revisar el estado OAuth;
2. revocar la autorización anterior si corresponde;
3. obtener un refresh token nuevo;
4. actualizar `.env.local`;
5. reiniciar el backend;
6. repetir `gmailIntegrationTest`.

---

# 4. Stripe Sandbox

## 4.1 Objetivo

Stripe se utiliza en modo Sandbox exclusivamente para demostrar pagos simulados.

No se utiliza:

- dinero real;
- tarjetas reales;
- claves Live;
- Stripe Live Mode;
- pagos financieros productivos.

## 4.2 Cuenta y entorno

Se configuró:

```text
Cuenta personal de Stripe
Sandbox de Coach Gym
Stripe CLI
Clave secreta de prueba
Webhook local
```

El sandbox permite revisar:

```text
Dashboard → Payments
Workbench → Events
Workbench → API logs
```

## 4.3 Variables correctas

El backend no utiliza variables `COACH_GYM_PAYMENT_STRIPE_*`.

Variables exactas:

```dotenv
STRIPE_ENABLED=true
STRIPE_SANDBOX=true

STRIPE_SECRET_KEY=REPLACE_WITH_STRIPE_SANDBOX_SECRET_KEY
STRIPE_WEBHOOK_SIGNING_SECRET=REPLACE_WITH_FRESH_STRIPE_CLI_WHSEC

STRIPE_SUCCESS_URL=http://localhost:8080/stripe/success
STRIPE_CANCEL_URL=http://localhost:8080/stripe/cancel

STRIPE_CONNECT_TIMEOUT=PT5S
STRIPE_READ_TIMEOUT=PT15S
STRIPE_WEBHOOK_TOLERANCE=PT5M
STRIPE_MAX_WEBHOOK_PAYLOAD_BYTES=262144
STRIPE_MAX_SIGNATURE_HEADER_LENGTH=1024
```

Reglas:

```text
STRIPE_ENABLED=true
STRIPE_SANDBOX=true
STRIPE_SECRET_KEY comienza con sk_test_
STRIPE_WEBHOOK_SIGNING_SECRET comienza con whsec_
Success URL y Cancel URL son absolutas
```

## 4.4 Ruta webhook

Ruta exacta:

```http
POST /api/v1/payment-provider/stripe/webhook
```

Listener local recomendado:

```bash
stripe listen --events checkout.session.completed,checkout.session.expired,payment_intent.payment_failed --forward-to http://127.0.0.1:8080/api/v1/payment-provider/stripe/webhook
```

Eventos admitidos:

```text
checkout.session.completed
checkout.session.expired
payment_intent.payment_failed
```

Eventos no admitidos no deben aceptarse artificialmente.

## 4.5 Correcciones realizadas

Se revisó y corrigió:

- metadata `payment_attempt_id` en Checkout Session;
- metadata `payment_attempt_id` en PaymentIntent;
- clasificación del evento para intento inexistente;
- respuesta HTTP cuando el servicio devuelve `REJECTED`;
- wiring y contexto de Stripe;
- pruebas de firma;
- idempotencia;
- importe y moneda;
- eventos admitidos;
- errores seguros.

Semántica esperada:

```text
Evento válido y procesado: 204
Evento válido duplicado: 204
Firma inválida: 400
Payload o evento incompatible: 400
Verificador no disponible: 503
Fallo interno transitorio: 500
```

Pruebas automatizadas completadas:

- compilación;
- StripeProperties;
- StripePropertiesSecurity;
- StripeConfiguration;
- OfficialStripeSdkClient;
- StripeWebhookVerifier;
- StripeWebhookController;
- PaymentProviderEventApplicationService;
- PaymentAttemptPersistence;
- seguridad de PaymentAttempt;
- Spring Modulith;
- `clean check`.

## 4.6 Por qué no se usa el trigger genérico como aceptación final

Este comando:

```bash
stripe trigger checkout.session.completed
```

crea fixtures genéricos en Stripe. No conoce automáticamente:

- el `PaymentAttempt` real de Coach Gym;
- la referencia de Checkout persistida;
- el periodo de membresía;
- el importe esperado;
- la moneda esperada;
- la sucursal;
- la metadata interna completa.

Por eso no debe exigirse un `204` al trigger genérico sin crear primero un intento real desde Coach Gym.

## 4.7 Prueba autoritativa pendiente

La prueba completa se realizará cuando exista el frontend:

```text
Coach Gym crea PaymentAttempt
→ POST /api/v1/payment-attempts/stripe-checkout
→ backend crea Checkout Session
→ frontend abre checkoutUrl
→ se completa una tarjeta oficial de prueba
→ Stripe genera checkout.session.completed
→ Stripe CLI o endpoint público reenvía el webhook
→ backend verifica firma y metadata
→ PaymentAttempt queda exitoso
→ Payment queda PAID
→ webhook responde 204
```

JSON base identificado:

```json
{
  "clientId": "UUID_DEL_CLIENTE",
  "membershipId": "UUID_DE_LA_MEMBRESIA",
  "membershipPeriodId": "UUID_DEL_PERIODO"
}
```

La sesión requiere:

- usuario autenticado `ADMIN` o `RECEPTIONIST` autorizado;
- cookie de sesión;
- CSRF;
- sucursal activa;
- cliente válido;
- membresía válida;
- periodo válido.

## 4.8 Pendientes hasta finalizar el frontend

- crear Checkout desde la interfaz;
- abrir `checkoutUrl` desde el frontend;
- probar pago exitoso en Sandbox;
- probar cancelación;
- probar rechazo;
- comprobar redirección success;
- comprobar redirección cancel;
- verificar `204` real con PaymentAttempt válido;
- verificar pago en Stripe Dashboard;
- verificar evento en Workbench;
- verificar PaymentAttempt y Payment en Coach Gym;
- probar idempotencia reenviando el mismo evento;
- configurar endpoint HTTPS de Render;
- obtener un `whsec_` nuevo para Render;
- reemplazar URLs locales por URLs de Vercel;
- realizar prueba full stack desplegada.

## 4.9 Manejo de secretos locales

El `whsec_` de Stripe CLI es local y temporal.

Cuando se despliegue:

```text
whsec_ local
→ no se usa en Render

whsec_ del endpoint HTTPS de Render
→ se configura en Render
```

Cualquier secreto compartido accidentalmente debe considerarse expuesto y reemplazarse.

---

# 5. Supabase PostgreSQL

## 5.1 Objetivo

Supabase PostgreSQL es la base de datos remota prevista para Coach Gym.

El backend mantiene:

- identidad propia;
- sesiones propias;
- roles propios;
- autorización propia;
- JPA y JDBC;
- Flyway como autoridad del esquema.

No se utiliza Supabase Auth.

## 5.2 Proyecto creado

Se creó un proyecto personal en Supabase para Coach Gym.

Configuración inicial:

```text
Enable Data API: habilitado
Automatically expose new tables: deshabilitado
Enable automatic RLS: deshabilitado
```

No se debe agregar el esquema `gym` a los esquemas expuestos por Data API.

## 5.3 Conexión elegida

Se utiliza:

```text
Shared Pooler
Session mode
Puerto 5432
SSL requerido
Base postgres
```

No se utiliza:

```text
Transaction Pooler
Puerto 6543
```

La configuración usa host y username copiados exactamente desde:

```text
Supabase Dashboard
→ Connect
→ Session pooler
```

Formato JDBC:

```text
jdbc:postgresql://HOST_SESSION_POOLER:5432/postgres?sslmode=require
```

## 5.4 Variables configuradas

```dotenv
SPRING_PROFILES_ACTIVE=supabase
CORS_ALLOWED_ORIGINS='http://localhost:5173'

SUPABASE_JDBC_URL='jdbc:postgresql://HOST_SESSION_POOLER:5432/postgres?sslmode=require'
SUPABASE_DB_USERNAME='postgres.PROJECT_REF'
SUPABASE_DB_PASSWORD='REPLACE_WITH_SUPABASE_DATABASE_PASSWORD'

DATABASE_MAX_POOL_SIZE=3
```

Variables Flyway opcionales no utilizadas actualmente:

```text
SUPABASE_FLYWAY_JDBC_URL
SUPABASE_FLYWAY_USERNAME
SUPABASE_FLYWAY_PASSWORD
```

Flyway reutiliza las credenciales principales.

## 5.5 Error encontrado y solución

Error inicial:

```text
FATAL: (EAUTHQUERY) user not found in the database
```

Causa:

- host y username del Session Pooler incorrectos o no correspondientes;
- valores construidos manualmente en vez de copiarse desde el mismo panel.

Solución:

- copiar host exacto;
- copiar username exacto `postgres.<project-ref>`;
- mantener puerto `5432`;
- usar base `postgres`;
- mantener contraseña separada de la URL.

Después de corregir host y usuario, Supabase PostgreSQL quedó conectado y funcionando con el backend.

## 5.6 Flyway y esquema

Estado:

```text
Esquema autoritativo: gym
Hibernate ddl-auto: validate
Flyway default schema: gym
Flyway schemas: gym
Historial: gym.flyway_schema_history
Migraciones: V1–V43
```

Se corrigió el perfil Supabase para que Flyway utilice `gym` y no cree un historial separado en `public`.

Reglas:

- no modificar V1–V43;
- no ejecutar Flyway repair;
- no crear tablas manualmente;
- no ejecutar migraciones una por una en SQL Editor;
- no usar `ddl-auto=update`;
- no editar `gym.flyway_schema_history`.

## 5.7 Estado validado

Se confirmó:

- perfil `supabase` activo;
- conexión PostgreSQL remota exitosa;
- backend funcionando con Supabase;
- esquema `gym` creado;
- Flyway funcionando;
- migraciones aplicadas;
- tablas administrables desde Supabase Dashboard;
- `.env.local` ignorado;
- secretos fuera del repositorio.

## 5.8 Comando de arranque

```bash
set -a
source .env.local
set +a

./gradlew bootRun --args='--spring.profiles.active=supabase'
```

## 5.9 Health

```bash
curl.exe -i http://127.0.0.1:8080/actuator/health
curl.exe -i http://127.0.0.1:8080/actuator/health/readiness
```

Resultado esperado:

```text
HTTP/1.1 200
status: UP
```

## 5.10 Consultas de verificación

```sql
select version,
       description,
       type,
       script,
       checksum,
       installed_on,
       success
from gym.flyway_schema_history
order by installed_rank;
```

```sql
select count(*) filter (where success) as successful_migrations,
       count(*) filter (where not success) as unsuccessful_migrations
from gym.flyway_schema_history
where version is not null;
```

```sql
select table_name
from information_schema.tables
where table_schema = 'gym'
  and table_type = 'BASE TABLE'
order by table_name;
```

Esperado:

```text
43 migraciones exitosas
0 migraciones fallidas
```

---

# 6. Supabase Storage

## 6.1 Arquitectura

El backend utiliza:

```text
Supabase Storage REST API
```

No utiliza:

```text
S3Client
Supabase Auth
URLs públicas permanentes
acceso directo desde frontend
```

El backend forma rutas equivalentes a:

```text
/storage/v1/object/{bucket}/{key}
```

Los archivos se descargan y retransmiten mediante el backend autorizado.

## 6.2 Cambios realizados

Se corrigió:

- propiedad de Secret Key moderna;
- uso de `apikey` para Supabase Storage;
- eliminación del tratamiento incorrecto de la Secret Key como JWT Bearer;
- pruebas de configuración;
- pruebas del cliente Storage;
- contrato de despliegue.

Variable anterior eliminada del perfil activo:

```text
SUPABASE_SERVICE_ROLE_KEY
```

Variable actual:

```text
SUPABASE_SECRET_KEY
```

No se debe crear un alias innecesario.

## 6.3 Variables configuradas

```dotenv
SUPABASE_STORAGE_URL='https://PROJECT_REF.supabase.co'
SUPABASE_SECRET_KEY='REPLACE_WITH_SUPABASE_SECRET_KEY'
SUPABASE_STORAGE_BUCKET=coach-gym-private
```

Opcionales:

```dotenv
SUPABASE_STORAGE_CONNECTION_TIMEOUT=PT5S
SUPABASE_STORAGE_REQUEST_TIMEOUT=PT30S
```

`SUPABASE_STORAGE_URL` no debe incluir:

```text
/storage/v1
```

## 6.4 Bucket

Bucket único:

```text
coach-gym-private
```

Configuración:

```text
Public bucket: desactivado
Tamaño máximo: 12 MB
```

MIME permitidos:

```text
application/pdf
image/jpeg
image/png
image/webp
```

Contenido previsto:

```text
clients/
→ fotos de clientes
→ máximo de aplicación: 5 MiB

staff-profiles/
→ fotos de empleados
→ máximo de aplicación: 5 MiB

receipts/
→ comprobantes PDF
→ máximo de aplicación: 10 MiB

access-credentials/
→ credenciales QR PNG
→ máximo de aplicación: 1 MiB
```

No se necesitan políticas anónimas ni acceso directo del frontend. La Secret Key es exclusiva del backend.

## 6.5 Estado actual

```text
Adaptador backend: preparado
Propiedades: preparadas
Secret Key local: configurada
Bucket privado: configurado
Pruebas automatizadas: correctas
Prueba remota real de archivos: pendiente
```

## 6.6 Pendiente hasta finalizar el frontend

- subir foto de cliente desde Coach Gym;
- recuperar foto de cliente mediante el backend;
- subir foto de empleado;
- recuperar foto de empleado;
- generar comprobante PDF;
- guardar PDF en Supabase Storage;
- recuperar PDF mediante autorización del backend;
- generar credencial QR PNG;
- guardar PNG en Supabase Storage;
- recuperar PNG mediante autorización del backend;
- verificar prefijos internos del bucket;
- comprobar MIME y límites;
- comprobar checksum;
- probar compensación ante fallos;
- verificar que las rutas de Storage no aparecen en APIs o logs;
- validar que no existan URLs públicas permanentes;
- realizar prueba con frontend y backend integrados;
- repetir todas las pruebas después del despliegue en Render.

---

# 7. Cambios locales de código pendientes de consolidar

Durante la configuración se realizaron cambios locales relacionados con Stripe y Supabase.

Entre ellos:

```text
PaymentProviderEventApplicationService.java
OfficialStripeSdkClient.java
StripeWebhookController.java
SupabaseStorageClient.java
SupabaseStorageProperties.java
application-supabase.yml
pruebas de configuración, Stripe, Supabase y persistencia
```

Antes de confirmar cambios en Git:

```bash
git status --short
git diff --stat
git diff --check
```

Existe o existió un archivo temporal:

```text
stripe-debug-local.log
```

Debe revisarse y eliminarse antes de confirmar cambios:

```bash
rm -f stripe-debug-local.log
```

`.env.example` debe revisarse manualmente para garantizar que solo contiene placeholders.

Nunca se debe agregar:

```text
.env.local
credenciales reales
logs temporales
payloads de Stripe
correos generados
archivos de Storage descargados
```

---

# 8. Estado de pruebas automatizadas

Se reportó como satisfactorio:

```text
compileJava
compileTestJava
pruebas Gmail offline
prueba Gmail live controlada
pruebas Stripe focalizadas
pruebas PaymentAttempt
pruebas de idempotencia
pruebas Supabase Storage
pruebas de configuración del datasource
pruebas de deployment readiness
Spring Modulith
clean check
git diff --check
```

Las pruebas normales no requieren:

- conexión real a Gmail;
- conexión real a Stripe;
- conexión real a Supabase;
- credenciales externas;
- Internet.

Las pruebas live deben permanecer opt-in.

---

# 9. Configuración futura en Render

Cuando el frontend esté terminado, se crearán variables privadas en:

```text
Render
→ Backend service
→ Environment
```

## Gmail

```text
EMAIL_ENABLED
GMAIL_API_BASE_URL
GOOGLE_OAUTH_TOKEN_URL
EMAIL_FROM_ADDRESS
EMAIL_FROM_NAME
GMAIL_SENDER_ADDRESS
GMAIL_CLIENT_ID
GMAIL_CLIENT_SECRET
GMAIL_REFRESH_TOKEN
```

No se debe habilitar la bandera de prueba live en producción.

## Stripe

```text
STRIPE_ENABLED
STRIPE_SANDBOX
STRIPE_SECRET_KEY
STRIPE_WEBHOOK_SIGNING_SECRET
STRIPE_SUCCESS_URL
STRIPE_CANCEL_URL
STRIPE_CONNECT_TIMEOUT
STRIPE_READ_TIMEOUT
STRIPE_WEBHOOK_TOLERANCE
STRIPE_MAX_WEBHOOK_PAYLOAD_BYTES
STRIPE_MAX_SIGNATURE_HEADER_LENGTH
```

En Render se usará un `whsec_` nuevo generado para el endpoint HTTPS público, no el secreto local de Stripe CLI.

## Supabase

```text
SPRING_PROFILES_ACTIVE=supabase
SUPABASE_JDBC_URL
SUPABASE_DB_USERNAME
SUPABASE_DB_PASSWORD
SUPABASE_STORAGE_URL
SUPABASE_SECRET_KEY
SUPABASE_STORAGE_BUCKET
DATABASE_MAX_POOL_SIZE
CORS_ALLOWED_ORIGINS
```

Opcionales:

```text
SUPABASE_STORAGE_CONNECTION_TIMEOUT
SUPABASE_STORAGE_REQUEST_TIMEOUT
FLYWAY_CONNECT_RETRIES
FLYWAY_CONNECT_RETRIES_INTERVAL
```

Todos los cambios de entorno requieren reinicio o redeploy para que Spring Boot los cargue.

---

# 10. Checklist de reanudación después del frontend

## 10.1 Gmail

- [ ] Frontend desplegado en Vercel
- [ ] Homepage pública disponible
- [ ] Política de privacidad disponible
- [ ] Términos disponibles
- [ ] URLs públicas de invitación y recuperación configuradas
- [ ] Branding OAuth completado
- [ ] OAuth cambiado a In production
- [ ] Refresh token definitivo regenerado
- [ ] Secretos configurados en Render
- [ ] Invitación desplegada probada
- [ ] Recuperación desplegada probada
- [ ] PDF enviado y recibido
- [ ] PNG enviado y recibido
- [ ] Enlaces HTTPS verificados
- [ ] Enviados, Bandeja, Spam y Promociones revisados

## 10.2 Stripe

- [ ] Frontend con flujo de Checkout
- [ ] Success URL real de Vercel
- [ ] Cancel URL real de Vercel
- [ ] Backend desplegado en Render
- [ ] Endpoint webhook HTTPS creado en Stripe
- [ ] Nuevo `whsec_` guardado en Render
- [ ] PaymentAttempt creado desde Coach Gym
- [ ] Checkout Sandbox completado
- [ ] Webhook devuelve 204
- [ ] PaymentAttempt exitoso
- [ ] Payment PAID persistido
- [ ] Pago visible en Stripe Dashboard
- [ ] Evento visible en Workbench
- [ ] Cancelación probada
- [ ] Rechazo probado
- [ ] Idempotencia probada

## 10.3 Supabase Storage

- [ ] Foto de cliente subida y descargada
- [ ] Foto de empleado subida y descargada
- [ ] Comprobante PDF guardado y recuperado
- [ ] Credencial QR PNG guardada y recuperada
- [ ] MIME y tamaños verificados
- [ ] Checksums verificados
- [ ] Descargas autorizadas
- [ ] Sin URLs públicas permanentes
- [ ] Sin rutas internas en APIs o logs
- [ ] Pruebas full stack completadas

## 10.4 Supabase PostgreSQL

- [ ] Variables configuradas en Render
- [ ] Session Pooler utilizado
- [ ] SSL activo
- [ ] Pool Hikari pequeño
- [ ] Flyway validado al desplegar
- [ ] Esquema gym correcto
- [ ] 43 migraciones exitosas
- [ ] Health y readiness UP
- [ ] Backup y restauración documentados

---

# 11. Estado oficial para retomar

```text
Backend:
100% terminado para el alcance aprobado

Gmail API:
Configuración local y pruebas hasta fase 10 completadas
Pendiente publicar OAuth, usar URLs públicas y validar despliegue

Stripe:
Sandbox y backend configurados
Correcciones y pruebas automatizadas completadas
Pendiente Checkout full stack desde frontend y webhook público

Supabase PostgreSQL:
Conectado y funcionando con el backend
Flyway y esquema gym funcionando

Supabase Storage:
Configurado y preparado
Pendiente probar fotos, PDF y PNG mediante el frontend

Siguiente trabajo principal:
frontend

Momento para retomar integraciones:
después de completar el frontend y antes del despliegue definitivo
```

---

# 12. Comandos útiles al retomar

## Cargar configuración local

```bash
set -a
source .env.local
set +a
```

## Verificar que `.env.local` está ignorado

```bash
git check-ignore -v .env.local
git status --short
```

## Arrancar con Supabase

```bash
./gradlew bootRun --args='--spring.profiles.active=supabase'
```

## Health

```bash
curl.exe -i http://127.0.0.1:8080/actuator/health
curl.exe -i http://127.0.0.1:8080/actuator/health/readiness
```

## Prueba Gmail live controlada

```bash
./gradlew gmailIntegrationTest
```

## Stripe CLI local

```bash
stripe listen --events checkout.session.completed,checkout.session.expired,payment_intent.payment_failed --forward-to http://127.0.0.1:8080/api/v1/payment-provider/stripe/webhook
```

## Quality gate

```bash
./gradlew clean check
git diff --check
```

---

# 13. Advertencia final

Este documento registra nombres de variables, decisiones y procedimientos, pero no incluye valores reales.

Si una credencial aparece accidentalmente en:

- una conversación;
- una captura;
- un log;
- un commit;
- un archivo versionado;

se debe:

1. considerarla expuesta;
2. revocarla o rotarla;
3. actualizar `.env.local`;
4. actualizar Render cuando exista;
5. reiniciar el backend;
6. repetir la prueba correspondiente.
