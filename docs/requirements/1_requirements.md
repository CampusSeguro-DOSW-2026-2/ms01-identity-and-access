# Estándar de Requerimientos

## Índice de Contenido
1. [Criterios de Separación](#criterios-de-separacion)
2. [Requerimientos Funcionales](#requerimientos-funcionales)
   - [RF-MS01-01 Registrar usuario institucional](#rf-ms01-01)
   - [RF-MS01-02 Autenticar usuario (login)](#rf-ms01-02)
   - [RF-MS01-03 Generar y validar tokens JWT (gestión de sesión)](#rf-ms01-03)
   - [RF-MS01-04 Cerrar sesión (logout)](#rf-ms01-04)
   - [RF-MS01-05 Gestión de roles y permisos](#rf-ms01-05)
   - [RF-MS01-06 Gestión del perfil](#rf-ms01-06)
   - [RF-MS01-07 Administración de usuarios: consulta y activación/inactivación](#rf-ms01-07)
   - [RF-MS01-08 Recuperación y cambio de contraseña](#rf-ms01-08)
3. [Requerimientos No Funcionales (RNF)](#requerimientos-no-funcionales-rnf)
4. [API: ms01-identity-and-access](#api-ms01-identity-and-access)

---

<a id="criterios-de-separacion"></a>
## Criterios de Separación

**¿Cuándo un requerimiento merece existir por separado?**
* Se reutiliza en varios flujos (auth, notificaciones, uploads).
* Tiene reglas de negocio propias y no triviales.
* Pertenece a otro microservicio/squad con su propio contrato de API o evento.
* Tiene su propio ciclo de pruebas independiente.
* Si un paso no cumple ninguna, va dentro del flujo base o como flujo alterno, no como requerimiento aparte.

---

<a id="requerimientos-funcionales"></a>
## Requerimientos Funcionales

<a id="rf-ms01-01"></a>
### RF-MS01-01 Registrar usuario institucional

| Atributo | Detalle |
|---|---|
| **ID** | RF-MS01-01 |
| **Nombre** | Registrar usuario institucional |
| **Épica / Grupo funcional** | MS01 - Registro |
| **Descripción** | Permitir que una persona se registre con su correo institucional, primer y segundo nombre, primer y segundo apellido (segundo nombre y segundo apellido opcionales) y contraseña. |
| **Actor(es)** | Usuario no registrado |
| **Precondiciones** | Dominio institucional configurado. Existe al menos un administrador inicial (ver [RN-MS01-12](#rn-ms01-12)). |
| **Flujo base** | <ol><li>El cliente envía correo, primer nombre, segundo nombre (opcional), primer apellido, segundo apellido (opcional), contraseña y confirmación de contraseña.</li><li>El sistema valida que los campos obligatorios estén presentes.</li><li>Valida que el correo tenga formato válido y pertenezca al dominio institucional.</li><li>Valida que ambas contraseñas coincidan y cumplan la política ([RN-MS01-03](#rn-ms01-03)).</li><li>Verifica que el correo no esté registrado.</li><li>Guarda la contraseña como hash y crea el usuario en estado ACTIVO con el rol ESTUDIANTE.</li><li>Emite a MS08 el evento de auditoría (acción REGISTRO).</li><li>Responde con el id, correo, roles y estado (nunca la contraseña). No inicia sesión automáticamente.</li></ol> |
| **Flujos alternos / excepciones** | <ul><li>**A1 (paso 5), correo ya registrado:** el sistema rechaza el registro (HTTP 409), informa que el correo ya está registrado y emite a MS08 el evento de auditoría.</li><li>**A2 (pasos 3 y 4), datos inválidos:** el sistema rechaza el registro (HTTP 400) y emite a MS08 el evento de auditoría. Si el problema es el correo, el mensaje es genérico (“correo no válido”), sin indicar si falló por dominio o formato. Si es la contraseña, indica que no cumple la política.</li><li>**A3 (paso 2), campos faltantes:** el sistema rechaza el registro (HTTP 400) e indica los campos faltantes.</li></ul> |
| **Reglas De Negocio** | <ul><li><a id="rn-ms01-01"></a>**RN-MS01-01:** Un correo no puede asociarse a más de un usuario (sin distinguir mayúsculas/minúsculas).</li><li><a id="rn-ms01-02"></a>**RN-MS01-02:** El rol inicial siempre es ESTUDIANTE. El solicitante no puede elegir ni enviar un rol.</li><li><a id="rn-ms01-03"></a>**RN-MS01-03:** La contraseña debe tener mínimo 8 caracteres, con mayúscula, minúscula, número y carácter especial.</li><li><a id="rn-ms01-04"></a>**RN-MS01-04:** Las contraseñas se almacenan solo como hash con algoritmo adaptativo (BCrypt). Nunca en texto plano.</li></ul> |
| **Postcondiciones** | El usuario existe, está ACTIVO y puede autenticarse con [RF-MS01-02](#rf-ms01-02). |
| **Requerimientos asociados** | [RF-MS01-02](#rf-ms01-02), [RF-MS01-05](#rf-ms01-05) |
| **Criterios de aceptación** | <ul><li>Un registro válido devuelve 201 y el usuario queda ACTIVO con rol ESTUDIANTE.</li><li>Un correo repetido devuelve 409 y no crea un segundo usuario.</li><li>Un correo externo o una contraseña débil devuelven 400 y no crean usuario.</li><li>La respuesta no contiene la contraseña ni su hash.</li><li>Cada intento, exitoso o fallido, genera un evento de auditoría hacia MS08.</li></ul> |
| **Prioridad** | Alta |
| **Estado / Squad responsable** | Pendiente / kAiRoS |

<br>

<a id="rf-ms01-02"></a>
### RF-MS01-02 Autenticar usuario (login)

| Atributo | Detalle |
|---|---|
| **ID** | RF-MS01-02 |
| **Nombre** | Autenticar usuario (login) |
| **Épica / Grupo funcional** | MS01 - Autenticación |
| **Descripción** | Permitir autenticarse con correo y contraseña para obtener un JWT que habilita el consumo de servicios protegidos. |
| **Actor(es)** | Todos los roles, ya registrados |
| **Precondiciones** | El usuario existe en MS01. |
| **Flujo base** | <ol><li>El cliente envía correo y contraseña.</li><li>El sistema busca el usuario por correo.</li><li>Verifica que la contraseña coincida con el hash almacenado.</li><li>Verifica que el usuario esté ACTIVO.</li><li>Genera el JWT ([RF-MS01-03](#rf-ms01-03)).</li><li>Emite a MS08 el evento de auditoría (acción LOGIN, resultado EXITO).</li><li>Responde con el token, su expiración y los datos básicos (id, correo, roles).</li></ol> |
| **Flujos alternos / excepciones** | <ul><li>**A1 (pasos 2 o 3), credenciales incorrectas o cuenta inexistente:** el sistema responde 401 con un mensaje genérico (“credenciales incorrectas”) y emite a MS08 el evento de auditoría del intento fallido.</li><li>**A2 (paso 4), cuenta inactiva:** el sistema responde 403, indica que debe contactar a un administrador y emite a MS08 el evento de auditoría.</li></ul> |
| **Reglas De Negocio** | <ul><li><a id="rn-ms01-05"></a>**RN-MS01-05:** Ante credenciales inválidas no se revela si falló el correo o la contraseña.</li><li><a id="rn-ms01-06"></a>**RN-MS01-06:** Una cuenta INACTIVA nunca inicia sesión, aunque las credenciales sean correctas.</li></ul> |
| **Postcondiciones** | El cliente posee un JWT válido hasta su expiración. |
| **Requerimientos asociados** | [RF-MS01-01](#rf-ms01-01), [RF-MS01-03](#rf-ms01-03), [RF-MS01-07](#rf-ms01-07) |
| **Criterios de aceptación** | <ul><li>Credenciales correctas de cuenta activa devuelven 200 y un JWT.</li><li>Un correo inexistente y una contraseña incorrecta devuelven exactamente la misma respuesta.</li><li>Una cuenta inactiva devuelve 403 aunque las credenciales sean correctas.</li><li>Todos los intentos generan su evento de auditoría hacia MS08.</li></ul> |
| **Prioridad** | Alta |
| **Estado / Squad responsable** | Pendiente / kAiRoS |

<br>

<a id="rf-ms01-03"></a>
### RF-MS01-03 Generar y validar tokens JWT

| Atributo | Detalle |
|---|---|
| **ID** | RF-MS01-03 |
| **Nombre** | Generar y validar tokens JWT (gestión de sesión) |
| **Épica / Grupo funcional** | MS01 - Sesiones |
| **Descripción** | Generar JWT firmados, con expiración y los datos necesarios para validar la sesión y aplicar autorización por rol en el ecosistema. |
| **Actor(es)** | Usuarios autenticados. Sistemas consumidores: API Gateway y microservicios. |
| **Precondiciones** | Autenticación exitosa ([RF-MS01-02](#rf-ms01-02)). Clave de firma configurada fuera del repositorio. Las peticiones a endpoints protegidos llegan con las cabeceras de identidad ([RN-MS01-23](#rn-ms01-23)). |
| **Flujo base** | <ol><li>Tras el login, MS01 genera un JWT firmado con: id de usuario, correo, roles, fecha de emisión y fecha de expiración.</li><li>El cliente envía el token en el encabezado Authorization: Bearer en cada petición.</li><li>Los servicios verifican firma, formato y expiración. MS01 recibe la identidad del usuario en las cabeceras X-User-Id y X-User-Role.</li><li>MS01 usa X-User-Role para autorizar la operación y X-User-Id para identificar al usuario.</li><li>MS01 expone un endpoint para consultar el usuario autenticado a partir de X-User-Id (id, correo, roles, estado).</li></ol> |
| **Flujos alternos / excepciones** | <ul><li>**A1, token expirado:** la petición se rechaza con 401. El cliente debe iniciar sesión de nuevo y es redirigido al login.</li><li>**A2, token inválido o manipulado:** la petición se rechaza con 401 y, cuando la validación se realiza en MS01, este emite a MS08 el evento de auditoría.</li><li>**A3, faltan las cabeceras de identidad en un endpoint protegido:** MS01 responde 401.</li></ul> |
| **Reglas De Negocio** | <ul><li><a id="rn-ms01-07"></a>**RN-MS01-07:** Los tokens siguen el estándar abierto JWT (RFC 7519), firmados con RS256 y llave configurable (parámetros en [`4_jwt-spec.md`](4_jwt-spec.md)), para ser compatibles con el Gateway y los demás servicios.</li><li><a id="rn-ms01-08"></a>**RN-MS01-08:** El JWT no contiene contraseñas ni datos sensibles.</li><li><a id="rn-ms01-09"></a>**RN-MS01-09:** Un cambio de rol o estado se refleja en el siguiente token emitido. Los tokens ya emitidos conservan sus datos hasta expirar, por eso la expiración debe ser corta.</li><li><a id="rn-ms01-23"></a>**RN-MS01-23:** Registro, login y recuperación de contraseña son públicos. Los demás endpoints reciben la identidad solo por las cabeceras X-User-Id, X-User-Role (roles separados por coma, p. ej. ESTUDIANTE,BRIGADISTA) y X-Correlation-ID, nunca por el cuerpo de la petición.</li></ul> |
| **Postcondiciones** | Solo peticiones con token válido y vigente acceden a recursos protegidos. |
| **Requerimientos asociados** | [RF-MS01-02](#rf-ms01-02), [RF-MS01-04](#rf-ms01-04), [RF-MS01-05](#rf-ms01-05), RF-GW (validación básica de token) |
| **Criterios de aceptación** | <ul><li>Un token vigente es aceptado.</li><li>Un token expirado, alterado o firmado con otra clave es rechazado con 401.</li><li>Un endpoint protegido sin cabeceras de identidad responde 401.</li><li>El token decodificado muestra los roles vigentes al momento del login.</li><li>Tras cambiar el rol de un usuario, su siguiente login emite un token con el nuevo rol.</li></ul> |
| **Prioridad** | Alta |
| **Estado / Squad responsable** | Pendiente / kAiRoS |

<br>

<a id="rf-ms01-04"></a>
### RF-MS01-04 Cerrar sesión (logout)

| Atributo | Detalle |
|---|---|
| **ID** | RF-MS01-04 |
| **Nombre** | Cerrar sesión (logout) |
| **Épica / Grupo funcional** | MS01 - Sesiones |
| **Descripción** | Permitir que el usuario autenticado cierre su sesión y quede registrado. |
| **Actor(es)** | Todos los roles autenticados |
| **Precondiciones** | El usuario posee un token válido. |
| **Flujo base** | <ol><li>El usuario solicita cerrar sesión con su token.</li><li>El sistema identifica al usuario autenticado (X-User-Id).</li><li>Emite a MS08 el evento de auditoría (acción LOGOUT).</li><li>Responde exitosamente. El cliente descarta el token y regresa al login.</li></ol> |
| **Flujos alternos / excepciones** | <ul><li>**A1, token inválido o expirado:** el sistema responde 401. El cliente descarta el token localmente.</li></ul> |
| **Reglas De Negocio** | <ul><li><a id="rn-ms01-10"></a>**RN-MS01-10:** El logout elimina la sesión en el cliente y queda auditado. El token sigue siendo técnicamente válido hasta expirar. Este límite del enfoque stateless se mitiga con una expiración corta.</li></ul> |
| **Postcondiciones** | El evento de cierre queda registrado. |
| **Requerimientos asociados** | [RF-MS01-03](#rf-ms01-03) |
| **Criterios de aceptación** | <ul><li>Un logout con token válido devuelve 200 y genera un registro de auditoría con el usuario y la fecha/hora.</li></ul> |
| **Prioridad** | Alta |
| **Estado / Squad responsable** | Pendiente / kAiRoS |

<br>

<a id="rf-ms01-05"></a>
### RF-MS01-05 Gestión de roles y permisos

| Atributo | Detalle |
|---|---|
| **ID** | RF-MS01-05 |
| **Nombre** | Gestión de roles y permisos |
| **Épica / Grupo funcional** | MS01 - Roles y permisos |
| **Descripción** | Permitir al administrador agregar y retirar roles a uno o varios usuarios y consultar los permisos de cada rol. |
| **Actor(es)** | Administrador<br>Secundarios: los demás roles, que consultan solo sus propios roles y permisos. |
| **Precondiciones** | El administrador está autenticado. El usuario objetivo existe. |
| **Flujo base** | <ol><li>El administrador busca usuarios (por correo o filtrando por rol) y consulta sus roles actuales.</li><li>Selecciona uno o varios usuarios y solicita agregar o retirar uno o varios roles.</li><li>El sistema valida que quien solicita es administrador (X-User-Role), que los usuarios objetivo no incluyen a quien solicita y que el resultado deja a cada usuario con al menos un rol.</li><li>Actualiza los roles de los usuarios.</li><li>Emite a MS08 el evento de auditoría (acción CAMBIO_ROL) con quién, a quién, rol anterior y rol nuevo, por cada usuario.</li><li>Responde con los roles resultantes por usuario.</li><li>Cualquier usuario autenticado puede consultar sus propios roles y los permisos asociados a ellos. Un no administrador solo recibe sus propios roles.</li></ol> |
| **Flujos alternos / excepciones** | <ul><li>**A1, el administrador intenta modificar sus propios roles:** el sistema responde 403 y emite a MS08 el evento de auditoría.</li><li>**A2, solicitante sin rol de administrador:** el sistema responde 403 y emite a MS08 el evento de auditoría.</li><li>**A3, usuario o rol inexistente:** el sistema responde 404 o 400 según el caso y no aplica ningún cambio (la operación es atómica).</li><li>**A4, algún usuario quedaría sin roles:** el sistema responde 400 y no aplica ningún cambio.</li></ul> |
| **Reglas De Negocio** | <ul><li><a id="rn-ms01-11"></a>**RN-MS01-11:** Solo el Administrador asigna o retira roles.</li><li><a id="rn-ms01-12"></a>**RN-MS01-12:** Nadie puede asignarse privilegios por sí mismo, incluido el administrador. El primer administrador se crea mediante un mecanismo de inicialización configurado fuera del repositorio.</li><li><a id="rn-ms01-13"></a>**RN-MS01-13:** Un usuario puede tener más de un rol, siempre con autorización del administrador.</li><li><a id="rn-ms01-14"></a>**RN-MS01-14:** El catálogo de roles (ESTUDIANTE, PROFESOR, ADMINISTRATIVO, BRIGADISTA, COORDINADOR, ORGANIZADOR, ADMIN, AUDITOR) y la matriz rol-permiso son definidos por el sistema (versionados en el código). No se editan en tiempo de ejecución. La matriz está en [`3_matriz-roles-permisos.md`](3_matriz-roles-permisos.md).</li></ul> |
| **Postcondiciones** | Los nuevos roles se reflejan en el siguiente token emitido ([RN-MS01-09](#rn-ms01-09)). |
| **Requerimientos asociados** | [RF-MS01-03](#rf-ms01-03) |
| **Criterios de aceptación** | <ul><li>Un administrador agrega o retira roles a uno o varios usuarios en una sola operación y la consulta posterior lo muestra.</li><li>Tras el cambio, el siguiente login del usuario emite un token con el nuevo rol.</li><li>Un administrador no puede modificar sus propios roles.</li><li>Un no administrador recibe 403.</li><li>Cada cambio queda auditado.</li></ul> |
| **Prioridad** | Media |
| **Estado / Squad responsable** | Pendiente / kAiRoS |

<br>

<a id="rf-ms01-06"></a>
### RF-MS01-06 Gestión del perfil

| Atributo | Detalle |
|---|---|
| **ID** | RF-MS01-06 |
| **Nombre** | Gestión del perfil |
| **Épica / Grupo funcional** | MS01 - Perfil |
| **Descripción** | Permitir al usuario consultar y actualizar su información básica de perfil. |
| **Actor(es)** | Todos los roles autenticados |
| **Precondiciones** | Usuario autenticado. |
| **Flujo base** | <ol><li>El usuario consulta su perfil (primer nombre, segundo nombre, primer apellido, segundo apellido, correo, roles, estado).</li><li>Envía los datos a modificar (primer nombre, segundo nombre, primer apellido, segundo apellido).</li><li>El sistema valida que los campos no estén vacíos y respeten longitud y formato.</li><li>Actualiza el perfil.</li><li>Responde con el perfil actualizado.</li></ol> |
| **Flujos alternos / excepciones** | <ul><li>**A1, datos inválidos:** el sistema responde 400 con el detalle de los campos y no modifica nada.</li><li>**A2, se intenta modificar correo, roles o estado:** el sistema rechaza la petición (400). Esos campos no son editables desde el perfil.</li></ul> |
| **Reglas De Negocio** | <ul><li><a id="rn-ms01-15"></a>**RN-MS01-15:** El correo institucional nunca puede ser modificado por el usuario.</li><li><a id="rn-ms01-16"></a>**RN-MS01-16:** Un usuario solo consulta y modifica su propio perfil (identificado por X-User-Id). Roles y estado no son editables aquí.</li></ul> |
| **Postcondiciones** | Los datos modificados persisten y se devuelven en consultas posteriores. |
| **Requerimientos asociados** | [RF-MS01-01](#rf-ms01-01), [RF-MS01-03](#rf-ms01-03) |
| **Criterios de aceptación** | <ul><li>La modificación válida se refleja en la siguiente consulta.</li><li>Un intento de cambiar el correo se rechaza con 400 y no tiene efecto.</li><li>Un usuario no puede ver ni editar el perfil de otro mediante este flujo.</li></ul> |
| **Prioridad** | Alta |
| **Estado / Squad responsable** | Pendiente / kAiRoS |

<br>

<a id="rf-ms01-07"></a>
### RF-MS01-07 Administración de usuarios

| Atributo | Detalle |
|---|---|
| **ID** | RF-MS01-07 |
| **Nombre** | Administración de usuarios: consulta y activación/inactivación |
| **Épica / Grupo funcional** | MS01 - Estado de usuarios |
| **Descripción** | Permitir al administrador listar y buscar usuarios, consultar su estado y cambiarlo entre ACTIVO e INACTIVO. |
| **Actor(es)** | Administrador |
| **Precondiciones** | El administrador está autenticado. El usuario objetivo existe. |
| **Flujo base** | <ol><li>El administrador lista o busca usuarios (por correo), con paginación y filtro opcional por estado o rol.</li><li>Selecciona uno o varios usuarios y solicita el nuevo estado.</li><li>El sistema valida que quien solicita es administrador y que no está modificando su propia cuenta.</li><li>Actualiza el estado.</li><li>Emite a MS08 el evento de auditoría (acción CAMBIO_ESTADO) por cada usuario.</li><li>Responde con el resultado por usuario.</li></ol> |
| **Flujos alternos / excepciones** | <ul><li>**A1, el administrador intenta cambiar su propio estado:** el sistema responde 403 y emite a MS08 el evento de auditoría.</li><li>**A2, solicitante sin rol de administrador:** el sistema responde 403.</li><li>**A3, usuario inexistente:** el sistema responde 404 y no aplica ningún cambio (la operación es atómica).</li><li>**A4, error en el proceso:** el sistema informa la causa y emite a MS08 el evento de auditoría del fallo.</li></ul> |
| **Reglas De Negocio** | <ul><li><a id="rn-ms01-17"></a>**RN-MS01-17:** Los únicos estados son ACTIVO e INACTIVO. Toda cuenta nueva nace ACTIVA.</li><li><a id="rn-ms01-18"></a>**RN-MS01-18:** Una cuenta INACTIVA no puede iniciar sesión ([RN-MS01-06](#rn-ms01-06)). Los tokens ya emitidos expiran por su vigencia ([RN-MS01-09](#rn-ms01-09)).</li><li><a id="rn-ms01-19"></a>**RN-MS01-19:** Solo el administrador cambia estados. No puede cambiar el propio.</li></ul> |
| **Postcondiciones** | El estado queda actualizado y condiciona el siguiente login |
| **Requerimientos asociados** | [RF-MS01-02](#rf-ms01-02), [RF-MS01-05](#rf-ms01-05) |
| **Criterios de aceptación** | <ul><li>Tras inactivar un usuario, su login falla con 403 aunque las credenciales sean correctas.</li><li>Tras reactivarlo, su login funciona.</li><li>Un administrador no puede inactivarse a sí mismo.</li><li>Cada cambio queda auditado.</li></ul> |
| **Prioridad** | Alta |
| **Estado / Squad responsable** | Pendiente / kAiRoS |

<br>

<a id="rf-ms01-08"></a>
### RF-MS01-08 Recuperación y cambio de contraseña

| Atributo | Detalle |
|---|---|
| **ID** | RF-MS01-08 |
| **Nombre** | Recuperación y cambio de contraseña |
| **Épica / Grupo funcional** | MS01 - Contraseña |
| **Descripción** | Permitir restablecer la contraseña olvidada mediante un proceso controlado y cambiarla estando autenticado. |
| **Actor(es)** | Todos los roles (restablecer: usuario no autenticado; cambiar: usuario autenticado). |
| **Precondiciones** | El usuario existe. Servicio de envío de correo configurado (institucional) |
| **Flujo base** | <ul><li><b>Flujo base A, restablecer contraseña olvidada:</b><ol><li>El usuario ingresa su correo institucional.</li><li>El sistema responde siempre con el mismo mensaje ("Si el correo está registrado, enviamos un código de recuperación.").</li><li>Si el correo corresponde a una cuenta ACTIVA, genera un código o token de un solo uso y con vigencia limitada, guarda solo su hash y lo envía al correo.</li><li>El usuario envía el código y la nueva contraseña con su confirmación.</li><li>El sistema valida el código (vigente y no usado) y la política de contraseña.</li><li>Actualiza la contraseña, invalida el código y emite a MS08 el evento de auditoría (acción RECUPERACION).</li><li>Responde éxito. El usuario debe iniciar sesión de nuevo.</li></ol></li><li><b>Flujo base B, cambiar contraseña autenticado:</b><ol><li>El usuario envía su contraseña actual, la nueva y la confirmación.</li><li>El sistema valida la contraseña actual y la política.</li><li>Actualiza la contraseña y emite a MS08 el evento de auditoría (acción CAMBIO_CONTRASENA).</li></ol></li></ul> |
| **Flujos alternos / excepciones** | <ul><li>**A1, código incorrecto, vencido o ya usado:** el sistema responde 400 con un mensaje genérico y emite a MS08 el evento de auditoría.</li><li>**A2, nueva contraseña inválida o confirmación distinta:** el sistema responde 400 y emite a MS08 el evento de auditoría.</li><li>**A3, contraseña actual incorrecta (flujo B):** el sistema responde 400 y emite a MS08 el evento de auditoría.</li></ul> |
| **Reglas De Negocio** | <ul><li><a id="rn-ms01-20"></a>**RN-MS01-20:** La respuesta de solicitud de código no revela si el correo existe.</li><li><a id="rn-ms01-21"></a>**RN-MS01-21:** El código es de un solo uso y tiene vigencia corta y configurable (15 min).</li><li><a id="rn-ms01-22"></a>**RN-MS01-22:** La nueva contraseña cumple [RN-MS01-03](#rn-ms01-03) y se almacena según [RN-MS01-04](#rn-ms01-04).</li></ul> |
| **Postcondiciones** | La contraseña anterior deja de funcionar. La nueva permite iniciar sesión. |
| **Requerimientos asociados** | [RF-MS01-01](#rf-ms01-01), [RF-MS01-02](#rf-ms01-02) |
| **Criterios de aceptación** | <ul><li>Con la contraseña anterior el login falla. Con la nueva funciona.</li><li>Un código usado o vencido es rechazado.</li><li>La solicitud de código tiene la misma respuesta para un correo existente y uno inexistente.</li><li>Todos los eventos quedan auditados.</li></ul> |
| **Prioridad** | Alta |
| **Estado / Squad responsable** | Pendiente / kAiRoS |

---

<a id="requerimientos-no-funcionales-rnf"></a>
## Requerimientos No Funcionales (RNF)

| ID | Requerimiento |
|---|---|
| **RNF-MS01-01** | Aplicación Spring Boot independiente (Java, Maven) con Spring Security, Spring Data JPA y base de datos PostgreSQL propia. Ningún otro servicio accede a ella. |
| **RNF-MS01-02** | Todos los endpoints bajo `/api/v1/`, documentados con OpenAPI/Swagger y publicados antes de que otros squads dependan de ellos. |
| **RNF-MS01-03** | El acceso externo se hace a través del API Gateway. MS01 recibe la identidad en X-User-Id, X-User-Role y X-Correlation-ID, y propaga el Correlation ID en toda llamada saliente, incluida MS08. |
| **RNF-MS01-04** | Errores consistentes con códigos HTTP apropiados (400, 401, 403, 404, 409) y cuerpo de error normalizado, sin exponer trazas internas. |
| **RNF-MS01-05** | Logs útiles para diagnóstico que inician con el Correlation ID, sin contraseñas ni tokens. Spring Boot Actuator habilitado. |
| **RNF-MS01-06** | Secretos (clave JWT, credenciales de BD y de correo) fuera del repositorio, mediante variables de entorno. |
| **RNF-MS01-07** | Pruebas unitarias y de integración de los flujos de registro, login, token, roles, estado, contraseña, lectura de cabeceras de identidad y emisión de eventos de auditoría a MS08. Análisis de calidad con SonarCloud. |
| **RNF-MS01-08** | Ejecución en contenedor Docker, con pipeline en GitHub Actions. Código integrado por Pull Request con revisión. |
| **RNF-MS01-09** | Entradas validadas en el servidor contra datos inválidos. Contraseñas con BCrypt. |
| **RNF-MS01-10** | La auditoría se delega en [ms08-analytics-and-audit](https://github.com/CampusSeguro-DOSW-2026-2/ms08-analytics-and-audit) (MS08), que almacena y consulta los eventos. MS01 los emite de forma asíncrona (acciones REGISTRO, LOGIN, LOGOUT, CAMBIO_ROL, CAMBIO_ESTADO, CAMBIO_CONTRASENA, RECUPERACION; resultado EXITO o FALLO) con el Correlation ID y sin contraseñas, hashes, códigos ni tokens. Si MS08 falla, la respuesta al cliente no cambia y el error se registra en logs. MS01 no tiene tablas ni endpoints de auditoría. |

---

<a id="api-ms01-identity-and-access"></a>
## API: ms01-identity-and-access
Usuarios, autenticación, roles, permisos y sesiones.

Contrato OpenAPI: [`ms01-identity-and-access.yml`](ms01-identity-and-access.yml). Los accesos “Público” no requieren JWT. Los demás reciben la identidad por cabeceras ([RN-MS01-23](#rn-ms01-23)).

| Categoría | Método | Ruta (/api/v1) | Acceso | RF asociado |
|---|---|---|---|---|
| **Autenticación y sesión** | POST | `/auth/register` | Público | [01](#rf-ms01-01) |
| | POST | `/auth/login` | Público | [02](#rf-ms01-02) |
| | POST | `/auth/logout` | Autenticado | [04](#rf-ms01-04) |
| **Recuperación y cambio de clave** | POST | `/auth/password/forgot` | Público | [08](#rf-ms01-08) |
| | POST | `/auth/password/reset` | Público | [08](#rf-ms01-08) |
| | PUT | `/users/me/password` | Autenticado | [08](#rf-ms01-08) |
| **Perfil propio** | GET | `/users/me` | Autenticado | [03](#rf-ms01-03), [06](#rf-ms01-06) |
| | PUT | `/users/me` | Autenticado | [06](#rf-ms01-06) |
| **Administración de usuarios** | GET | `/users` | ADMIN | [07](#rf-ms01-07) |
| | GET | `/users/{id}` | ADMIN | [05](#rf-ms01-05), [07](#rf-ms01-07) |
| | PATCH | `/users/status` | ADMIN | [07](#rf-ms01-07) |
| **Roles y permisos** | GET | `/roles` | Autenticado | [05](#rf-ms01-05) |
| | PATCH | `/users/roles` | ADMIN | [05](#rf-ms01-05) |

La consulta de auditoría no pertenece a MS01: la expone ms08-analytics-and-audit (RNF-MS01-10).