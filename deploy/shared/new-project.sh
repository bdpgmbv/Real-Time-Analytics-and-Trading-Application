#!/bin/bash
# Give a project its own database on the shared server.
#   ./deploy/shared/new-project.sh rtat
set -e

name="${1:?usage: new-project.sh <name>}"
user="${SHARED_DB_USER:-dev}"

docker exec -i shared-postgres psql -U "$user" -d postgres <<SQL
SELECT 'CREATE DATABASE $name' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$name')\gexec
SQL

echo "database '$name' is ready on localhost:5432"
echo "point the project at: jdbc:postgresql://localhost:5432/$name"
