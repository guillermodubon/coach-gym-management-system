# Contrato HTTP de F01

`f01.openapi.json` contiene las seis operaciones autorizadas de F01 y el cierre de sus referencias a esquemas y seguridad. Se capturó el 17 de octubre de 2026 desde `/v3/api-docs` del backend compilado de este repositorio, sin redactar DTOs equivalentes a mano.

- Revisión al capturar: `f13dd8904eb50a452ac52313aefb846cbe05c3ce`.
- Árbol Git de `backend/`: `9b5732d3e200e8bef6195e69e0aa917104bc76bc`.
- Java 21, Spring Boot 4.1.0, Springdoc 3.0.3 y OpenAPI 3.1.0.
- PostgreSQL 17 temporal y aislado, con Flyway V1–V43; sin datos del usuario, bootstrap, correo ni Stripe habilitados.
- Las propiedades públicas del documento se reducen al alcance F01 y un servidor relativo `/`. Los esquemas de las operaciones se conservan como los exporta Springdoc.

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

## Diferencias observadas entre metadatos y ejecución

1. Springdoc anuncia `csrfToken` como parámetro de consulta obligatorio en `GET /auth/csrf`. El controlador lo obtiene del argumento de seguridad de Spring; la petición real sin query respondió `200` con `token`, `headerName` y `parameterName`. El transporte no envía ese parámetro ni coloca el token en una URL. Se conserva el metadato exportado para hacer visible la discrepancia. Corregir el exportador requeriría un alcance backend propio.
2. Las respuestas basadas en records no declaran todas sus propiedades como `required`, y `activeBranch` no figura como nullable aunque la implementación puede devolver `null`. Los consumidores comprueban los campos mínimos y producen una proyección segura con `activeBranch: null` cuando falta el valor. No se interpreta un tipo generado como validación en ejecución.
3. Los errores de filtros y algunos handlers no aparecen completamente descritos en las respuestas de estas operaciones. La conversión de `ProblemDetail` se contrastó con `ApiProblemFactory`, `ApiExceptionHandler`, los handlers de seguridad y de perfil/contexto. Solo se conservan códigos de F01 comprobados y metadatos seguros; respuestas desconocidas reciben un error genérico.

No se modificó Java, SQL, CSRF ni CORS para resolver estas diferencias.

## Referencias de implementación

- [API Node de openapi-typescript](https://openapi-ts.dev/node).
- [Defaults de TanStack Query](https://tanstack.com/query/latest/docs/framework/react/guides/important-defaults).
- [Integración Node de MSW](https://mswjs.io/guides/integrations/node).

Las versiones y APIs efectivas se comprobaron también en los paquetes instalados: MSW 3 usa `onUnhandledFrame`, y el generador se ejecuta con su peer TypeScript compatible.
