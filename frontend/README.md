# Frontend de Coach Gym

F00 separó este directorio del backend. B01 creó la base de TypeScript, React, Ant Design y las herramientas de calidad. B02 agrega contratos revisados, transporte HTTP, CSRF, errores seguros y la infraestructura de TanStack Query. B03 incorpora temas claros/oscuros, layouts adaptables y paneles de estado. B04 conecta la sesión del servidor, login/logout y rutas protegidas. B05 permite cambiar la contraseña obligatoria y exige iniciar sesión de nuevo. B06 agrega el menú accesible de cuenta y el contexto de sucursal de solo lectura desde `/auth/me`. B07 incorpora un runner de integración contra el backend real.

Esta base se entrega en `feature/frontend-foundation-auth`. La app consulta `/api/v1/auth/me` al iniciarse y muestra login o el contenido privado conforme a la respuesta del servidor. El layout visual aislado de B03 se carga únicamente desde la entrada de Playwright; no existe una ruta de producto que evite la autenticación.

El layout usa espaciado fluido, contenido con ancho máximo y navegación lateral desde 992 px; por debajo utiliza un Drawer. En móviles, el header conserva navegación, tema y avatar, con la identidad completa en el menú y la sucursal completa en Inicio. Las tarjetas y formularios ajustan textos y acciones sin exigir desplazamiento horizontal. Los estilos viven junto a sus layouts, funcionalidades y componentes compartidos; `styles.css` conserva únicamente base, variables y utilidades comunes. La [revisión responsive](../docs/branch-plans/frontend-foundation-auth-responsive-report.md) registra pruebas entre 280 y 2560 px, ambos temas y orientación horizontal.

Los reportes locales de [B06](../docs/branch-plans/frontend-foundation-auth-b06-report.md) y [B07](../docs/branch-plans/frontend-foundation-auth-b07-report.md) registran el alcance y las pruebas. Los bloques B01–B07 se entregan en esta rama. La integración real R01–R13 y el gate general pasan; el cierre de F01 aún requiere completar la revisión manual de zoom real al 200%.

## Requisitos

- Node.js 24 LTS; la versión mayor está declarada en `.nvmrc`.
- npm 11 o 12, según los requisitos de `package.json`.
- Para desarrollo integrado, el backend local debe escuchar en `http://localhost:8080`.
- Para pruebas de navegador, instalar Chromium una vez con `npx playwright install chromium`.

Las dependencias están fijadas en `package.json` y reproducidas por `package-lock.json`. Instala las dependencias desde este directorio:

```powershell
npm ci
```

## Comandos

```powershell
npm run dev
npm run types:generate
npm run types:check
npm run check
npm run build
npm run test:e2e
npm run preview
```

`check` comprueba el contrato y la reproducibilidad de los tipos, TypeScript, lint, formato, pruebas Vitest con MSW, procesamiento de OpenAPI y regresiones del lanzador de integración. Vitest usa un worker para mantener determinista el bootstrap de sesión en sus pruebas jsdom. `dev`, `typecheck`, `test:run` y `build` generan los tipos necesarios automáticamente; `types:check` también funciona en una copia sin tipos generados. Si encuentra una salida existente distinta de la entrada revisada, requiere regenerarla explícitamente.

`test:e2e` inicia Vite, recorre login/logout, el menú de cuenta, la actualización del contexto y el layout con respuestas HTTP interceptadas en la prueba, y revisa accesibilidad con Chromium y axe-core en ambos temas y varios anchos. Las auditorías esperan la finalización de las transiciones CSS antes de medir el contraste del tema resultante. Los recorridos de identidad prueban la UI y el transporte con datos sintéticos; no prueban cookies reales del backend. La integración de servidor/cookie se prueba por separado con `test:e2e:integration`.

`test:e2e:integration` construye el frontend en un directorio aislado, compila y arranca el JAR del backend contra PostgreSQL 17 de Testcontainers, compara la proyección OpenAPI viva y ejecuta los casos reales R01–R13 en Chromium mediante un proxy de mismo origen. Requiere Java 21, Docker accesible y Chromium. El runner crea y limpia solo sus procesos, contenedor y carpeta temporal; no lee `.env` del frontend/backend ni publica trazas, cookies o contraseñas. El job `integration` del workflow ejecuta esta misma ruta cuando cambian archivos del frontend o del backend que afectan F01.

El wrapper se invoca desde `backend/` mediante `cmd.exe` y `gradlew.bat` en Windows, y mediante `sh ./gradlew` en Linux/macOS. Así funciona también en clones POSIX donde Git conserva `gradlew` con modo `100644`, sin depender de un `chmod` ejecutado en otro job. Los errores de lanzamiento muestran únicamente la fase y un código de sistema permitido; se suprimen rutas, argumentos y errores crudos. La prueba de permisos POSIX se ejecuta en Linux/macOS y se omite únicamente en Windows; las comprobaciones de selección de plataforma y privacidad se ejecutan en todas las plataformas.

El tema sigue la preferencia `coach-gym.theme` (`light` o `dark`) en localStorage. Sin preferencia explícita, sigue la configuración del sistema; si el almacenamiento no está disponible, conserva la selección solo en memoria. Es la única preferencia que B03 persiste.

La aplicación conserva TypeScript 6. El generador `openapi-typescript` requiere TypeScript 5, por lo que se aísla en el workspace `tools/contracts/` con TypeScript 5.9.3. Un solo `npm ci` desde `frontend/` instala ambos entornos con el mismo lockfile; no se utilizan excepciones de peer dependencies ni instalaciones manuales en el workspace.

El servidor local publica la aplicación en `http://127.0.0.1:5173` y reenvía `/api` y `/v3/api-docs` al backend. Si el servidor usa otro origen local, configura `COACH_GYM_BACKEND_ORIGIN` en el proceso que ejecuta Vite. Debe contener únicamente un origen HTTP o HTTPS, sin credenciales, rutas ni query string. Esta configuración la consume el servidor de desarrollo y no se incorpora al bundle.

El proxy solo está configurado para desarrollo. `preview` sirve los archivos compilados; el entorno que despliegue la aplicación tendrá que enrutar la API según las decisiones de F15.

La configuración pública del navegador no debe contener contraseñas, cookies de sesión, tokens ni claves de proveedores. No se debe leer ni copiar la configuración privada de `../backend/`. El cambio obligatorio de B05 conserva los valores solo mientras se completa la petición y los borra antes de resolver la transición. Ante un resultado incierto no repite el POST y exige una comprobación manual de sesión. B07 ya está implementado y automatizado localmente; F01 queda pendiente solo de la revisión manual de zoom real al 200%.

## Contratos y transporte de B02

La entrada versionada [contracts/f01.openapi.json](contracts/f01.openapi.json) es una proyección del OpenAPI real del backend con las seis operaciones de F01 y sus esquemas referenciados. Su procedencia, las diferencias de metadatos y el proceso de actualización se documentan en [contracts/README.md](contracts/README.md). La generación y las comprobaciones normales no necesitan iniciar backend, base de datos ni proveedores.

Los tipos derivados viven en `src/shared/api/generated/` y están ignorados por Git, ESLint y Prettier. No se versionan ni se editan manualmente. Las funciones de `src/shared/api/identity.ts` consumen esos tipos y validan las respuestas en ejecución; la experiencia de sesión de B04 las compone sin duplicar el transporte.

`createApiClient` es el único transporte. Usa exclusivamente las rutas y verbos de F01 bajo el mismo origen del navegador, cookies `same-origin`, caché HTTP desactivada y redirecciones rechazadas. El despliegue deberá enrutar `/api/v1` desde ese origen; el proxy local existente cubre desarrollo. No admite un destino externo para enviar credenciales.

El CSRF se obtiene desde `/api/v1/auth/csrf`, se deduplica y conserva solo en memoria. Se usa el nombre de cabecera devuelto por el backend, validado antes de enviarlo. Se descarta después de transiciones de sesión, incluidas respuestas inciertas. Ante `CSRF_TOKEN_INVALID`, se prepara un token nuevo y se rechaza la operación original; únicamente una acción explícita puede volver a enviarla. Un fallo de renovación conserva el rechazo original.

`ApiError` separa errores HTTP, red, timeout, cancelación, solicitud no permitida y respuesta inválida. Conserva solo códigos conocidos, correlación acotada, espera de `Retry-After` cuando es accesible y campos conocidos con mensajes locales. Nunca incluye el JSON crudo, HTML, credenciales, `Response` o causa de red. Un fallo de red, timeout o respuesta inválida después de enviar una mutación no confirma su resultado en el servidor.

QueryClient utiliza únicamente memoria, sin reintentos, sondeo ni relecturas automáticas por foco o reconexión. Las mutaciones no quedan pausadas para reenviarse al recuperar conexión. Las funciones de credenciales se llaman directamente, fuera de query keys y cachés de mutaciones. `clearPrivateData` cancela solicitudes y consultas y vacía las cachés; B04 la integra con las transiciones de sesión y la revalidación por eventos.

MSW se carga solo en la configuración de Vitest. Las solicitudes sin handler fallan y las pruebas usan datos sintéticos; no hay service worker, backend simulado ni bypass de autenticación en desarrollo o producción.

## Estructura y documentos

El código vive en `src/`, los contratos en `contracts/`, el generador en `tools/contracts/` y las pruebas de navegador en `tests/e2e/`. Las dependencias son independientes de Gradle.

Las [reglas del frontend](AGENTS.md) y la documentación de contratos de este directorio permanecen versionadas. El [blueprint](../docs/blueprint/frontend-blueprint.md), el [plan F01](../docs/branch-plans/frontend-foundation-auth-plan.md) y el inventario compartido se conservan localmente bajo `docs/`, ignorados por Git por decisión del propietario; esos enlaces solo estarán disponibles donde exista esa documentación local.
