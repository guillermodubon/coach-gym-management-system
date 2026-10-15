# F00 — Organización del repositorio y revisión de contratos

- **Estado:** COMPLETADO y validado el 14 de octubre de 2026.
- **Fecha:** 13 de octubre de 2026.
- **Rama de ejecución inicial:** `feature/multi-branch-hardening-code-only`.
- **Rama de entrega:** `chore/monorepo-layout`; Git sigue bajo control del usuario.
- **Autorización:** reorganizar y limpiar lo necesario para separar el repositorio, preparando el espacio del frontend y conservando el funcionamiento del backend.
- **Referencia:** [blueprint del frontend](../blueprint/frontend-blueprint.md).

## Alcance autorizado

F00 se ejecuta como una única entrega de organización. No se inicia F01.

1. Trasladar fuentes, Gradle, Docker, Compose y documentación técnica del backend a `backend/`.
2. Conservar los accesos Gradle desde la raíz mediante delegación al backend.
3. Preparar `frontend/` únicamente con documentación e instrucciones: sin aplicación, dependencias, `package.json` ni componentes.
4. Mantener documentación común, reglas de repositorio y workflows en la raíz.
5. Actualizar CI, atributos de archivos y exclusiones de Git para la estructura nueva.
6. Trasladar de forma segura configuración privada, datos y cachés locales del backend, conservando su contenido.
7. Consolidar un inventario de contratos y las dependencias de consulta pendientes, sin crear ni cambiar APIs.
8. Validar compilación, contratos de configuración, arquitectura y el quality gate completo del backend.

## Exclusiones

- No implementar React, TypeScript, diseño ni autenticación del frontend.
- No añadir endpoints, dependencias, reglas comerciales o migraciones.
- No modificar contenido de migraciones aplicadas ni reparar Flyway.
- No modificar credenciales, llamar proveedores reales ni desplegar servicios.
- No cambiar de rama, preparar staging, confirmar cambios ni sincronizar Git.
- No eliminar archivos personales, el snapshot adjunto o configuración del IDE.

## Re-onboarding y evidencia inicial

El árbol de trabajo inicial no presenta cambios visibles. La rama tiene un commit exclusivo respecto de `main` y `main` tiene otro exclusivo respecto de la rama; no se realiza sincronización. El plan anterior de hardening marca sus ocho bloques como completados. El blueprint canónico del backend mencionado en ese plan continúa ausente y se registra esa diferencia documental.

Módulos verificados: auth, user, organization, client, plan, promotion, membership, payment, accesscredential, access, equipment, maintenance, notification, reporting, audit, configuration y shared. Sus contratos públicos y eventos están en la raíz de cada módulo; shared expone interfaces nombradas web, storage, security e identity-email.

Se mantienen capas domain, application, infrastructure y web, constructor injection, contratos inmutables y transacciones de aplicación. Roles: ADMIN y RECEPTIONIST, con alcance persistido y asignaciones. Autenticación: sesión, CSRF, CORS explícito y revisión de vigencia de autoridad. PostgreSQL es la autoridad; hay 43 migraciones, V1–V43. Persisten historiales, versiones, idempotencia y auditoría.

La API usa `/api/v1`, ProblemDetail y OpenAPI. Reporting ofrece resumen por alcance, tendencias administrativas y comparación; el dashboard anterior permanece restringido a administración de organización. Los tests usan MockMvc, Testcontainers con PostgreSQL y verificaciones de arquitectura, OpenAPI, privacidad y restricciones de base de datos. Gmail live es una tarea separada y opt-in; la suite normal usa dobles de proveedores.

La compilación inicial `./gradlew compileJava compileTestJava` pasó antes del traslado. Docker estaba detenido y se solicitó iniciar Docker Desktop para ejecutar pruebas aisladas. Las fuentes y contratos actuales permiten esta reorganización; las consultas pendientes C-01 a C-03 pertenecen a futuras entregas de soporte, sin implementación en F00.

El re-onboarding concluye con base suficiente para trabajar. La sesión está preparada para solicitudes en cualquier estructura y, al concluir esta revisión inicial, no se había iniciado implementación.

## Estrategia de traslado

Todas las rutas de origen y destino deben resolverse dentro del workspace y ningún destino preexistente será sobrescrito. Se verificará el contenido de archivos mediante hashes antes y después del traslado, con un resumen que no exponga valores privados.

El directorio de trabajo operativo pasa a ser `backend/`. Así, `src/`, `compose.yaml`, `.env` y `data/` conservan sus rutas relativas dentro de la aplicación. Las tareas lanzadas desde la raíz delegan a ese directorio; las tareas del CI trabajan allí explícitamente. Solo las pruebas que leen archivos compartidos del repositorio necesitan adaptar su referencia al padre.

Compose conservará nombre de proyecto, contenedor y volumen. La configuración privada local se mueve junto a la aplicación y permanece excluida de Git y del contexto Docker. Los archivos del IDE y la exportación del backend se conservan en su ubicación original.

## Archivos afectados

- Traslado: `src/`, `gradle/`, wrappers originales, archivos Gradle, Dockerfile, `.dockerignore`, Compose y documentos técnicos del backend.
- Compatibilidad: delegadores Gradle en la raíz.
- Repositorio: `.gitignore`, `.gitattributes`, `AGENTS.md`, README y workflow de backend.
- Pruebas: referencia al workflow compartido y comprobaciones de estructura y delegación.
- Documentación: instrucciones por aplicación, índice de planes, contrato inicial, blueprint y reporte de ejecución de F00.
- Archivos locales ignorados: `.env`, `.env.local`, `data/`, `.gradle/` y `build/`, sin incorporarlos a Git.

## Seguridad, base de datos e integridad

La reorganización no modifica autorización, CSRF, DTOs, eventos, auditoría ni adaptadores de proveedores. La revisión aplicará security-and-privacy-review al manejo de configuración privada, contexto Docker y exclusiones de Git. Database-migration-safety verificará que la cadena de migraciones se conserva; no se escribirá SQL.

Las reglas de exclusión deben proteger configuración privada, datos, cachés y salidas generadas tanto en la raíz como bajo backend. La documentación de proyecto se podrá versionar y será revisada antes de entregarla. No se copiarán valores secretos a las guías.

## Validación progresiva

1. Compilación previa al traslado: completada.
2. Compilación después del traslado, desde `backend/`.
3. Pruebas enfocadas de estructura, Gmail offline, despliegue y configuración.
4. Spring Modulith y pruebas del contrato OpenAPI del frontend.
5. `clean check` completo mediante el acceso de raíz.
6. Construcción del artefacto ejecutable y verificación de Compose/contexto Docker según disponibilidad del entorno.
7. Revisión independiente de consistencia y seguridad; no requiere delegar a otro agente.
8. `git status --short`, `git diff --check`, `git diff --stat`, validación de archivos nuevos y exclusiones.

Los resultados se registrarán en [el reporte F00](frontend-repository-contracts-report.md). Una limitación de infraestructura se identificará como tal, sin atribuirla al producto.

El cierre pasó `clean check` con 2.985 pruebas en 649 suites, sin fallos, errores ni omitidas. También pasó el arranque de la imagen con PostgreSQL aislado: health UP, 43 migraciones, 115 rutas OpenAPI y seguridad HTTP/CSRF básica. Véase el reporte para comandos, adaptaciones de pruebas, recuperación de Docker y limitaciones.

## Criterios de cierre

- backend tiene todas sus fuentes, herramientas y archivos operativos en su directorio.
- frontend solo contiene instrucciones y documentación para la siguiente etapa.
- El backend se compila y sus pruebas se ejecutan con las rutas nuevas.
- Se mantienen nombres de base de datos, volumen, configuración y rutas relativas de datos.
- Fuentes y migraciones se conservan sin cambios de comportamiento.
- Documentación y CI explican y utilizan la estructura actual.
- Contratos pendientes quedan identificados sin prometer su implementación.
- Git queda sin staging ni mutaciones de historial.
- Se reporta evidencia y se detiene después de F00.
