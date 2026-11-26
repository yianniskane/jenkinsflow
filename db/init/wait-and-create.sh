#!/bin/bash
set -euo pipefail

echo "Waiting for SQL Server..."
for i in $(seq 1 60); do
  if /opt/mssql-tools18/bin/sqlcmd -S sqlserver -U sa -P "${MSSQL_SA_PASSWORD}" -C -Q "SELECT 1" >/dev/null 2>&1; then
    echo "SQL Server is up"
    /opt/mssql-tools18/bin/sqlcmd -S sqlserver -U sa -P "${MSSQL_SA_PASSWORD}" -C -i /init/create-catalog.sql
    echo "catalog database ensured"
    exit 0
  fi
  sleep 2
done

echo "SQL Server did not become ready in time" >&2
exit 1
