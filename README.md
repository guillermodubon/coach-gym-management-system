# Coach Gym Management System

Repositorio compartido para el backend de Coach Gym y su futura aplicación web interna.

## Organización

```text
backend/                  Spring Boot, Gradle, PostgreSQL/Flyway, Docker y tests
frontend/                 Espacio reservado e instrucciones para la aplicación web
docs/                     Blueprints, planes, ADRs y guías de integración
.github/                  Workflows y plantilla de revisión
.codex/skills/            Skills del proyecto
AGENTS.md                 Reglas compartidas de trabajo
gradlew / gradlew.bat     Accesos de compatibilidad al backend
```

El backend mantiene su arquitectura de monolito modular. El frontend consumirá sus contratos HTTP y tendrá herramientas y dependencias independientes.

## Trabajar con el backend

Requisitos: Java 21 y Docker Desktop con motor Linux para las pruebas PostgreSQL/Testcontainers. Gradle se ejecuta mediante el wrapper incluido.

Desde la raíz, en PowerShell:

```powershell
.\gradlew compileJava compileTestJava
.\gradlew clean check
.\gradlew bootRun
```

Los accesos de raíz entran en `backend/` antes de ejecutar el wrapper original. De esta forma la aplicación, las pruebas y la configuración mantienen sus rutas relativas.

También puede trabajarse directamente desde el backend:

```powershell
Set-Location backend
.\gradlew compileJava compileTestJava
```

En Git Bash o Linux, el acceso equivalente desde la raíz es `sh ./gradlew`, que también funciona en un checkout donde el archivo todavía no tiene permiso de ejecución. No existe un proyecto Gradle adicional en la raíz.

Para importar el proyecto Java en un IDE, seleccionar `backend/settings.gradle.kts`. Las configuraciones locales del IDE preexistentes se conservan; puede ser necesario reimportar Gradle o actualizar el directorio de trabajo de una configuración de arranque anterior.

## Configuración local y datos

La configuración privada del backend se ubica en `backend/.env` y `backend/.env.local`; sus datos locales, en `backend/data/`. Estos archivos y carpetas están excluidos de Git y del contexto Docker.

Spring conserva la carga opcional de `.env` desde el directorio de trabajo del backend. `.env.local` conserva su mecanismo de carga previo: no se carga automáticamente por el cambio de estructura. Véase [la guía del backend](backend/README.md).

Compose mantiene el proyecto `coach-gym` y el volumen `coach-gym-postgres-data`. El traslado no recrea ni elimina ese volumen.

Desde la raíz, para operar el Compose del backend con su configuración local:

```powershell
docker compose --project-directory backend -f backend/compose.yaml up -d
```

Para construir su imagen utilizando únicamente el contexto del backend:

```powershell
docker build --tag coach-gym-backend:local backend
```

El despliegue deberá utilizar `backend/Dockerfile` con contexto `backend/`. La aplicación frontend y sus herramientas se incorporan por separado en `feature/frontend-foundation-auth`; la entrega F00 reserva su directorio y establece sus límites.

## Documentación

- [Backend: arranque y validación](backend/README.md).
- [Arquitectura del backend](backend/ARCHITECTURE.md).
- [Contribución](backend/CONTRIBUTING.md) y [estilo](backend/CODE_STYLE.md).
- [Blueprint del frontend](docs/blueprint/frontend-blueprint.md).
- [Planes de trabajo](docs/branch-plans/README.md).
- [Contratos de partida para el frontend](docs/integration/frontend-backend-contracts.md).
- [Reporte de reorganización F00](docs/branch-plans/frontend-repository-contracts-report.md).

Git permanece bajo control del usuario. F00 se entrega en `chore/monorepo-layout`; F01 y sus bloques se versionan en `feature/frontend-foundation-auth`, con autorización individual para cada bloque.
