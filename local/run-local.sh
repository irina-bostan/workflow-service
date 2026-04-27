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

COGNITO_SEED_DIR="$SCRIPT_DIR/.cognito/seed"
COGNITO_DB_DIR="$SCRIPT_DIR/.cognito/db"

for arg in "$@"; do
  case "$arg" in
    --no-start) START_BACKEND=false ;;
    -h|--help)
      sed -n '1,12p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
  esac
done

# ─── 1. Seed cognito-local DB (only if empty — preserves runtime mutations) ──
mkdir -p "$COGNITO_DB_DIR"
SEEDED=false
for seed in "$COGNITO_SEED_DIR"/*.json; do
  [[ -f "$seed" ]] || continue
  target="$COGNITO_DB_DIR/$(basename "$seed")"
  if [[ ! -f "$target" ]]; then
    cp "$seed" "$target"
    echo "▶ Seeded $(basename "$target") (fixed pool/client IDs from .cognito/seed/)"
    SEEDED=true
  fi
done

# ─── 2. Compose up ────────────────────────────────────────────────────────────
echo "▶ Starting Docker Compose services (postgres, redis, localstack, cognito-local)..."
docker compose -f "$SCRIPT_DIR/docker-compose.yml" up -d

# cognito-local reads its DB once at startup. If we just dropped a new seed
# next to an already-running container, restart it so the file gets picked up.
if $SEEDED && docker ps --format '{{.Names}}' | grep -q '^workflow-cognito-local$'; then
  echo "▶ Restarting cognito-local to load freshly-seeded state..."
  docker restart workflow-cognito-local >/dev/null
fi

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
  --queue-name booking-events-dlq.fifo \
  --attributes FifoQueue=true \
  --region eu-west-1 >/dev/null 2>&1 || true

DLQ_ARN=$(docker exec workflow-localstack awslocal sqs get-queue-attributes \
  --queue-url "http://sqs.eu-west-1.localhost.localstack.cloud:4566/000000000000/booking-events-dlq.fifo" \
  --attribute-names QueueArn \
  --query 'Attributes.QueueArn' --output text 2>/dev/null || echo "")

docker exec workflow-localstack awslocal sqs create-queue \
  --queue-name booking-events.fifo \
  --attributes FifoQueue=true,ContentBasedDeduplication=true,RedrivePolicy="{\"deadLetterTargetArn\":\"$DLQ_ARN\",\"maxReceiveCount\":\"3\"}" \
  --region eu-west-1 >/dev/null 2>&1 || true

for kv in \
  "/workflow-service/db-url|jdbc:postgresql://localhost:5432/workflowdb" \
  "/workflow-service/redis-host|localhost" \
  "/workflow-service/sqs-booking-events-url|http://sqs.eu-west-1.localhost.localstack.cloud:4566/000000000000/booking-events.fifo" \
  "/workflow-service/sqs-booking-events-dlq-url|http://sqs.eu-west-1.localhost.localstack.cloud:4566/000000000000/booking-events-dlq.fifo"
do
  name="${kv%%|*}"
  value="${kv##*|}"
  docker exec workflow-localstack awslocal ssm put-parameter \
    --name "$name" --value "$value" --type String --overwrite >/dev/null 2>&1 || true
done

# ─── 4. cognito-local: pool/client/user are pre-seeded; just print the IDs ───
echo "▶ cognito-local seeded with fixed IDs:"
bash "$SCRIPT_DIR/setup-cognito.sh" | sed 's/^/  /'

# ─── 5. Bootstrap ui/.env from the template on first run ─────────────────────
UI_ENV="$PROJECT_DIR/ui/.env"
UI_ENV_EXAMPLE="$PROJECT_DIR/ui/.env.example"
if [[ ! -f "$UI_ENV" && -f "$UI_ENV_EXAMPLE" ]]; then
  cp "$UI_ENV_EXAMPLE" "$UI_ENV"
  echo "▶ Created ui/.env from .env.example"
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
