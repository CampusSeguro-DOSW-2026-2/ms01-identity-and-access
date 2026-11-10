# MS01 - IDENTITY AND ACCESS

MS01 - Users, authentication, roles, permissions, and sessions service.

| Name                            | GitHub Username                   |
|---------------------------------|-----------------------------------|
| Daniel Santiago Morales Perdomo | `Fukuyaku88`                      |
| Edgar Daniel Ruiz Patiño        | `DaniRuiz11`                      |
| Miguel Ángel Acero Laverde      | `Miguelim101`                     |
| Thomas Sebastian García Gomez   | `thomassebastiangarciagomez-ctrl` |

---

Documentación (requerimientos, contratos, modelo de datos y diagramas): [`ms01-identity-and-access/`](https://github.com/CampusSeguro-DOSW-2026-2/documentation/tree/main/ms01-identity-and-access) en el repositorio `documentation`.

## Ejecución local

Requisitos: JDK 21, Maven 3.9 y Docker. El `docker-compose.yml` solo levanta la infraestructura: PostgreSQL 17 en `5432` y Mailpit (SMTP en `1025`, bandeja en http://localhost:8025). MS01 corre con Maven en `http://localhost:8081`.

1. Variables. Los valores por defecto de `application.yaml` ya sirven en local. Para cambiar alguno, copie la plantilla y defina las mismas variables en la terminal o en el IDE (Maven no lee `.env`):
   ```bash
   cp .env.example .env        # Linux / macOS
   copy .env.example .env      # Windows (cmd o PowerShell)
   ```
2. Volumen de la BD. Se crea una sola vez, porque el compose lo declara externo:
   ```bash
   docker volume create campus-postgres-data
   ```
3. Infraestructura. Al crear el volumen, Postgres ejecuta `database/*.sql`:
   ```bash
   docker compose up -d
   ```
4. MS01, con Swagger en http://localhost:8081/swagger-ui.html:
   ```bash
   SWAGGER_ENABLED=true mvn spring-boot:run                    # Linux / macOS
   $env:SWAGGER_ENABLED="true"; mvn spring-boot:run            # Windows (PowerShell)
   ```
   Sin `JWT_PRIVATE_KEY`, MS01 genera una llave efímera: los tokens dejan de valer al reiniciar. Sin `GATEWAY_URL`, la auditoría solo va al log.
5. Pruebas:
   ```bash
   mvn verify
   ```
6. Imagen Docker (la misma que publica el CI):
   ```bash
   docker build -t ms01-identity-and-access .
   ```

## CI/CD

| Workflow | Cuándo | Qué hace |
|---|---|---|
| `ci.yml` | Cada push y PR | Compila y prueba (`mvn verify`), y analiza en SonarCloud si existe `SONAR_TOKEN`. Construye la imagen. Desde `develop` y `main` la publica en `ghcr.io/campusseguro-dosw-2026-2/ms01-identity-and-access` con `:develop` o `:latest` y `:sha-xxxxxxx`. |
| `ci.yml` → `deploy.yml` | Push a `develop` | Despliega esa imagen en el entorno de **prueba** (`:8443`). |
| `deploy-prod.yml` | A mano, desde `main` | Despliega en **producción** (`:443`) una imagen `sha-xxxxxxx` que ya pasó por prueba, sin reconstruirla. Otro tag anterior es el rollback. |

## Despliegue en la nube (prueba y producción)

MS01 corre en su propia VM, detrás del API Gateway (ADR-006 del GW):
* El firewall solo acepta la IP del GW.
* Un Caddy de borde exige el certificado de cliente del GW (mTLS).
* Prueba y producción comparten la VM, cada una con su stack (MS01 + PostgreSQL), sus secretos y sus certificados:

| Puerto de la VM | Uso |
|---|---|
| `443` | Producción (`ms01-prod`) |
| `8443` | Prueba (`ms01-test`) |
| `22` | SSH, solo desde la IP de quien administra |

Los pasos 1 a 6 se hacen una sola vez. Los comandos marcados *(VM)* van en la VM por SSH; los demás, en su PC.

### 1. VM y firewall
1. Cree una VM **Ubuntu 24.04 LTS x86_64** con IP pública **estática** (Azure: IP Standard estática; AWS: Elastic IP).
2. Reglas de entrada del NSG (en AWS, Security Group):

   | Puerto | Origen |
   |---|---|
   | `443/tcp` y `8443/tcp` | Solo `<IP del GW>/32` |
   | `22/tcp` | Solo su IP |

   No abra nada más: ni `8081` ni `5432`. La salida queda abierta, porque MS01 necesita SMTP, GitHub y el GW.
3. Instale Docker *(VM)*:
   ```bash
   curl -fsSL https://get.docker.com | sudo sh
   sudo usermod -aG docker "$USER"   # cierre la sesión SSH y vuelva a entrar
   ```

### 2. Intercambio con el dueño del GW
Por un canal privado. Detalle en la guía [Conexión al API Gateway](https://github.com/CampusSeguro-DOSW-2026-2/documentation/wiki/Conexi%C3%B3n-al-API-Gateway).

| Usted entrega | Recibe, por entorno (prueba y producción) |
|---|---|
| La IP estática de la VM | `servidor.crt`, `servidor.key` y `ca.pem` |
| La llave pública del JWT de cada entorno (paso 5) | `GATEWAY_SERVICE_KEY` |
| | El dominio del GW, para `GATEWAY_URL` |

### 3. Borde Caddy (mTLS)
1. Plantillas del GW *(VM)*:
   ```bash
   mkdir -p ~/campusseguro/edge && cd ~/campusseguro/edge
   curl -fsSLO https://raw.githubusercontent.com/CampusSeguro-DOSW-2026-2/api-gateway/develop/infra/caddy/Caddyfile.microservicio
   curl -fsSLO https://raw.githubusercontent.com/CampusSeguro-DOSW-2026-2/api-gateway/develop/infra/caddy/docker-compose.microservicio.yml
   printf 'UPSTREAM_PROD=ms01-prod:8081\nUPSTREAM_TEST=ms01-test:8081\n' > .env
   ```
2. Certificados: en su PC, ponga los archivos recibidos en esta estructura y cópiela a la VM. `scp` funciona igual en Linux y en Windows (PowerShell):
   ```text
   certs/prod/servidor.crt  certs/prod/servidor.key  certs/prod/ca.pem
   certs/test/servidor.crt  certs/test/servidor.key  certs/test/ca.pem
   ```
   ```bash
   scp -r certs <usuario>@<IP de la VM>:~/campusseguro/edge/
   ```
3. Levante el borde *(VM)*:
   ```bash
   cd ~/campusseguro/edge
   chmod 600 certs/*/servidor.key
   docker compose -f docker-compose.microservicio.yml up -d
   ```
   El borde crea la red `campus-edge`, a la que se unen los stacks. Mientras no haya stacks, responde `502`, y solo a quien presente el certificado del GW.

### 4. Runner self-hosted
1. En GitHub: **Settings › Actions › Runners › New self-hosted runner** (Linux, x64). Ejecute en la VM los comandos de *Download* que muestra la página.
2. Al configurarlo, agregue la etiqueta `ms01` y luego instálelo como servicio *(VM)*:
   ```bash
   ./config.sh --url https://github.com/CampusSeguro-DOSW-2026-2/ms01-identity-and-access --token <TOKEN de la página> --labels ms01 --unattended
   sudo ./svc.sh install "$USER"
   sudo ./svc.sh start
   ```
   El runner corre con su usuario, que ya está en el grupo `docker`.
3. El repositorio es público: en **Settings › Actions › General › Fork pull request workflows from outside collaborators**, elija **Require approval for all external contributors**. Así un PR externo no puede ejecutarse en la VM.

### 5. Llaves del JWT (una por entorno)
En su PC. En Windows, use Git Bash:
```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-test.pem
openssl pkey -in jwt-test.pem -pubout -out jwt-test.pub.pem
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-prod.pem
openssl pkey -in jwt-prod.pem -pubout -out jwt-prod.pub.pem
```
* `jwt-<entorno>.pem` es la llave privada: va al secreto `JWT_PRIVATE_KEY` de ese entorno.
* `jwt-<entorno>.pub.pem` va al dueño del GW, que la pone en su `JWT_PUBLIC_KEY`.
* Guarde los archivos fuera del repositorio. `.gitignore` ya ignora `*.pem`.

### 6. Environments de GitHub
Cree `ms01-test` y `ms01-prod` en **Settings › Environments**:
* `ms01-test`: en **Deployment branches**, permita solo `develop`.
* `ms01-prod`: active **Required reviewers** y, en **Deployment branches**, permita solo `main`.

| En `ms01-test` y `ms01-prod` | Tipo | Valor |
|---|---|---|
| `GATEWAY_URL` | Variable | Prueba: `https://<dominio del GW>:8443`. Producción: `https://<dominio del GW>`. |
| `MAIL_HOST` | Variable (opcional) | Servidor SMTP con STARTTLS. Por defecto `smtp.gmail.com`. |
| `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_FROM` | Variable (opcional) | Por defecto `587`, `proyectometx@gmail.com` y `CampusSeguro <proyectometx@gmail.com>`. Con Gmail el remitente debe ser la misma cuenta. |
| `BOOTSTRAP_ADMIN_EMAIL` | Variable (opcional) | Correo del primer administrador. Solo se crea si no existe ningún ADMIN. |
| `MS01_EXTRA_ENV` | Variable (opcional) | Líneas `CLAVE=valor` extra, p. ej. `INTERNAL_ALLOWED_SERVICES=ms03,ms05` o `RECOVERY_*`. |
| `DEPLOY_DIR` | Variable (opcional) | `~/campusseguro/ms01` por defecto. |
| `DB_PASSWORD` | Secreto | `openssl rand -base64 24`. Solo se aplica cuando se crea la BD (primer despliegue). |
| `JWT_PRIVATE_KEY` | Secreto | Contenido completo de `jwt-<entorno>.pem` (paso 5). |
| `GATEWAY_SERVICE_KEY` | Secreto | La que entregó el dueño del GW para ese entorno. |
| `MAIL_PASSWORD` | Secreto | Contraseña de aplicación de Google de `proyectometx@gmail.com` (Cuenta de Google > Seguridad > Contraseñas de aplicaciones; requiere la verificación en 2 pasos). Sin ella no salen los correos de recuperación. |
| `BOOTSTRAP_ADMIN_PASSWORD` | Secreto (opcional) | Contraseña del primer administrador. |

Ningún valor puede contener comillas simples (`'`).

### 7. Desplegar
1. **Prueba:** cada merge o push a `develop` publica la imagen y la despliega en `ms01-test`. Siga el job **desplegar-test** en la pestaña *Actions*.
2. **Producción:** después de mezclar en `main`, vaya a **Actions › Desplegar a producción › Run workflow**, rama `main`, con el tag de la imagen que ya funciona en prueba. Es `sha-` más los 7 primeros caracteres del commit de `develop` que se desplegó, y aparece en el log de *desplegar-test*.

`infra/deploy.sh` verifica cada despliegue dentro de la VM:
* `/actuator/health` responde 200 por la red `campus-edge`;
* el borde rechaza conexiones sin el certificado del GW.

Si algo falla, el log del job muestra las últimas líneas de MS01.

### 8. Verificación desde fuera
```bash
# Acceso directo: timeout (el firewall solo acepta al GW)
curl -m 5 -k https://<IP de la VM>:443/
# A través del GW: login del primer administrador
curl -i https://<dominio del GW>/api/v1/auth/login -H "Content-Type: application/json" -d '{"correo":"<BOOTSTRAP_ADMIN_EMAIL>","contrasena":"<BOOTSTRAP_ADMIN_PASSWORD>"}'
# [Windows]: curl.exe, y en PowerShell el JSON con comillas escapadas: -d '{\"correo\":\"...\",\"contrasena\":\"...\"}'
```
