#!/bin/bash
# One-time setup: create workflow user and databases in local PostgreSQL.
# Run as a PostgreSQL superuser: bash local/setup-local-postgres.sh

set -e

psql postgres <<'SQL'
DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'workflow') THEN
    CREATE ROLE workflow WITH LOGIN PASSWORD 'workflow';
  END IF;
END
$$;

SELECT 'CREATE DATABASE workflowdb OWNER workflow'
  WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'workflowdb') \gexec

SELECT 'CREATE DATABASE workflowdb_shadow OWNER workflow'
  WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'workflowdb_shadow') \gexec

GRANT ALL PRIVILEGES ON DATABASE workflowdb TO workflow;
GRANT ALL PRIVILEGES ON DATABASE workflowdb_shadow TO workflow;
SQL

echo "Done. Databases 'workflowdb' and 'workflowdb_shadow' are ready."
