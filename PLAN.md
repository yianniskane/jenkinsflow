# CI/CD Learning Lab — Plan

**Status:** Phase 1 implemented in this repo  
**GitHub:** https://github.com/yianniskane/jenkinsflow  
**Runtime:** Docker Compose on your PC (IntelliJ optional for `apps/catalog-api`)

---

## Locked decisions

| Decision | Choice |
|----------|--------|
| Build tool | Maven |
| Cache demo | Valkey in Phase 1 (`GET /api/books`) |
| Registry | GHCR (`ghcr.io/yianniskane/jenkinsflow-catalog-api`) |
| Domain | `authors` / `books` |
| Phase 2 | Traefik → Keycloak (Valkey already present) |

---

## Phase 1 delivered

| Piece | Location |
|-------|----------|
| Spring Boot 4.2 API | `apps/catalog-api` |
| SQL Server + Flyway | Compose `sqlserver` + `db/migration` |
| Valkey cache | Compose `valkey` + `/api/cache/*` |
| Observability | Prometheus, Grafana, Loki, Promtail |
| GitHub Actions CI | `.github/workflows/ci.yml` |
| Jenkins CD | `jenkins/Jenkinsfile` + Compose profile `jenkins` |

How to run: see **README.md**.

---

## Why this shape

- **Actions = CI** (verify every commit, publish image).  
- **Jenkins = CD** (optional self-hosted deploy loop).  
- **Grafana/Loki/Prometheus** so you can see requests, latency, and `CACHE MISS` logs.  
- **Valkey early** so you can watch cache behaviour without waiting for Phase 2.

---

## Phase 2 (not built yet)

1. **Traefik** — reverse proxy / routing in front of the API  
2. **Keycloak** — OIDC / JWT protection  

Same Compose lab, extended — not a rewrite.
