#!/usr/bin/env bash
# Deploys MS01 on its VM: stack ms01-<environment> behind the edge Caddy, with its database in Amazon RDS, and verifies
# the deployment.
# It is run by the self-hosted runner from .github/workflows/deploy.yml with the variables and secrets of the
# GitHub ms01-<environment> Environment. It is idempotent: running it again with another TAG is the rollback.
# The edge (Caddy with mTLS) is started only once by hand, with the "Conexión al API Gateway" guide of the wiki.
set -euo pipefail

missing() { echo "::error::Missing variable $1 (Environment ms01-${ENTORNO:-?} in GitHub)" >&2; exit 1; }
# The email goes out through Gmail with the account of the project unless the Environment indicates another server; without
# the application password (MAIL_PASSWORD secret) Gmail rejects the sending, that is why it is required
MAIL_HOST="${MAIL_HOST:-smtp.gmail.com}"
MAIL_USERNAME="${MAIL_USERNAME:-proyectometx@gmail.com}"
for VAR in ENTORNO DB_HOST DB_NAME DB_USERNAME DB_PASSWORD JWT_PRIVATE_KEY GATEWAY_URL GATEWAY_SERVICE_KEY MAIL_PASSWORD; do
    [ -n "${!VAR:-}" ] || missing "$VAR"
done
[[ "$ENTORNO" =~ ^(test|prod)$ ]] || { echo "::error::ENTORNO must be test or prod" >&2; exit 1; }

REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"
# Same tag that the CI publishes (docker/metadata-action, type=sha): the first 7 characters of the commit.
TAG="${TAG:-sha-$(git -C "$REPO_DIR" rev-parse HEAD | cut -c1-7)}"
[[ "$TAG" =~ ^sha-[0-9a-f]{7}$ ]] || { echo "::error::TAG must have the form sha-xxxxxxx (received: $TAG)" >&2; exit 1; }
IMAGE="${IMAGE:-ghcr.io/campusseguro-dosw-2026-2/ms01-identity-and-access}"
DEPLOY_DIR="${DEPLOY_DIR:-$HOME/campusseguro/ms01}"
PORT=$([ "$ENTORNO" = prod ] && echo 443 || echo 8443)
STACK_DIR="$DEPLOY_DIR/$ENTORNO"
# CA bundle of Amazon RDS: MS01 verifies with it the certificate and the name of the database server (sslmode=verify-full).
RDS_CA_URL="https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem"

docker network inspect campus-edge >/dev/null 2>&1 || {
    echo "::error::The campus-edge network does not exist: start the edge Caddy of the VM first (README, Cloud deployment)" >&2
    exit 1
}

# One-line values for the .env (between single quotes, without interpolation): the PEM key goes with escaped \n.
one_line() { printf '%s' "$1" | awk 'BEGIN{ORS=""} NR>1{print "\\n"} {print}'; }
env_line() {
    local value; value="$(one_line "$2")"
    [[ "$value" != *"'"* ]] || { echo "::error::$1 must not contain single quotes" >&2; exit 1; }
    printf "%s='%s'\n" "$1" "$value"
}

umask 077
mkdir -p "$STACK_DIR"

echo "== Deployment files ($ENTORNO, $TAG) in $STACK_DIR"
cp "$REPO_DIR/infra/docker-compose.prod.yml" "$STACK_DIR/docker-compose.yml"
# The schema is applied on RDS by hand (README, Cloud deployment): the stack carries no SQL scripts.
rm -rf "$STACK_DIR/database"
curl -fsSL --retry 3 -o "$STACK_DIR/rds-ca.pem.new" "$RDS_CA_URL"
grep -q "BEGIN CERTIFICATE" "$STACK_DIR/rds-ca.pem.new" || {
    echo "::error::$RDS_CA_URL did not return certificates" >&2
    exit 1
}
mv "$STACK_DIR/rds-ca.pem.new" "$STACK_DIR/rds-ca.pem"
# MS01 reads the CA with its own non-root user: the file is public and carries no secrets.
chmod 644 "$STACK_DIR/rds-ca.pem"
{
    env_line ENTORNO "$ENTORNO"
    env_line IMAGE "$IMAGE"
    env_line TAG "$TAG"
    env_line DB_HOST "$DB_HOST"
    env_line DB_NAME "$DB_NAME"
    env_line DB_USERNAME "$DB_USERNAME"
    env_line DB_PASSWORD "$DB_PASSWORD"
    env_line JWT_PRIVATE_KEY "$JWT_PRIVATE_KEY"
    env_line GATEWAY_URL "$GATEWAY_URL"
    env_line GATEWAY_SERVICE_KEY "$GATEWAY_SERVICE_KEY"
    env_line MAIL_HOST "$MAIL_HOST"
    env_line MAIL_PORT "${MAIL_PORT:-587}"
    env_line MAIL_USERNAME "${MAIL_USERNAME:-}"
    env_line MAIL_PASSWORD "${MAIL_PASSWORD:-}"
    # Without a mail user there is no SMTP authentication.
    env_line MAIL_SMTP_AUTH "$([ -n "${MAIL_USERNAME:-}" ] && echo true || echo false)"
    [ -z "${MAIL_FROM:-}" ] || env_line MAIL_FROM "$MAIL_FROM"
    [ -z "${BOOTSTRAP_ADMIN_EMAIL:-}" ] || env_line BOOTSTRAP_ADMIN_EMAIL "$BOOTSTRAP_ADMIN_EMAIL"
    [ -z "${BOOTSTRAP_ADMIN_PASSWORD:-}" ] || env_line BOOTSTRAP_ADMIN_PASSWORD "$BOOTSTRAP_ADMIN_PASSWORD"
} > "$STACK_DIR/.env"
printf '%s\n' "${MS01_EXTRA_ENV:-}" > "$STACK_DIR/extra.env"

echo "== Image $IMAGE:$TAG"
# On the VM the runner provides GHCR_USER/GHCR_TOKEN; PULL_POLICY=missing is only useful to test with a local image.
if [ -n "${GHCR_TOKEN:-}" ]; then
    printf '%s' "$GHCR_TOKEN" | docker login ghcr.io -u "${GHCR_USER:?Missing GHCR_USER}" --password-stdin >/dev/null
fi
C=(docker compose --project-directory "$STACK_DIR")
"${C[@]}" config --quiet
"${C[@]}" up -d --remove-orphans --pull "${PULL_POLICY:-always}"
[ -z "${GHCR_TOKEN:-}" ] || docker logout ghcr.io >/dev/null

# The firewall and the mTLS prevent testing from outside: it is tested inside the VM, through the campus-edge network.
echo "== Verification of http://ms01-$ENTORNO:8081/actuator/health (waits up to 3 min: startup of MS01)"
CURL=(docker run --rm --network campus-edge curlimages/curl:8.11.1 -s -o /dev/null -w '%{http_code}')
for _ in $(seq 1 36); do
    if [ "$("${CURL[@]}" "http://ms01-$ENTORNO:8081/actuator/health" || true)" = 200 ]; then
        # Without the GW certificate, the edge must cut the connection in TLS (curl 35, 55 or 56), never answer.
        # Another code (e.g. 0 = answered, 7 = nobody listens) means that the edge is missing or does not require the certificate.
        RC=0; curl -sk -o /dev/null "https://127.0.0.1:$PORT/actuator/health" || RC=$?
        [[ "$RC" =~ ^(35|55|56)$ ]] || {
            echo "::error::The edge :$PORT did not reject the connection without a client certificate (curl ended with $RC)"
            exit 1
        }
        echo "Deployment verified: health 200 and edge :$PORT requires the GW certificate ($ENTORNO, $TAG)"
        exit 0
    fi
    sleep 5
done
echo "::error::MS01 did not answer 200 on /actuator/health. Check: docker compose --project-directory $STACK_DIR logs"
"${C[@]}" logs --tail=50 ms01 || true
exit 1
