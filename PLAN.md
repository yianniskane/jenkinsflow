# CI/CD Learning Lab — Plan for Review

**Status:** decisions partially locked — awaiting “proceed” to build the app  
**Goal:** learn the real path of *build → test → ship → observe* with Docker, not build a production platform yet.  
**Runtime assumption:** you already have Docker running on your PC — Phase 1 is designed to run primarily via `docker compose` locally (app, SQL Server, Valkey, observability, Jenkins). The cloud agent scaffolds and pushes; you start the stack on your machine.  
**GitHub repo:** [https://github.com/yianniskane/jenkinsflow](https://github.com/yianniskane/jenkinsflow) — plan commits are pushed here; GitHub Actions will live in this repo.

### Locked decisions (from you)

| Decision | Choice |
|----------|--------|
| Build tool | **Maven** |
| Valkey | **Include in Phase 1** — small, visible usage only (e.g. cache `GET /api/books` so you can see hit/miss in logs/metrics) |
| Git remote | **https://github.com/yianniskane/jenkinsflow.git** |
| Traefik / Keycloak | Still **Phase 2** |

---

## Verdict on your idea

Your sequence is **sound**. Phase 1 (app + DB + CI/CD + observability) teaches the core loop. Phase 2 (Traefik, Keycloak, Valkey) is the right “complete the journey” layer (edge routing, identity, caching) and should wait until Phase 1 works end-to-end.

Doing Traefik/Keycloak/Valkey too early usually muddies learning: failures become hard to attribute (pipeline vs auth vs proxy vs cache).

---

## What we will build (Phase 1)

Everything runs in Docker Compose locally so you can start/stop the whole lab with one command.

```text
┌─────────────┐     ┌──────────────┐     ┌─────────────────────────┐
│ Spring Boot │────▶│ SQL Server   │     │ Observability           │
│ API (JVM)   │     │ (2 tables)   │     │ Prometheus + Grafana    │
│ + Actuator  │     └──────────────┘     │ Loki + Promtail         │
└─────────────┘                          └─────────────────────────┘
        ▲
        │ images built by CI
┌───────┴────────┐   ┌─────────────┐
│ GitHub Actions │   │ Jenkins     │
│ (PR CI)        │   │ (CD / learn │
│ build+test+img │   │  pipelines) │
└────────────────┘   └─────────────┘
```

### 1. Spring Boot 4 API (no frontend)

- **Stack:** Spring Boot **4.2.x**, Java 21, Maven, Spring Web, Spring Data JPA, Actuator, Micrometer.
- **Why Spring Boot 4:** current stable line; good Actuator/metrics story for Grafana later.
- **Why no frontend yet:** keeps focus on API, DB, pipelines, and logs/metrics.

**Suggested endpoints (small and concrete):**

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/api/health-demo` | Simple “app is alive” beyond Actuator |
| `GET` | `/api/books` | List books from DB |
| `POST` | `/api/books` | Create a book |
| `GET` | `/api/books/{id}` | Get one book |
| `GET` | `/actuator/health` | Health for Compose/Jenkins |
| `GET` | `/actuator/prometheus` | Metrics scrape target |

Seed a couple of rows on startup so the API is usable immediately.

### 2. SQL Server + two tables

- **Why SQL Server:** matches what many enterprises still run; forces you to learn JDBC/MSSQL specifics in Docker (unlike Postgres, which is “too easy” for this lab).
- **Tables (proposed):**
  - `authors` — `id`, `name`
  - `books` — `id`, `title`, `author_id` (FK), `published_year`
- Schema via Flyway (or Liquibase) so migrations are part of the CI story, not hand-waved SQL.

### 3. Git + GitHub Actions

- Repo layout with Dockerfiles, Compose, workflows, and a short README.
- **GitHub Actions role = CI (continuous integration):**
  - on PR / push to `main`
  - compile + unit/integration tests
  - build Docker image
  - optionally push to a registry (local registry in Compose, or GHCR if you want)
- **Why Actions first:** it is the default “repo-native” CI; you learn YAML pipelines where the code lives.

### 4. Jenkins

- Jenkins in Docker (controller + Docker-in-Docker or Docker socket — we’ll pick the safer learning-friendly option).
- **Jenkins role = CD / second pipeline brain (continuous delivery):**
  - pipeline that pulls the repo (or an image built by Actions)
  - runs tests again (or trusts the CI artifact)
  - deploys/restarts the Compose stack (or a “staging” profile)
- **Why both Actions and Jenkins:** not because production needs both, but because *you* want to learn both. We will keep responsibilities distinct so you compare them instead of duplicating the same job twice with no lesson.

| Tool | Learns | Does in this lab |
|------|--------|------------------|
| GitHub Actions | cloud/repo CI | verify every commit |
| Jenkins | self-hosted CD / classic enterprise | deploy + parameterized jobs |

### 5. Grafana / Loki (+ what else is appropriate)

Minimum useful observability set:

| Piece | Role |
|-------|------|
| **Prometheus** | scrape Spring Actuator metrics |
| **Grafana** | dashboards (JVM, HTTP, DB pool, cache) |
| **Loki** | log store |
| **Promtail** | ship container logs → Loki |
| **Spring Actuator + Micrometer** | app-side metrics/health |
| **Valkey** | Redis-compatible cache — cache the book list so you can *see* cache working |

**Why this set:** Loki alone shows logs; without Prometheus you miss “is latency climbing?” — the pair is the standard teaching stack. Valkey is in Phase 1 only as a thin demo (not a full caching strategy).

Optional stretch (only if Phase 1 still feels light): Alertmanager with one toy alert (e.g. 5xx rate). Easy to skip.

### Valkey in Phase 1 (how you’ll “see it work”)

- Compose service: `valkey/valkey:latest` (or a pinned tag).
- App uses Spring Data Redis / cache abstraction against Valkey.
- `GET /api/books` served from cache after the first DB hit; logs or a tiny `/api/cache/stats` (or Actuator metrics) show hits vs misses.
- Enough to learn the client + Compose wiring; not a production cache design.

---

## Explicitly deferred (Phase 2 — after Phase 1 works)

| Component | Why later |
|-----------|-----------|
| **Traefik** | reverse proxy / TLS / routing — needs a stable app first |
| **Keycloak** | auth/OIDC — changes every endpoint and testing story |

Phase 2 sketch (not built now): Traefik in front of the API, Keycloak for JWT/OIDC. Valkey already present from Phase 1. Same Compose lab, extended — not a rewrite.

---

## Proposed repo layout

```text
.
├── PLAN.md                 ← this file
├── README.md               ← how to run the lab
├── docker-compose.yml      ← app + db + valkey + observability (+ jenkins profile)
├── apps/
│   └── catalog-api/        ← Spring Boot 4 / Maven project
├── db/
│   └── init/               ← optional seed scripts (Flyway lives in app)
├── jenkins/
│   └── Jenkinsfile
├── observability/
│   ├── prometheus/
│   ├── grafana/provisioning/
│   └── loki/
└── .github/workflows/
    └── ci.yml
```

---

## Learning flow (what you will actually practice)

1. Change an endpoint → push → Actions fails/passes.
2. Green CI → Jenkins job deploys updated image to the local stack.
3. Hit the API → see request metrics in Grafana and logs in Loki.
4. Break the DB connection → watch health go red and logs show the cause.

That loop is the whole point of Phase 1.

---

## Design choices I recommend (say if you disagree)

1. **Java 21 LTS** with Spring Boot 4.2.x (not bleeding-edge JDK unless you want it).
2. **Maven** over Gradle (simpler for a first Spring CI lab; Gradle is fine if you prefer).
3. **Flyway** migrations in the app, not manual SQL-only init.
4. **GitHub Actions = CI**, **Jenkins = CD/deploy** (clear split).
5. **No Kubernetes yet** — Compose is enough; K8s is a separate learning arc.
6. **No real cloud deploy in Phase 1** — local Docker registry + Compose keeps cost/complexity down.
7. **SQL Server Developer/Express image** in Compose; accept that it is heavier than Postgres (that’s intentional). Give Docker Desktop enough RAM (about **4 GB+ free** for MSSQL alone is a common baseline; the full lab will want more).

---

## Out of scope for Phase 1

- Frontend / SPA
- Traefik, Keycloak, Valkey
- Kubernetes / Helm
- Production hardening (secrets managers, multi-AZ, HA Jenkins)
- Multi-service microservices sprawl

---

## Git

Source of truth for this lab: **https://github.com/yianniskane/jenkinsflow**

Commits are pushed to that GitHub repo (and may also sync to the Cursor project remote). GitHub Actions workflows in `.github/workflows/` will run against this repository.

---

## Remaining open questions

1. **Say “proceed”** when Phase 1 scope above is OK to build.
2. Image registry: **GHCR** (fits this GitHub repo) or local Docker registry in Compose?
3. Jenkins: full CD deploy job from day one, or start with a “mirror CI” job then add deploy?
4. Domain still `authors` / `books`, or something else?
5. Phase 2 order: **Traefik → Keycloak** (Valkey already in Phase 1)?

---

## Next step after you say proceed

1. Spring Boot 4 + Maven app + Flyway + SQL Server in Compose  
2. Valkey + simple book-list cache demo  
3. Actuator/Prometheus metrics  
4. Grafana + Loki + Promtail  
5. GitHub Actions CI  
6. Jenkins + Jenkinsfile deploy path  
7. README walkthrough of the full loop  

No application code until you say **proceed** (optionally after linking GitHub).
