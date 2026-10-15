# Planes de trabajo de Coach Gym

## Trabajo actual

- [Blueprint del frontend](../blueprint/frontend-blueprint.md).
- [F00 — Organización del repositorio y contratos](frontend-repository-contracts-plan.md).
- [Reporte de ejecución F00](frontend-repository-contracts-report.md).
- [Contratos iniciales del frontend](../integration/frontend-backend-contracts.md).

F00 quedó completada y validada el 14 de octubre de 2026: `clean check` pasó 2.985 pruebas sin fallos, errores u omitidas. Su entrega se organiza en `chore/monorepo-layout`. El plan y la base de F01 se versionan por separado en `feature/frontend-foundation-auth`. Las consultas pendientes documentadas no autorizan cambios de API.

## Planes anteriores del backend

- [Hardening multisucursal](multi-branch-hardening-plan.md): ocho bloques marcados como completados.
- [Reportes y auditoría multisucursal](multi-branch-reporting-audit-plan.md).
- [Correo mediante Gmail API](gmail-api-email-delivery-plan.md).
- [Hardening anterior](backend-hardening-plan-final.md).
- [Plantilla de plan](BRANCH_PLAN_TEMPLATE.md).

Los comandos y rutas de esos planes conservan evidencia histórica. Para ejecutar actualmente el backend se trabaja desde `backend/` o con los delegadores de raíz. Las APIs y migraciones conservan sus nombres originales.

## Siguiente etapa

La reorganización conserva por separado el trabajo de F01. Antes de continuar con los módulos de negocio se preparará el plan de soporte de contratos en `feature/backend-frontend-contracts`, con autorización propia. F01 continuará en `feature/frontend-foundation-auth` según el estado y las autorizaciones de su plan individual.
