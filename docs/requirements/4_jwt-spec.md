# Especificación del JWT

Define cómo MS01 emite el token y qué debe poder verificar el API Gateway. Complementa [RF-MS01-03](1_requirements.md#rf-ms01-03), [RN-MS01-07 a 09](1_requirements.md#rn-ms01-07) y [RN-MS01-23](1_requirements.md#rn-ms01-23).

## Índice de Contenido
1. [Parámetros](#parametros)
2. [Estructura del token](#estructura-del-token)
3. [Mapeo a cabeceras](#mapeo-a-cabeceras)
4. [Ciclo de vida](#ciclo-de-vida)
5. [Configuración y llaves](#configuracion-y-llaves)
6. [Pendiente: acuerdos con el Gateway](#pendiente-gateway)

---

<a id="parametros"></a>
## Parámetros

| Parámetro | Valor | Nota |
|---|---|---|
| **Estándar** | JWT firmado (JWS, RFC 7519) | RN-MS01-07 |
| **Algoritmo** | **RS256** (RSA 2048) | La llave privada solo existe en MS01. El Gateway verifica con la llave pública y no puede emitir tokens (la propuesta le prohíbe generarlos). |
| **Expiración** | **60 minutos** (`JWT_EXPIRATION_MINUTES=60`) | Corta y configurable (RN-MS01-09). Sin refresh token: al expirar se inicia sesión de nuevo. |
| **Issuer (`iss`)** | `ms01-identity-and-access` | Configurable. |
| **Audience (`aud`)** | `campusseguro` | Configurable. |
| **Transporte** | `Authorization: Bearer <JWT>` | Respuesta de login: `tipo: Bearer`, `expira_en` = `exp` en ISO 8601. |
| **Tolerancia de reloj** | 30 s (sugerida al verificador) | |

> **Alternativa HS256** (un secreto compartido): más simple, pero obliga a que el Gateway conozca el secreto de firma y pueda emitir tokens. Se descarta por esa razón.

---

<a id="estructura-del-token"></a>
## Estructura del token

**Header**
```json
{ "alg": "RS256", "typ": "JWT", "kid": "ms01-2026-01" }
```

**Payload**
```json
{
  "iss": "ms01-identity-and-access",
  "aud": "campusseguro",
  "sub": "USR-1034",
  "email": "maria.perez@escuelaing.edu.co",
  "roles": ["ESTUDIANTE", "BRIGADISTA"],
  "iat": 1792510000,
  "exp": 1792513600,
  "jti": "5b1f0c2e-8d4a-4c1b-9a77-3f2e6d1a0b9c"
}
```

| Claim | Tipo | Descripción |
|---|---|---|
| `iss` | string | Emisor. |
| `aud` | string | Destino del token. |
| `sub` | string | Id del usuario (el mismo `id` del API). |
| `email` | string | Correo institucional. |
| `roles` | string[] | Roles vigentes al login, del catálogo de 8 ([matriz](3_matriz-roles-permisos.md)). Mínimo 1. |
| `iat` / `exp` | número | Emisión y expiración (epoch en segundos). `exp = iat + 3600`. |
| `jti` | string (UUID) | Identificador único del token (permite una futura revocación). |

**No incluye** contraseñas, hashes, nombres, estado ni permisos (RN-MS01-08). Los permisos se derivan del rol con la [matriz](3_matriz-roles-permisos.md).

---

<a id="mapeo-a-cabeceras"></a>
## Mapeo a cabeceras

Lo que el Gateway debe traducir tras verificar el token y lo que MS01 espera recibir (RN-MS01-23):

| Origen | Cabecera hacia MS01 | Ejemplo |
|---|---|---|
| `sub` | `X-User-Id` | `USR-1034` |
| `roles` (unidos por coma) | `X-User-Role` | `ESTUDIANTE,BRIGADISTA` |
| Generado por el Gateway | `X-Correlation-ID` | `c19b-472d-a12b` |

---

<a id="ciclo-de-vida"></a>
## Ciclo de vida

1. **Emisión:** solo en `POST /auth/login` exitoso, con cuenta ACTIVA ([RF-MS01-02](1_requirements.md#rf-ms01-02)).
2. **Cambios de rol o estado:** se reflejan en el siguiente token emitido (RN-MS01-09).
3. **Logout:** no revoca el token; queda válido hasta `exp` (RN-MS01-10).
4. **Cambio o restablecimiento de contraseña:** tampoco invalida tokens ya emitidos; el usuario debe iniciar sesión de nuevo y los anteriores expiran solos.
5. **Token inválido, alterado o expirado:** se rechaza con 401 antes de llegar a MS01.

---

<a id="configuracion-y-llaves"></a>
## Configuración y llaves

Variables de entorno (RNF-MS01-06, nunca en el repositorio):

| Variable | Servicio | Descripción |
|---|---|---|
| `JWT_PRIVATE_KEY` | MS01 | Llave privada RSA en PEM (PKCS#8). |
| `JWT_PUBLIC_KEY` | MS01 y Gateway | Llave pública en PEM. |
| `JWT_EXPIRATION_MINUTES` | MS01 | Por defecto `60`. |
| `JWT_ISSUER` / `JWT_AUDIENCE` | MS01 y Gateway | Valores de la tabla de parámetros. |

Generación de llaves:
```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out private.pem
openssl rsa -in private.pem -pubout -out public.pem
```

La rotación es manual: se genera un par nuevo, se cambia el `kid` y se despliega la nueva llave pública en el Gateway antes de que MS01 firme con la privada nueva.

---

<a id="pendiente-gateway"></a>
## Pendiente: acuerdos con el Gateway

Se documentan en los requerimientos del Gateway, no aquí. MS01 asume lo siguiente:

* Verifica firma RS256, `exp`, `iss` y `aud` (la propuesta solo exige token presente, bien formado y no expirado; la verificación de firma es lo que hace seguro confiar en las cabeceras).
* Elimina `Authorization` y toda cabecera `X-` externa antes de inyectar las suyas.
* Deja pasar sin JWT solo los endpoints públicos de MS01: `/auth/register`, `/auth/login`, `/auth/password/forgot` y `/auth/password/reset`.