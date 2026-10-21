# Matriz Rol–Permiso

Fuente: *Propuesta DOSW · CampusSeguro* (§5 actores y §8 funcionalidades). Cumple [RN-MS01-14](1_requirements.md#rn-ms01-14): catálogo y matriz definidos por el sistema, versionados en el código y no editables en ejecución.

## Índice de Contenido
1. [Convenciones](#convenciones)
2. [Matriz por módulo](#matriz-por-modulo)
3. [Reglas de aplicación](#reglas-de-aplicacion)
4. [Supuestos a validar](#supuestos-a-validar)

---

<a id="convenciones"></a>
## Convenciones

* **Código de permiso:** `<ms>:<recurso>:<acción>` (p. ej. `ms02:building:write`). Estos códigos son los que devuelve `GET /roles` en `permisos`.
* **Roles (8):** ESTUDIANTE · PROFESOR · ADMINISTRATIVO · BRIGADISTA · COORDINADOR · ORGANIZADOR · ADMIN · AUDITOR. Columnas: **EST** · **PROF** · **ADMTV** · **BRIG** · **COORD** · **ORG** · **ADMIN** · **AUD** (ADMTV = ADMINISTRATIVO).
* **✅** = permitido. Celda vacía = denegado (denegado por defecto).
* **COM** (comunidad en campus) = EST, PROF, ADMTV, BRIG, COORD, ORG.

---

<a id="matriz-por-modulo"></a>
## Matriz por módulo

### MS01 · Identidad y acceso

| Permiso | Descripción | EST | PROF | ADMTV | BRIG | COORD | ORG | ADMIN | AUD |
|---|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| `ms01:profile:read` | Consultar el propio perfil | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `ms01:profile:write` | Actualizar el propio perfil (nombres y apellidos) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `ms01:password:change` | Cambiar la propia contraseña | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `ms01:session:logout` | Cerrar sesión | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `ms01:role:read_own` | Consultar los propios roles y sus permisos | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `ms01:role:read_all` | Consultar el catálogo completo de roles y permisos |  |  |  |  |  |  | ✅ |  |
| `ms01:user:read` | Listar, buscar y consultar usuarios |  |  |  |  |  |  | ✅ |  |
| `ms01:user:status` | Activar o inactivar usuarios |  |  |  |  |  |  | ✅ |  |
| `ms01:user:roles` | Agregar o retirar roles a usuarios |  |  |  |  |  |  | ✅ |  |

### MS02 · Campus y seguridad

| Permiso | Descripción | EST | PROF | ADMTV | BRIG | COORD | ORG | ADMIN | AUD |
|---|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| `ms02:campus:read` | Consultar edificios, pisos, espacios, puntos de encuentro y zonas seguras | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `ms02:route:read` | Consultar la ruta de evacuación recomendada | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `ms02:preventive_info:read` | Consultar información preventiva por edificio o espacio | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `ms02:building:write` | Crear, modificar e inactivar edificios y pisos |  |  |  |  |  |  | ✅ |  |
| `ms02:space:write` | Gestionar espacios |  |  |  |  |  |  | ✅ |  |
| `ms02:meeting_point:write` | Gestionar puntos de encuentro |  |  |  |  |  |  | ✅ |  |
| `ms02:safe_zone:write` | Gestionar zonas seguras |  |  |  |  |  |  | ✅ |  |
| `ms02:route:write` | Asociar espacios con rutas de evacuación |  |  |  |  |  |  | ✅ |  |
| `ms02:route_status:write` | Cambiar el estado de una ruta (disponible, bloqueada, restringida) |  |  |  |  | ✅ |  | ✅ |  |
| `ms02:qr:manage` | Asociar códigos QR a zonas o edificios (opcional) |  |  |  |  |  |  | ✅ |  |

### MS03 · Alertas y emergencias

| Permiso | Descripción | EST | PROF | ADMTV | BRIG | COORD | ORG | ADMIN | AUD |
|---|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| `ms03:alert:receive` | Recibir alertas y confirmar su recepción | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |  |  |
| `ms03:alert:manage` | Crear, programar, activar, segmentar, modificar y finalizar alertas de cualquier tipo |  |  |  |  | ✅ |  |  |  |
| `ms03:alert:manage_drill` | Igual que el anterior, solo para alertas de tipo SIMULACRO (originadas desde MS06) |  |  |  |  |  | ✅ |  |  |
| `ms03:alert:read_history` | Consultar el historial de alertas |  |  |  |  | ✅ | ✅ |  | ✅ |

### MS04 · Evacuación y check-in

| Permiso | Descripción | EST | PROF | ADMTV | BRIG | COORD | ORG | ADMIN | AUD |
|---|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| `ms04:checkin:write` | Reportar y actualizar el propio estado y punto de encuentro | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |  |  |
| `ms04:checkin:read_own` | Consultar el propio estado vigente | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |  |  |
| `ms04:assistance:request` | Solicitar asistencia desde el check-in | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |  |  |
| `ms04:dashboard:read` | Ver consolidación por punto de encuentro, personas pendientes y dashboard |  |  |  | ✅ | ✅ | ✅ |  | ✅ |
| `ms04:history:read` | Consultar el histórico de check-ins de un evento |  |  |  |  | ✅ | ✅ |  | ✅ |

### MS05 · Brigadistas e incidentes

| Permiso | Descripción | EST | PROF | ADMTV | BRIG | COORD | ORG | ADMIN | AUD |
|---|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| `ms05:brigadier:assign` | Asignar brigadistas a edificios, zonas o puntos de encuentro |  |  |  |  | ✅ |  |  |  |
| `ms05:panel:read_own` | Consultar las propias zonas y tareas asignadas |  |  |  | ✅ |  |  |  |  |
| `ms05:assistance:read` | Consultar solicitudes de asistencia |  |  |  | ✅ | ✅ |  |  |  |
| `ms05:assistance:register` | Registrar una solicitud de asistencia |  |  |  | ✅ | ✅ |  |  |  |
| `ms05:assistance:assign` | Asignar una solicitud a un brigadista y cerrarla |  |  |  |  | ✅ |  |  |  |
| `ms05:assistance:update_status` | Cambiar el estado de atención (en atención, resuelta) |  |  |  | ✅ | ✅ |  |  |  |
| `ms05:incident:report` | Registrar un incidente con descripción y evidencia |  | ✅ |  | ✅ | ✅ |  |  |  |
| `ms05:incident:read` | Consultar y dar seguimiento a incidentes |  |  |  | ✅ | ✅ |  |  | ✅ |
| `ms05:incident:update` | Actualizar un incidente |  |  |  | ✅ | ✅ |  |  |  |

### MS06 · Simulacros

| Permiso | Descripción | EST | PROF | ADMTV | BRIG | COORD | ORG | ADMIN | AUD |
|---|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| `ms06:drill:manage` | Crear y editar simulacros, escenario y participantes |  |  |  |  |  | ✅ |  |  |
| `ms06:drill:execute` | Iniciar y terminar un simulacro |  |  |  |  |  | ✅ |  |  |
| `ms06:drill:evaluate` | Evaluar un simulacro |  |  |  |  |  | ✅ |  |  |
| `ms06:drill:read` | Consultar simulacros y su seguimiento |  |  |  |  | ✅ | ✅ |  | ✅ |
| `ms06:drill:compare_report` | Comparar simulacros y consultar el informe final |  |  |  |  | ✅ | ✅ |  | ✅ |

### MS07 · Formación y asistente IA

| Permiso | Descripción | EST | PROF | ADMTV | BRIG | COORD | ORG | ADMIN | AUD |
|---|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| `ms07:content:read` | Consultar contenidos del centro de aprendizaje | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |  |  |
| `ms07:quiz:take` | Realizar evaluaciones y consultar el propio progreso | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |  |  |
| `ms07:tutor:use` | Usar el tutor CampusSeguro | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |  |  |
| `ms07:content:manage` | Gestionar contenidos y la información que alimenta al tutor |  |  |  |  |  |  | ✅ |  |

### MS08 · Analítica y auditoría

| Permiso | Descripción | EST | PROF | ADMTV | BRIG | COORD | ORG | ADMIN | AUD |
|---|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| `ms08:indicators:read` | Consultar indicadores operativos e históricos |  |  |  |  | ✅ | ✅ |  | ✅ |
| `ms08:audit:read` | Consultar la auditoría |  |  |  |  |  |  | ✅ | ✅ |

---

<a id="reglas-de-aplicacion"></a>
## Reglas de aplicación

* **MR-01 · Multi-rol:** un usuario con varios roles tiene la unión de sus permisos (RN-MS01-13). Ningún rol hereda a otro.
* **MR-02 · Alcance propio:** los permisos `*_own` y `profile:*` solo aplican a recursos del usuario identificado por `X-User-Id`.
* **MR-03 · Alcance de brigadista:** `dashboard:read`, `assistance:read` y `assistance:update_status` del BRIG se limitan a sus zonas o solicitudes asignadas (lo valida MS04/MS05).
* **MR-04 · Simulacros:** `alert:manage_drill` solo opera sobre alertas de tipo SIMULACRO. Las llamadas entre servicios (MS06 → MS03) propagan las cabeceras de identidad originales, por eso el ORG necesita ese permiso.
* **MR-05 · Sin autoescalamiento:** nadie modifica sus propios roles ni, siendo ADMIN, su propio estado (RN-MS01-12, RN-MS01-19).
* **MR-06 · Vigencia:** un cambio de roles aplica en el siguiente token emitido (RN-MS01-09).
* **MR-07 · Verificación:** cada microservicio autoriza sus endpoints leyendo `X-User-Role` (roles separados por coma) contra esta matriz.

---

<a id="supuestos-a-validar"></a>
## Supuestos a validar

La propuesta no los define de forma explícita; se asumieron así:

1. El ADMIN no tiene permisos operativos de emergencia (alertas, check-in, brigadas, simulacros). Si también es parte de la comunidad, se le asigna un rol adicional.
2. El AUDITOR es solo lectura: consulta históricos, indicadores y registros; sin check-in ni alertas.
3. El COORDINADOR puede cambiar el estado de las rutas (incidente de ruta bloqueada), además del ADMIN.
4. El PROFESOR puede reportar incidentes (la propuesta indica que “puede reportar su estado o incidentes”).
5. Solo el ADMIN gestiona contenidos del centro de aprendizaje y la base de conocimiento del tutor.
6. Todos los roles de comunidad (COM) pueden hacer check-in, recibir alertas y usar el tutor.