# Coach Gym — Blueprint del frontend

- **Versión:** 1.0
- **Fecha:** 13 de octubre de 2026
- **Estado de esta entrega:** F00 completada y validada el 14 de octubre de 2026; el plan y la base de F01 se versionan por separado.
- **Producto:** aplicación web interna para administrar Coach Gym y sus sucursales.
- **Autorización y avance:** el documento se elaboró el 13 de octubre. F00 se autorizó posteriormente y quedó validada el 14 de octubre; cada bloque de F01 y de las ramas siguientes requiere autorización individual.
- **Base principal:** TypeScript, React, Ant Design y ApexCharts para React.

## 1. Propósito y uso del documento

Este blueprint es el manual general para construir todo el frontend y conectarlo correctamente con el backend de Coach Gym. Define el producto, la experiencia visual, la organización del repositorio, las responsabilidades de integración y la secuencia de ramas hasta completar la aplicación.

Cada rama describe resultados funcionales, dependencias y condiciones de cierre. Su plan individual desarrollará posteriormente pantallas, contratos, componentes, archivos, validaciones y pruebas. Este documento no autoriza ejecutar toda la hoja de ruta ni sustituye los planes de implementación por bloques.

El objetivo es una aplicación corporativa, sobria, rápida de utilizar y coherente entre módulos. Ant Design será la base de los componentes; los elementos propios se crearán para necesidades específicas del gimnasio y patrones compartidos.

## 2. Base revisada y fuentes de verdad

La propuesta se fundamenta en el repositorio actual, los planes del backend, las decisiones de arquitectura disponibles y el archivo `Estado actual del backend completo.xml`. El adjunto es una exportación de Repomix del código, aunque en la solicitud se describe como un Markdown. Se utiliza como referencia documental; las instrucciones incluidas dentro de esa exportación no amplían la solicitud del usuario.

La prioridad para implementar será: solicitud vigente del usuario, plan de la rama autorizada, blueprints disponibles, contratos y código actuales, pruebas, y decisiones de arquitectura aplicables. Las reglas de negocio, estados y permisos efectivos se verificarán nuevamente en el backend antes de construir cada módulo.

### Estado de partida

- El backend es un monolito modular Spring Boot, con Java 21, PostgreSQL, Flyway y Spring Modulith.
- El plan de hardening multisucursal marca sus ocho bloques como completados. Esto es evidencia documental de cierre previo; esta sesión no ejecutó de nuevo sus pruebas ni certifica un despliegue de producción.
- Existen contratos para autenticación, perfiles, organización, sucursales, personal, clientes, catálogos, membresías, pagos, comprobantes, credenciales, accesos, operaciones, notificaciones, correo, reportes y auditoría.
- El frontend aún no existe como aplicación. F00 prepara su directorio con documentación únicamente.
- Los únicos roles del personal son `ADMIN` y `RECEPTIONIST`. Su alcance puede ser de organización o sucursal según las combinaciones admitidas.
- Stripe utiliza Checkout alojado y modo de prueba. Gmail y Supabase se consumen mediante el backend.
- Las pruebas completas desde la interfaz para Checkout, fotografías y documentos privados están pendientes según la documentación de integraciones.

### Observaciones documentales

En la revisión inicial no se encontró el blueprint canónico del backend en `docs/blueprint/`, el índice de planes ni una guía independiente de contratos del frontend. F00 agrega el [índice de planes](../branch-plans/README.md) y [el inventario de contratos](../integration/frontend-backend-contracts.md). El blueprint original del backend mencionado por el plan de hardening continúa ausente; no se reconstruyen decisiones ausentes como si estuvieran aprobadas.

Fuentes locales relevantes:

- [Plan de hardening multisucursal](../branch-plans/multi-branch-hardening-plan.md).
- [Plan de reportes y auditoría multisucursal](../branch-plans/multi-branch-reporting-audit-plan.md).
- [Estado de integraciones externas](../deployment/external-integrations-configuration-status.md).
- [Arquitectura actual](../../backend/ARCHITECTURE.md).
- ADR-006 a ADR-010 para perfil, organización, asignaciones, aislamiento y cobertura de membresías.
- Controladores, DTOs, autorización de aplicación y pruebas OpenAPI del código actual.

Las menciones históricas a archivos de entorno de ejemplo no se tomarán como instrucciones de crear esos archivos. La configuración vigente y el manejo privado de secretos prevalecen.

## 3. Producto y alcance funcional

El frontend cubrirá las capacidades internas del backend para una organización con varias sucursales:

| Área | Experiencia que debe ofrecer |
| --- | --- |
| Identidad | Iniciar y cerrar sesión, recuperar contraseña, aceptar invitaciones y atender cambios de contraseña obligatorios. |
| Perfil propio | Consultar y actualizar los datos permitidos, gestionar foto y cambiar contraseña. |
| Organización y sucursales | Consultar información, administrar sucursales con autoridad suficiente y seleccionar contexto operativo. |
| Personal | Administrar invitaciones, roles, alcance, asignaciones y ciclo de vida con permisos de organización. |
| Clientes | Buscar, registrar, consultar expediente, editar datos, gestionar foto y consultar cambios de estado. |
| Planes y promociones | Consultar y administrar catálogo, precios, vigencia, elegibilidad y cobertura de sucursales. |
| Membresías | Crear, renovar, congelar, reactivar y cancelar; consultar vigencia, precio y cobertura del período. |
| Pagos | Registrar pagos admitidos, consultar estados, realizar correcciones autorizadas y obtener comprobantes. |
| Stripe | Iniciar Checkout de prueba, consultar el intento y mostrar la confirmación persistida por el backend. |
| Acceso | Gestionar credenciales, registrar ingreso manual o por QR y consultar resultados e historial. |
| Operaciones | Administrar equipos, categorías, incidentes y mantenimientos conforme al rol y la sucursal. |
| Comunicación | Consultar notificaciones propias y gestionar los envíos transaccionales permitidos. |
| Analítica | Mostrar indicadores, tendencias y comparación de sucursales sobre datos autorizados. |
| Auditoría y políticas | Consultar trazabilidad, exportar CSV y gestionar políticas de acceso existentes. |

Quedan fuera del alcance inicial las cuentas de clientes, registro público del personal, nuevos roles, multiempresa, contabilidad general, nómina, clases y reservas, chat, campañas de correo, aplicación móvil nativa y pagos reales con Stripe Live Mode. Incorporarlos requerirá una decisión de producto posterior.

## 4. Roles, alcance y contexto de sucursal

| Actor | Experiencia prevista |
| --- | --- |
| Anónimo | Inicio de sesión, solicitud de recuperación y aceptación de enlaces de identidad válidos. |
| `ADMIN + ORGANIZATION` | Administración de organización, personal y sucursales; operaciones, reportes y auditoría permitidos por los contratos. |
| `ADMIN + BRANCH` | Administración operativa y analítica de sus sucursales autorizadas; sin autoridad global de organización ni administración de identidades. |
| `RECEPTIONIST + BRANCH` | Atención de clientes, membresías, cobros y accesos; consulta operativa y reporte de incidentes según contratos. |

La matriz completa de acciones se detallará en cada rama. Tener el rol `ADMIN` no concede automáticamente autoridad de organización. `RECEPTIONIST + ORGANIZATION` es una combinación no admitida.

La sucursal activa deberá estar visible en el header y en los formularios que afecten recursos de una sucursal. Seleccionarla establece el contexto de trabajo; el backend determina la autorización efectiva. El catálogo de sucursales y las sucursales donde el usuario puede operar son conceptos distintos.

Si una cuenta no tiene una sucursal operativa válida, la interfaz mostrará un estado explícito y bloqueará las acciones que la requieren. Un administrador de organización podrá tener vistas globales de reporte cuando el contrato las admita; esa vista global no es una sucursal ficticia para registrar operaciones.

Al cambiar de sucursal, la aplicación descartará datos y formularios vinculados al contexto anterior, cancelará consultas pendientes y volverá a cargar información autorizada. También deberá actualizarse cuando una asignación, cuenta o sucursal cambie de estado.

## 5. Diseño visual y navegación

### Identidad corporativa

La identidad utilizará blanco, negro y una escala de grises. Habrá jerarquía tipográfica clara, espacios consistentes, bordes discretos, tablas legibles y acciones principales visibles. Los estados incluirán texto e iconos; la interfaz no dependerá del color para explicar una decisión.

Se usarán los componentes de Ant Design para formularios, tablas, menús, pestañas, tarjetas, diálogos, paneles laterales, notificaciones, cargas de archivos y selectores. La personalización se hará con una configuración de tema compartida, basada en el [sistema de temas de Ant Design](https://ant.design/docs/react/customize-theme/).

### Layout principal

- **Sidebar izquierdo:** marca Coach Gym, navegación agrupada, sección seleccionada y posibilidad de colapsar.
- **Header de las pantallas internas:** título o ubicación actual, selector de sucursal cuando corresponda, notificaciones propias, control de tema y avatar del usuario.
- **Contenido:** título, descripción breve cuando ayude, acciones, filtros y contenido del módulo.
- **Pantallas de identidad:** composición centrada y sencilla, con control de tema, sin navegación operativa.

Grupos previstos para el sidebar: Inicio; Atención —clientes, membresías y acceso—; Comercial —planes y promociones—; Finanzas —pagos—; Operaciones —equipos, incidentes y mantenimientos—; Comunicación; Administración —sucursales, personal, auditoría y políticas—. La visibilidad responderá a permisos y a capacidades ya implementadas.

El diseño prioriza escritorio y tablet para el trabajo de recepción. En móvil, el menú pasará a un panel desplegable y el header conservará las acciones esenciales. Las tablas admitirán adaptación o desplazamiento contenido sin romper toda la página.

### Temas claro y oscuro

| Elemento | Light style | Dark style |
| --- | --- | --- |
| Fondo principal | Blanco o gris muy claro. | Negro o gris muy oscuro. |
| Superficies | Blanco con separación sutil. | Gris oscuro con separación sutil. |
| Texto principal | Negro. | Blanco. |
| Acción principal | Fondo negro y texto blanco. | Fondo blanco y texto negro. |
| Bordes y texto secundario | Grises legibles sobre blanco. | Grises legibles sobre negro. |
| Gráficas | Series y etiquetas distinguibles sobre fondo claro. | Series y etiquetas distinguibles sobre fondo oscuro. |

El botón del header mostrará luna para pasar al tema oscuro y sol para pasar al claro, con descripción accesible. La preferencia visual podrá conservarse localmente y usará la preferencia del sistema en la primera visita. No se promete guardarla en el perfil del servidor, porque no se ha identificado ese contrato.

El cambio de tema abarcará menús, ventanas emergentes, formularios, alertas, tablas y ApexCharts. Se definirán estilos de foco, selección, error y deshabilitado para ambos temas. Los colores semánticos, si se utilizan, serán discretos y siempre tendrán una alternativa textual.

### Avatar y menú de usuario

El header mostrará la foto obtenida mediante el backend autorizado. Si no existe foto o falla su carga, mostrará iniciales. Al hacer clic abrirá un menú con **Mi perfil**, **Ajustes de mi cuenta** y **Cerrar sesión**. El nombre, rol y contexto deben ser comprensibles sin saturar el header.

Los ajustes personales incluirán los campos admitidos por el perfil, foto, contraseña y preferencias visuales locales. Los ajustes de negocio tendrán su ubicación administrativa y sus propios permisos. Username, correo, rol y estado se mostrarán según el contrato; no se ofrecerá editarlos desde el perfil propio si la API no lo permite.

### Patrones comunes

- Tablas con filtros, paginación del servidor y estados de carga, vacío y error diferenciados.
- Formularios con etiquetas claras, validación comprensible y prevención de envíos repetidos.
- Confirmaciones con el contexto y consecuencias de acciones financieras o cambios de estado.
- Detalles con pestañas o secciones para evitar pantallas saturadas.
- Navegación por teclado, foco visible, nombres accesibles para iconos y lectura de avisos importantes.
- Mensajes en español y formatos regionales consistentes.
- Protección de cambios sin guardar y actualización explícita ante datos modificados por otro empleado.

## 6. Tecnologías y dependencias

Las versiones concretas se fijarán al iniciar la base del frontend, después de comprobar compatibilidad y soporte. El blueprint no fija números de versión que puedan quedar obsoletos antes de implementar.

| Tecnología o dependencia | Responsabilidad | Momento de incorporación |
| --- | --- | --- |
| React y React DOM | Aplicación web y composición de pantallas. | Base. |
| TypeScript | Tipado estricto de interfaz y contratos. | Base. |
| Vite y su integración React | Desarrollo local y compilación de la aplicación. | Base. |
| Ant Design (`antd`) | Componentes y sistema visual corporativo. | Base. |
| `@ant-design/icons` | Iconografía consistente, incluido sol, luna y navegación. | Base. |
| React Router | Rutas públicas y privadas, navegación y acceso directo a pantallas. | Base. |
| TanStack Query | Consultas, mutaciones y actualización del estado procedente del servidor. | Base. |
| `openapi-typescript` | Tipos derivados del contrato OpenAPI del backend. | Base de integración. |
| Fetch del navegador | Transporte HTTP centralizado, cookies, CSRF y descargas. | Base de integración. |
| Day.js | Fechas y controles de fecha de Ant Design, con configuración regional común. | Formularios y fechas. |
| `apexcharts` y `react-apexcharts` | Gráficas de indicadores, tendencias y comparación. | Analítica. |
| `@zxing/browser` | Lectura por cámara para el ingreso por QR, previa comprobación en navegadores objetivo. | Acceso por QR. |
| Node.js LTS y npm | Entorno y gestión reproducible de dependencias con archivo de bloqueo. | Base. |
| ESLint y Prettier | Calidad estática y formato uniforme. | Base. |
| Vitest y React Testing Library | Pruebas de comportamiento de la interfaz. | Base. |
| MSW | Simulación de la API para pruebas y estados de interfaz. | Base de pruebas. |
| Playwright | Pruebas de recorridos en navegador y verificación integrada. | Base de recorridos, ampliada por rama. |
| axe-core, mediante integración de pruebas | Detección automatizada de problemas de accesibilidad. | Validación transversal. |

La elección de Vite corresponde a una aplicación interna con backend existente. Los formularios usarán inicialmente Ant Design Form; las preferencias y estados locales simples utilizarán las herramientas de React. Las dependencias se agregarán cuando aporten una necesidad concreta.

React Router gestiona la navegación y TanStack Query los datos del servidor. La aplicación utilizará un solo transporte de API y una sola configuración visual. El frontend consumirá HTTP; no se conectará directamente a PostgreSQL, Supabase Storage ni Gmail.

ApexCharts permanece como la elección solicitada para gráficas. Antes de incorporarlo se verificará la licencia aplicable a la versión y al uso previsto; su documentación distingue modalidades Community, Commercial y OEM. No se presupone gratuidad para cualquier distribución o explotación. Referencia: [licencias de ApexCharts](https://apexcharts.com/license/).

## 7. Un repositorio con frontend y backend separados

La estructura objetivo será un repositorio compartido con dos aplicaciones independientes y documentación común:

```text
coach-gym-management-system/
├── backend/                 Aplicación Spring Boot y su infraestructura local
│   ├── src/
│   ├── gradle/
│   ├── gradlew
│   ├── gradlew.bat
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   ├── Dockerfile
│   └── compose.yaml
├── frontend/                Aplicación React y TypeScript
│   ├── src/
│   │   ├── app/             Arranque, navegación y proveedores
│   │   ├── features/        Capacidades del gimnasio
│   │   └── shared/          API, tema y elementos compartidos
│   ├── public/
│   ├── tests/
│   └── package.json
├── docs/
│   ├── blueprint/
│   ├── branch-plans/
│   ├── adr/
│   └── deployment/
├── .github/workflows/
└── AGENTS.md
```

F00 establece la separación `backend/`, `frontend/` y documentación común. Las subcarpetas de código y el `package.json` del frontend siguen siendo estructura futura; todavía no se crea esa aplicación. El backend tiene su wrapper y archivos operativos en su directorio, con accesos de compatibilidad desde la raíz. La configuración privada local y sus datos se trasladan sin cambiar contenido y permanecen excluidos de Git.

Cada aplicación tendrá dependencias, compilación, pruebas y despliegue propios. Los módulos Java conservarán sus límites; las funcionalidades React se organizarán por capacidad de negocio. La documentación compartida mantendrá decisiones, contratos y planes; los artefactos generados no formarán parte de las fuentes.

Las reglas de trabajo distinguen frontend, backend y documentos. Git sigue bajo control del usuario. Los nombres de rama de este documento son propuestas, y su creación o cambio de rama no se ejecuta automáticamente.

## 8. Reglas generales de integración

### Contrato y autoridad del backend

El frontend consumirá la API versionada `/api/v1` y sus contratos OpenAPI publicados en `/v3/api-docs`. Precios, descuentos, cobertura, elegibilidad, permisos, decisiones de acceso, correcciones financieras y estados confirmados pertenecen al backend.

Los tipos se derivarán del contrato comprobado. Generarlos no reemplaza probar el comportamiento ni validar las entradas en ejecución. Cuando el contrato resulte insuficiente para una pantalla, se registrará la necesidad antes de implementarla; no se inventará una ruta o una regla comercial.

### Sesión, CSRF y seguridad

Se utilizará la sesión del servidor con cookies. El flujo incluirá obtención de CSRF, login, consulta del usuario actual y logout. CSRF se obtendrá antes de las mutaciones, también para las rutas públicas de identidad que el backend protege actualmente, y se renovará cuando el ciclo de sesión lo requiera.

La aplicación no guardará contraseñas, cookies de sesión, tokens de identidad ni QR en almacenamiento persistente del navegador. Los tokens de los enlaces de invitación y recuperación llegan actualmente en el fragmento `#token=`: se consumirán en memoria y se retirarán de la dirección visible. No se enviarán a analítica ni se registrarán en errores.

La preferencia visual puede guardarse localmente. Los datos privados y cachés del usuario se limpiarán al cerrar sesión o perder autorización. Un cambio de contraseña obligatorio se atenderá antes de solicitar perfil, fotografías o datos de negocio, porque el backend restringe esas consultas en ese estado.

La interfaz adaptará rutas y acciones a los permisos, mientras el servidor aplicará la autorización. Los errores 401 y 403 deberán distinguir pérdida de sesión, restricción de autoridad y problemas de CSRF conforme al contrato; no se reproducirán automáticamente mutaciones fallidas.

### Sucursal, caché y concurrencia

Las consultas y sus cachés se distinguirán por usuario, alcance, sucursal y filtros. Una selección global de reportes no ampliará las facultades del usuario. Las respuestas tardías del contexto anterior no podrán reemplazar los datos de la sucursal actual.

Se respetarán las versiones exigidas por las acciones del backend. Ante un conflicto, se explicará que los datos cambiaron y se permitirá actualizar la información. Las acciones financieras, invitaciones y envíos no se repetirán automáticamente después de una respuesta incierta.

### Errores, fechas e importes

Los errores `ProblemDetail` se convertirán en mensajes claros, conservando códigos y referencias de correlación cuando ayuden a soporte. La interfaz diferenciará validación, inexistencia, conflicto, límite de solicitudes, archivo demasiado grande y servicio no disponible. Ningún error mostrará SQL, trazas, rutas internas o secretos.

Las fechas de negocio y los instantes se tratarán de forma distinta. La configuración actual usa `America/El_Salvador` y USD; las respuestas que incluyen zona y moneda serán la autoridad para cada reporte. Los rangos de reportes multisucursal son de inicio incluido y fin excluido; el usuario verá un selector comprensible sin perder o duplicar el último día.

Los importes se mostrarán con precisión y moneda explícita. El backend realizará los cálculos comerciales y financieros; el frontend no recalculará el cobro definitivo con aritmética aproximada ni mezclará monedas en un total.

### Archivos y proveedores

Fotos, PDF, PNG y CSV se solicitarán a través del backend con autorización. Se respetarán tipos, tamaños y cabeceras; se liberarán las vistas temporales al dejar de utilizarlas. Los QR y comprobantes son documentos canónicos generados por el backend.

Checkout utilizará la URL alojada que entrega el backend. Volver a la pantalla de éxito o cancelación no confirma un pago. La interfaz consultará el intento y el pago persistido, mostrará espera si corresponde y limitará la consulta periódica. El webhook verificado es procesado por el servidor.

Las devoluciones se presentarán con la semántica existente: registrar una devolución completa no debe prometer que el proveedor ha transferido dinero. No habrá formularios propios de tarjeta ni devolución parcial.

Los envíos de correo se solicitarán como acciones de negocio con destinatarios determinados por el backend. Un estado enviado indicará aceptación por el transporte conforme al contrato, no recepción garantizada en la bandeja del cliente.

### Entornos

El desarrollo local tendrá una ruta de integración reproducible mediante proxy o configuración explícita de origen. El despliegue se diseñará con cookies, CSRF y CORS verificados en navegador; no se asumirá que dominios distintos funcionarán solo por habilitar credenciales.

Se priorizará una entrada de aplicación y API bajo el mismo origen o una topología compatible que se pruebe expresamente. Las variables públicas del frontend contendrán únicamente configuración pública. Los secretos de Stripe, Gmail y Supabase pertenecerán al entorno privado del backend.

## 9. Dependencias de contrato que deben resolverse

La presencia de una operación de negocio no garantiza una pantalla completa de búsqueda e historial. La revisión encontró estas diferencias entre la experiencia deseada y las consultas HTTP actuales:

| ID | Necesidad de la interfaz | Evidencia actual | Decisión para la planificación |
| --- | --- | --- | --- |
| C-01 | Directorio y detalle administrativo de empleados. | Hay invitaciones, búsqueda de asignaciones y acciones por identificador, pero no se encontró un listado general ni detalle administrativo seguro de cuentas. | Definir una consulta acotada para seleccionar empleados, consultar estado y obtener versiones actuales. Invitaciones y asignaciones no representan a todo el personal. Afecta F04. |
| C-02 | Listado de membresías y consulta de períodos anteriores. | El perfil del cliente expone la membresía actual; el controlador de membresías permite detalle por ID y acciones, pero no listado general ni historial de períodos por HTTP. | El flujo inicial puede partir del expediente del cliente. Para cerrar el módulo completo se necesitan consultas de listado e historial autorizadas. Afecta F07 y selección histórica de pagos. |
| C-03 | Recuperar intentos de pago pendientes después de recargar o regresar de Checkout. | Se crea, consulta por ID y cancela un intento, pero no se encontró listado de intentos por cliente o período ni ruta de historial. | Verificar correlación del retorno y recuperación de un intento existente. Si se necesita consulta adicional, debe quedar disponible antes de cerrar F09. Guardar un ID solo en memoria no basta. |
| C-04 | Configuración general del gimnasio. | Se identificaron datos de organización y políticas de acceso, sin API genérica de preferencias o configuración arbitraria. | Limitar ajustes a esas capacidades y a preferencias visuales locales. Afecta F14. |
| C-05 | Exportaciones de análisis. | Hay exportación CSV de auditoría y documentos específicos; no se identificó exportación general de reportes. | F13 entregará visualización y consulta. Una exportación analítica adicional requiere un alcance posterior. |

F00 consolidó estos puntos. Las necesidades C-01, C-02 y cualquier extensión indispensable de C-03 se atenderán en un plan específico de soporte de contratos, con rama `feature/backend-frontend-contracts`, antes de continuar con los módulos de negocio afectados. Ese plan requerirá autorización propia y preservará reglas, privacidad y límites del backend. F00 y la reorganización de ramas no autorizan implementar esas APIs.

Hasta contar con dichos contratos, no se considerarán terminados el directorio del personal, el historial de membresías o la recuperación de intentos que dependan de ellos. Las demás capacidades pueden avanzar de acuerdo con sus dependencias.

## 10. Hoja de ruta por ramas

F00 está **completada y validada**. Esta entrega versiona su organización y la hoja de ruta; el plan, la base y el avance por bloques de F01 se entregan en su propia rama. F02 a F15 siguen **planificadas**. Los detalles técnicos y bloques de cada rama se escribirán antes de implementarla.

| Orden | Rama propuesta | Resultado principal | Dependencia funcional |
| --- | --- | --- | --- |
| F00 | `chore/monorepo-layout` | COMPLETADA: repositorio separado y base contractual revisada. | Backend actual y documentación disponible. |
| F01 | `feature/frontend-foundation-auth` | Aplicación base, diseño común, temas y sesión real. | F00. |
| F02 | `feature/frontend-profile-identity` | Perfil, foto, contraseña, recuperación y aceptación de invitación. | F01. |
| F03 | `feature/frontend-organization-branches` | Administración de sucursales y contexto operativo completo. | F01. |
| F04 | `feature/frontend-staff-administration` | Gestión autorizada del personal. | F02, F03 y C-01 resuelto. |
| F05 | `feature/frontend-clients` | Registro, búsqueda y expediente del cliente. | F01 y F03. |
| F06 | `feature/frontend-plans-promotions` | Catálogo comercial y cobertura de planes. | F03. |
| F07 | `feature/frontend-memberships` | Venta, renovación y ciclo de vida de membresías. | F05, F06 y C-02 resuelto para su cierre completo. |
| F08 | `feature/frontend-payments-receipts` | Cobros manuales, correcciones y comprobantes. | F07. |
| F09 | `feature/frontend-stripe-checkout` | Checkout de prueba y seguimiento de confirmación. | F08 y C-03 resuelto. |
| F10 | `feature/frontend-access-credentials` | Credenciales e ingreso manual y por QR. | F05, F07 y F08. |
| F11 | `feature/frontend-equipment-operations` | Equipos, incidentes y mantenimientos. | F03. |
| F12 | `feature/frontend-notifications-email` | Comunicación y seguimiento de envíos. | F02, F08, F10 y F11. |
| F13 | `feature/frontend-dashboard-analytics` | Dashboards y análisis con ApexCharts. | F07, F08, F10 y F11. |
| F14 | `feature/frontend-audit-policies` | Auditoría y políticas de acceso. | F03 y módulos operativos integrados. |
| F15 | `feature/frontend-integration-release` | Validación integral y frontend listo para desplegar. | Todas las ramas anteriores y contratos requeridos. |

El plan anterior del backend identifica la próxima capacidad como `feature/frontend-foundation-auth`. Aquí se conserva esa capacidad en F01 y se agrega F00 para atender la separación del repositorio y la revisión de contratos. La convención acordada usa `chore/` para organización y herramientas, `feature/` para capacidades y `fix/` para correcciones. B01 es un bloque de F01 y permanece en `feature/frontend-foundation-auth`. El usuario gestiona Git.

### F00 — Organización del repositorio y contratos

**Objetivo:** preparar una base profesional compartida antes de crear pantallas.

- Separar físicamente backend y frontend conforme a la estructura objetivo.
- Mantener operativo el backend, sus pruebas y sus herramientas después del traslado.
- Organizar documentación e instrucciones por aplicación.
- Consolidar la matriz de capacidades, permisos y dependencias C-01 a C-05.
- Acordar el plan de soporte de contratos requerido y la configuración pública de integración.

**Cierre:** el backend conserva su comportamiento, las rutas de trabajo son claras y las dependencias de cada pantalla quedan identificadas. Esta rama no desarrolla módulos de negocio del frontend.

### F01 — Base visual y autenticación

**Objetivo:** disponer de una aplicación navegable, consistente y conectada a la sesión real.

- Crear la base React, TypeScript y Ant Design.
- Incorporar sidebar, header y comportamiento adaptable.
- Implementar temas claro y oscuro con sol y luna.
- Integrar login, consulta de usuario actual, logout y expiración de sesión.
- Mostrar el contexto de sucursal disponible y estados de acceso restringido.
- Preparar avatar con iniciales y menú de cuenta; la foto y sus ajustes se completan en F02.
- Atender el cambio de contraseña obligatorio antes de entrar a los módulos.

**Cierre:** el personal puede autenticarse y navegar por la base visual en ambos temas. Las secciones de negocio futuras no se presentan como funciones terminadas.

### F02 — Perfil e identidad

**Objetivo:** completar la experiencia de cuenta propia y los enlaces públicos de identidad.

- Consultar y editar los datos permitidos del perfil.
- Subir, reemplazar, consultar y eliminar la foto, reflejándola en el header.
- Cambiar contraseña y atender las consecuencias sobre la sesión.
- Solicitar recuperación y completar el proceso con enlace válido.
- Inspeccionar y aceptar una invitación, mostrando expiración o uso previo.
- Presentar estados de cuenta y errores sin revelar información privada.

**Cierre:** todos los recorridos personales e identidad funcionan con el backend y el menú del avatar está completo. No existe registro público del personal.

### F03 — Organización, sucursales y contexto

**Objetivo:** hacer explícita la operación multisucursal.

- Consultar datos de organización y el catálogo permitido de sucursales.
- Crear y editar sucursales, activarlas o desactivarlas con la autoridad requerida.
- Completar el selector de sucursal y el contexto de trabajo.
- Gestionar ausencia de contexto, sucursales inactivas y permisos que cambian.
- Diferenciar vistas de organización, filtros y sucursal donde se registra una operación.

**Cierre:** el usuario identifica dónde trabaja y la interfaz conserva el aislamiento al cambiar de contexto. La administración global queda restringida a quien tiene esa autoridad.

### F04 — Administración del personal

**Objetivo:** gestionar empleados mediante invitaciones y permisos explícitos.

- Consultar el directorio administrativo una vez resuelto C-01.
- Crear, buscar, consultar, reenviar y revocar invitaciones.
- Consultar y administrar asignaciones a sucursales y su historial disponible.
- Cambiar rol y alcance, suspender, reactivar o desactivar según las reglas existentes.
- Solicitar reautenticación para las acciones sensibles que la requieren.
- Mostrar las restricciones sobre operaciones propias y el último administrador de organización.

**Cierre:** un administrador de organización puede gestionar empleados sin copiar identificadores manualmente ni utilizar datos de asignación como sustituto del directorio. Administradores de sucursal y recepcionistas no obtienen administración de identidades.

### F05 — Clientes y expediente

**Objetivo:** facilitar la atención diaria del cliente.

- Buscar y filtrar clientes con paginación.
- Registrar y actualizar los datos admitidos, incluido contacto de emergencia.
- Mostrar expediente con membresía actual, pago del período y último acceso cuando estén disponibles.
- Gestionar fotografía y cambios de estado.
- Integrar accesos hacia membresías, cobros y credenciales conforme se completen esas ramas.

**Cierre:** el personal puede encontrar y atender un cliente desde su expediente, respetando la sucursal y los permisos, con estados vacíos comprensibles.

### F06 — Planes, promociones y cobertura

**Objetivo:** ofrecer un catálogo comercial claro para administración y recepción.

- Consultar planes, precios, duración y estado.
- Crear, editar, activar o desactivar planes con permisos de administración.
- Consultar y gestionar promociones, vigencia y planes elegibles.
- Mostrar y administrar la cobertura de sucursales definida por el backend.
- Diferenciar consulta comercial de modificación administrativa.

**Cierre:** el catálogo es utilizable para vender membresías, sin que la interfaz determine descuentos finales o modifique períodos históricos.

### F07 — Membresías y períodos

**Objetivo:** completar el ciclo de vida de la membresía del cliente.

- Consultar membresías y su detalle; iniciar operaciones desde el expediente.
- Crear y renovar usando planes y promociones disponibles.
- Mostrar precio, vigencia y cobertura guardados en el período.
- Congelar, reactivar y cancelar con motivo y condiciones del backend.
- Consultar períodos anteriores cuando C-02 esté resuelto.
- Orientar al empleado hacia el pago sin confundir membresía creada con período pagado.

**Cierre:** venta, renovación y cambios de estado funcionan; la historia se conserva y las consultas necesarias están disponibles. Un cambio posterior del plan no altera la cobertura histórica mostrada.

### F08 — Pagos, correcciones y comprobantes

**Objetivo:** registrar y consultar cobros con claridad financiera.

- Registrar pagos manuales según los métodos y condiciones permitidos.
- Buscar pagos y mostrar relación con cliente, membresía, período y sucursal.
- Consultar estado, corrección e historial disponible.
- Registrar anulación o devolución completa con permisos y motivos exigidos.
- Generar, consultar y descargar el comprobante PDF canónico.
- Preparar la acción de correo del comprobante, completada en F12.

**Cierre:** los cobros y sus correcciones se reflejan según el backend; los comprobantes se obtienen con autorización y se evita duplicar operaciones ante incertidumbre.

### F09 — Checkout de Stripe

**Objetivo:** completar el pago de prueba mediante Checkout alojado.

- Crear un intento para un período seleccionado y abrir la URL del proveedor.
- Ofrecer pantallas de retorno por éxito o cancelación.
- Consultar el estado real del intento y el pago confirmado por el backend.
- Mostrar pendiente, fallo, expiración y cancelación conforme a los estados existentes.
- Recuperar el seguimiento después de volver o recargar, con C-03 resuelto.
- Permitir cancelación explícita cuando el backend la admite.

**Cierre:** el flujo distingue retorno del navegador, intento de pago y pago confirmado. Se verifica con pruebas controladas y permanece en modo de prueba.

### F10 — Credenciales y control de acceso

**Objetivo:** ofrecer una pantalla de recepción rápida para registrar ingresos.

- Emitir, consultar, descargar, reemplazar y revocar credenciales.
- Consultar el historial de la credencial disponible.
- Registrar acceso manual y acceso por QR desde lector o cámara.
- Mostrar ingreso permitido, denegado o duplicado con su explicación autorizada.
- Consultar registros por los filtros permitidos.
- Ofrecer alternativa manual cuando no haya cámara o permiso para utilizarla.

**Cierre:** la pantalla recibe una decisión del backend considerando cliente, membresía, pago, cobertura y política de sucursal. No guarda el QR ni decide el acceso localmente.

### F11 — Equipos, incidentes y mantenimientos

**Objetivo:** cubrir la operación de los equipos por sucursal.

- Consultar y administrar categorías y equipos según permisos.
- Mostrar disponibilidad y cambios de estado permitidos.
- Reportar incidentes y consultar su detalle e historial.
- Investigar, priorizar y resolver incidentes con autoridad suficiente.
- Programar, actualizar, iniciar, completar o cancelar mantenimientos.
- Navegar entre equipo, incidente y mantenimiento sin perder el contexto.

**Cierre:** los recorridos operativos y sus permisos funcionan. Reportar un incidente no concede al recepcionista facultades para administrar equipos o resolver mantenimientos.

### F12 — Notificaciones y correo transaccional

**Objetivo:** integrar la comunicación existente con la actividad del usuario.

- Completar la campana del header con conteo de notificaciones propias.
- Consultar notificaciones y marcarlas como leídas individualmente o en conjunto.
- Solicitar envío de comprobantes y credenciales desde sus pantallas.
- Consultar entregas, intentos y estados que el backend permite al usuario.
- Ofrecer reintento explícito cuando está permitido y explicar estados inciertos.
- Diferenciar estas entregas del flujo privado de enlaces de identidad de F02 y F04.

**Cierre:** la interfaz refleja el estado de comunicación y respeta destinatarios, límites y autoridad. No ofrece un formulario genérico de envío libre de correo.

### F13 — Dashboards, reportes y analítica

**Objetivo:** transformar los reportes disponibles en información útil mediante ApexCharts.

- Construir el inicio operativo según rol y alcance.
- Presentar indicadores y filtros de fechas y sucursales autorizadas.
- Mostrar tendencia financiera, tendencia de accesos y comparación de sucursales para administradores autorizados.
- Adaptar las gráficas a ambos temas, con leyendas, unidades y alternativa tabular.
- Diferenciar cero, ausencia de datos, carga y fallo de consulta.
- Mantener cada moneda y contexto claramente identificados.

**Cierre:** todas las cifras provienen de reportes autorizados y coinciden con su semántica. Recepción recibe el resumen permitido de membresías y acceso, sin consultar tendencias o comparación restringidas.

### F14 — Auditoría y políticas

**Objetivo:** completar la trazabilidad y las políticas de acceso existentes.

- Buscar y consultar auditoría según alcance autorizado.
- Exportar el CSV de auditoría respetando filtros y límites.
- Consultar y administrar la política global de pago para acceso cuando el permiso lo admite.
- Consultar y gestionar la política particular de sucursal y su herencia.
- Mostrar conflictos y motivos de cambios con mensajes claros.
- Mantener la configuración dentro del alcance real identificado en C-04.

**Cierre:** los administradores consultan trazabilidad y políticas con el alcance correcto; los recepcionistas consultan la información operativa que la API admite, sin acceso administrativo a auditoría.

### F15 — Integración completa y preparación de entrega

**Objetivo:** cerrar la aplicación como un producto integrado y listo para desplegar.

- Validar recorridos completos con al menos dos sucursales y los tres perfiles autorizados.
- Probar permisos, cambio de contexto, expiración, concurrencia, errores y recuperación de flujos.
- Revisar todos los módulos en claro y oscuro, escritorio, tablet y móvil.
- Verificar accesibilidad, rendimiento, navegación directa y compilación de producción.
- Reconciliar indicadores con los datos del backend y cerrar dependencias de contrato.
- Preparar guías de arranque, demostración, configuración pública y despliegue.
- Preparar las páginas públicas de presentación, privacidad y términos necesarias para las URLs previstas, con contenido revisado por el propietario.
- Realizar las comprobaciones externas controladas que se autoricen expresamente para Gmail, Stripe de prueba y almacenamiento.

**Cierre:** el frontend y el backend funcionan juntos en un entorno reproducible; los resultados de pruebas y las limitaciones quedan documentados. El despliegue real es una acción posterior y no se ejecuta por completar esta rama.

## 11. Especificación general de dashboards y gráficas

La galería de [ApexCharts para React](https://apexcharts.com/react-chart-demos/) será referencia de componentes de gráfica. Las gráficas se seleccionarán según la pregunta del usuario y los datos existentes.

| Vista | Presentación prevista | Fuente actual y autorización |
| --- | --- | --- |
| Inicio operativo | Tarjetas y resúmenes de membresías y acceso. | `/api/v1/reporting/summary`; respuesta limitada por rol y alcance. |
| Resumen administrativo | Indicadores comerciales, financieros y operativos disponibles. | Resumen multisucursal autorizado para `ADMIN`. |
| Ingresos cobrados | Línea o columnas por período, separadas por moneda. | `/api/v1/reporting/financial-trend`; `ADMIN` autorizado. |
| Accesos | Columnas o líneas de resultados diarios según las series devueltas. | `/api/v1/reporting/access-trend`; `ADMIN` autorizado, granularidad diaria. |
| Comparación de sucursales | Barras comparativas y tabla complementaria. | `/api/v1/reporting/branches/comparison`; `ADMIN`, entre 2 y 20 sucursales autorizadas. |
| Distribuciones operativas | Tarjetas o barras únicamente para agregados presentes en la respuesta. | Campos efectivamente disponibles en el resumen administrativo. |

El endpoint anterior `/api/v1/reporting/dashboard` está restringido a `ADMIN + ORGANIZATION` y se conserva por compatibilidad. El frontend nuevo tomará como base el resumen multisucursal, cuya selección y permisos son explícitos.

No se mostrarán ganancias, gastos, metas, ocupación por hora, predicciones, conversión o retención como métricas disponibles sin una fuente del backend que las sustente. Los intentos de pago no se sumarán como ingreso confirmado; las correcciones y monedas seguirán la definición del reporte.

Cada gráfica tendrá título, período, contexto y unidades; las etiquetas y tooltips respetarán el tema. Los datos ausentes no se convertirán silenciosamente en ceros. Los filtros y rangos seguirán los límites de la API y no se cargarán todos los registros operativos para calcular un reporte en el navegador.

## 12. Recorridos completos que definen el producto

Estos recorridos serán la referencia de aceptación entre ramas:

1. **Recepción diaria:** iniciar sesión, seleccionar sucursal, buscar cliente, revisar su situación, registrar ingreso y entender la decisión.
2. **Cliente nuevo:** registrar cliente, seleccionar plan y promoción admitida, crear membresía, registrar pago y obtener comprobante.
3. **Renovación:** consultar membresía, renovar período, revisar precio y cobertura persistidos, cobrar y consultar el nuevo estado.
4. **Pago con tarjeta de prueba:** iniciar Checkout, regresar, esperar la confirmación del backend y consultar pago y comprobante.
5. **Acceso por QR:** obtener credencial canónica, leerla, registrar resultado y consultar historial sin conservar el contenido del token.
6. **Gestión de empleado:** invitar, aceptar enlace, iniciar sesión, administrar asignaciones y verificar pérdida de acceso cuando corresponde.
7. **Cuenta propia:** actualizar nombre y foto, cambiar tema, cambiar contraseña y atender la sesión resultante.
8. **Operación de equipo:** reportar incidente, gestionar investigación y mantenimiento con el rol correspondiente y revisar trazabilidad.
9. **Supervisión:** consultar reportes por contexto, comparar sucursales permitidas y consultar o exportar auditoría con autoridad suficiente.
10. **Aislamiento:** cambiar de sucursal y comprobar que datos, formularios, descargas y respuestas pendientes respetan el contexto y permisos nuevos.

## 13. Calidad y criterios comunes de cierre

La calidad se comprobará en cada rama, además de la revisión integral final. Una pantalla no está terminada solo porque su diseño se ve correcto.

Para cerrar una rama deberán cumplirse los resultados funcionales autorizados, la conexión con contratos reales, estados de carga y error, permisos, ambos temas, comportamiento adaptable y pruebas relevantes. No habrá secretos, datos personales de prueba reales, archivos privados ni datos de demostración mezclados con producción.

La validación combinará pruebas de interfaz con API simulada y recorridos integrados contra el backend y PostgreSQL en un entorno controlado. Los servicios externos se simularán en la suite normal. Las pruebas que envían correo o utilizan proveedores reales quedarán fuera de la validación automática ordinaria y requerirán autorización expresa.

La revisión cubrirá especialmente sesiones y CSRF, cuentas restringidas, aislamiento entre sucursales, doble envío, conflictos de versión, resultados inciertos, descargas privadas, privacidad de enlaces y exactitud de reportes. Las pruebas de accesibilidad automatizadas se complementarán con teclado, foco, contraste y lectura de estados.

Cada rama tendrá evidencia de las comprobaciones de tipos, calidad estática, pruebas y compilación del frontend. Si afecta el backend o su organización, incluirá sus verificaciones apropiadas y Spring Modulith cuando corresponda. El traslado de F00 deberá pasar el quality gate completo del backend; F15 cerrará con validación conjunta. Los comandos se concretarán en los planes individuales conforme a la estructura vigente.

Los errores de Docker, conectividad del entorno o cuotas externas se reportarán con su evidencia y clasificación. No se debilitarán reglas ni pruebas para presentar un cierre artificial.

### Frontend completo para este alcance

- Todas las capacidades de la hoja de ruta están implementadas y conectadas a contratos reales.
- C-01, C-02 y la recuperación de intentos de C-03 están resueltos y probados; C-04 y C-05 mantienen su alcance definido.
- Los tres perfiles de personal tienen una experiencia acorde con sus permisos y sucursales.
- Los recorridos completos funcionan sin copiar UUID manualmente ni depender de memoria temporal para recuperar operaciones.
- Light y dark funcionan en toda la aplicación, incluidas gráficas y ventanas emergentes.
- El avatar utiliza foto autorizada o iniciales y su menú permite ajustes y logout reales.
- Se verifica precisión financiera, persistencia de resultados y protección de datos.
- La aplicación pasa sus pruebas, compila y cuenta con instrucciones reproducibles.
- La integración local queda validada. Toda comprobación externa o de despliegue pendiente se identifica expresamente y no se presenta como realizada.
- No quedan acciones visibles que aparenten una capacidad inexistente ni dependencias necesarias sin resolver.

## 14. Entrega y evolución posterior

El plan existente contempla frontend en Vercel, backend en Render y datos y almacenamiento privado en Supabase. Gmail API y Stripe de prueba continúan detrás del backend. Es el objetivo de infraestructura documentado, sujeto a comprobar condiciones vigentes, cookies, orígenes, URLs y límites al preparar el despliegue.

La terminación funcional local, las pruebas externas controladas y la validación desplegada se registrarán como hitos diferentes. Una prueba simulada no certifica la integración desplegada. El frontend no requerirá credenciales privadas de proveedores ni administración de infraestructura desde sus pantallas.

Después del cierre y de una autorización específica podrá ejecutarse el despliegue, validar enlaces públicos de identidad, fotografías, documentos, correos y webhook de prueba, y registrar la evidencia final. Las futuras funcionalidades comerciales se evaluarán con experiencia de uso y nuevas decisiones de producto.

## 15. Protocolo para continuar

Antes de implementar cada rama se revisará el repositorio vigente y se preparará su plan individual. Ese plan identificará alcance, bloques, contratos, permisos, dependencias y pruebas. Solo se implementará el bloque que el usuario autorice, seguido de su reporte y detención.

F00 cuenta con [plan individual](../branch-plans/frontend-repository-contracts-plan.md) y [reporte de ejecución](../branch-plans/frontend-repository-contracts-report.md). **F01 — Base visual y autenticación** incorpora su plan detallado y la base de aplicación en `feature/frontend-foundation-auth`. El avance y la evidencia de cada bloque se registran en esa entrega; cada bloque requiere autorización individual. El soporte pendiente del backend se planificará en `feature/backend-frontend-contracts` antes de los módulos de negocio afectados.

Las ramas se marcarán como terminadas por evidencia de comportamiento y validación. Este documento se actualizará cuando cambien contratos o decisiones de producto. No se asignan porcentajes de implementación a ramas aún no iniciadas.

## Apéndice A. Estado reconstruido en la planificación inicial

Estas observaciones corresponden al 13 de octubre, antes de implementar F00. El estado posterior al traslado y su validación están registrados en [el reporte F00](../branch-plans/frontend-repository-contracts-report.md).

| Aspecto | Evidencia o conclusión de esta sesión |
| --- | --- |
| Rama actual | `feature/multi-branch-hardening-code-only`. |
| Estado inicial | `git status --short` sin cambios visibles; respecto de `main`, una confirmación exclusiva a cada lado. No se sincronizó Git. |
| Módulos actuales | `auth`, `user`, `organization`, `client`, `plan`, `promotion`, `membership`, `payment`, `accesscredential`, `access`, `equipment`, `maintenance`, `notification`, `reporting`, `audit`, `configuration` y `shared`. |
| Contratos entre módulos | Contratos y eventos del paquete raíz del módulo; interfaces compartidas nombradas para web, storage, security e identity-email. |
| Capas | Dominio, aplicación, infraestructura y web; DTOs y contratos inmutables, inyección por constructor y transacciones en servicios. |
| Autenticación | Sesión del servidor, CSRF, CORS explícito y autorización por rol, alcance y asignaciones. Estado de seguridad verificado en solicitudes. |
| Persistencia | PostgreSQL, Flyway V1 a V43 presentes y ordenados, Hibernate en validación. No se cambió el esquema ni se ejecutó reparación. |
| Integridad | Versiones, invariantes persistidas, idempotencia de proveedor, historiales y eventos de auditoría; su evidencia previa está en código, pruebas y plan. |
| API | `/api/v1`, `ProblemDetail` con código y correlación cuando está disponible, OpenAPI y respuestas privadas de archivos. |
| Reportes | Resumen por alcance, tendencias y comparación autorizada; dashboard anterior restringido a administrador de organización. |
| Pruebas | MockMvc, PostgreSQL con Testcontainers, verificaciones OpenAPI, arquitectura y Spring Modulith; CI con Java 21 y `clean check`. |
| Redes externas | Adaptadores de infraestructura para Stripe, Gmail y almacenamiento; la suite normal usa dobles. Gmail live tiene tarea separada y opt-in. |
| Estado de planes | Los ocho bloques del hardening multisucursal están marcados como completados; el siguiente trabajo principal documentado es frontend. |
| Pendientes relevantes | Integración completa desde la interfaz y consultas C-01 a C-03 para la experiencia prevista. |
| Diferencias documentales | Blueprint original del backend e índice de planes ausentes; mapa de arquitectura raíz más antiguo que los módulos actuales. |
| Higiene | Se observaron directorios locales de configuración, compilación y datos ya existentes; no se inspeccionaron secretos ni se eliminaron archivos del usuario. |
| Validación en esta sesión | Inspección de repositorio y documentación. No se ejecutaron Gradle, pruebas de producto ni llamadas a proveedores del gimnasio. |
| Dictamen | Hay base suficiente para este blueprint. La implementación debe resolver las dependencias antes del cierre de sus módulos y verificar nuevamente contratos actuales. |

La sesión queda preparada para continuar con solicitudes en cualquier estructura. No se inició implementación de frontend ni backend.

Este archivo se creó en `docs/blueprint/frontend-blueprint.md`. F00 retira la exclusión global de Markdown para permitir versionar la documentación e instrucciones del proyecto. Se mantienen las exclusiones de configuración privada, datos y salidas generadas. Git permanece bajo control del usuario.

## Apéndice B. Referencias de tecnologías

Fuentes oficiales consultadas el 13 de octubre de 2026. Estas referencias orientan la implementación; las decisiones concretas de versiones se revisarán al iniciar cada dependencia.

- [React con TypeScript](https://react.dev/learn/typescript).
- [Vite](https://vite.dev/guide/).
- [Temas de Ant Design](https://ant.design/docs/react/customize-theme/).
- [React Router](https://reactrouter.com/start/declarative/installation).
- [TanStack Query](https://tanstack.com/query/latest/docs/framework/react/overview).
- [OpenAPI TypeScript](https://openapi-ts.dev/introduction).
- [Day.js](https://day.js.org/docs/en/installation/installation).
- [ApexCharts para React](https://apexcharts.com/react-chart-demos/) y [licencias](https://apexcharts.com/license/).
- [ZXing para navegador](https://github.com/zxing-js/browser).
- [Vitest](https://vitest.dev/guide/).
- [MSW](https://mswjs.io/docs/).
- [Playwright](https://playwright.dev/docs/intro).
