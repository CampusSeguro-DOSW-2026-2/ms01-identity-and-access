# MS01 - Requerimientos MVP vs Fase 2

## Objetivo

Documentar los requerimientos funcionales mínimos para la **Fase 1 (MVP)**, diferenciando claramente lo que NO se implementa en esta fase y queda como preparación para **Fase 2 (Seguridad + JWT + Gateway)**.

## 1. Alcance MVP

Este MVP tiene como objetivo entregar un dominio funcional de Identidad y Acceso consumible directamente por el microservicio de Frontend, priorizando **valor funcional al usuario**.

El esquema de base de datos vigente (`database/001_create_schema.sql`) modela **un único rol por usuario** (`users.rol` VARCHAR + `CHECK ckk_rol`). No existen tablas de roles/permisos ni de auditoría, por lo que el MVP no expone gestión de permisos ni RBAC granular: eso queda para Fase 2, cuando se definan esas tablas.

## 2. Contrato de roles (importante para el Frontend)

- En **JSON** y en el **catálogo** `GET /api/v1/roles` el rol viaja como **código de API** (nombre del enum, sin espacios):
  `ESTUDIANTE`, `PROFESOR`, `PERSONAL_ADMINISTRATIVO`, `BRIGADISTA`, `COORDINADOR_DE_EMERGENCIAS`, `ORGANIZADOR_DE_SIMULACROS`, `AUDITOR`.
- En la **base de datos** el valor se almacena con espacios (`PERSONAL ADMINISTRATIVO`), tal como exige el constraint `ckk_rol`. La conversión la hace `UserRolConverter`.
- Por conveniencia, las entradas **aceptan ambas formas** (mayúsculas/minúsculas y espacios): `"PERSONAL_ADMINISTRATIVO"` y `"PERSONAL ADMINISTRATIVO"` son válidas tanto en `rol` de JSON como en `?rol=` de query param.
- Un rol no permitido produce `400 Bad Request`.

## 3. Requerimientos Funcionales (RF) - Fase 1

| ID | Nombre | Descripción | Criterios de Aceptación | Estado |
|---|---|---|---|---|
| RF-MS01-MVP-01 | Registro de usuario | Registrar un nuevo usuario con email, contraseña y rol. | `POST /api/v1/auth/register` crea el usuario; password cifrado con BCrypt; no se retorna `pw`; email único (409 si existe); rol validado. | ✅ |
| RF-MS01-MVP-02 | Autenticación básica | Validar credenciales (email + password) y retornar el usuario autenticado. | `POST /api/v1/auth/login` con credenciales válidas retorna `authenticated=true` + `user`; credenciales inválidas → 401 con mensaje genérico; no emite JWT en esta fase. | ✅ |
| RF-MS01-MVP-03 | Perfil actual | Consultar el perfil del usuario autenticado (flujo frontend). | `GET /api/v1/auth/me` retorna el usuario identificado por la cabecera `X-Email`; 404 si no existe; 400 si falta la cabecera. Público en MVP. | ✅ |
| RF-MS01-MVP-04 | Listado de usuarios | Obtener el listado de usuarios. | `GET /api/v1/users` retorna la lista completa; no expone `pw`. | ✅ |
| RF-MS01-MVP-05 | Obtener usuario por ID | Consultar el detalle de un usuario. | `GET /api/v1/users/{id}` retorna el usuario; 404 si no existe. | ✅ |
| RF-MS01-MVP-06 | Crear usuario | Crear usuario desde administración. | `POST /api/v1/users` crea el usuario; mismas validaciones que registro; 201. | ✅ |
| RF-MS01-MVP-07 | Actualizar usuario (PATCH) | Actualización parcial. | `PATCH /api/v1/users/{id}` actualiza solo los campos enviados; valida unicidad de email si cambia. | ✅ |
| RF-MS01-MVP-08 | Actualizar usuario (PUT) | Actualización completa. | `PUT /api/v1/users/{id}` reemplaza email, password y rol; valida unicidad de email. | ✅ |
| RF-MS01-MVP-09 | Eliminar usuario | Eliminación de usuario. | `DELETE /api/v1/users/{id}` elimina el registro y retorna 204; 404 si no existe. | ✅ |
| RF-MS01-MVP-10 | Asignación de rol | Asignar rol a un usuario. | `POST /api/v1/users/{id}/roles?rol=<codigo>` asigna un rol válido del constraint; 400 con rol inválido; 404 si el usuario no existe. | ✅ |
| RF-MS01-MVP-11 | Catálogo de roles | Obtener los roles disponibles. | `GET /api/v1/roles` retorna `[{code, name}]` con los 7 roles del constraint. | ✅ |
| RF-MS01-MVP-12 | Detalle de rol | Obtener detalle de un rol con sus permisos. | — | ⏸️ Fase 2 (no hay tabla de permisos) |
| RF-MS01-MVP-13 | Catálogo de permisos | Listar permisos. | — | ⏸️ Fase 2 (no hay tabla de permisos) |
| RF-MS01-MVP-14 | CRUD de roles/permisos | Gestión de roles y permisos. | — | ⏸️ Fase 2 (requiere tablas `roles`/`permissions`) |

## 4. Reglas de Negocio (RN) - Fase 1

| ID | Descripción | Estado |
|---|---|---|
| RN-MS01-MVP-01 | Email único a nivel de base de datos (`uk_users_email`) y validado en el servicio. | ✅ |
| RN-MS01-MVP-02 | Password cifrado obligatoriamente con BCrypt antes de persistirse; nunca en texto plano. | ✅ |
| RN-MS01-MVP-03 | Rol perteneciente al conjunto del constraint `ckk_rol`; validado además en la capa de entrada. | ✅ |
| RN-MS01-MVP-04 | Las respuestas HTTP nunca incluyen el password (`pw`/`password`). | ✅ |
| RN-MS01-MVP-05 | Los errores de autenticación son genéricos ("Credenciales inválidas") para evitar user-enumeration. | ✅ |
| RN-MS01-MVP-06 | Validaciones de entrada con Bean Validation (`@Valid`). | ✅ |
| RN-MS01-MVP-07 | Errores en formato estandarizado: `timestamp`, `status`, `error`, `message`, `path`, `correlationId` (opcional) y `details` (solo validación). | ✅ |
| RN-MS01-MVP-08 | El email se normaliza (trim + lower case) antes de comparar/almacenar, de modo que `Ana@Uni.edu` y `ana@uni.edu` sean el mismo usuario. | ✅ |

## 5. Endpoints del MVP

| Método | Endpoint | Descripción |
|---|---|---|
| POST | `/api/v1/auth/register` | Registro de usuario |
| POST | `/api/v1/auth/login` | Validación de credenciales |
| GET | `/api/v1/auth/me` | Perfil del usuario (cabecera `X-Email`) |
| GET | `/api/v1/users` | Listado de usuarios |
| GET | `/api/v1/users/{id}` | Detalle de usuario |
| POST | `/api/v1/users` | Crear usuario |
| PUT | `/api/v1/users/{id}` | Actualizar usuario (completo) |
| PATCH | `/api/v1/users/{id}` | Actualizar usuario (parcial) |
| POST | `/api/v1/users/{id}/roles?rol=<codigo>` | Asignar rol |
| DELETE | `/api/v1/users/{id}` | Eliminar usuario |
| GET | `/api/v1/roles` | Catálogo de roles |
| GET | `/actuator/health` | Health check |
| GET | `/swagger-ui.html`, `/v3/api-docs` | Documentación OpenAPI |

## 6. NO IMPLEMENTADO en Fase 1 (Preparado para Fase 2)

| Aspecto | Estado Fase 1 | Notas para Fase 2 |
|---|---|---|
| **JWT (Access Token)** | NO | Implementar `AuthTokenProvider` (`security/token`) con `jjwt`; generar token en `AccessServiceImpl.login()`; rellenar `tokenType`/`accessToken`/`expiresAt` (hoy `null`, contrato ya estable). |
| **Validación JWT** | NO | Implementar `JwtAuthenticationFilter` (stub existente) y registrarlo en el `SecurityFilterChain`. |
| **Autorización por roles/permisos** | NO | Hoy `anyRequest().permitAll()`. Cambiar a reglas por ruta/rol/permiso (`@PreAuthorize`). |
| **Validación en API Gateway** | NO (explícitamente omitido) | El Gateway aún no existe. Preparado para validar el JWT en el perímetro. |
| **Correlation ID** | Stub, no obligatorio | `CorrelationIdFilter` existe sin registrar. Activar lectura/`X-Correlation-Id`, MDC y propagación en errores. |
| **Auditoría (`audit_logs`)** | NO | No hay tabla. Cuando exista: registrar LOGIN (éxito/fallo) y acciones sensibles con IP, User-Agent y correlationId. |
| **Refresh Token / revocación** | NO | Stateless. Evaluar según Fase 2. |
| **`/auth/me` con token** | Header `X-Email` (stopgap MVP) | En Fase 2 el usuario se obtendrá del `subject` del JWT. |
| **Permisos/RBAC granular** | NO | Requiere tablas `roles`, `permissions`, `user_roles`, `role_permissions`. |

## 7. Requisitos No Funcionales (RNF) - Fase 1

| ID | Descripción | Criterio de Aceptación | Estado |
|---|---|---|---|
| RNF-MS01-MVP-01 | Cobertura de pruebas | Cobertura de instrucciones ≥ 70% (JaCoCo, regla BUNDLE) sobre la lógica de dominio; stubs de seguridad excluidos del `check`. | ✅ 77.7% |
| RNF-MS01-MVP-02 | Tests automatizados | Unit (JUnit 5 + Mockito + AssertJ), `@WebMvcTest` (MockMvc + `@MockitoBean` + ObjectMapper), `@DataJpaTest`, un test de integración del flujo principal. | ✅ 51 tests |
| RNF-MS01-MVP-03 | Estándares de API REST | Endpoints versionados `/api/v1/*`, verbos HTTP correctos, códigos 200/201/204/400/401/404/409. | ✅ |
| RNF-MS01-MVP-04 | Seguridad básica | BCrypt, password nunca expuesto en respuestas ni logs, mensajes de error sanitizados (sin trazas internas). | ✅ |
| RNF-MS01-MVP-05 | Portabilidad | `Dockerfile` multi-stage (Java 21), `.dockerignore`, imagen construible. | ✅ |
| RNF-MS01-MVP-06 | CI/CD base | `mvn clean verify` compila, ejecuta tests, genera reporte JaCoCo y pasa el check ≥70%. Workflow de GitHub Actions con build + docker build. | ✅ |
| RNF-MS01-MVP-07 | Documentación de API | Swagger UI (`/swagger-ui.html`) con endpoints, DTOs y ejemplos. | ✅ |
| RNF-MS01-MVP-08 | Configuración por entornos | `application.yml` + `application-dev.yml` + `application-test.yml`; variables externas `DB_URL`, `DB_USER`, `DB_PASSWORD`, `SERVER_PORT`. Sin dependencias JWT obligatorias. | ✅ |
| RNF-MS01-MVP-09 | Coherencia con la BD | `ddl-auto: validate` en dev; entidades JPA alineadas a `001_create_schema.sql` + `002_migrate_pw_length.sql` + `003_add_unique_email.sql`. | ✅ |
| RNF-MS01-MVP-10 | Observabilidad básica | `/actuator/health` expuesto (solo `health`). | ✅ |
| RNF-MS01-MVP-11 | Consumo desde Frontend | CORS habilitado para `/api/**` (se ajustará con el Gateway en Fase 2). | ✅ |

## 8. Definition of Done - MVP

- [x] Requerimientos MVP documentados
- [x] RF-MS01-MVP-01 a 11 implementados y verificados
- [x] RF-MS01-MVP-12 a 14 diferidos explícitamente a Fase 2 (sin soporte de esquema)
- [x] RN aplicadas
- [x] RNF cumplidos
- [x] Tests verdes (51), cobertura ≥ 70%
- [x] `mvn clean verify` OK
- [x] `docker build` OK
- [x] Swagger UI funcional y `/actuator/health` OK
- [x] README actualizado para consumo Frontend
- [x] Código preparado con stubs + TODOs Fase 2
- [x] Producto funcional y consumible por Frontend (verificado contra PostgreSQL real)
