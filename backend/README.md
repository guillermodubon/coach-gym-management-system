# Backend de Coach Gym

Aplicación Spring Boot independiente, con Java 21, PostgreSQL, Flyway y Spring Modulith. Todas las rutas operativas de este documento son relativas a `backend/`.

## Validar

En PowerShell, desde este directorio:

```powershell
.\gradlew compileJava compileTestJava
.\gradlew clean check
```

Docker Desktop debe tener disponible su motor Linux para Testcontainers. Las pruebas normales utilizan bases aisladas y dobles de servicios externos; no requieren proveedores ni credenciales reales. La tarea `gmailIntegrationTest` continúa separada, opt-in y fuera de `check`.

Desde la raíz del repositorio se pueden ejecutar los mismos comandos mediante los delegadores existentes. El wrapper real y sus archivos están en este directorio; no se duplican dependencias ni existe otro build Gradle de aplicación.

## Arranque local

```powershell
.\gradlew bootRun
```

La configuración vigente determina la conexión PostgreSQL. El perfil local puede indicarse con `--args='--spring.profiles.active=local'`. El archivo privado `.env`, cuando existe, se importa como antes mediante `optional:file:./.env[.properties]`.

`compose.yaml` permanece junto a la aplicación y conserva su proyecto, contenedor y volumen. Las rutas por defecto de fotos, recibos y credenciales permanecen dentro de `data/`. La base de datos y sus datos persistidos no se trasladan con las carpetas del repositorio.

## Configuración privada para integraciones

`.env.local` se trasladó sin modificar su contenido. No se agrega una carga automática ni se cambia el formato esperado de sus valores.

Si se utiliza el mecanismo de carga previo en Git Bash, ejecutar desde `backend/`:

```bash
set -a
source .env.local
set +a
./gradlew bootRun --args='--spring.profiles.active=supabase'
```

En PowerShell se conserva la configuración privada de entorno usada anteriormente. No se deben imprimir valores secretos ni copiar archivos privados a documentación, imágenes Docker o Git. Esta reorganización no ejecuta conexiones reales a Gmail, Stripe o Supabase.

## Docker

Desde este directorio:

```powershell
docker compose config --quiet
docker build --tag coach-gym-backend:local .
```

El contexto de la imagen es `backend/`, con exclusiones de `.env`, datos, caches y pruebas. El workflow compartido se encuentra en `../.github/workflows/backend-ci.yml` y ejecuta sus pasos desde este directorio.

## Organización interna

- `src/main/`: implementación y recursos del backend.
- `src/test/`: pruebas normales.
- `src/gmailIntegrationTest/`: pruebas Gmail reales autorizadas por separado.
- `src/main/resources/db/migration/`: las mismas 43 migraciones, V1–V43.
- `gradle/`, `gradlew`, `gradlew.bat`: wrapper original.
- [Arquitectura](ARCHITECTURE.md), [estilo](CODE_STYLE.md) y [contribución](CONTRIBUTING.md).

Las APIs y reglas de negocio no cambian en F00. Las necesidades adicionales de consulta para la futura interfaz están registradas en [el inventario de contratos](../docs/integration/frontend-backend-contracts.md).
