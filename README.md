# MS01 - IDENTITY AND ACCESS

MS01 - Users, authentication, roles, permissions, and sessions service.

| Name                            | GitHub Username                   |
|---------------------------------|-----------------------------------|
| Daniel Santiago Morales Perdomo | `Fukuyaku88`                      |
| Edgar Daniel Ruiz Patiño        | `DaniRuiz11`                      |
| Miguel Ángel Acero Laverde      | `Miguelim101`                     |
| Thomas Sebastian García Gomez   | `thomassebastiangarciagomez-ctrl` |
| Rodrigo Gualtero                | `Rodrigoreg92`                    |

---

## Estado: Fase 1 (MVP funcional)

Esta entrega implementa el **dominio funcional de Identidad y Acceso** para que el
microservicio de Frontend pueda consumirlo **hoy**:

- Registro y autenticación básica (validación de credenciales, **sin JWT**).
- Gestión de usuarios (listar, consultar, crear, actualizar, eliminar).
- Catálogo de roles y asignación de rol por usuario.

**No incluido en esta fase (preparado para Fase 2):** emisión/validación de JWT,
autorización por roles/permisos, validación en el API Gateway, correlation-id
obligatorio y auditoría. El código ya trae los puntos de extensión
(`security/token`, `security/filter`, `SecurityConfig`) marcados con `TODO Fase 2`.

Ver [`docs/requirements/MS01-requirements-mvp.md`](docs/requirements/MS01-requirements-mvp.md)
para el detalle de requerimientos, reglas de negocio y el alcance diferido.

---

## Requisitos

| Herramienta | Versión |
|---|---|
| Java | 21 (el `Dockerfile` usa Temurin 21) |
| Maven | 3.9+ |
| PostgreSQL | 14+ |
| Docker | opcional, para imagen y base de datos local |

## Configuración

Todas las credenciales se inyectan por variables de entorno (ver `.env.example`):

| Variable | Default | Descripción |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/campusseguro_db` | URL JDBC |
| `DB_USER` | `campus_user` | Usuario de la base de datos |
| `DB_PASSWORD` | `campus_password` | Password de la base de datos |
| `SERVER_PORT` | `8081` | Puerto HTTP |
| `SPRING_PROFILES_ACTIVE` | `dev` | Perfil de Spring |
| `JPA_SHOW_SQL` | `false` | Mostrar SQL de Hibernate |

> No hay secretos *hardcodeados*: los valores por defecto son de desarrollo local.

## Puesta en marcha

### 1. Base de datos

```bash
docker run -d --name ms01-postgres \
  -e POSTGRES_USER=campus_user -e POSTGRES_PASSWORD=campus_password \
  -e POSTGRES_DB=campusseguro_db -p 5432:5432 postgres:16-alpine
```

Aplicar el esquema y las migraciones **en orden**:

```bash
for f in database/001_create_schema.sql database/002_migrate_pw_length.sql database/003_add_unique_email.sql; do
  docker exec -i ms01-postgres psql -v ON_ERROR_STOP=1 -U campus_user -d campusseguro_db < "$f"
done
```

| Script | Propósito |
|---|---|
| `001_create_schema.sql` | Tabla `users` original (id, email, pw, rol + `CHECK ckk_rol`) |
| `002_migrate_pw_length.sql` | Amplía `pw` a `VARCHAR(255)` para almacenar el hash BCrypt |
| `003_add_unique_email.sql` | Constraint `uk_users_email` (email único) |

El perfil `dev` usa `spring.jpa.hibernate.ddl-auto=validate`, por lo que la
aplicación **exige** que el esquema esté migrado antes de arrancar.

### 2. Aplicación

```bash
mvn spring-boot:run
# o
mvn clean package && java -jar target/login-0.0.1-SNAPSHOT.jar
```

- API: <http://localhost:8081/api/v1>
- Swagger UI: <http://localhost:8081/swagger-ui.html>
- OpenAPI JSON: <http://localhost:8081/v3/api-docs>
- Health: <http://localhost:8081/actuator/health>

### 3. Docker

```bash
docker build -t ms01-identity-and-access:latest .
docker run -p 8081:8081 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/campusseguro_db \
  -e DB_USER=campus_user -e DB_PASSWORD=campus_password \
  ms01-identity-and-access:latest
```

---

## API del MVP

### Autenticación

| Método | Endpoint | Descripción |
|---|---|---|
| POST | `/api/v1/auth/register` | Registro (`201`) |
| POST | `/api/v1/auth/login` | Validación de credenciales (`200` / `401`) |
| GET | `/api/v1/auth/me` | Perfil actual (cabecera `X-Email`) |

### Usuarios y roles

| Método | Endpoint | Descripción |
|---|---|---|
| GET | `/api/v1/users` | Listar usuarios |
| GET | `/api/v1/users/{id}` | Detalle de usuario |
| POST | `/api/v1/users` | Crear usuario (`201`) |
| PUT | `/api/v1/users/{id}` | Reemplazo completo |
| PATCH | `/api/v1/users/{id}` | Actualización parcial |
| POST | `/api/v1/users/{id}/roles?rol=<codigo>` | Asignar rol |
| DELETE | `/api/v1/users/{id}` | Eliminar usuario (`204`) |
| GET | `/api/v1/roles` | Catálogo de roles |

### Contrato de rol

- En JSON y en `GET /roles` el rol es un **código** sin espacios:
  `ESTUDIANTE`, `PROFESOR`, `PERSONAL_ADMINISTRATIVO`, `BRIGADISTA`,
  `COORDINADOR_DE_EMERGENCIAS`, `ORGANIZADOR_DE_SIMULACROS`, `AUDITOR`.
- Se aceptan también los valores con espacios (`"PERSONAL ADMINISTRATIVO"`) y
  es tolerante a mayúsculas/minúsculas, tanto en el JSON como en `?rol=`.
- En la base de datos se guardan **con espacios**, según el `CHECK ckk_rol`.

### Formato de error

```json
{
  "timestamp": "2026-10-14T12:34:56.789Z",
  "status": 409,
  "error": "Conflict",
  "message": "El email ya está registrado: ana@uni.edu",
  "path": "/api/v1/auth/register",
  "correlationId": null,
  "details": null
}
```

`correlationId` se omite mientras sea `null` (se completará en Fase 2).
`details` solo aparece en errores de validación (`400`), con el detalle por campo.

### Ejemplos con `curl`

```bash
BASE=http://localhost:8081

# Registrar
curl -X POST $BASE/api/v1/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"ana@uni.edu","password":"Secreta123","rol":"ESTUDIANTE"}'

# Iniciar sesión (MVP: authenticated=true, accessToken=null)
curl -X POST $BASE/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"ana@uni.edu","password":"Secreta123"}'

# Perfil actual
curl $BASE/api/v1/auth/me -H 'X-Email: ana@uni.edu'

# Listar usuarios
curl $BASE/api/v1/users

# Crear usuario
curl -X POST $BASE/api/v1/users -H 'Content-Type: application/json' \
  -d '{"email":"leo@uni.edu","password":"Secreta123","rol":"PROFESOR"}'

# Actualización parcial
curl -X PATCH $BASE/api/v1/users/2 -H 'Content-Type: application/json' \
  -d '{"rol":"BRIGADISTA"}'

# Asignar rol
curl -X POST "$BASE/api/v1/users/2/roles?rol=COORDINADOR_DE_EMERGENCIAS"

# Catálogo de roles
curl $BASE/api/v1/roles

# Eliminar
curl -X DELETE $BASE/api/v1/users/2
```

### Respuesta de login (contrato estable hacia Fase 2)

```json
{
  "authenticated": true,
  "user": { "id": 1, "email": "ana@uni.edu", "rol": "ESTUDIANTE" },
  "tokenType": null,
  "accessToken": null,
  "expiresAt": null
}
```

En Fase 2, `tokenType`/`accessToken`/`expiresAt` se rellenan con el JWT **sin
cambiar el contrato** para el Frontend.

---

## Pruebas y cobertura

```bash
mvn clean test     # 51 pruebas
mvn verify         # pruebas + reporte JaCoCo + check de cobertura
```

- Reporte HTML: `target/site/jacoco/index.html`
- Umbral exigido: **70%** de instrucciones (regla `BUNDLE`); excluye los stubs
  de seguridad no implementados (`security/filter`, `security/userdetails`, `JwtTokenProvider`).

Tipos de prueba incluidos:

| Tipo | Framework | Qué cubre |
|---|---|---|
| Unitarias de servicio | JUnit 5 + Mockito + AssertJ | `AccessServiceImpl` |
| Slice web | `@WebMvcTest` + `MockMvc` + `@MockitoBean` + `ObjectMapper` | `AuthController`, `AccessController` |
| Persistencia | `@DataJpaTest` (H2 en modo PostgreSQL) | `UserRepository` |
| Integración | `@SpringBootTest` + `MockMvc` | Flujo registro → login → perfil → roles |

---

## Estructura del proyecto

```text
src/main/java/edu/eci/dosw/login
├── config/       SecurityConfig (permitAll en MVP), WebConfig (CORS), OpenApiConfig,
│                 UserRolRequestConverter
├── controller/   AuthController, AccessController
├── dto/          LoginRequest/Response, RegisterRequest/Response/UpdateRequest,
│                 UserPatchRequest, RoleResponse
├── entity/       UserEntity, UserRol, UserRolConverter
├── exception/    ApiError, GlobalExceptionHandler, excepciones de dominio
├── repository/   UserRepository
├── security/     filter/ y token/ (stubs + TODO Fase 2)
└── service/      AccessService, AccessServiceImpl
```

## Integración continua

`.github/workflows/ci-ms01.yml` ejecuta en cada push/PR a `main`/`develop`:

1. `mvn -B clean verify` (compilación + 51 pruebas + JaCoCo ≥70%).
2. Publicación del reporte JaCoCo como artefacto.
3. `docker build` de la imagen.
