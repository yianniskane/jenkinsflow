# Docker troubleshooting guide — jenkinsflow lab

This document is a **command cookbook** for diagnosing problems in the local Compose stack (catalog-api, SQL Server, Valkey, Prometheus, Grafana, Loki, Promtail, optional Jenkins).

Run all commands from the repo root unless noted:

```powershell
cd C:\jenkinsflow
```

On macOS/Linux, the same commands work in bash/zsh (`curl` instead of `Invoke-WebRequest` where shown).

---

## Table of contents

1. [Golden rules](#1-golden-rules)
2. [Everyday Compose commands](#2-everyday-compose-commands)
3. [Inspect status and health](#3-inspect-status-and-health)
4. [Logs (the first thing to check)](#4-logs-the-first-thing-to-check)
5. [Exec into a running container](#5-exec-into-a-running-container)
6. [Ports and “already allocated”](#6-ports-and-already-allocated)
7. [Images, build, and cache](#7-images-build-and-cache)
8. [Networks](#8-networks)
9. [Volumes and data](#9-volumes-and-data)
10. [Service-by-service checks](#10-service-by-service-checks)
11. [Error → command playbooks](#11-error--command-playbooks)
12. [Clean restart recipes](#12-clean-restart-recipes)
13. [Useful one-liners cheat sheet](#13-useful-one-liners-cheat-sheet)

---

## 1. Golden rules

1. **Read the failing service’s logs first** — Compose’s summary line rarely has the real cause.
2. **Fix one layer at a time**: Docker daemon → container start → healthcheck → app logic.
3. **`docker compose ps`** tells you *what* is wrong; **`docker compose logs`** tells you *why*.
4. After changing `docker-compose.yml` or Dockerfiles: `git pull` then rebuild/recreate the affected service.
5. Prefer **targeted** restarts (`up -d --build catalog-api`) over wiping everything, unless data/state is corrupted.

---

## 2. Everyday Compose commands

### Start / stop

```powershell
# Start the default lab (no Jenkins)
docker compose up -d

# Build images and start
docker compose up -d --build

# Start including Jenkins profile
docker compose --profile jenkins up -d

# Stop containers (keep volumes/data)
docker compose stop

# Stop and remove containers/networks (keep volumes)
docker compose down

# Stop and remove containers + volumes (DESTROYS DB data, Grafana, etc.)
docker compose down -v
```

### Recreate one service

```powershell
docker compose up -d --build --force-recreate catalog-api
docker compose up -d --force-recreate loki
docker compose restart grafana
```

### Pull latest images (base images only)

```powershell
docker compose pull
docker compose up -d
```

### Validate Compose file

```powershell
docker compose config
docker compose config --quiet
```

If validation fails (example you hit: `services.jenkins.groups false schema`), the YAML key is invalid for your Compose version — remove or rename it, then `git pull` / fix the file.

---

## 3. Inspect status and health

```powershell
# Overview of this project
docker compose ps
docker compose ps -a

# Wider Docker view
docker ps
docker ps -a --filter "name=jf-"

# Health of one container
docker inspect jf-catalog-api --format "{{json .State.Health}}"
docker inspect jf-sqlserver --format "{{.State.Status}} {{.State.Health.Status}}"

# Why a container exited
docker inspect jf-sqlserver-init --format "{{.State.Status}} exit={{.State.ExitCode}} err={{.State.Error}}"
docker inspect jf-sqlserver-init --format "{{.State.OOMKilled}}"
```

### Watch until healthy

```powershell
docker compose ps
# repeat, or:
docker events --filter container=jf-sqlserver
```

---

## 4. Logs (the first thing to check)

```powershell
# All services (noisy)
docker compose logs

# Last N lines
docker compose logs --tail 100

# Follow live
docker compose logs -f catalog-api
docker compose logs -f sqlserver sqlserver-init
docker compose logs -f loki promtail grafana

# Timestamps
docker compose logs -t --tail 50 catalog-api

# Since a time
docker compose logs --since 30m catalog-api
docker compose logs --since 2026-11-26T11:00:00 catalog-api
```

### By container name (same thing, Docker CLI)

```powershell
docker logs jf-catalog-api --tail 100
docker logs -f jf-sqlserver-init
docker logs jf-loki --tail 80
```

---

## 5. Exec into a running container

```powershell
# Shell (image-dependent)
docker compose exec catalog-api sh
docker compose exec valkey sh
docker compose exec sqlserver bash

# One-shot commands (no interactive shell)
docker compose exec catalog-api curl -fsS http://localhost:3845/actuator/health
docker compose exec valkey valkey-cli ping
docker compose exec valkey valkey-cli KEYS "*"
```

If `exec` fails with “container is not running”, start it first or check `docker compose ps -a`.

---

## 6. Ports and “already allocated”

### Symptom

```text
Bind for 0.0.0.0:41433 failed: port is already allocated
Bind for 127.0.0.1:14333 failed: port is already allocated
```

### Find who owns the port

**Windows (PowerShell as Admin helps):**

```powershell
netstat -ano | findstr :41433
netstat -ano | findstr :3845
netstat -ano | findstr :3300
# Note the PID in the last column, then:
tasklist /FI "PID eq <PID>"
```

**Docker itself holding the port:**

```powershell
docker ps --format "table {{.Names}}\t{{.Ports}}"
docker compose ps
```

### Fix options

1. Stop the other process / old container:
   ```powershell
   docker stop <container>
   docker rm <container>
   docker compose down
   ```
2. Or change the **host** port in `docker-compose.yml` (left side of `HOST:CONTAINER`), e.g. `"41433:1433"`, then update `application.yml` if you run the API outside Compose.

### Published ports in this lab (defaults)

| Service      | Host port | Container port |
|--------------|-----------|----------------|
| catalog-api  | 3845      | 3845           |
| SQL Server   | 41433     | 1433           |
| Valkey       | 16379     | 6379           |
| Grafana      | 3300      | 3000           |
| Prometheus   | 39090     | 9090           |
| Loki         | 33100     | 3100           |
| Jenkins      | 38080     | 8080           |
| Local registry (profile) | 35000 | 5000     |

---

## 7. Images, build, and cache

```powershell
# List images used by the project
docker images | findstr jenkinsflow
docker images | findstr mssql
docker images | findstr valkey
docker images | findstr grafana
docker images | findstr loki

# Rebuild without cache (when Dockerfile changes seem ignored)
docker compose build --no-cache catalog-api
docker compose up -d catalog-api

# Remove dangling images
docker image prune -f

# Force remove a specific image (only if nothing uses it)
docker rmi ghcr.io/yianniskane/jenkinsflow-catalog-api:local
```

### Build context debugging

```powershell
docker compose build catalog-api
docker compose images
docker history ghcr.io/yianniskane/jenkinsflow-catalog-api:local
```

---

## 8. Networks

```powershell
docker network ls
docker network inspect jenkinsflow_lab

# Which containers are on the lab network?
docker network inspect jenkinsflow_lab --format "{{range .Containers}}{{.Name}} {{end}}"
```

### Connectivity between services

From inside the API container (names are Compose DNS names):

```powershell
docker compose exec catalog-api sh -c "getent hosts sqlserver || ping -c 1 sqlserver"
docker compose exec catalog-api sh -c "getent hosts valkey"
```

SQL Server does not ship with `ping`/`curl` in a useful way; prefer `sqlcmd` from a tools-capable image or rely on healthchecks.

---

## 9. Volumes and data

```powershell
docker volume ls
docker volume ls | findstr jenkinsflow

docker volume inspect jenkinsflow_sqlserver-data
docker volume inspect jenkinsflow_grafana-data
docker volume inspect jenkinsflow_loki-data
```

### Reset only the database

```powershell
docker compose stop catalog-api sqlserver-init sqlserver
docker compose rm -f sqlserver-init
docker volume rm jenkinsflow_sqlserver-data
docker compose up -d sqlserver sqlserver-init catalog-api
```

### Nuclear reset (everything)

```powershell
docker compose down -v
docker compose up -d --build
```

---

## 10. Service-by-service checks

### catalog-api

```powershell
docker compose ps catalog-api
docker compose logs --tail 100 catalog-api

curl http://localhost:3845/api/health-demo
curl http://localhost:3845/actuator/health
curl http://localhost:3845/api/books
curl http://localhost:3845/api/cache/stats
curl -X DELETE http://localhost:3845/api/cache/books
curl http://localhost:3845/actuator/prometheus
```

**Windows PowerShell alternatives:**

```powershell
Invoke-RestMethod http://localhost:3845/api/books
Invoke-RestMethod http://localhost:3845/api/cache/stats
Invoke-RestMethod -Method Delete http://localhost:3845/api/cache/books
```

### sqlserver / sqlserver-init

```powershell
docker compose ps sqlserver
docker compose logs --tail 100 sqlserver
docker compose logs sqlserver-init

docker inspect jf-sqlserver --format "{{.State.Health.Status}}"
```

Create DB manually if init failed (password from `.env`):

```powershell
docker compose exec sqlserver /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "Your_strong_Password123" -C -Q "IF DB_ID(N'catalog') IS NULL CREATE DATABASE catalog;"
```

If `mssql-tools18` is missing, try:

```powershell
docker compose exec sqlserver /opt/mssql-tools/bin/sqlcmd -S localhost -U sa -P "Your_strong_Password123" -C -Q "SELECT name FROM sys.databases;"
```

### valkey

```powershell
docker compose exec valkey valkey-cli ping
docker compose exec valkey valkey-cli INFO keyspace
docker compose exec valkey valkey-cli KEYS "*"
docker compose exec valkey valkey-cli FLUSHALL
```

### prometheus

```powershell
curl http://localhost:39090/-/healthy
curl http://localhost:39090/api/v1/targets
# Open UI: http://localhost:39090
```

### grafana

```powershell
docker compose logs --tail 50 grafana
# UI: http://localhost:3300  (admin / admin)
```

Loki Explore example query:

```logql
{compose_service="catalog-api"}
{compose_service="catalog-api"} |= "CACHE"
```

### loki / promtail

```powershell
docker compose logs --tail 80 loki
docker compose logs --tail 80 promtail

curl http://localhost:33100/ready
curl http://localhost:33100/metrics
```

Promtail must see the Docker socket (Compose mounts `/var/run/docker.sock`). On Docker Desktop this is normal; if labels never appear in Grafana, check Promtail logs for permission or socket errors.

### jenkins (optional profile)

```powershell
docker compose --profile jenkins up -d jenkins
docker compose logs -f jenkins
# UI: http://localhost:38080
```

---

## 11. Error → command playbooks

### A. `services.*.groups false schema` (Compose validation)

**Meaning:** invalid Compose key for your engine.

```powershell
docker compose config
# Fix YAML (remove invalid key), then:
git pull
docker compose up -d --build
```

---

### B. `port is already allocated`

```powershell
docker ps --format "table {{.Names}}\t{{.Ports}}"
netstat -ano | findstr :41433
docker compose down
# if a stray container remains:
docker rm -f jf-sqlserver
docker compose up -d
```

If still blocked, change the host port in `docker-compose.yml` and `git pull` when the repo already has a new port (this lab uses **41433** for SQL Server).

---

### C. `sqlserver-init` exit code ≠ 0 / `service_completed_successfully` failed

```powershell
docker compose logs sqlserver-init
docker compose logs --tail 50 sqlserver
docker inspect jf-sqlserver-init --format "exit={{.State.ExitCode}} err={{.State.Error}}"
docker compose ps -a sqlserver sqlserver-init
```

**Common causes**

| Cause | What to do |
|-------|------------|
| SQL Server not healthy yet | Wait; check `docker compose ps` until sqlserver is healthy, re-run init |
| Wrong SA password | Align `.env` `MSSQL_SA_PASSWORD` with what sqlserver uses; `down -v` if needed |
| Windows CRLF broke a mounted `.sh` | Prefer inline init in Compose (current repo); `git pull` |
| `sqlcmd` missing path | Use tools18 path (see §10) |

Retry init only:

```powershell
docker compose rm -f sqlserver-init
docker compose up sqlserver-init
docker compose up -d catalog-api prometheus grafana
```

---

### D. catalog-api not started because dependency failed

```powershell
docker compose ps -a
docker compose logs sqlserver-init
docker compose logs sqlserver --tail 50
# After dependency is green:
docker compose up -d catalog-api
```

---

### E. `GET /api/books` → 500 on the **second** call

**Meaning:** usually Valkey cache deserialization (first call writes cache; second reads it).

```powershell
docker compose logs --tail 100 catalog-api
curl http://localhost:3845/api/cache/stats
curl -X DELETE http://localhost:3845/api/cache/books
docker compose exec valkey valkey-cli FLUSHALL
```

Then rebuild API if you pulled a fix:

```powershell
git pull
docker compose up -d --build catalog-api
curl -X DELETE http://localhost:3845/api/cache/books
curl http://localhost:3845/api/books
curl http://localhost:3845/api/books
```

Inspect app stack traces:

```powershell
docker compose logs catalog-api 2>&1 | findstr /I "ERROR Exception CACHE"
```

---

### F. Grafana: `Failed to read plugin provisioning files .../plugins`

**Meaning:** missing empty `provisioning/plugins` directory (noise; UI still works).

```powershell
git pull
docker compose up -d grafana
docker compose logs --tail 20 grafana
```

---

### G. Loki: `error getting ingester clients` / `empty ring`

**Meaning:** usually **benign at startup** for single-node Loki until the instance joins its own ring.

```powershell
docker compose logs --tail 50 loki
curl http://localhost:33100/ready
```

If it **repeats forever** and Explore stays empty:

```powershell
git pull
docker compose up -d --force-recreate loki promtail
docker compose logs -f promtail
```

Confirm labels exist in Grafana Explore (Label browser), or:

```powershell
# Generate traffic so something is logged
curl http://localhost:3845/api/books
docker compose logs --tail 5 catalog-api
```

---

### H. Grafana Explore Loki shows **No data**

Checklist:

```powershell
docker compose ps loki promtail catalog-api
docker compose logs --tail 50 promtail
curl http://localhost:33100/ready
curl http://localhost:3845/api/health-demo
```

In Explore, set a label filter first, e.g.:

- Label: `compose_service` = `catalog-api`
- Or: `container` = `jf-catalog-api`

LogQL:

```logql
{compose_service="catalog-api"}
```

Widen time range to **Last 1 hour** / **Last 6 hours**.

---

### I. SQL Server container unhealthy / keeps restarting

```powershell
docker compose ps sqlserver
docker compose logs --tail 100 sqlserver
docker inspect jf-sqlserver --format "{{json .State.Health}}"
```

**Common causes:** weak password (must meet MSSQL complexity), insufficient RAM, corrupted volume.

```powershell
# Password must be strong (this lab default is in .env.example)
type .env
# Reset DB volume if corrupted:
docker compose down
docker volume rm jenkinsflow_sqlserver-data
docker compose up -d
```

Give Docker Desktop more memory if SQL Server dies with OOM:

```powershell
docker inspect jf-sqlserver --format "{{.State.OOMKilled}}"
```

---

### J. Valkey connection errors in catalog-api logs

```powershell
docker compose ps valkey
docker compose exec valkey valkey-cli ping
docker compose logs --tail 50 catalog-api
docker compose restart catalog-api
```

From the API container, Redis host must be `valkey` (Compose DNS), port `6379` — not `localhost:16379` (that host port is only for the PC).

---

### K. Image build fails (Maven / Dockerfile)

```powershell
docker compose build --progress=plain catalog-api
docker compose logs
```

Run tests on the host (if JDK/Maven installed) or via Maven container:

```powershell
cd apps\catalog-api
mvn -B test
# or:
docker run --rm -v ${PWD}:/ws -w /ws maven:3.9.11-eclipse-temurin-21 mvn -B test
```

---

### L. “Cannot connect to the Docker daemon”

```powershell
# Docker Desktop must be running on Windows
docker version
docker info
```

Restart Docker Desktop, then:

```powershell
docker compose up -d
```

---

### M. Stale containers after `git pull`

```powershell
git pull
docker compose up -d --build
# if weird state:
docker compose down
docker compose up -d --build
```

---

### N. Disk full / Docker eating space

```powershell
docker system df
docker system prune -f
# careful — removes unused images/networks:
docker system prune -a -f
# volumes too (DESTROYS unused volumes):
docker volume prune -f
```

---

## 12. Clean restart recipes

### Soft restart (keep data)

```powershell
docker compose restart
# or per service:
docker compose restart catalog-api grafana loki
```

### Rebuild app only

```powershell
git pull
docker compose up -d --build catalog-api
curl -X DELETE http://localhost:3845/api/cache/books
```

### Full stack recreate (keep volumes)

```powershell
git pull
docker compose down
docker compose up -d --build
```

### Full wipe (fresh lab)

```powershell
git pull
docker compose down -v
docker compose up -d --build
```

---

## 13. Useful one-liners cheat sheet

```powershell
docker compose ps
docker compose logs -f --tail 100 catalog-api
docker compose up -d --build catalog-api
docker compose down
docker compose down -v
docker compose exec valkey valkey-cli ping
docker compose exec catalog-api curl -fsS http://localhost:3845/actuator/health
docker inspect jf-sqlserver --format "{{.State.Health.Status}}"
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
docker system df
curl http://localhost:3845/api/books
curl http://localhost:3845/api/cache/stats
curl http://localhost:33100/ready
curl http://localhost:39090/-/healthy
```

---

## Related files in this repo

| File | Purpose |
|------|---------|
| `docker-compose.yml` | Full lab definition |
| `.env.example` / `.env` | Secrets & image tag defaults |
| `README.md` | Happy-path quick start |
| `PLAN.md` | Architecture / phases |
| `apps/catalog-api/Dockerfile` | API image build |
| `observability/` | Prometheus / Grafana / Loki / Promtail configs |
| `jenkins/Jenkinsfile` | Optional CD pipeline |
| `docs/JENKINS_PIPELINE.md` | Jenkins job setup & stage-by-stage guide |

When an error is new, capture:

```powershell
docker compose ps -a > compose-ps.txt
docker compose logs --tail 200 > compose-logs.txt
```

Those two files are usually enough to diagnose the next failure.
