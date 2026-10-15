# Contratos de partida para el frontend

- **Fecha de revisión:** 13 de octubre de 2026.
- **Propósito:** consolidación documental de F00, sin cambios de API.
- **Backend:** `backend/`, contrato `/api/v1` y OpenAPI `/v3/api-docs`.
- **Autoridad:** implementación, DTOs, políticas de aplicación y pruebas actuales; este inventario no reemplaza OpenAPI ni crea permisos.

## Identidad y sesión

| Capacidad | Contrato actual | Condición relevante |
| --- | --- | --- |
| CSRF | `GET /api/v1/auth/csrf` | Obtener token y nombre de header antes de las mutaciones. |
| Login | `POST /api/v1/auth/login` | Credenciales y CSRF; crea sesión, respuesta 204. |
| Cuenta actual | `GET /api/v1/auth/me` | Identidad, roles, alcance, sucursal activa, sucursales disponibles y cambio obligatorio de contraseña. |
| Logout | `POST /api/v1/auth/logout` | Invalida sesión; requiere CSRF. |
| Perfil propio | `/api/v1/me/profile` | Consulta y actualización de los campos admitidos para el propio usuario. |
| Foto propia | `/api/v1/me/profile/photo` | Foto autorizada; carga, lectura y eliminación según contrato. |
| Contraseña propia | `POST /api/v1/me/profile/password` | Requisitos actuales y efectos sobre la sesión. |
| Contexto de sucursal | `/api/v1/me/branch-context` | Consulta, selección y limpieza de contexto; seleccionar no concede autoridad. |
| Recuperación | `/api/v1/auth/password-recovery-requests` y `/api/v1/auth/password-recovery/complete` | Respuesta de solicitud genérica, enlace de uso único, límites y CSRF vigente. |
| Invitaciones | `/api/v1/staff-invitations` | Administración de organización; inspect y accept son públicos y retienen CSRF. |

Los enlaces de invitación y recuperación actuales llevan `#token=`. La futura interfaz mantendrá el valor solo en memoria y retirará el fragmento. El estado `passwordChangeRequired` restringe las consultas de negocio y perfil; el cambio obligatorio debe resolverse antes de solicitar esos recursos.

## Roles y alcance

Combinaciones admitidas: `ADMIN + ORGANIZATION`, `ADMIN + BRANCH` y `RECEPTIONIST + BRANCH`. Clientes son registros comerciales y no tienen cuentas de aplicación.

- Administración de identidades, invitaciones y asignaciones: administrador de organización.
- Operaciones de sucursal: autoridad y asignaciones persistidas, además del rol de cada operación.
- Consulta de planes y promociones: personal admitido; administración del catálogo: ADMIN según política de aplicación.
- Pagos y comprobantes: personal autorizado; correcciones: ADMIN.
- Incidentes: personal puede reportar y consultar según alcance; resolución y mantenimiento tienen restricciones adicionales.
- Auditoría: ADMIN con visibilidad limitada por alcance.
- Resumen de reportes: respuesta diferenciada por rol. Recepción no accede a tendencias ni comparación administrativa.

Cada plan de frontend extraerá la matriz exacta desde la autorización HTTP, servicios de aplicación, políticas de alcance y pruebas. Ocultar controles complementa la autorización del servidor; nunca la reemplaza.

## Inventario funcional

| Área | Familias de rutas disponibles | Observaciones |
| --- | --- | --- |
| Organización | `/api/v1/organization`, `/api/v1/branches` | Catálogo y ciclo de vida; distinguir catálogo de sucursales operativamente autorizadas. |
| Personal | `/api/v1/staff/branch-assignments`, `/api/v1/staff/{userId}/...` | Asignaciones, scope y acciones de identidad; no constituyen directorio completo. |
| Clientes | `/api/v1/clients`, perfil, foto y status-history | Búsqueda paginada y expediente consolidado, con membresía actual, pago del período y último acceso cuando existen. |
| Planes | `/api/v1/plans`, branch-coverage | Catálogo y cobertura de sucursales. |
| Promociones | `/api/v1/promotions`, eligible-plans | Catálogo, vigencia y elegibilidad. |
| Membresías | `/api/v1/memberships` y acciones por ID | Creación, detalle y ciclo de vida; períodos actuales y cobertura persistida. |
| Pagos | `/api/v1/payments` | Listado, registro, detalle, corrección e historial de estados. |
| Comprobantes | `/api/v1/payments/{paymentId}/receipt` y receipt.pdf | Documento canónico generado y autorizado por backend. |
| Checkout | `/api/v1/payment-attempts` | Creación de Stripe Checkout, consulta por ID y cancelación explícita. |
| Credenciales | `/api/v1/clients/{clientId}/access-credential` | Emisión, metadatos, content PNG, reemplazo, revocación e historial. |
| Accesos | `/api/v1/access` | Check-in manual y QR; registros de acceso. |
| Equipos | `/api/v1/equipment-categories`, `/api/v1/equipment` | Catálogo y acciones operativas autorizadas. |
| Operaciones | `/api/v1/incidents`, `/api/v1/maintenances` | Ciclo de vida, relaciones e historiales. |
| Notificaciones | `/api/v1/notifications` | Bandeja propia, detalle, conteo y lectura. |
| Correo | `/api/v1/email-deliveries` | Envíos de documentos, consulta de entregas/intentos y reintento permitido. |
| Reportes | `/api/v1/reporting/summary`, financial-trend, access-trend y branches/comparison | Filtros y alcance explícitos. |
| Auditoría | `/api/v1/audit-entries`, export.csv | Consulta, detalle y CSV con restricciones de autoridad. |
| Políticas | `/api/v1/settings/access-payment-policy`, `/api/v1/branches/{branchId}/access-payment-policy` | Política global y override de sucursal; no API genérica de ajustes. |

Las familias de rutas no implican que todos los verbos estén disponibles en cada ruta. Los nombres, filtros, estados, versiones y cuerpos se tomarán de OpenAPI al planificar cada rama.

## Condiciones comunes de consumo

- Credenciales de sesión mediante cookies; CSRF en las mutaciones que el backend protege.
- CORS limitado a orígenes configurados y credenciales; cabeceras públicas de descarga y correlación según configuración.
- `ProblemDetail` con código, timestamp y correlación cuando existe; tratar por separado 401, 403, 404, 409, 413, 429 y fallos de servicio.
- Paginación, filtros y ordenamientos acotados por contrato, sin descargar toda la base para filtrar localmente.
- Los reportes nuevos usan intervalos de inicio incluido y fin excluido, zona de negocio retornada y separación por moneda.
- Importe, precio, cobertura y decisiones comerciales permanecen en el servidor.
- PaymentAttempt no es Payment; un retorno de Checkout no confirma un pago.
- Descargar fotos, PDF, PNG y CSV mediante backend autorizado; no acceder directamente a Storage.
- No almacenar tokens, QR, contraseñas, claves de proveedores o secretos en frontend ni analítica.

El endpoint anterior `/api/v1/reporting/dashboard` continúa como compatibilidad restringida a ADMIN de organización. El frontend nuevo debe basarse en summary y los reportes multisucursal.

## Dependencias consolidadas

| ID | Evidencia actual | Resolución prevista |
| --- | --- | --- |
| C-01 | Los controladores de personal ofrecen búsqueda de asignaciones y acciones por ID, pero no directorio completo ni detalle administrativo general con estado y versión. | Preparar una entrega backend posterior de consultas seguras para F04. |
| C-02 | MembershipController ofrece detalle por ID y acciones; ClientOperationalProfile ofrece membresía actual. No se encontró listado general ni consulta HTTP de períodos históricos. | Preparar consultas necesarias antes del cierre completo de F07. Puede iniciarse desde expediente para los flujos actuales. |
| C-03 | PaymentAttemptController crea, consulta por ID y cancela; no lista intentos por cliente/período. | El plan de F09 debe verificar correlación de retorno y recuperación después de recarga. Extender consultas solo si se autoriza y resulta necesario. |
| C-04 | Hay organización y políticas de acceso, sin configuración genérica ni persistencia de preferencias visuales personales. | Limitar ajustes a contratos reales; tema como preferencia local. |
| C-05 | Hay CSV de auditoría y PDF/PNG específicos, sin exportación analítica general. | Entregar consultas y visualización en F13; una exportación nueva requiere alcance posterior. |

F00 registra estas dependencias. No implementa las consultas nuevas ni modifica autorización. El plan específico de soporte de contratos se preparará cuando el usuario autorice ese trabajo en la rama `feature/backend-frontend-contracts`, antes de continuar con los módulos de negocio afectados.

## Evidencia verificable

- `backend/src/main/java/io/github/guillermodubon/coachgym/auth/web/AuthenticationController.java`.
- `backend/src/main/java/io/github/guillermodubon/coachgym/user/web/` para identidad, perfil, invitaciones y asignaciones.
- `backend/src/main/java/io/github/guillermodubon/coachgym/client/ClientOperationalProfile.java`.
- `backend/src/main/java/io/github/guillermodubon/coachgym/membership/web/MembershipController.java`.
- `backend/src/main/java/io/github/guillermodubon/coachgym/payment/web/PaymentAttemptController.java`.
- `backend/src/main/java/io/github/guillermodubon/coachgym/reporting/web/ReportingApiController.java`.
- `backend/src/test/java/io/github/guillermodubon/coachgym/configuration/FrontendApiContractOpenApiIntegrationTest.java`.

Estos son puntos de inspección y no una copia estática de los esquemas. Antes de implementar se revisará el contrato actual de cada operación.
