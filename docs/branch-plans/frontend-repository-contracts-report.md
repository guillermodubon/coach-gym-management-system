# Reporte F00 — Organización del repositorio y contratos

- **Inicio:** 13 de octubre de 2026.
- **Revisión y reanudación:** 14 de octubre de 2026.
- **Estado:** F00 COMPLETADA y validada el 14 de octubre de 2026.
- **Alcance:** solicitud del usuario de separar el repositorio sin comenzar el frontend y conservando el backend.
- **Rama de ejecución original:** `feature/multi-branch-hardening-code-only`, sin cambio de rama ni staging durante la ejecución de F00.
- **Rama de entrega:** `chore/monorepo-layout`, creada posteriormente por el usuario para separar F00 de B01.

## 1. Bloque

F00: organización del repositorio y consolidación documental de contratos. Su alcance no incluye la aplicación frontend.

## 2. Alcance implementado

- Backend independiente en `backend/`, con fuentes, Gradle, Docker, Compose, migraciones, pruebas y documentación técnica.
- Frontend reservado con README e instrucciones únicamente.
- Documentación, GitHub y skills compartidas en la raíz.
- Accesos Gradle de raíz que delegan al directorio del backend para mantener las rutas relativas.
- Workflow del backend con directorio de trabajo explícito.
- Markdown habilitado para control de versiones y exclusiones de datos, configuración y salidas generadas adaptadas.
- Inventario inicial de contratos, dependencias C-01 a C-05 e índice de planes.

## 3. Archivos creados

- `README.md`.
- `backend/README.md` y `backend/AGENTS.md`.
- `frontend/README.md` y `frontend/AGENTS.md`.
- `docs/branch-plans/README.md`.
- `docs/branch-plans/frontend-repository-contracts-plan.md`.
- `docs/branch-plans/frontend-repository-contracts-report.md`.
- `docs/integration/frontend-backend-contracts.md`.

Los accesos Gradle originales se trasladaron a backend; los de raíz se sustituyeron por delegadores.

## 4. Archivos trasladados y modificados

Se trasladaron `src/`, `gradle/`, `build.gradle.kts`, `settings.gradle.kts`, wrappers originales, Dockerfile, `.dockerignore`, Compose, ARCHITECTURE, CODE_STYLE, CONTRIBUTING y HELP a backend.

Se actualizaron `.gitignore`, `.gitattributes`, `AGENTS.md`, el workflow del backend, los delegadores de raíz y el blueprint del frontend. La arquitectura del backend explicita la ubicación y completa su mapa con organization y accesscredential.

En Java, se adapta la ruta del workflow compartido en `GmailOfflineBuildContractTest` y se corrige un fixture temporal preexistente en `MembershipExpiredRenewalApiIntegrationTest`, detectado por el quality gate. Las fuentes de producción, recursos operativos, APIs, dependencias y SQL no se modificaron.

Los archivos privados `.env`, `.env.local`, `data/`, `.gradle/` y `build/` se trasladaron junto al backend. No se modificaron valores de configuración ni se eliminaron datos. Las configuraciones del IDE y el snapshot XML del usuario permanecen en la raíz.

## 5. Base de datos

Sin cambios de esquema, migraciones o contenido SQL. Se conservan V1–V43, el proyecto Compose `coach-gym`, el contenedor configurado y el volumen `coach-gym-postgres-data`. El movimiento de carpetas no trasladó ni reinicializó la base de datos.

Antes y después del traslado se compararon hashes SHA-256 de 2.160 archivos: cero diferencias. La comparación inicial normalizada contra Git de 2.152 archivos trasladados detectó únicamente la prueba de ruta del workflow: cero fuentes de producción y cero migraciones modificadas. La corrección temporal posterior afecta una segunda prueba y se detalla abajo.

La comparación final de los mismos 2.152 archivos confirmó exactamente dos pruebas modificadas: GmailOfflineBuildContractTest y MembershipExpiredRenewalApiIntegrationTest. Ningún archivo de producción ni migración cambió de contenido.

## 6. Seguridad y privacidad

- No se modificaron autorización, sesión, CSRF, CORS, DTOs, eventos, auditoría ni contratos de proveedores.
- La configuración privada y los datos locales siguen ignorados por Git y excluidos del contexto Docker.
- Se verificaron exclusiones bajo backend y futuras salidas del frontend.
- Se revisaron documentos con patrones de credenciales sin imprimir valores privados; no se detectaron coincidencias en la revisión inicial de 30 archivos.
- La suite ordinaria no llama servicios reales del gimnasio. Gmail live permanece separado y opt-in.
- Docker Desktop se inició para permitir pruebas aisladas. No se ejecutaron despliegues ni llamadas a Gmail, Stripe o Supabase.

## 7. Pruebas

Se actualizó `GmailOfflineBuildContractTest` para leer `../.github/workflows/backend-ci.yml` desde el directorio del backend.

La primera suite completa detectó un fixture temporal preexistente: la renovación de una membresía expirada solicitaba `2026-10-05` esperando 200. La política existente rechaza fechas anteriores al día operativo, por lo que esa prueba dejó de ser válida después de esa fecha. Se ajustaron sus fechas usando el Clock inyectado del backend: período inicial tres meses antes, renovación explícita un día después y fin esperado un mes después. Se conservan todas las comprobaciones de estado, versión, períodos, historial, importes y auditoría. No se altera la política de producción ni se debilitan sus validaciones.

La primera ejecución focalizada desde la raíz pasó 24 pruebas, pero el delegador Windows devolvió un error porque fue normalizado mientras esa ejecución estaba activa. Se estabilizó el archivo antes de volver a verificar el acceso. Ese intento no se usa como prueba de éxito del comando de raíz; el resultado definitivo se registra abajo.

## 8. Comandos ejecutados y resultados

| Comprobación | Resultado |
| --- | --- |
| `./gradlew compileJava compileTestJava` previo al traslado | PASS, 34 s. |
| `./gradlew compileJava compileTestJava` desde backend después del traslado | PASS, 1 min 38 s. |
| Pruebas enfocadas: Gmail offline, deployment readiness, migration contract, Spring Modulith y layering | 24 pruebas, cero fallos, errores u omitidas. Verificación definitiva del comando de raíz: PASS, 13 s, resultado de pruebas en caché. |
| `docker compose --project-directory backend -f backend/compose.yaml config --quiet` | PASS. |
| `docker build --tag coach-gym-backend:f00 backend` | PASS; bootJar generado dentro de la imagen y runtime exportado. |
| Acceso raíz desde Git Bash, tarea help | PASS, 5 s. |
| Primera ejecución de `./gradlew clean check` desde raíz | Interrumpida después de identificar el fixture temporal descrito; no se presenta como quality gate aprobado. |
| Compilación posterior a la corrección temporal | PASS, 11 s. |
| Pruebas enfocadas de renovación después de la corrección | PASS, 34 pruebas en 8 suites; cero fallos, errores u omitidas. Comando completado en 19 min 3 s. |
| Segunda ejecución de `./gradlew clean check` desde raíz | Cancelada durante test después de 13 min 17 s, según el log de Gradle; sin resultado completo y no aprobada. |
| `./gradlew clean check` definitivo desde backend | PASS el 14 de octubre, 23 min 44 s; 2.985 pruebas en 649 suites, cero fallos, errores u omitidas. |
| Arranque de la imagen con PostgreSQL temporal aislado | PASS: health UP, 43 migraciones, OpenAPI con 115 rutas, endpoint privado devuelve 401 y CSRF disponible. |
| Contrato OpenAPI integrado de la suite completa | PASS: 10 pruebas de FrontendApiContractOpenApiIntegrationTest, además de las suites OpenAPI de cada módulo. |
| Spring Modulith y layering en la suite completa | PASS: 1 prueba de límites modulares y 14 pruebas de capas. |
| `./gradlew clean` después de registrar resultados | PASS, 6 s; salida de compilación y reportes generados retirados mediante Gradle. |
| `git diff --check` y validación de exclusiones | PASS; documentos UTF-8 sin BOM y exclusiones privadas verificadas. |

Las compilaciones mantienen los avisos existentes de APIs deprecadas y operaciones no comprobadas. No se cambiaron fuentes para ocultarlos.

La prueba de imagen utilizó dos contenedores y una red temporal propios, sin volumen existente ni configuración privada. El puerto HTTP fue aleatorio y limitado a loopback. Los contenedores y su red se retiraron después de la comprobación; la imagen local `coach-gym-backend:f00` queda disponible. No se publicó ninguna imagen.

### Recuperación del entorno el 14 de octubre

La ejecución anterior fue cancelada y sus procesos ya no estaban activos al retomar. Docker Desktop no podía iniciar su motor: el log identificó un fallo al acceder al endpoint temporal `dockerInference`, coincidente con [la incidencia de arranque reportada en Docker](https://github.com/docker/desktop-feedback/issues/625).

El cambio de nombre del endpoint individual falló con un error de acceso de Windows. Se cerraron los procesos de Docker iniciados durante este intento de recuperación y se apartó de forma reversible únicamente su directorio local de ejecución `run`, conservándolo como `run.f00-stale-20261014-162950` dentro de la carpeta local de Docker. Docker recreó sus archivos temporales y volvió a exponer el motor, versión 29.7.2. Se conservaron configuración, imágenes, datos WSL y volúmenes; no se ejecutó limpieza de datos ni reinstalación. El respaldo permanece fuera del repositorio.

## 9. Resultado de validación

**Dictamen de quality-gate-review: PASS WITH NON-BLOCKING NOTES.** La suite completa, compilaciones, contratos, arquitectura, configuración y arranque de la imagen pasaron. La revisión de consistencia confirma cero cambios de contenido en producción o migraciones, dos adaptaciones de pruebas justificadas y las exclusiones de información privada.

Las notas no bloqueantes son el blueprint histórico del backend ausente, la reimportación eventual del IDE y las consultas futuras registradas para el frontend. F00 no amplía reglas comerciales ni certifica integraciones externas desplegadas.

## 10. Limitaciones y trabajo diferido

- El plan y el código de F01 se entregan por separado en `feature/frontend-foundation-auth`, con autorización individual para sus bloques.
- C-01 y C-02 requieren futuras consultas administrativas y de membresías. C-03 necesita revisar recuperación/correlación de intentos en F09. No se implementaron APIs nuevas.
- Las configuraciones locales del IDE pueden necesitar reimportación de `backend/settings.gradle.kts` o actualización de su directorio de trabajo.
- `.env.local` conserva el mecanismo de carga previo; ahora se utiliza desde backend.
- Los planes históricos y el snapshot conservan las rutas que existían cuando se elaboraron. Las guías actuales explican las nuevas rutas.
- El blueprint original del backend referido por el plan anterior continúa ausente; F00 no inventa ese documento ni modifica decisiones de negocio.
- No se validaron proveedores reales ni despliegue de producción.

## 11. Estado del repositorio

Durante la ejecución original de F00 no se ejecutaron staging, commits, cambio de rama, push, rebase, merge ni sincronización. Git permanece bajo control del usuario.

Antes de staging, Git muestra fuentes originales eliminadas y backend como árbol nuevo. Esa representación corresponde al traslado; la comparación de contenido demuestra su integridad. La documentación e instrucciones antes ignoradas ahora son visibles para control de versiones. Los archivos privados y salidas generadas permanecen ignorados.

En la reorganización posterior, el usuario conservó F00 y B01 en el stash `8874a59fb2e23e4cb53756aa5204af3baa0302da`, protegido por la etiqueta local `backup/f00-b01-before-split-20261015`, y creó `chore/monorepo-layout` desde `origin/main`. Antes de ajustar la documentación para separar las entregas se compararon 2.212 archivos del respaldo, sin diferencias; los cambios de archivos seguidos también coincidían. El código, las herramientas y el plan de B01 quedan fuera de la selección de F00.

## 12. Siguiente etapa

Entregar F00 en `chore/monorepo-layout` y conservar el plan y la base de F01 para `feature/frontend-foundation-auth`. El soporte de contratos pendiente se planificará en `feature/backend-frontend-contracts` antes de los módulos de negocio afectados. Esta reorganización no inicia funcionalidades adicionales.

## 13. Detención

Stopped after the authorized block. Waiting for explicit authorization to continue.
