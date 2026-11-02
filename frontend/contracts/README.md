# Contratos HTTP de F01, F02 y F03

`f01.openapi.json` contiene las seis operaciones autorizadas de F01 y el cierre de sus referencias a esquemas y seguridad. Se recapturó el 19 de octubre de 2026 desde `/v3/api-docs` del JAR del backend compilado en un directorio aislado, sin redactar DTOs equivalentes a mano.

`f02.openapi.json` contiene las diez operaciones H01–H10 en siete rutas, incluido H06 para verificar compatibilidad con F01. Se capturó el 20 de octubre de 2026 desde el backend integrado en `802bdcf` con el harness aislado. Las rutas con más de un método conservan todos sus métodos. H09 refleja literalmente la salida Springdoc (`type: object` sin propiedades de dominio); el decoder cliente se basa en el DTO Java observado y no se declara como schema del backend.

`f03.openapi.json` contiene las diez operaciones H01–H10 en seis rutas: consulta de organización, catálogo y detalle de sucursal, alta/edición/estado y contexto de sesión. H08 (`GET /api/v1/me/branch-context`) coincide con F01; H09 y H10 son las ampliaciones de selección y limpieza. Se capturó el 31 de octubre de 2026 con el harness aislado sobre el backend integrado por PR #64 (`0fd9b49`), Springdoc 3.0.3 y PostgreSQL 17 temporal con Flyway V1–V43. La captura preserva las referencias reales de Springdoc; no añade rutas ni campos por suposición.

- Base Git al capturar: `HEAD` `a2dce237ed3e32f7af7290132b09d1a981edb499` en `feature/backend-frontend-contracts`; el JAR incluye los cambios locales sin commit de B02–B07.
- Java 21.0.12, Spring Boot 4.1.0, Springdoc 3.0.3 y OpenAPI 3.1.0.
- PostgreSQL 17 temporal y aislado, con Flyway V1–V43; sin datos del usuario, bootstrap, correo ni Stripe habilitados.
- La captura conserva las seis operaciones F01, el servidor relativo `/` y los esquemas exportados por Springdoc. No incorpora los nuevos endpoints de personal, membresías, períodos o intentos de pago.
- La captura F03 verifica que las proyecciones F01 y F02 coinciden antes de escribir el nuevo snapshot. En el modo normal, el harness compara los tres scopes y nunca reescribe snapshots.
- B07 corrige el parámetro CSRF ficticio, documenta campos requeridos y nullable conforme a la respuesta real y añade estados HTTP, media types y cuerpos de error comprobados. Los errores usan el esquema extensible `ApiProblemResponse`, que representa los campos RFC 9457 y las extensiones aplanadas `code`, `timestamp` y `correlationId`; no expone una propiedad `properties` anidada que el JSON real no devuelve. El componente `OpenApiNullValue` usa `type: "null"` de OpenAPI 3.1; el customizer se limita a ese componente y no infiere requiredness global.

| Método | Ruta                          | Consumo en B02                                           |
| ------ | ----------------------------- | -------------------------------------------------------- |
| GET    | `/api/v1/auth/csrf`           | Bootstrap del transporte; solo memoria.                  |
| POST   | `/api/v1/auth/login`          | Función directa, CSRF y respuesta 204.                   |
| GET    | `/api/v1/auth/me`             | Identidad validada en ejecución.                         |
| POST   | `/api/v1/auth/logout`         | Función directa, CSRF y respuesta 204.                   |
| POST   | `/api/v1/me/profile/password` | Función directa y resultado de reautenticación validado. |
| GET    | `/api/v1/me/branch-context`   | Lectura validada; no selección ni limpieza.              |

| Método           | Ruta                                      | Consumo F02                                                                    |
| ---------------- | ----------------------------------------- | ------------------------------------------------------------------------------ |
| GET, PUT         | `/api/v1/me/profile`                      | Lectura propia y actualización con versionado.                                 |
| GET, PUT, DELETE | `/api/v1/me/profile/photo`                | Descarga privada, carga multipart y eliminación con `version`.                 |
| POST             | `/api/v1/auth/password-recovery-requests` | Solicitud pública con respuesta 202 genérica.                                  |
| POST             | `/api/v1/auth/password-recovery/complete` | Completar recuperación con respuesta 204.                                      |
| POST             | `/api/v1/staff-invitations/inspect`       | Preview público con decoder cliente H09; OpenAPI no declara campos de dominio. |
| POST             | `/api/v1/staff-invitations/accept`        | Aceptación pública con respuesta 201 y login manual posterior.                 |

| Método           | Ruta                               | Consumo F03                                                       |
| ---------------- | ---------------------------------- | ----------------------------------------------------------------- |
| GET              | `/api/v1/organization`             | Proyección según rol; detalles administrativos pueden omitirse.   |
| GET, POST        | `/api/v1/branches`                 | Catálogo filtrable y creación estricta.                           |
| GET, PUT         | `/api/v1/branches/{id}`            | Detalle y edición con UUID y versionado.                          |
| POST             | `/api/v1/branches/{id}/activate`   | Transición explícita con motivo y versión.                        |
| POST             | `/api/v1/branches/{id}/deactivate` | Transición explícita con motivo y versión.                        |
| GET, PUT, DELETE | `/api/v1/me/branch-context`        | H08 lectura compartida; H09 selección CSRF; H10 limpieza con 204. |

F03 B02 conecta funciones API directas y sus pantallas se completaron en B03–B07. B08 comparó F01/F02/F03 con OpenAPI vivo sin reescribir snapshots y pasó los flujos reales de F01 (12/12), F02 (10/10), F03 (3/3) y expiración (1/1). No se modificó el backend.

## Generación offline

Desde `frontend/`:

```powershell
npm ci
npm run types:generate
npm run types:check
```

Las salidas ignoradas y regenerables son `src/shared/api/generated/schema.d.ts` (F01), `f02-schema.d.ts` (F02) y `f03-schema.d.ts` (F03). `types:check` valida los tres alcances y comprueba la generación reproducible en memoria, incluso en un checkout sin salidas generadas. Si una salida ya existe, debe coincidir; el comando no crea ni sobrescribe archivos. `typecheck`, que se ejecuta después en `npm run check`, genera las salidas para compilar TypeScript. Las pruebas del generador cubren los tres contratos en un directorio temporal sin salidas y comprueban que una salida desactualizada sigue fallando sin modificarse. No consulta un servidor para validar vigencia: esa comprobación ocurre en el harness de integración.

El generador está en el workspace `tools/contracts/` porque `openapi-typescript@7.13.0` declara TypeScript `^5.x` como peer. Usa TypeScript 5.9.3 allí; la aplicación, Vite y MSW conservan TypeScript 6.0.3. El lockfile de `frontend/` incluye ambos sin forzar peers.

## Actualización revisada

Solo contra un backend local aislado, con sus proveedores desactivados y sin usar configuración o datos privados:

```powershell
npm run contracts:capture -- http://127.0.0.1:8080/v3/api-docs --scope=f03
```

Usa el puerto real de un backend local aislado. El comando admite únicamente loopback HTTP y `/v3/api-docs`, sin credenciales, query ni fragmento; rechaza redirecciones. `--scope=f01` sigue siendo el valor por defecto y conserva el uso anterior que solo especificaba URL. La captura proyecta exclusivamente las operaciones del scope elegido y sus referencias; no crea ni configura el backend.

Para capturar F02 con la base PostgreSQL temporal que administra el harness, sin depender de una configuración local del backend:

```powershell
npm run test:e2e:integration -- --capture-f02-contract
```

Este modo compila y ejecuta el backend aislado y captura solo F02; omite build del frontend y pruebas Chromium. Para la captura F03:

```powershell
npm run test:e2e:integration -- --capture-f03-contract
```

El modo F03 compara primero F01 y F02 con el backend vivo, después escribe solo `contracts/f03.openapi.json` y omite build/browser. La ejecución normal de `npm run test:e2e:integration` compara F01, F02 y F03 con el backend vivo y nunca reescribe snapshots.

Antes de aceptar una actualización, revisar los cambios contra controladores, DTOs, configuración de seguridad y handlers actuales. Después regenerar, ejecutar `npm run check` y actualizar la procedencia. B08 contrasta las tres entradas con el backend de integración y registra los recorridos reales. La generación offline por sí sola no demuestra vigencia ni cookies correctas en navegador.

## Resultado de contratos F03 B02

1. El snapshot contiene exactamente diez operaciones en seis rutas. H08 conserva la misma operación que F01; no se capturan `PUT /api/v1/organization`, asignaciones ni políticas F14.
2. El catálogo usa las ocho claves documentadas por el backend (`status`, `search`, `city`, `countryCode`, `page`, `size`, `sort`, `direction`), con límites y valores allowlisted en el transporte.
3. Los decoders admiten la proyección segura de recepción, campos de sucursal opcionales/nulos y los metadatos de paginación, y rechazan respuestas incompatibles sin propagar el cuerpo crudo.
4. Las funciones directas construyen payloads allowlisted, usan el cliente compartido y respetan CSRF en POST/PUT/DELETE. H10 es DELETE vacío con respuesta 204; la eliminación de foto F02 conserva query `version` y respuesta JSON 200.
5. `npm run test:e2e:integration` comparó F01/F02/F03 con el backend integrado y aprobó los proyectos reales de sesión F01 (12/12), F02 (10/10) y expiración (1/1). Los flujos de escritura F03 con roles reales quedan para la integración correspondiente de F03; este bloque no los declara probados en UI.

## Resultado de integración real F03 B08

1. El backend integrado publicó contratos Springdoc que coincidieron con las proyecciones versionadas F01, F02 y F03; la ejecución normal no reescribió snapshots.
2. Chromium verificó los recorridos de organización y sucursales, creación/edición, activación/desactivación, protección de la sucursal inicial y contexto entre pestañas con cookies y CSRF reales: F03 3/3.
3. La regresión conservó F01 normal 12/12, F02 10/10 y expiración F01 1/1.
4. Las cuentas y sucursales fueron sintéticas y se cargaron en PostgreSQL temporal de Testcontainers. La suite no usó datos privados, credenciales externas ni servicios de correo o pago.
5. El backend mantuvo autoridad sobre rol/alcance, cambios de estado, versión y contexto; los casos directos negativos demostraron respuestas 401, 403 y 409.

## Resultado de compatibilidad F01 B07

1. `GET /api/v1/auth/csrf` fue solicitado sin query y respondió `200` con los nombres y el token CSRF. El parámetro de seguridad `CsrfToken` ya no aparece como parámetro HTTP; el token continúa viajando en el header configurado para mutaciones.
2. `CurrentUserResponse.activeBranch` y `StaffBranchContextResponse.activeBranch` están presentes y admiten `null`, validado con una cuenta sintética que no tiene sucursal seleccionada. `roles` y `availableBranches` se declaran como arrays requeridos y no nullable. El test también verifica presencia y nullabilidad por separado en OpenAPI 3.1.
3. Los DTOs F01 y los DTOs nuevos cubiertos por B07 declaran explícitamente sus campos requeridos; las promociones, coberturas, períodos e identificadores/fechas opcionales de pago reflejan nullabilidad comprobada.
4. Las respuestas documentan los códigos aplicables por operación: autenticación, autorización, filtros de cambio de contraseña, CSRF, tamaño de cuerpo, rate limit, reautenticación, errores de perfil/contexto, conflictos y recurso inexistente. Las respuestas `204`, las rutas y las reglas de sesión/contraseña se mantuvieron.
5. La prueba integra el esquema generado y las respuestas HTTP reales; la comparación del runner confirmó que la proyección viva F01 coincide con este snapshot. La integración real finalizó con 12/12 recorridos normales y 1/1 de expiración aprobados.

La prueba OpenAPI incluye un usuario y una sucursal sintéticos y termina la asignación temporal mediante una sesión ADMIN. No cambia el contrato de runtime ni requiere migración.

## Referencias de implementación

- [API Node de openapi-typescript](https://openapi-ts.dev/node).
- [Defaults de TanStack Query](https://tanstack.com/query/latest/docs/framework/react/guides/important-defaults).
- [Integración Node de MSW](https://mswjs.io/guides/integrations/node).

Las versiones y APIs efectivas se comprobaron también en los paquetes instalados: MSW 3 usa `onUnhandledFrame`, y el generador se ejecuta con su peer TypeScript compatible.
