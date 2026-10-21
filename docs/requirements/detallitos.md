# 📓 Bitácora de Investigación: Arquitectura de Microservicios

Este documento recopila las investigaciones, decisiones técnicas y buenas prácticas adoptadas para el desarrollo del ecosistema de microservicios.

---

## 🧭 Índice de Módulos
*   [Módulo 1: Autenticación con JWT y Estrategia de Validación](#módulo-1-autenticación-con-jwt-y-estrategia-de-validación)
*   [Módulo 2: Trazabilidad Extrema con Correlation ID](#módulo-2-trazabilidad-extrema-con-correlation-id)
*   [Módulo 3: Centralización de Auditoría y Analítica](#módulo-3-centralización-de-auditoría-y-analítica)
*   [Módulo 4: Integración de Servicios, Gestión de Perfiles y Control de Roles](#módulo-4-integración-de-servicios-gestión-de-perfiles-y-control-de-roles) 
*  
*   *(Espacio para añadir futuros módulos de investigación...)*

---

## Módulo 1: Autenticación con JWT y Estrategia de Validación

### 📄 Contexto Rápido
Análisis sobre cómo controlar el acceso de los usuarios a los diferentes servicios del ecosistema de manera segura y eficiente, evitando la saturación de las bases de datos en cada petición.

### 🧩 Glosario del Módulo
*   **MS_Identidad:** El componente "portero". Se encarga de validar credenciales (`POST /auth/login`) y generar la llave de acceso.
*   **JWT (JSON Web Token):** La "pulsera de acceso". Una credencial digital compacta que el cliente envía en la cabecera de cada petición (`Authorization: Bearer <TOKEN>`).
*   **API Gateway:** La aduana de entrada. Recibe todo el tráfico externo y lo redirige internamente hacia la red privada.
*   **Microservicios de Negocio (MS01, MS02, etc.):** Los encargados de la lógica pura de la aplicación (ej. gestión de alumnos, carga de notas).

### ⚙️ Flujo Técnico
1. El cliente realiza un `POST` con correo y contraseña al API Gateway, el cual redirige la solicitud al `MS_Identidad`.
2. `MS_Identidad` valida los datos y responde al cliente con un **JWT** firmado.
3. El cliente almacena ese JWT y lo incluye en los *Headers* para todas las consultas posteriores.

### 🧠 Alternativas Evaluadas
*   **Estrategia A (Descentralizada):** El Gateway pasa el JWT intacto a cada microservicio y cada uno valida la firma criptográfica de forma local.
*   **Estrategia B (Centralizada - SELECCIONADA):** El Gateway recibe el JWT, comprueba su validez/expiración, lo "desempaqueta" y transfiere a los microservicios internos únicamente cabeceras HTTP limpias con los datos esenciales (ej. `X-User-Id: 45`, `X-User-Role: estudiante`).

### 🎯 Justificación de la Estrategia B
*   **Aislamiento de complejidad:** Los microservicios de negocio (`MS01`, `MS02`) no requieren configuraciones de seguridad complejas ni librerías criptográficas. Solo leen encabezados de texto plano.
*   **Punto único de modificación:** Si las reglas de seguridad cambian o se requiere revocar tokens, la modificación se realiza exclusivamente en el **API Gateway**, sin necesidad de alterar el resto de los servicios.

---

## Módulo 2: Trazabilidad Extrema con Correlation ID

### 📄 Contexto Rápido
En un entorno de microservicios, una acción del cliente desencadena llamadas en cascada (Gateway -> MS_Identidad -> MS01). Si ocurre un fallo a mitad de camino, rastrear el error en logs separados es inviable. Se requiere un identificador único transversal.

### 🧩 Glosario del Módulo
*   **Correlation ID:** Un código único universal (UUID aleatorio como `c19b-472d`) generado en la aduana de entrada para marcar el ciclo de vida completo de una petición.

### ⚙️ Flujo Técnico
1. El API Gateway recibe la petición externa y **genera un UUID único**.
2. Antes de distribuir la petición a los microservicios internos, el Gateway inyecta este código en la cabecera HTTP `X-Correlation-ID`.
3. **Regla arquitectónica:** Si `MS01` necesita consumir datos de `MS02`, es obligatorio leer esa cabecera y arrastrarla en la nueva llamada HTTP.
4. Cada microservicio debe configurar su sistema de logs para imprimir de forma obligatoria este UUID al inicio de cada mensaje.

### 🎯 Justificación de la Solución
Permite ingresar a la plataforma de monitoreo de logs, buscar el UUID específico y reconstruir la línea de tiempo exacta de la petición a través de todos los servidores de manera cronológica.

---

## Módulo 3: Centralización de Auditoría y Analítica

### 📄 Contexto Rápido
Evaluación del mecanismo idóneo para registrar las acciones del sistema con fines estadísticos y de seguridad, garantizando que este proceso no penalice los tiempos de respuesta del usuario final.

### 🧩 Glosario del Módulo
*   **MS_Auditoría:** La "caja negra" del ecosistema. Almacena de forma centralizada los eventos del sistema para procesar métricas y bitácoras históricas.

### ⚙️ Flujo Técnico
1. El procesamiento de auditoría se ejecuta de forma **asíncrona** (en segundo plano) para liberar la conexión del cliente rápidamente.
2. Tras procesar con éxito una acción, el Gateway o los microservicios (`MS01`) envían un reporte secundario hacia el `MS_Auditoría`.
3. **Mecanismo de enlace:** El reporte de auditoría viaja acompañado de manera obligatoria por el `X-Correlation-ID` generado en el Módulo 2.

### 🎯 Justificación de la Solución
El `MS_Auditoría` puede indexar millones de registros procedentes de fuentes distribuidas y agruparlos mediante el UUID. Esto facilita auditorías de seguridad eficaces desde una única interfaz de control.

---

## Módulo 4: Integración de Servicios, Gestión de Perfiles y Control de Roles

### 📄 Contexto Rápido
Análisis de la estrategia para conectar los microservicios de negocio (`MS01`, `MS02`) con el servicio de identidad, permitiendo que hereden los roles/permisos de manera eficiente y delegando correctamente la edición de datos del perfil (restringiendo el cambio de correo electrónico).

### 🧩 Glosario del Módulo
*   **Claims:** Los datos de identidad y permisos (como `userId` o `role`) que viajan encriptados dentro del cuerpo del JWT.
*   **Inyección de Cabeceras:** Proceso mediante el cual el API Gateway traduce los *claims* del JWT en encabezados HTTP estándar de texto plano antes de enviar la petición a la red interna.
*   **Inmutabilidad de Campos:** Regla de negocio que impide la modificación de un dato crítico (en este caso, el correo electrónico) durante una operación de actualización.

### ⚙️ Flujo Técnico

#### A. Propagación de Roles y Permisos (Lectura)
Para evitar que los microservicios de negocio saturen al servicio de identidad consultando permisos en cada petición, la información viaja de forma descendente gracias al Gateway:

Usa el código con precaución.
[Cliente] --- (JWT con rol) ---> [API Gateway]
|
(Valida JWT y extrae "role")
|
v
[MS01 / MS02] <--- (X-User-Role: estudiante) --- (Inyecta cabeceras planas)

1. El cliente incluye el JWT en la cabecera `Authorization: Bearer <TOKEN>`.
2. El API Gateway intercepta la petición, valida la firma, extrae el rol de los *claims* del token y limpia la petición original.
3. El Gateway inyecta cabeceras HTTP planas hacia el microservicio destino:
   ```http
   X-User-Id: 12345
   X-User-Role: estudiante
   X-Correlation-ID: c19b-472d-a12b
   ```
4. El microservicio de negocio lee `X-User-Role` y decide si otorga o deniega el acceso a la lógica solicitada, sin realizar llamadas de red adicionales.

#### B. Modificación de Datos de Perfil (Escritura)
La actualización del perfil es una operación centralizada en el microservicio de identidad, dueño de la base de datos de usuarios.

1. El cliente envía un `PUT /auth/perfil` con el JWT y el JSON de los campos modificados.
2. El Gateway procesa el JWT, inyecta el `X-User-Id` extraído del token (para asegurar que un usuario solo pueda modificarse a sí mismo) y redirige la solicitud internamente.
3. El microservicio de identidad recibe la estructura y aplica un filtro estricto (capa DTO) donde los campos inmutables como el `email` son ignorados o rechazados. Solo se actualizan campos permitidos (nombre, teléfono, contraseña, etc.).

### 🎯 Justificación de la Solución
*   **Desacoplamiento total:** Los microservicios de negocio operan de forma autónoma. No necesitan conocer la estructura de la base de datos de usuarios ni conectarse al servicio de identidad para validar permisos en el día a día.
*   **Garantía de Integridad:** Centralizar la actualización de perfiles en el servicio de identidad asegura que las reglas críticas (como no permitir el cambio de correo electrónico) se apliquen de manera uniforme en un solo punto del sistema, evitando inconsistencias o brechas de seguridad.

---

## Módulo 5: Estrategia de Pruebas de Integración para el Flujo de Cabeceras

### 📄 Contexto Rápido
Dado que los microservicios de negocio (`MS01`, `MS02`) confían ciegamente en las cabeceras HTTP limpias (`X-User-Id`, `X-User-Role`) que envía el API Gateway, se requiere un conjunto de pruebas de integración automatizadas para verificar que el Gateway intercepte correctamente los JWT, bloquee peticiones inválidas y propague la identidad sin alteraciones.

### 🧩 Glosario del Módulo
*   **Prueba de Integración E2E (End-to-End):** Pruebas que validan el comportamiento del sistema simulando el camino real que recorre una petición desde el cliente a través de múltiples componentes.
*   **Mocking de Servicios:** Simulación del comportamiento de un microservicio real para aislar el componente que se está probando (en este caso, simular `MS01` para verificar lo que le llega desde el Gateway).

### ⚙️ Escenarios de Prueba Obligatorios

Para garantizar la estabilidad de la arquitectura, las herramientas de pruebas (como Postman, Newman o frameworks de código como Supertest) deben ejecutar de forma automatizada los siguientes tres escenarios:

#### Escenario 1: Petición Válida (Flujo Feliz)
*   **Condición inicial:** El cliente envía un JWT válido con el rol `estudiante` hacia una ruta protegida (ej: `GET /api/v1/cursos`).
*   **Comportamiento esperado del Gateway:** Debe validar la firma del token con éxito, remover el encabezado `Authorization` original para no exponerlo internamente, y redirigir la petición inyectando `X-User-Role: estudiante` y un `X-Correlation-ID` nuevo.
*   **Resultado esperado:** El microservicio interno recibe las cabeceras limpias y retorna un código `200 OK`.

#### Escenario 2: Petición con Token Inválido o Expirado
*   **Condición inicial:** El cliente envía un token alterado matemáticamente o cuya fecha de expiración ya caducó.
*   **Comportamiento esperado del Gateway:** El Gateway debe rechazar la petición inmediatamente en la frontera perimetral. **No debe transferir** la llamada a ningún microservicio interno.
*   **Resultado esperado:** El Gateway responde directamente al cliente con un código `401 Unauthorized` y un mensaje claro de error.

#### Escenario 3: Intento de Inyección Maliciosa desde el Exterior
*   **Condición inicial:** Un atacante intenta saltarse la seguridad enviando una petición que incluye directamente las cabeceras internas en el texto (ej: envía de forma manual `X-User-Role: administrador` en su petición pública).
*   **Comportamiento esperado del Gateway:** El Gateway debe aplicar una política de **limpieza estricta (*Sanitization*)**. Debe destruir cualquier cabecera que empiece con `X-` proveniente del exterior y sobrescribirla únicamente con los datos reales extraídos del JWT verificado.
*   **Resultado esperado:** Si el JWT del atacante dice que es `estudiante`, el Gateway borrará el intento de fraude e inyectará `X-User-Role: estudiante`. El sistema permanece seguro.

### 🎯 Justificación de la Solución
Automatizar estas pruebas en el pipeline de Integración Continua (CI/CD) asegura que cualquier cambio en la configuración del API Gateway o en la estructura del JWT sea detectado de inmediato antes de pasar a producción, blindando la comunicación interna del ecosistema.
