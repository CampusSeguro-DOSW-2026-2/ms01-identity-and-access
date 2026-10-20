# Diccionario de Datos, Formularios y Entradas

## Índice de Contenido
1. [Reglas generales de entrada](#reglas-generales-de-entrada)
2. [Formularios y Entradas](#formularios-y-entradas)
   - [F-01 · Registro de usuario](#f-01)
   - [F-02 · Inicio de sesión](#f-02)
   - [F-03 · Perfil de usuario](#f-03)
   - [F-04 · Solicitud de recuperación de contraseña](#f-04)
   - [F-05 · Restablecer contraseña con código](#f-05)
   - [F-06 · Cambio de contraseña autenticado](#f-06)
   - [F-07 · Búsqueda y filtros de usuarios](#f-07)
   - [F-08 · Cambio de estado de usuario](#f-08)
   - [F-09 · Asignación de roles](#f-09)
   - [F-10 · Consulta de auditoría](#f-10)

---

<a id="reglas-generales-de-entrada"></a>
## Reglas generales de entrada

* **RG-01**: Todo texto se recorta (trim) de espacios al inicio y al final antes de validar. El correo se normaliza a minúsculas. La contraseña no se recorta ni se normaliza.
* **RG-02**: Un campo obligatorio vacío o con solo espacios se considera faltante.
* **RG-03**: Se rechazan entradas con etiquetas HTML o scripts (<, >) en campos de texto libre.
* **RG-04**: Se ignoran o rechazan campos no definidos en el contrato (en especial roles, estado o id enviados por el usuario en registro y perfil).
* **RG-05**: Los mensajes de error indican el campo y la regla incumplida, salvo en login y recuperación (**RN-MS01-05**, **RN-MS01-20**).
* **RG-06**: Los códigos de error de validación son HTTP 400. Un conflicto de unicidad es 409.

---

<a id="formularios-y-entradas"></a>
## Formularios y Entradas

<a id="f-01"></a>
### F-01 · Registro de usuario
Referencia: **RF-MS01-01**

| Campo | Tipo | Obligatorio | Longitud / formato | Reglas de validación | Mensaje de error | Ejemplo válido |
|---|---|---|---|---|---|---|
| Nombre Coompleto | Texto | Sí | 2 a 125 caracteres | Solo letras (con tildes y ñ), espacios, guion y apóstrofe. Sin números ni símbolos. | "Los nombres completos deben tener entre 2 y 125 letras." | María José |
| Correo institucional | Correo | Sí | Máx. 100 caracteres | Formato de correo válido. Dominio igual al institucional configurado. Único en el sistema (sin distinguir mayúsculas). | Formato o dominio: "Correo no válido." Duplicado (409): "El correo ya está registrado." | maria.perez@escuelaing.edu.co |
| Contraseña | Texto oculto | Sí | 8 a 64 caracteres | Al menos 1 mayúscula, 1 minúscula, 1 número y 1 carácter especial. Sin espacios. | "La contraseña debe tener 8 a 64 caracteres, con mayúscula, minúscula, número y carácter especial." | Campus#2026 |
| Confirmar contraseña | Texto oculto | Sí | Igual a Contraseña | Debe ser idéntica a Contraseña. | "Las contraseñas no coinciden." | Campus#2026 |

*Mockup F-01: (espacio reservado para insertar imagen del formulario de registro)*

---

<a id="f-02"></a>
### F-02 · Inicio de sesión
Referencia: **RF-MS01-02**

| Campo | Tipo | Obligatorio | Longitud / formato | Reglas de validación | Mensaje de error | Ejemplo válido |
|---|---|---|---|---|---|---|
| Correo institucional | Correo | Sí | Máx. 100 caracteres | Formato de correo válido. | Formato (400): "Ingrese un correo válido." | maria.perez@escuelaing.edu.co |
| Contraseña | Texto oculto | Sí | 1 a 64 caracteres | No vacía. No se revalida la política en el login. | Vacía (400): "Ingrese su contraseña." | Campus#2026 |

**Mensajes globales de formulario:**
* Errores de autenticación: credenciales incorrectas o cuenta inexistente (401): "Credenciales incorrectas."
* Cuenta inactiva (403): "Cuenta inactiva. Contacte al administrador."

*Mockup F-02: (espacio reservado para insertar imagen del formulario de login)*

---

<a id="f-03"></a>
### F-03 · Perfil de usuario
Referencia: **RF-MS01-06**

| Campo | Tipo | Obligatorio | Editable | Longitud / formato | Reglas de validación | Mensaje de error |
|---|---|---|---|---|---|---|
| Nombre Completo | Texto | Sí | Sí | 2 a 125 caracteres | Misma regla que F-01. | "Los nombres deben tener entre 2 y 125 letras." |
| Correo institucional | Correo | — | No (solo lectura) | — | Cualquier intento de cambio se rechaza (**RN-MS01-15**). | "El correo no se puede modificar." |
| Roles | Lista | — | No (solo lectura) | — | Se modifican solo por el administrador ([F-09](#f-09)). | "Los roles no se pueden modificar desde el perfil." |
| Estado | Etiqueta | — | No (solo lectura) | ACTIVO / INACTIVO | Se modifica solo por el administrador ([F-08](#f-08)). | "El estado no se puede modificar desde el perfil." |

*Mockup F-03: (espacio reservado — insertar imagen de la pantalla de perfil)*

---

<a id="f-04"></a>
### F-04 · Solicitud de recuperación de contraseña
Referencia: **RF-MS01-08**

| Campo | Tipo | Obligatorio | Longitud / formato | Reglas de validación | Mensaje de error | Ejemplo válido |
|---|---|---|---|---|---|---|
| Correo institucional | Correo | Sí | Máx. 100 caracteres | Formato de correo válido. | Formato (400): "Ingrese un correo válido." | maria.perez@escuelaing.edu.co |

**Mensaje global de formulario:**
Respuesta única (exista o no el correo): "Si el correo está registrado, enviamos un código de recuperación."

*Mockup F-04: (espacio reservado para insertar imagen)*

---

<a id="f-05"></a>
### F-05 · Restablecer contraseña con código
Referencia: **RF-MS01-08**

| Campo | Tipo | Obligatorio | Longitud / formato | Reglas de validación | Mensaje de error | Ejemplo válido |
|---|---|---|---|---|---|---|
| Correo institucional | Correo | Sí | Máx. 100 caracteres | Formato de correo válido. | "Ingrese un correo válido." | maria.perez@escuelaing.edu.co |
| Código de recuperación | Texto numérico | Sí | 6 dígitos | Solo dígitos. Vigente (15 min por defecto) y no usado. | "Código incorrecto o vencido." (genérico) | 482913 |
| Nueva contraseña | Texto oculto | Sí | 8 a 64 caracteres | Misma política que F-01. | "La contraseña debe tener 8 a 64 caracteres, con mayúscula, minúscula, número y carácter especial." | Nueva#2026 |
| Confirmar nueva contraseña | Texto oculto | Sí | Igual a Nueva contraseña | Debe ser idéntica. | "Las contraseñas no coinciden." | Nueva#2026 |

*Mockup F-05: (espacio reservado para insertar imagen)*

---

<a id="f-06"></a>
### F-06 · Cambio de contraseña autenticado
Referencia: **RF-MS01-08**

| Campo | Tipo | Obligatorio | Longitud / formato | Reglas de validación | Mensaje de error | Ejemplo válido |
|---|---|---|---|---|---|---|
| Contraseña actual | Texto oculto | Sí | 1 a 64 caracteres | Debe coincidir con la almacenada. | "La contraseña actual es incorrecta." | Campus#2026 |
| Nueva contraseña | Texto oculto | Sí | 8 a 64 caracteres | Misma política que F-01. Distinta de la actual. | "La nueva contraseña no cumple la política o es igual a la actual." | Nueva#2026 |
| Confirmar nueva contraseña | Texto oculto | Sí | Igual a Nueva contraseña | Debe ser idéntica. | "Las contraseñas no coinciden." | Nueva#2026 |

*Mockup F-06: (espacio reservado para insertar imagen)*

---

<a id="f-07"></a>
### F-07 · Búsqueda y filtros de usuarios
Referencia: **RF-MS01-07**

| Campo | Tipo | Obligatorio | Longitud / formato | Reglas de validación | Mensaje de error | Ejemplo válido |
|---|---|---|---|---|---|---|
| Búsqueda por correo | Texto | No | 1 a 100 caracteres | Búsqueda parcial. Sin etiquetas HTML. | "Búsqueda no válida." | maria.perez |
| Filtro de estado | Lista | No | ACTIVO / INACTIVO / Todos | Solo valores del catálogo. | "Estado no válido." | ACTIVO |
| Filtro de rol | Lista | No | Uno de los 8 roles / Todos | Solo valores del catálogo. | "Rol no válido." | BRIGADISTA |
| Página | Número entero | No | ≥ 0 (por defecto 0) | Entero no negativo. | "Página no válida." | 0 |
| Tamaño de página | Número entero | No | 1 a 100 (por defecto 20) | Entero en el rango. | "Tamaño de página no válido (1 a 100)." | 20 |

*Mockup F-07: (espacio reservado para insertar imagen del listado de usuarios)*

---

<a id="f-08"></a>
### F-08 · Cambio de estado de usuario
Referencia: **RF-MS01-07**

| Campo | Tipo | Obligatorio | Longitud / formato | Reglas de validación | Mensaje de error | Ejemplo válido |
|---|---|---|---|---|---|---|
| Usuario(s) | Selección de uno o más usuarios | Sí | Ids existentes | Deben existir. No puede incluir al propio administrador. | Inexistente (404): "Usuario no encontrado." Propio (403): "No puede cambiar su propio estado." | USR-1034 |
| Nuevo estado | Lista | Sí | ACTIVO / INACTIVO | Solo valores del catálogo. | "Estado no válido." | INACTIVO |

*Mockup F-08: (espacio reservado para insertar imagen de la confirmación de cambio de estado)*

---

<a id="f-09"></a>
### F-09 · Asignación de roles
Referencia: **RF-MS01-05**

| Campo | Tipo | Obligatorio | Longitud / formato | Reglas de validación | Mensaje de error | Ejemplo válido |
|---|---|---|---|---|---|---|
| Usuario | Selección | Sí | Id existente | Debe existir. No puede ser el propio administrador. | Inexistente (404): "Usuario no encontrado." Propio (403): "No puede modificar sus propios roles." | USR-1034 |
| Roles | Selección múltiple | Sí | 1 a 8 roles | Solo roles del catálogo, sin repetir. Debe quedar al menos un rol. | "Seleccione al menos un rol válido." | ESTUDIANTE, BRIGADISTA |

*Mockup F-09: (espacio reservado para insertar imagen de la edición de roles)*

---

<a id="f-10"></a>
### F-10 · Consulta de auditoría
Referencia: **RF-MS01-09**

| Campo | Tipo | Obligatorio | Longitud / formato | Reglas de validación | Mensaje de error | Ejemplo válido |
|---|---|---|---|---|---|---|
| Usuario | Texto | No | 1 a 100 caracteres | Correo o id de usuario. | "Usuario no válido." | maria.perez@escuelaing.edu.co |
| Acción | Lista | No | Catálogo: REGISTRO, LOGIN, LOGOUT, CAMBIO_ROL, CAMBIO_ESTADO, CAMBIO_CONTRASENA, RECUPERACION | Solo valores del catálogo. | "Acción no válida." | LOGIN |
| Resultado | Lista | No | EXITO / FALLO / Todos | Solo valores del catálogo. | "Resultado no válido." | FALLO |
| Fecha desde | Fecha y hora | No | ISO 8601 | Formato válido. No futura. | "Fecha no válida." | 2026-10-01T00:00 |
| Fecha hasta | Fecha y hora | No | ISO 8601 | Mayor o igual que Fecha desde. No futura. | "La fecha final debe ser posterior a la inicial." | 2026-10-17T23:59 |
| Página y tamaño | Números enteros | No | Página ≥ 0, tamaño 1 a 100 | Igual que [F-07](#f-07). | "Paginación no válida." | 0 / 20 |

*Mockup F-10: (espacio reservado para insertar imagen de la consulta de auditoría)*