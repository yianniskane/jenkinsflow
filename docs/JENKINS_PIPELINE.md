# Jenkins CD pipeline — detailed walkthrough

This lab uses **two** automation tools on purpose:

| Tool | Role here | When it runs |
|------|-----------|--------------|
| **GitHub Actions** | CI — verify every push/PR, publish image to GHCR | In the cloud, on GitHub |
| **Jenkins** | CD — on *your* PC, rebuild/redeploy `catalog-api` into the local Compose stack | When *you* click **Build** (or later, on a webhook) |

GitHub Actions answers: *“Is this commit good?”*  
Jenkins answers: *“Take a known-good build and put it onto my local lab.”*

You do **not** need Jenkins for day-to-day API learning. Start it when you want to practise classic enterprise CD.

---

## What the pipeline does (stage by stage)

Source of truth: [`jenkins/Jenkinsfile`](../jenkins/Jenkinsfile).

```text
Checkout → Test → Build image → Deploy (compose) → Smoke check
```

### 1. Checkout

```groovy
checkout scm
```

Jenkins clones/updates the Git repo configured on the job (your `jenkinsflow` GitHub repo) into the job workspace.  
Everything later runs against **that** checkout — not whatever happens to be in `C:\jenkinsflow` unless they are the same commit.

### 2. Test

```text
docker run --rm -v "$PWD":/ws -w /ws maven:3.9.11-eclipse-temurin-21 mvn -B test
```

- Runs inside a **Maven + JDK 21** container (so Jenkins itself does not need a JDK Maven install).
- Mounts `apps/catalog-api` and runs unit tests (`BookControllerTest`, etc.).
- If tests fail, the pipeline **stops** — no image, no deploy.
- Job parameter **`SKIP_TESTS=true`** skips this stage (useful only for learning; do not make it a habit).

### 3. Build image

```text
docker build -t ghcr.io/yianniskane/jenkinsflow-catalog-api:local \
             -t ghcr.io/yianniskane/jenkinsflow-catalog-api:jenkins-<BUILD_NUMBER> \
             apps/catalog-api
```

- Builds the Spring Boot image using `apps/catalog-api/Dockerfile`.
- Tags it as `:local` (what Compose uses by default) and `:jenkins-<n>` (so you can see which Jenkins build produced which image).
- Uses the **host Docker engine** via `/var/run/docker.sock` (Docker-out-of-Docker). The Jenkins container has the Docker *CLI*; your Desktop engine does the real work.

### 4. Deploy (compose)

```text
docker compose up -d --no-deps catalog-api
```

- Recreates **only** `catalog-api`.
- **`--no-deps`** means: do **not** restart SQL Server, Valkey, Grafana, etc. That is intentional CD behaviour — ship the app, leave the data plane alone.
- Compose picks up the image tag from env (`IMAGE_NAME` / `IMAGE_TAG`).

### 5. Smoke check

```text
curl http://host.docker.internal:3845/actuator/health
curl http://host.docker.internal:3845/api/health-demo
```

- From **inside** the Jenkins container, `localhost:3845` is Jenkins itself — not your API.
- `host.docker.internal` reaches the published port on your PC (Compose maps `3845:3845`).
- Retries for ~60s; fails the build if health never becomes `UP`.

---

## Prerequisites

1. Lab stack already running (at least SQL Server + Valkey; full `docker compose up -d` is fine).
2. Docker Desktop running (Jenkins needs the engine + socket).
3. This repo available on GitHub: `https://github.com/yianniskane/jenkinsflow.git`

Start Jenkins:

```powershell
cd C:\jenkinsflow
git pull
docker compose --profile jenkins up -d --build jenkins
```

Open **http://localhost:38080**

First boot can take several minutes while plugins load.  
The image is built from `jenkins/Dockerfile` (Pipeline/Git plugins + Docker CLI preinstalled; setup wizard disabled).

Check Jenkins is up:

```powershell
docker compose --profile jenkins ps jenkins
docker compose logs --tail 50 jenkins
```

---

## Two ways to create the Pipeline job

### Option A — Pipeline script from SCM (recommended)

This is the “real” way: the Jenkinsfile lives **in Git**. Change the file → commit → next build uses the new pipeline.

#### Steps in the Jenkins UI

1. From the Jenkins home page, click **New Item**.
2. Enter a name, e.g. `catalog-api-cd`.
3. Choose **Pipeline** → **OK**.
4. (Optional) Check **This project is parameterized** — the Jenkinsfile already declares parameters; Jenkins will discover them after the first SCM-loaded run. You can also add them manually:
   - `IMAGE_TAG` (String) default `local`
   - `SKIP_TESTS` (Boolean) default unchecked
5. Scroll to **Pipeline**.
6. **Definition:** `Pipeline script from SCM`
7. **SCM:** `Git`
8. **Repository URL:** `https://github.com/yianniskane/jenkinsflow.git`
9. **Credentials:**  
   - Public repo → leave blank.  
   - Private repo → **Add** → Username/password or SSH key (GitHub PAT as password is common).
10. **Branches to build:** `*/main`
11. **Script Path:** `jenkins/Jenkinsfile`  
    (important — not the repo root)
12. Save.
13. Click **Build Now** (or **Build with Parameters**).

#### What “from SCM” means

```text
GitHub repo
└── jenkins/Jenkinsfile   ← Jenkins reads this on every build
```

You edit the pipeline in IntelliJ/VS Code, push to GitHub, then build again. Jenkins always runs the version from the branch you configured.

---

### Option B — Paste the Jenkinsfile (first local experiment)

Use this once to learn the UI without wiring Git credentials.

1. **New Item** → name e.g. `catalog-api-cd-paste` → **Pipeline** → OK.
2. **Definition:** `Pipeline script` (not “from SCM”).
3. Open [`jenkins/Jenkinsfile`](../jenkins/Jenkinsfile) on disk or GitHub, copy the whole file.
4. Paste into the **Script** box.
5. Save → **Build Now**.

**Caveats**

- `checkout scm` expects SCM on the job. With a pasted script, either:
  - change the first stage to clone manually, or
  - still configure Git under the job, or
  - prefer Option A.
- Edits in Git do **not** update a pasted job until you paste again.

For a quick paste-only experiment you can temporarily replace Checkout with:

```groovy
stage('Checkout') {
  steps {
    git branch: 'main', url: 'https://github.com/yianniskane/jenkinsflow.git'
  }
}
```

---

## Running a build and reading the result

1. Open the job → **Build with Parameters**.
2. Leave `IMAGE_TAG=local`, `SKIP_TESTS` unchecked.
3. **Build**.
4. Open the build number → **Console Output**.

You should see Maven test output, `docker build` layers, `docker compose up`, then smoke-check curls.

Verify on the host:

```powershell
curl http://localhost:3845/api/health-demo
curl http://localhost:3845/api/books
docker images | findstr jenkinsflow-catalog-api
docker compose ps catalog-api
```

---

## Parameters explained

| Parameter | Default | Meaning |
|-----------|---------|---------|
| `IMAGE_TAG` | `local` | Image tag Compose should run (`IMAGE_TAG` env for compose) |
| `SKIP_TESTS` | `false` | Skip Maven tests — only for demos when you already trust the commit |

---

## How this interacts with GitHub Actions

```text
Developer pushes to GitHub
        │
        ▼
GitHub Actions (CI)
  mvn test → build image → push GHCR (:latest / :sha)
        │
        ▼ (separate, manual or later automated)
Jenkins on your PC (CD)
  mvn test → build local image → compose up catalog-api → smoke
```

They are **not** chained automatically in Phase 1. That is deliberate so you can see each tool. Later you could:

- have Jenkins pull `ghcr.io/...:latest` instead of building locally, or  
- trigger Jenkins from a GitHub webhook after Actions succeeds.

---

## Working directory note (Compose deploy)

The Deploy stage runs `docker compose` from the Jenkins job workspace (the Git checkout). That directory must contain this repo’s `docker-compose.yml`.

The Jenkins container also mounts the host clone read-only at `/workspace` for convenience, but the Jenkinsfile uses the **job workspace** checkout. Keep them in sync by pushing/pulling Git as usual.

If Compose says it cannot find the project, check Console Output for the current directory and that `docker-compose.yml` is present in the workspace.

---

## Common failures

### Jenkins UI never loads / plugin errors

```powershell
docker compose --profile jenkins logs -f jenkins
docker compose --profile jenkins up -d --build jenkins
```

Wait until the log shows Jenkins is fully up.

### `docker: not found` in Console Output

The Jenkins image must include Docker CLI (`jenkins/Dockerfile`). Rebuild:

```powershell
docker compose --profile jenkins up -d --build jenkins
```

### `permission denied` on `/var/run/docker.sock`

Docker Desktop + socket mount issue. Confirm Compose has:

```yaml
volumes:
  - /var/run/docker.sock:/var/run/docker.sock
```

Restart Jenkins after Docker Desktop restarts.

### Smoke check cannot reach the API

```powershell
curl http://localhost:3845/actuator/health
docker compose ps catalog-api
```

From Jenkins we use `host.docker.internal` (Compose sets `extra_hosts` for Linux engines). If smoke fails but host curl works, check that `extra_hosts` entry and Jenkins logs.

### `checkout scm` fails (pasted job)

Use Option A, or replace Checkout with an explicit `git url: '...'` step (see Option B).

### Tests fail in Jenkins but pass in Actions

Compare the commit SHA. Jenkins tests the branch it checked out; local uncommitted edits are invisible to Jenkins unless you push them.

---

## Minimal “I just want to see it work” path

```powershell
cd C:\jenkinsflow
git pull
docker compose up -d
docker compose --profile jenkins up -d --build jenkins
```

1. Open http://localhost:38080  
2. New Item → `catalog-api-cd` → Pipeline  
3. Pipeline script from SCM → Git → `https://github.com/yianniskane/jenkinsflow.git`  
4. Script Path: `jenkins/Jenkinsfile`  
5. Build Now → watch Console Output  
6. `curl http://localhost:3845/api/health-demo`

That is the full local CD loop this lab is meant to teach.
