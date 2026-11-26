# jenkinsflow — CI/CD learning lab

Docker-first lab for learning **build → test → ship → observe**.

Stack (Phase 1):

- **Spring Boot 4.2** catalog API (Maven, Java 21)
- **SQL Server** (`authors`, `books` via Flyway)
- **Valkey** (Redis-compatible cache for `GET /api/books`)
- **GitHub Actions** CI → tests + image push to **GHCR**
- **Jenkins** CD (optional Compose profile) to rebuild/redeploy locally
- **Prometheus + Grafana + Loki + Promtail**

Repo: https://github.com/yianniskane/jenkinsflow

---

## Prerequisites

- Docker Desktop (or Docker Engine + Compose v2) with enough RAM for SQL Server (plan on **8 GB+** for the full lab)
- Optional: IntelliJ IDEA + JDK 21 for editing `apps/catalog-api`
- Optional: Maven 3.9+ on the host (or use the Maven wrapper / Docker)

Copy env defaults:

```bash
cp .env.example .env
```

---

## Quick start (local Compose)

From the repo root:

```bash
docker compose up -d --build
```

Wait until `catalog-api` is healthy, then try:

```bash
curl http://localhost:3845/api/health-demo
curl http://localhost:3845/api/books
curl http://localhost:3845/api/cache/stats
# second call should be a Valkey hit (no "CACHE MISS" in logs)
curl http://localhost:3845/api/books
```

Create a book (evicts cache):

```bash
curl -X POST http://localhost:3845/api/books \
  -H 'Content-Type: application/json' \
  -d '{"title":"Clean Pipelines","authorId":1,"publishedYear":2024}'
```

### Useful URLs

| What | URL |
|------|-----|
| API | http://localhost:3845 |
| Actuator health | http://localhost:3845/actuator/health |
| Prometheus metrics | http://localhost:3845/actuator/prometheus |
| Grafana (admin/admin) | http://localhost:3300 |
| Prometheus UI | http://localhost:39090 |
| Loki | http://localhost:33100 |
| SQL Server | `localhost:41433` (sa / see `.env`) |
| Valkey | `localhost:16379` |

See Valkey in action: open Grafana → Explore → Loki, query `{compose_service="catalog-api"} |= "CACHE"`.

Stop:

```bash
docker compose down
# wipe DB/volumes too:
docker compose down -v
```

---

## IntelliJ

1. Clone this repo.
2. Open `apps/catalog-api` (or the repo root) as a Maven project.
3. Start infra only:

```bash
docker compose up -d sqlserver sqlserver-init valkey
```

4. Run `CatalogApiApplication` with the defaults in `application.yml` (ports `41433` / `16379`).

---

## GitHub Actions (CI)

Workflow: `.github/workflows/ci.yml`

On every PR/push to `main`:

1. `mvn test` + package  
2. Build Docker image  
3. On push to `main`, push to `ghcr.io/yianniskane/jenkinsflow-catalog-api`

Packages are private/public based on your GHCR settings. You may need to accept the package and set visibility once under GitHub → Packages.

---

## Jenkins (CD, optional)

Start Jenkins (uses Docker socket on your machine):

```bash
docker compose --profile jenkins up -d jenkins
```

Open http://localhost:38080

Create a **Pipeline** job pointing at this repo’s `jenkins/Jenkinsfile` (Pipeline script from SCM), or paste the Jenkinsfile contents for a first local experiment.

The pipeline: test → build image → `docker compose up -d --no-deps catalog-api` → smoke check.

> First Jenkins boot can take a while. For learning, prefer GitHub Actions for CI and use Jenkins when you want to practise self-hosted CD.

---

## API map

| Method | Path | Notes |
|--------|------|--------|
| GET | `/api/health-demo` | Simple liveness demo |
| GET | `/api/books` | Cached in Valkey (`books:all`) |
| GET | `/api/books/{id}` | Not cached (DB) |
| POST | `/api/books` | Creates book, evicts cache |
| GET | `/api/cache/stats` | Is the cache warm? |
| DELETE | `/api/cache/books` | Clear Valkey books cache |
| GET | `/actuator/health` | Health |
| GET | `/actuator/prometheus` | Metrics for Prometheus |

---

## Layout

```text
apps/catalog-api/     Spring Boot 4 API
db/init/              Creates the catalog database
observability/        Prometheus, Grafana, Loki, Promtail
jenkins/Jenkinsfile   Local CD pipeline
.github/workflows/    GitHub Actions CI
docker-compose.yml    Full lab
PLAN.md               Design notes / Phase 2
```

---

## Troubleshooting

Detailed Docker / Compose error playbooks and commands:

→ **[docs/DOCKER_TROUBLESHOOTING.md](docs/DOCKER_TROUBLESHOOTING.md)**

## Phase 2 (later)

Traefik → Keycloak (Valkey already present). See `PLAN.md`.
