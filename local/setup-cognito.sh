#!/usr/bin/env bash
# Bootstraps a local Cognito user pool, app client, and test user against
# cognito-local (http://localhost:9229).
#
# IMPORTANT: cognito-local assigns a random pool ID (e.g. `local_70VnjvDS`)
# at creation — it is NOT the same as the pool name. The issuer-uri in
# application-local.yaml MUST match the generated ID, not the name.
# This script captures the real ID and prints the issuer-uri to use.
set -euo pipefail

ENDPOINT=http://localhost:9229
POOL_NAME=workflow-pool
CLIENT_NAME=workflow-service-local
TEST_USERNAME=${TEST_USERNAME:-tester@techquarter.local}
TEST_PASSWORD=${TEST_PASSWORD:-Test1234!}

export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=local

aws_cognito() {
  aws --endpoint-url="$ENDPOINT" --region us-east-1 cognito-idp "$@"
}

echo "Waiting for cognito-local at $ENDPOINT ..."
for _ in {1..30}; do
  if curl -sf "$ENDPOINT/health" >/dev/null 2>&1 || curl -sf "$ENDPOINT" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

# Look up an existing pool with this name; create one only if missing.
POOL_ID=$(aws_cognito list-user-pools --max-results 60 \
  --query "UserPools[?Name=='$POOL_NAME'].Id | [0]" --output text 2>/dev/null || true)

if [[ -z "$POOL_ID" || "$POOL_ID" == "None" ]]; then
  echo "Creating user pool '$POOL_NAME'..."
  POOL_ID=$(aws_cognito create-user-pool \
    --pool-name "$POOL_NAME" \
    --policies "PasswordPolicy={MinimumLength=8,RequireUppercase=true,RequireLowercase=true,RequireNumbers=true}" \
    --query 'UserPool.Id' --output text)
else
  echo "Reusing existing user pool '$POOL_NAME' (id=$POOL_ID)"
fi

# Look up an existing client with this name; create one only if missing.
CLIENT_ID=$(aws_cognito list-user-pool-clients --user-pool-id "$POOL_ID" \
  --query "UserPoolClients[?ClientName=='$CLIENT_NAME'].ClientId | [0]" --output text 2>/dev/null || true)

if [[ -z "$CLIENT_ID" || "$CLIENT_ID" == "None" ]]; then
  echo "Creating app client '$CLIENT_NAME'..."
  CLIENT_ID=$(aws_cognito create-user-pool-client \
    --user-pool-id "$POOL_ID" \
    --client-name "$CLIENT_NAME" \
    --explicit-auth-flows "ALLOW_USER_PASSWORD_AUTH" "ALLOW_REFRESH_TOKEN_AUTH" \
    --no-generate-secret \
    --query 'UserPoolClient.ClientId' --output text)
else
  echo "Reusing existing app client (id=$CLIENT_ID)"
fi

echo "Ensuring test user $TEST_USERNAME exists..."
aws_cognito admin-create-user \
  --user-pool-id "$POOL_ID" \
  --username "$TEST_USERNAME" \
  --user-attributes Name=email,Value="$TEST_USERNAME" Name=email_verified,Value=true \
  --message-action SUPPRESS \
  >/dev/null 2>&1 || echo "  (user already exists — continuing)"

aws_cognito admin-set-user-password \
  --user-pool-id "$POOL_ID" \
  --username "$TEST_USERNAME" \
  --password "$TEST_PASSWORD" \
  --permanent >/dev/null

ISSUER_URI="$ENDPOINT/$POOL_ID"

echo
echo "Local Cognito ready."
echo "  Pool name   : $POOL_NAME"
echo "  Pool ID     : $POOL_ID"
echo "  Client ID   : $CLIENT_ID"
echo "  Username    : $TEST_USERNAME"
echo "  Password    : $TEST_PASSWORD"
echo "  Issuer URI  : $ISSUER_URI"
echo
echo "Set in src/main/resources/application-local.yaml:"
echo "  spring.security.oauth2.resourceserver.jwt:"
echo "    issuer-uri: $ISSUER_URI"
echo "    jwk-set-uri: $ISSUER_URI/.well-known/jwks.json"
echo
echo "Get a token:"
echo "  aws --endpoint-url=$ENDPOINT --region us-east-1 cognito-idp initiate-auth \\"
echo "    --auth-flow USER_PASSWORD_AUTH \\"
echo "    --client-id $CLIENT_ID \\"
echo "    --auth-parameters USERNAME=$TEST_USERNAME,PASSWORD=$TEST_PASSWORD \\"
echo "    --query 'AuthenticationResult.IdToken' --output text"
echo
echo "Then call the API:"
echo "  curl -H \"Authorization: Bearer <token>\" http://localhost:8080/employees"
