#!/usr/bin/env bash
# Prints the fixed Cognito identifiers used by cognito-local. The pool, app
# client, and test user are seeded from local/.cognito/seed/ on first run by
# run-local.sh — there are no AWS-CLI calls here. Run this anytime you need to
# remind yourself of the IDs (e.g. for an Insomnia env or a curl request).
#
# To reset cognito-local to the seeded state:
#   rm -rf local/.cognito/db && bash local/run-local.sh
set -euo pipefail

ENDPOINT=http://localhost:9229
POOL_ID=local_devwflow
CLIENT_ID=devwflowlocalclient00000z
POOL_NAME=workflow-pool
CLIENT_NAME=workflow-service-local
TEST_USERNAME=tester@techquarter.local
TEST_PASSWORD='Test1234!'
ISSUER_URI="$ENDPOINT/$POOL_ID"

cat <<EOF
Pool name   : $POOL_NAME
Pool ID     : $POOL_ID
Client name : $CLIENT_NAME
Client ID   : $CLIENT_ID
Username    : $TEST_USERNAME
Password    : $TEST_PASSWORD
Issuer URI  : $ISSUER_URI

application-local.yaml:
  spring.security.oauth2.resourceserver.jwt:
    issuer-uri: $ISSUER_URI
    jwk-set-uri: $ISSUER_URI/.well-known/jwks.json

ui/.env:
  EXPO_PUBLIC_COGNITO_CLIENT_ID=$CLIENT_ID

Get a token:
  aws --endpoint-url=$ENDPOINT --region us-east-1 cognito-idp initiate-auth \\
    --auth-flow USER_PASSWORD_AUTH \\
    --client-id $CLIENT_ID \\
    --auth-parameters USERNAME=$TEST_USERNAME,PASSWORD=$TEST_PASSWORD \\
    --query 'AuthenticationResult.IdToken' --output text
EOF
