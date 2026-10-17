# Frontend de Coach Gym

La entrega F00 reserva este directorio y documenta los límites de la futura aplicación web interna. La base ejecutable, sus dependencias y sus herramientas se versionan por separado en `feature/frontend-foundation-auth`.

## Alcance de F00

- Directorio del frontend separado de la aplicación Spring Boot.
- Reglas propias que complementan el `AGENTS.md` de la raíz.
- Blueprint e inventario de contratos compartidos.
- La aplicación React y sus comandos de instalación, compilación y pruebas pertenecen a F01.

## Límites de integración

- Consumir los contratos HTTP `/api/v1` del backend; no acceder a PostgreSQL, almacenamiento privado o proveedores desde el navegador.
- Mantener sesión del servidor, CSRF, permisos y aislamiento por sucursal.
- Mantener las dependencias, fuentes y herramientas del frontend dentro de este directorio.
- Usar únicamente configuración pública en el navegador. La configuración privada y los datos locales del backend pertenecen a `../backend/` y permanecen excluidos de Git.

## Documentos

Revisar el [blueprint](../docs/blueprint/frontend-blueprint.md), las [reglas del frontend](AGENTS.md) y los [contratos actuales del backend](../docs/integration/frontend-backend-contracts.md). El plan detallado de F01 se incorpora con su entrega; cada bloque requiere autorización individual.
