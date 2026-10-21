# Contrato HTTP de F01

`f01.openapi.json` contiene las seis operaciones autorizadas de F01 y el cierre de sus referencias a esquemas y seguridad. Se recapturó el 19 de octubre de 2026 desde `/v3/api-docs` del JAR del backend compilado en un directorio aislado, sin redactar DTOs equivalentes a mano.

- Base Git al capturar: `HEAD` `a2dce237ed3e32f7af7290132b09d1a981edb499` en `feature/backend-frontend-contracts`; el JAR incluye los cambios locales sin commit de B02–B07.
- Java 21.0.12, Spring Boot 4.1.0, Springdoc 3.0.3 y OpenAPI 3.1.0.
- PostgreSQL 17 temporal y aislado, con Flyway V1–V43; sin datos del usuario, bootstrap, correo ni Stripe habilitados.
- La captura conserva las seis operaciones F01, el servidor relativo `/` y los esquemas exportados por Springdoc. No incorpora los nuevos endpoints de personal, membresías, períodos o intentos de pago.
- B07 corrige el parámetro CSRF ficticio, documenta campos requeridos y nullable conforme a la respuesta real y añade estados HTTP, media types y cuerpos de error comprobados. Los errores usan el esquema extensible `ApiProblemResponse`, que representa los campos RFC 9457 y las extensiones aplanadas `code`, `timestamp` y `correlationId`; no expone una propiedad `properties` anidada que el JSON real no devuelve. El componente `OpenApiNullValue` usa `type: "null"` de OpenAPI 3.1; el customizer se limita a ese componente y no infiere requiredness global.

| Método | Ruta                          | Consumo en B02                                           |
| ------ | ----------------------------- | -------------------------------------------------------- |
| GET    | `/api/v1/auth/csrf`           | Bootstrap del transporte; solo memoria.                  |
| POST   | `/api/v1/auth/login`          | Función directa, CSRF y respuesta 204.                   |
| GET    | `/api/v1/auth/me`             | Identidad validada en ejecución.                         |
| POST   | `/api/v1/auth/logout`         | Función directa, CSRF y respuesta 204.                   |
| POST   | `/api/v1/me/profile/password` | Función directa y resultado de reautenticación validado. |
| GET    | `/api/v1/me/branch-context`   | Lectura validada; no selección ni limpieza.              |

Las funciones no se invocan desde la entrada visual actual. B04–B06 integrarán sus flujos; B02 no certifica autenticación completa en navegador.

## Generación offline

Desde `frontend/`:

```powershell
npm ci
npm run types:generate
npm run types:check
```

La salida es `src/shared/api/generated/schema.d.ts`, ignorada y regenerable. `types:check` valida el alcance, resuelve solo referencias locales y comprueba la reproducibilidad del generador y la correspondencia de la salida existente. Si la salida no existe, la crea. No consulta un servidor para validar vigencia: eso es una comprobación diferente.

El generador está en el workspace `tools/contracts/` porque `openapi-typescript@7.13.0` declara TypeScript `^5.x` como peer. Usa TypeScript 5.9.3 allí; la aplicación, Vite y MSW conservan TypeScript 6.0.3. El lockfile de `frontend/` incluye ambos sin forzar peers.

## Actualización revisada

Solo contra un backend local aislado, con sus proveedores desactivados y sin usar configuración o datos privados:

```powershell
npm run contracts:capture -- http://127.0.0.1:8080/v3/api-docs
```

Usa el puerto real de ese backend. El comando admite únicamente loopback HTTP y `/v3/api-docs`, sin credenciales, query ni fragmento; rechaza redirecciones. Proyecta los seis métodos existentes y sus referencias, sin añadir operaciones de negocio. No crea ni configura el backend.

Antes de aceptar una actualización, revisar los cambios contra controladores, DTOs, configuración de seguridad y handlers actuales. Después regenerar, ejecutar `npm run check` y actualizar la procedencia. B07 deberá volver a contrastar la entrada con el backend de integración y registrar sus recorridos reales. La generación offline por sí sola no demuestra vigencia ni cookies correctas en navegador.

## Resultado de compatibilidad B07

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
