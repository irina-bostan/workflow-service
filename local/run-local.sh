#!/usr/bin/env bash
# Single-entrypoint local bootstrap for workflow-service.
#
#   bash local/run-local.sh             — provision everything + start backend
#   bash local/run-local.sh --no-start  — provision everything; you start the app yourself
#
# Provisions: PostgreSQL, Redis, LocalStack (SQS + SSM), cognito-local
# (user pool + client + test user). Then writes the Cognito Client ID into
# ui/.env so the React Native app can authenticate against it.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
START_BACKEND=true

for arg in "$@"; do
  case "$arg" in
    --no-start) START_BACKEND=false ;;
    -h|--help)
      sed -n '1,12p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
  esac
done

# ─── 1. Compose up ────────────────────────────────────────────────────────────
echo "▶ Starting Docker Compose services (postgres, redis, localstack, cognito-local)..."
docker compose -f "$SCRIPT_DIR/docker-compose.yml" up -d

# ─── 2. Wait for each service ─────────────────────────────────────────────────
echo "▶ Waiting for services to be healthy..."

until pg_isready -h localhost -U workflow -d workflowdb &>/dev/null; do
  echo "  · waiting for PostgreSQL..."
  sleep 2
done
echo "  · PostgreSQL ready"

until docker exec workflow-redis redis-cli ping &>/dev/null; do
  echo "  · waiting for Redis..."
  sleep 2
done
echo "  · Redis ready"

until curl -sf http://localhost:4566/_localstack/health &>/dev/null; do
  echo "  · waiting for LocalStack..."
  sleep 2
done
echo "  · LocalStack ready"

until curl -sf http://localhost:9229 &>/dev/null \
   || curl -sf http://localhost:9229/health &>/dev/null; do
  echo "  · waiting for cognito-local..."
  sleep 2
done
echo "  · cognito-local ready"

# ─── 3. LocalStack: SQS queue + SSM parameters ────────────────────────────────
echo "▶ Provisioning LocalStack (SQS + SSM)..."
docker exec workflow-localstack awslocal sqs create-queue \
  --queue-name booking-events.fifo \
  --attributes FifoQueue=true,ContentBasedDeduplication=true \
  --region eu-west-1 >/dev/null 2>&1 || true

for kv in \
  "/workflow-service/db-url|jdbc:postgresql://localhost:5432/workflowdb" \
  "/workflow-service/redis-host|localhost" \
  "/workflow-service/sqs-booking-events-url|http://sqs.eu-west-1.localhost.localstack.cloud:4566/000000000000/booking-events.fifo"
do
  name="${kv%%|*}"
  value="${kv##*|}"
  docker exec workflow-localstack awslocal ssm put-parameter \
    --name "$name" --value "$value" --type String --overwrite >/dev/null 2>&1 || true
done

# ─── 4. cognito-local: pool, client, test user → capture Client ID ────────────
echo "▶ Provisioning cognito-local..."
COGNITO_OUTPUT=$(bash "$SCRIPT_DIR/setup-cognito.sh")
echo "$COGNITO_OUTPUT" | sed 's/^/  /'

CLIENT_ID=$(echo "$COGNITO_OUTPUT" | awk -F': *' '/^[[:space:]]*Client ID/ {print $2; exit}')
if [[ -z "${CLIENT_ID:-}" ]]; then
  echo "✘ Could not extract Cognito Client ID from setup-cognito.sh output." >&2
  exit 1
fi

# ─── 5. Sync ui/.env with the captured Client ID ──────────────────────────────
UI_ENV="$PROJECT_DIR/ui/.env"
UI_ENV_EXAMPLE="$PROJECT_DIR/ui/.env.example"
if [[ ! -f "$UI_ENV" && -f "$UI_ENV_EXAMPLE" ]]; then
  cp "$UI_ENV_EXAMPLE" "$UI_ENV"
  echo "▶ Created ui/.env from .env.example"
fi

if [[ -f "$UI_ENV" ]]; then
  if grep -q '^EXPO_PUBLIC_COGNITO_CLIENT_ID=' "$UI_ENV"; then
    # Portable in-place update (avoids GNU vs BSD sed -i divergence)
    awk -v id="$CLIENT_ID" '
      /^EXPO_PUBLIC_COGNITO_CLIENT_ID=/ {print "EXPO_PUBLIC_COGNITO_CLIENT_ID=" id; next}
      {print}
    ' "$UI_ENV" >"$UI_ENV.tmp" && mv "$UI_ENV.tmp" "$UI_ENV"
  else
    echo "EXPO_PUBLIC_COGNITO_CLIENT_ID=$CLIENT_ID" >>"$UI_ENV"
  fi
  echo "▶ Wrote Cognito Client ID into ui/.env"
fi

# ─── 6. Done ──────────────────────────────────────────────────────────────────
cat <<EOF

✔ Local stack ready.

Next steps:
  · Start the backend : mvn spring-boot:run -Plocal
  · Start the UI      : cd ui && npm install && npm start
  · Tear down         : docker compose -f local/docker-compose.yml down

EOF

if $START_BACKEND; then
  echo "▶ Starting workflow-service (Spring profile: local)..."
  cd "$PROJECT_DIR"
  exec mvn spring-boot:run -Dspring-boot.run.profiles=local
fi
