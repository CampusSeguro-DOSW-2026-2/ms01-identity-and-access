# Especificación del Modelo de Datos

Base de datos PostgreSQL propia de MS01 ([RNF-MS01-01](1_requirements.md)). Ningún otro microservicio la lee ni la modifica; se accede a ella solo mediante la API.

## Índice de Contenido
1. [Alcance](#alcance)
2. [Convenciones](#convenciones)
3. [Tablas](#tablas)
    - [usuarios](#usuarios)
    - [usuario_rol](#usuario_rol)
    - [codigos_recuperacion](#codigos_recuperacion)
4. [Relaciones](#relaciones)
5. [Correspondencia API ↔ columnas](#correspondencia-api)
6. [Reglas de integridad](#reglas-de-integridad)
7. [Operaciones transaccionales y consultas](#operaciones)
8. [Datos iniciales](#datos-iniciales)

---

<a id="alcance"></a>
## Alcance

**Se almacena:** usuarios, la asignación usuario–rol y los códigos de recuperación de contraseña.

**No se almacena, y por qué:**
* **Auditoría:** la delega MS08 ([RNF-MS01-10](1_requirements.md)).
* **Catálogo de roles:** es el enum `Rol` del código, fuente única junto con sus permisos; no existe tabla de roles ([RN-MS01-14](1_requirements.md#rn-ms01-14)).
* **Permisos:** la matriz rol–permiso está versionada en el código ([RN-MS01-14](1_requirements.md#rn-ms01-14), [`3_matriz-roles-permisos.md`](3_matriz-roles-permisos.md)).
* **Sesiones y tokens:** el JWT es stateless y no se persiste ([RN-MS01-07](1_requirements.md#rn-ms01-07), [RN-MS01-10](1_requirements.md#rn-ms01-10), [`4_jwt-spec.md`](4_jwt-spec.md)).
* **Datos de otros dominios** (alertas, check-ins, etc.): pertenecen a sus propios microservicios.

---

<a id="convenciones"></a>
## Convenciones

* Nombres de tablas y columnas en `snake_case` y en español, alineados con el contrato OpenAPI.
* Fechas y horas en `TIMESTAMPTZ`, guardadas en UTC.
* Identificador de usuario: texto `USR-` + número secuencial (desde 1000), generado por el servicio (ejemplo `USR-1034`).
* El correo se guarda en minúsculas y sin espacios en los extremos ([RG-01](2_forms-inputs.md#reglas-generales-de-entrada)).
* Los roles se guardan como texto con `@Enumerated(EnumType.STRING)` (nunca `ORDINAL`) y la base de datos los valida con un `CHECK`. Cambiar el catálogo exige actualizar el enum y el `CHECK` en la misma migración y Pull Request.
* No hay borrado físico de usuarios: se inactivan ([RN-MS01-17](1_requirements.md#rn-ms01-17)).
* El esquema se versiona con migraciones (Flyway o Liquibase); no se usa generación automática de esquema en producción.

---

<a id="tablas"></a>
## Tablas

<a id="usuarios"></a>
### usuarios
Una fila por persona registrada.

| Columna | Tipo | Nulo | Por defecto | Restricciones | Descripción |
|---|---|---|---|---|---|
| `id` | VARCHAR(20) | No | — | PK | Identificador `USR-<n>`. |
| `primer_nombre` | VARCHAR(30) | No | — | 2 a 30 caracteres | [F-01](2_forms-inputs.md#f-01). |
| `segundo_nombre` | VARCHAR(30) | Sí | NULL | Si existe, 2 a 30 | Opcional. |
| `primer_apellido` | VARCHAR(30) | No | — | 2 a 30 caracteres | |
| `segundo_apellido` | VARCHAR(30) | Sí | NULL | Si existe, 2 a 30 | Opcional. |
| `correo` | VARCHAR(100) | No | — | UNIQUE; `correo = lower(correo)` | Correo institucional. Inmutable tras el registro ([RN-MS01-15](1_requirements.md#rn-ms01-15)). |
| `contrasena_hash` | VARCHAR(100) | No | — | — | Hash BCrypt ([RN-MS01-04](1_requirements.md#rn-ms01-04)). Nunca se devuelve por la API. |
| `estado` | VARCHAR(10) | No | `'ACTIVO'` | CHECK IN (`ACTIVO`, `INACTIVO`) | [RN-MS01-17](1_requirements.md#rn-ms01-17). |
| `creado_en` | TIMESTAMPTZ | No | `now()` | — | Fecha de registro. |
| `actualizado_en` | TIMESTAMPTZ | No | `now()` | — | Se actualiza en cada modificación (perfil, estado, contraseña). |

**Índices:** PK `id`; único `correo`; `idx_usuarios_estado (estado)`.

<a id="usuario_rol"></a>
### usuario_rol
Asignación de roles del enum `Rol`. Un usuario puede tener varios ([RN-MS01-13](1_requirements.md#rn-ms01-13)).

| Columna | Tipo | Nulo | Por defecto | Restricciones | Descripción |
|---|---|---|---|---|---|
| `usuario_id` | VARCHAR(20) | No | — | PK (con `rol`); FK → `usuarios.id` | |
| `rol` | VARCHAR(20) | No | — | PK (con `usuario_id`); CHECK IN (`ESTUDIANTE`, `PROFESOR`, `ADMINISTRATIVO`, `BRIGADISTA`, `COORDINADOR`, `ORGANIZADOR`, `ADMIN`, `AUDITOR`) | Código del rol. |
| `asignado_en` | TIMESTAMPTZ | No | `now()` | — | Fecha de asignación. |

**Índices:** PK compuesta (`usuario_id`, `rol`), que impide roles repetidos; `idx_usuario_rol_rol (rol)` para el filtro por rol.

<a id="codigos_recuperacion"></a>
### codigos_recuperacion
Códigos de un solo uso para restablecer la contraseña ([RF-MS01-08](1_requirements.md#rf-ms01-08)).

| Columna | Tipo | Nulo | Por defecto | Restricciones | Descripción |
|---|---|---|---|---|---|
| `id` | BIGINT | No | autogenerado | PK | |
| `usuario_id` | VARCHAR(20) | No | — | FK → `usuarios.id` | Dueño del código. |
| `codigo_hash` | VARCHAR(100) | No | — | — | Hash del código de 6 dígitos; el código en claro nunca se guarda. |
| `expira_en` | TIMESTAMPTZ | No | — | — | Creación + vigencia (15 min, configurable; [RN-MS01-21](1_requirements.md#rn-ms01-21)). |
| `usado_en` | TIMESTAMPTZ | Sí | NULL | — | NULL = no usado. Con valor = consumido o invalidado. |
| `creado_en` | TIMESTAMPTZ | No | `now()` | — | |

**Índices:** PK `id`; `idx_codigos_usuario (usuario_id, expira_en)`.
**Vigente** = `usado_en IS NULL` y `expira_en > now()`.

---

<a id="relaciones"></a>
## Relaciones

| Relación | Cardinalidad | Borrado |
|---|---|---|
| `usuarios` → `usuario_rol` | 1 a N (mínimo 1 fila por usuario, validado en el servicio) | RESTRICT |
| `usuarios` → `codigos_recuperacion` | 1 a N (solo uno vigente a la vez) | RESTRICT |

`usuario_rol` es la única tabla de roles: relaciona a cada usuario con valores del enum `Rol`.

---

<a id="correspondencia-api"></a>
## Correspondencia API ↔ columnas

| Campo del contrato | Columna | Notas |
|---|---|---|
| `id` | `usuarios.id` | |
| `primer_nombre`, `segundo_nombre`, `primer_apellido`, `segundo_apellido` | columnas homónimas de `usuarios` | No se guarda un nombre completo. |
| `correo` | `usuarios.correo` | |
| `contrasena`, `nueva_contrasena` | `usuarios.contrasena_hash` | Solo entra; se guarda como hash y no se devuelve. |
| `estado` | `usuarios.estado` | |
| `roles` | `usuario_rol.rol` (filas del usuario) | Arreglo ordenado alfabéticamente. |
| `codigo` | `codigos_recuperacion.codigo_hash` | Se compara contra el hash; no se devuelve. |
| `expira_en` (login) | — | Viene del claim `exp` del JWT, no de la BD. |

---

<a id="reglas-de-integridad"></a>
## Reglas de integridad

| Regla | Dónde se garantiza |
|---|---|
| Correo único sin distinguir mayúsculas ([RN-MS01-01](1_requirements.md#rn-ms01-01)) | BD: UNIQUE + `correo = lower(correo)`; el servicio normaliza antes de guardar. |
| Estados válidos ([RN-MS01-17](1_requirements.md#rn-ms01-17)) | BD: CHECK. |
| Roles solo del catálogo y sin repetir | BD: CHECK sobre `usuario_rol.rol` y PK compuesta; en el código, enum `Rol`. |
| Todo usuario conserva al menos un rol | Servicio (transacción). |
| Rol inicial ESTUDIANTE ([RN-MS01-02](1_requirements.md#rn-ms01-02)) | Servicio, al registrar. |
| Nadie cambia sus propios roles ni, siendo ADMIN, su propio estado ([RN-MS01-12](1_requirements.md#rn-ms01-12), [RN-MS01-19](1_requirements.md#rn-ms01-19)) | Servicio: compara `X-User-Id` con los ids objetivo. |
| Un solo código vigente por usuario; un solo uso ([RN-MS01-21](1_requirements.md#rn-ms01-21)) | Servicio: al generar uno nuevo se marca `usado_en` en los anteriores; al restablecer se marca el consumido. |
| Cuenta INACTIVA no inicia sesión ([RN-MS01-06](1_requirements.md#rn-ms01-06)) | Servicio, al autenticar. |
| Política de contraseña ([RN-MS01-03](1_requirements.md#rn-ms01-03)) | Servicio, antes de calcular el hash; la BD solo ve el hash. |

---

<a id="operaciones"></a>
## Operaciones transaccionales y consultas

Cada operación de escritura es una sola transacción: o se aplica completa o no se aplica nada.

| Operación | Lecturas | Escrituras |
|---|---|---|
| Registro ([RF-01](1_requirements.md#rf-ms01-01)) | Existe `correo` | INSERT `usuarios` (ACTIVO) + INSERT `usuario_rol` (ESTUDIANTE) |
| Login ([RF-02](1_requirements.md#rf-ms01-02)) | `usuarios` por `correo` + sus roles | Ninguna |
| Actualizar perfil ([RF-06](1_requirements.md#rf-ms01-06)) | `usuarios` por `X-User-Id` | UPDATE de los 4 campos de nombre + `actualizado_en` |
| Cambio de estado en lote ([RF-07](1_requirements.md#rf-ms01-07)) | Todos los ids existen; ninguno es el solicitante | UPDATE `estado` de cada usuario |
| Cambio de roles en lote ([RF-05](1_requirements.md#rf-ms01-05)) | Ids existen; solicitante no incluido; cada usuario conserva ≥ 1 rol | INSERT las filas de `agregar` que falten; DELETE las de `retirar` |
| Solicitar código ([RF-08](1_requirements.md#rf-ms01-08)) | `usuarios` por `correo` | Si existe y está ACTIVO: marca `usado_en` en vigentes previos + INSERT nuevo código |
| Restablecer contraseña | Último código vigente del usuario | Marca `usado_en` + UPDATE `contrasena_hash` |
| Cambiar contraseña autenticado | `usuarios` por `X-User-Id` | UPDATE `contrasena_hash` |

**Listado `GET /users` ([F-07](2_forms-inputs.md#f-07)):**
* `correo`: coincidencia parcial sin distinguir mayúsculas (`ILIKE '%texto%'`).
* `estado`: igualdad sobre `usuarios.estado`.
* `rol`: usuarios con una fila en `usuario_rol` de ese rol.
* Paginación con `page` (desde 0) y `size` (1 a 100, por defecto 20); orden por `creado_en` descendente y luego `id`.
* Cada elemento incluye sus roles completos, no solo el rol filtrado.

---

<a id="datos-iniciales"></a>
## Datos iniciales

1. **Primer administrador** ([RN-MS01-12](1_requirements.md#rn-ms01-12)): se crea al arrancar solo si no existe ningún usuario con rol ADMIN, con el correo y la contraseña definidos en variables de entorno (`BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD`), nunca en el repositorio. Queda ACTIVO con el rol ADMIN.
2. **Limpieza opcional:** una tarea periódica puede eliminar códigos usados o vencidos con más de 24 horas de antigüedad.