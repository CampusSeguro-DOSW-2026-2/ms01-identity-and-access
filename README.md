# MS01 - IDENTITY AND ACCESS

MS01 - Users, authentication, roles, permissions, and sessions service.

| Name                            | GitHub Username                   |
|---------------------------------|-----------------------------------|
| Daniel Santiago Morales Perdomo | `Fukuyaku88`                      |
| Edgar Daniel Ruiz Patiño        | `DaniRuiz11`                      |
| Miguel Ángel Acero Laverde      | `Miguelim101`                     |
| Thomas Sebastian García Gomez   | `thomassebastiangarciagomez-ctrl` |

---

Documentation (requirements, contracts, data model and diagrams, in Spanish): [`ms01-identity-and-access/`](https://github.com/CampusSeguro-DOSW-2026-2/documentation/tree/main/ms01-identity-and-access) in the `documentation` repository.

## What it does

* Registration and login of institutional users. Login issues an RS256 JWT with the user's roles and permissions.
* Logout, change of password and password recovery with a one-time code sent by email.
* Own profile and administration of users (list, search, activate or deactivate, add or remove roles).
* Administration of roles and of the permissions each role has. The roles and the permission catalog live in the database.
* Session cuts: a change of a user's roles or status, or of a role's permissions, makes the API Gateway reject the tokens issued before the change.
* Internal routes for other microservices (`MS03`, `MS04`, `MS05`) to resolve users by id or by role, and for the API Gateway to query the session cuts.
* Audit events sent to MS08 through the API Gateway.

The API contract (JSON fields, response messages, error codes, enum values and role names) is in Spanish, as defined in the documentation. The code, comments, logs, tests and database objects are in English.

## Local run

Requirements: JDK 21, Maven 3.9 and Docker. `docker-compose.yml` only starts the infrastructure: PostgreSQL 17 on `5432` and Mailpit (SMTP on `1025`, inbox at http://localhost:8025). MS01 runs with Maven at `http://localhost:8081`.

1. Variables. The default values of `application.yaml` already work locally. To change any of them, copy the template and define the same variables in the terminal or in the IDE (Maven does not read `.env`):
   ```bash
   cp .env.example .env        # Linux / macOS
   copy .env.example .env      # Windows (cmd or PowerShell)
   ```
2. Database volume. It is created only once, because the compose file declares it as external:
   ```bash
   docker volume create campus-postgres-data
   ```
3. Infrastructure. On its first start with the empty volume, Postgres runs the scripts `database/001_create_schema.sql` and `database/002_index_schema.sql` ([Database](#database)):
   ```bash
   docker compose up -d
   ```
4. MS01, with Swagger at http://localhost:8081/swagger-ui.html:
   ```bash
   SWAGGER_ENABLED=true mvn spring-boot:run                    # Linux / macOS
   $env:SWAGGER_ENABLED="true"; mvn spring-boot:run            # Windows (PowerShell)
   ```
   Without `JWT_PRIVATE_KEY`, MS01 generates an ephemeral key: the tokens stop being valid when it restarts. Without `GATEWAY_URL`, the audit events only go to the log.
5. Tests:
   ```bash
   mvn verify
   ```
6. Docker image (the same one that CI publishes):
   ```bash
   docker build -t ms01-identity-and-access .
   ```

## Database

MS01 has its own PostgreSQL database. The application never creates or changes the schema: it only validates it when it starts (`spring.jpa.hibernate.ddl-auto: validate`). The schema is versioned as SQL scripts in `database/`:

| Script | When | What it does |
|---|---|---|
| `001_create_schema.sql` | New database. Postgres runs it by itself with an empty volume | Tables (`users`, `roles`, `permissions`, `role_permissions`, `user_roles`, `password_reset_codes`), functions, triggers and the initial data: the 48-permission catalog, the eight initial roles and their permissions according to the Role-Permission Matrix. A single transaction |
| `002_index_schema.sql` | New database, after `001` | Indexes, including the `pg_trgm` index of the search by email |
| `migrations/004_migration_roles_permissions.sql` | By hand, on a database deployed with the previous model (Spanish table names and fixed roles) | Takes it to the current schema in a single transaction, keeping its data |
| `maintenance/005_maintenance_roles_permissions.sql` | By hand, every time the Role-Permission Matrix gains permissions or assignments | Leaves the catalog and the permissions of the eight initial roles exactly as the Matrix says. The roles created through the API keep only the mandatory permissions. It cuts the sessions of all roles, so every user logs in again with the new permissions |

Postgres only runs the scripts that are directly in `database/`; the `migrations/` and `maintenance/` subfolders are applied by hand, after taking a backup:

```bash
pg_dump -h <host> -U <user> -Fc -f backup.dump <database>
psql -h <host> -U <user> -d <database> -v ON_ERROR_STOP=1 -f database/maintenance/005_maintenance_roles_permissions.sql
```

Tables, columns, constraints, indexes, functions and triggers have English names. The values that are stored (`ACTIVO` and `INACTIVO`, the role names and the descriptions of roles and permissions) are in Spanish, like the API. The data model is described in [`4_modelo-datos.md`](https://github.com/CampusSeguro-DOSW-2026-2/documentation/blob/main/ms01-identity-and-access/4_modelo-datos.md).

## Roles and permissions

* The database is the source of truth. The roles, the permission catalog and the permissions of each role are rows, so an administrator can create, rename, describe and delete roles and change their permissions through the API without changing the code or the schema.
* A permission is a code `ms<NN>:<resource>:<action>` (for example `ms04:checkin:write`). MS01 defines in its code only its own permissions (`ms01:*`, enum `Permission`) and synchronizes them with the database when it starts. The permissions of `ms02` to `ms08` are loaded from the Role-Permission Matrix of the wiki by the database scripts. No route creates, edits or deletes permissions.
* Mandatory permissions: every role, including the ones created by an administrator, always has `ms01:profile:read` and `ms01:session:logout`. `ESTUDIANTE` and `ADMIN` also keep `ms01:profile:write`, and `ms01:role:manage` belongs only to `ADMIN`. The database enforces these rules with a deferred constraint trigger, and the API checks them first. `POST /api/v1/roles` adds the two mandatory permissions by itself; `PUT /api/v1/roles/{rol}/permissions` answers 422 if the new list does not include them.
* Internal routes (`/api/v1/users/batch` and `/api/v1/users/by-role`) are authorized by the calling service, not by permissions: `INTERNAL_ALLOWED_SERVICES` (`ms03,ms04,ms05` by default).

## CI/CD

| Workflow | When | What it does |
|---|---|---|
| `ci.yml` | Every push and PR | Builds and tests (`mvn verify`), and analyzes with SonarCloud if `SONAR_TOKEN` exists. Builds the image. From `develop` and `main` it publishes it to `ghcr.io/campusseguro-dosw-2026-2/ms01-identity-and-access` with `:develop` or `:latest` and `:sha-xxxxxxx`. |
| `ci.yml` → `deploy.yml` | Push to `develop` | Deploys that image to the **test** environment (`:8443`). |
| `deploy-prod.yml` | By hand, from `main` | Deploys to **production** (`:443`) an image `sha-xxxxxxx` that already went through test, without rebuilding it. Any earlier tag is the rollback. |

The workflows and jobs keep their Spanish names in GitHub Actions: `Desplegar` (deploy), `Desplegar a producción` (deploy to production), `desplegar-test` and `verificar`.

## Cloud deployment (test and production)

MS01 runs on its own VM, behind the API Gateway (ADR-006 of the GW):
* The firewall only accepts the IP of the GW.
* An edge Caddy requires the client certificate of the GW (mTLS).
* Test and production share the VM, each one with its own stack (MS01 + PostgreSQL), secrets and certificates:

| VM port | Use |
|---|---|
| `443` | Production (`ms01-prod`) |
| `8443` | Test (`ms01-test`) |
| `22` | SSH, only from the IP of whoever administers |

Steps 1 to 6 are done only once. The commands marked *(VM)* run on the VM over SSH; the others, on your PC.

### 1. VM and firewall
1. Create an **Ubuntu 24.04 LTS x86_64** VM with a **static** public IP (Azure: Standard static IP; AWS: Elastic IP).
2. Inbound rules of the NSG (in AWS, Security Group):

   | Port | Source |
   |---|---|
   | `443/tcp` and `8443/tcp` | Only `<IP of the GW>/32` |
   | `22/tcp` | Only your IP |

   Do not open anything else: neither `8081` nor `5432`. Outbound traffic stays open, because MS01 needs SMTP, GitHub and the GW.
3. Install Docker *(VM)*:
   ```bash
   curl -fsSL https://get.docker.com | sudo sh
   sudo usermod -aG docker "$USER"   # close the SSH session and log in again
   ```

### 2. Exchange with the owner of the GW
Through a private channel. Details in the guide [Conexión al API Gateway](https://github.com/CampusSeguro-DOSW-2026-2/documentation/wiki/Conexi%C3%B3n-al-API-Gateway) (Connection to the API Gateway).

| You hand over | You receive, per environment (test and production) |
|---|---|
| The static IP of the VM | `servidor.crt`, `servidor.key` and `ca.pem` |
| The public JWT key of each environment (step 5) | `GATEWAY_SERVICE_KEY` |
| | The domain of the GW, for `GATEWAY_URL` |

### 3. Caddy edge (mTLS)
1. Templates of the GW *(VM)*:
   ```bash
   mkdir -p ~/campusseguro/edge && cd ~/campusseguro/edge
   curl -fsSLO https://raw.githubusercontent.com/CampusSeguro-DOSW-2026-2/api-gateway/develop/infra/caddy/Caddyfile.microservicio
   curl -fsSLO https://raw.githubusercontent.com/CampusSeguro-DOSW-2026-2/api-gateway/develop/infra/caddy/docker-compose.microservicio.yml
   printf 'UPSTREAM_PROD=ms01-prod:8081\nUPSTREAM_TEST=ms01-test:8081\n' > .env
   ```
2. Certificates: on your PC, put the files you received in this structure and copy it to the VM. `scp` works the same on Linux and on Windows (PowerShell):
   ```text
   certs/prod/servidor.crt  certs/prod/servidor.key  certs/prod/ca.pem
   certs/test/servidor.crt  certs/test/servidor.key  certs/test/ca.pem
   ```
   ```bash
   scp -r certs <user>@<IP of the VM>:~/campusseguro/edge/
   ```
3. Start the edge *(VM)*:
   ```bash
   cd ~/campusseguro/edge
   chmod 600 certs/*/servidor.key
   docker compose -f docker-compose.microservicio.yml up -d
   ```
   The edge creates the `campus-edge` network, which the stacks join. While there are no stacks, it answers `502`, and only to whoever presents the certificate of the GW.

### 4. Self-hosted runner
1. In GitHub: **Settings › Actions › Runners › New self-hosted runner** (Linux, x64). Run on the VM the *Download* commands that the page shows.
2. When configuring it, add the label `ms01` and then install it as a service *(VM)*:
   ```bash
   ./config.sh --url https://github.com/CampusSeguro-DOSW-2026-2/ms01-identity-and-access --token <TOKEN from the page> --labels ms01 --unattended
   sudo ./svc.sh install "$USER"
   sudo ./svc.sh start
   ```
   The runner runs with your user, who is already in the `docker` group.
3. The repository is public: in **Settings › Actions › General › Fork pull request workflows from outside collaborators**, choose **Require approval for all external contributors**. This way an external PR cannot run on the VM.

### 5. JWT keys (one per environment)
On your PC. On Windows, use Git Bash:
```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-test.pem
openssl pkey -in jwt-test.pem -pubout -out jwt-test.pub.pem
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-prod.pem
openssl pkey -in jwt-prod.pem -pubout -out jwt-prod.pub.pem
```
* `jwt-<environment>.pem` is the private key: it goes to the `JWT_PRIVATE_KEY` secret of that environment.
* `jwt-<environment>.pub.pem` goes to the owner of the GW, who puts it in their `JWT_PUBLIC_KEY`.
* Keep the files outside the repository. `.gitignore` already ignores `*.pem`.

### 6. GitHub Environments
Create `ms01-test` and `ms01-prod` in **Settings › Environments**:
* `ms01-test`: in **Deployment branches**, allow only `develop`.
* `ms01-prod`: enable **Required reviewers** and, in **Deployment branches**, allow only `main`.

| In `ms01-test` and `ms01-prod` | Type | Value |
|---|---|---|
| `GATEWAY_URL` | Variable | Test: `https://<domain of the GW>:8443`. Production: `https://<domain of the GW>`. |
| `MAIL_HOST` | Variable (optional) | SMTP server with STARTTLS. `smtp.gmail.com` by default. |
| `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_FROM` | Variable (optional) | `587`, `proyectometx@gmail.com` and `CampusSeguro <proyectometx@gmail.com>` by default. With Gmail the sender must be the same account. |
| `BOOTSTRAP_ADMIN_EMAIL` | Variable (optional) | Email of the first administrator. It is only created if no ADMIN exists. |
| `MS01_EXTRA_ENV` | Variable (optional) | Extra `KEY=value` lines, e.g. `INTERNAL_ALLOWED_SERVICES=ms03,ms04,ms05` or `RECOVERY_*`. |
| `DEPLOY_DIR` | Variable (optional) | `~/campusseguro/ms01` by default. |
| `DB_PASSWORD` | Secret | `openssl rand -base64 24`. It only applies when the database is created (first deployment). |
| `JWT_PRIVATE_KEY` | Secret | Full content of `jwt-<environment>.pem` (step 5). |
| `GATEWAY_SERVICE_KEY` | Secret | The one the owner of the GW gave for that environment. |
| `MAIL_PASSWORD` | Secret | Google application password of `proyectometx@gmail.com` (Google Account > Security > App passwords; it requires 2-step verification). Without it the recovery emails are not sent. |
| `BOOTSTRAP_ADMIN_PASSWORD` | Secret (optional) | Password of the first administrator. |

No value can contain single quotes (`'`).

### 7. Deploy
1. **Test:** every merge or push to `develop` publishes the image and deploys it to `ms01-test`. Follow the **desplegar-test** job in the *Actions* tab.
2. **Production:** after merging into `main`, go to **Actions › Desplegar a producción › Run workflow**, branch `main`, with the tag of the image that already works in test. It is `sha-` plus the first 7 characters of the `develop` commit that was deployed, and it appears in the log of *desplegar-test*.

`infra/deploy.sh` verifies each deployment inside the VM:
* `/actuator/health` answers 200 through the `campus-edge` network;
* the edge rejects connections without the certificate of the GW.

If something fails, the log of the job shows the last lines of MS01.

### 8. Verification from outside
```bash
# Direct access: timeout (the firewall only accepts the GW)
curl -m 5 -k https://<IP of the VM>:443/
# Through the GW: login of the first administrator
curl -i https://<domain of the GW>/api/v1/auth/login -H "Content-Type: application/json" -d '{"correo":"<BOOTSTRAP_ADMIN_EMAIL>","contrasena":"<BOOTSTRAP_ADMIN_PASSWORD>"}'
# [Windows]: curl.exe, and in PowerShell the JSON with escaped quotes: -d '{\"correo\":\"...\",\"contrasena\":\"...\"}'
```
