# Frontend de Coach Gym

F00 reservó este directorio para la aplicación React y documentó sus límites. B01 creó la base ejecutable de TypeScript, React, Ant Design y las herramientas de calidad. Los siguientes bloques de F01 todavía deben autorizarse e implementarse; aquí aún no existen login, temas, navegación operativa ni conexión de sesión.

Esta base se entrega en `feature/frontend-foundation-auth`, creada desde `main` con F00 integrado. La recuperación de B01 conserva su código y herramientas; B02 permanece pendiente de autorización.

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
npm run check
npm run build
npm run test:e2e
npm run preview
```

`check` valida TypeScript, lint, formato y pruebas unitarias. `test:e2e` inicia el servidor Vite y prueba que la entrada de la aplicación cargue correctamente y no presente errores automáticos de accesibilidad.

El servidor local publica la aplicación en `http://127.0.0.1:5173` y reenvía `/api` y `/v3/api-docs` al backend. Si el servidor usa otro origen local, configura `COACH_GYM_BACKEND_ORIGIN` en el proceso que ejecuta Vite. Debe contener únicamente un origen HTTP o HTTPS, sin credenciales, rutas ni query string. Esta configuración la consume el servidor de desarrollo y no se incorpora al bundle.

El proxy solo está configurado para desarrollo. `preview` sirve los archivos compilados; el entorno que despliegue la aplicación tendrá que enrutar la API según las decisiones de F15.

La configuración pública del navegador no debe contener contraseñas, cookies de sesión, tokens ni claves de proveedores. No se debe leer ni copiar la configuración privada de `../backend/`. F01 agregará la experiencia visual y los flujos de identidad mediante bloques autorizados.

## Estructura y documentos

El código vive en `src/`, las pruebas de navegador en `tests/e2e/` y las dependencias son independientes de Gradle. Para construir la experiencia, revisar el [blueprint](../docs/blueprint/frontend-blueprint.md), el [plan F01](../docs/branch-plans/frontend-foundation-auth-plan.md), las [reglas del frontend](AGENTS.md) y los [contratos actuales del backend](../docs/integration/frontend-backend-contracts.md).
