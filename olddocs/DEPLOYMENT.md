# Deployment Runbook — Synoptic Engine

Target stack for the demo: **Neon** (Postgres) · **Fly.io** (Spring Boot API) ·
**Vercel** (Nuxt frontend) · **Mailtrap** (SMTP). Hobby tier on each is enough.

This deploys the **existing** app so you have a live URL for the presentation. The by-hand
rebuild in [`REBUILD_ROADMAP.md`](REBUILD_ROADMAP.md) is a separate, longer track.

---

## 0. Local development setup

### Prerequisites

| Tool | Version | Install |
|---|---|---|
| JDK (Temurin) | **25** | [adoptium.net](https://adoptium.net) or `sdk install java 25-tem` |
| Docker | latest | [docs.docker.com/get-docker](https://docs.docker.com/get-docker/) — needed for compose + Testcontainers |
| Node.js | **22** | via `nvm` or [nodejs.org](https://nodejs.org) |
| pnpm | **9+** | `npm install -g pnpm` |

### Backend (`api/`)

Spring Boot's Docker Compose integration (`spring-boot-docker-compose`) **automatically starts**
the local Postgres 18 and Mailpit containers when you run `bootRun` — you do not need a separate
`docker compose up`.

```bash
cd api
cp .env.example .env     # Postgres container config; defaults work out of the box
./gradlew bootRun        # starts on :8090; Flyway runs migrations on first boot
```

`application-local.yaml` provides safe dev-only defaults for every required secret — JWT signing
key, admin credentials, AES-256-GCM encryption key, and SMTP settings. You only need to add vars
to `api/.env` if you want to override them (see comments in `.env.example`).

Verify: `curl http://localhost:8090/api/actuator/health` → `{"status":"UP"}`

Email is captured by **Mailpit** — nothing is actually delivered. Browse the inbox at
[http://localhost:8025](http://localhost:8025).

### Frontend (`web/`)

No `.env` file is required for local dev — the `api-base` plugin defaults to
`http://localhost:8090` when `NUXT_PUBLIC_API_BASE` is unset in dev mode.

```bash
cd web
pnpm install     # also runs postinstall → regenerates app/api/* from ../api-docs.json
pnpm dev         # starts on :3000
```

Open [http://localhost:3000](http://localhost:3000). Log in with `admin@synoptic.dev` /
`Admin@123` (the defaults from `application-local.yaml`; change via `SYNOPTIC_ADMIN_EMAIL` /
`SYNOPTIC_ADMIN_PASSWORD` in `api/.env`).

### Test suite

```bash
cd api
./gradlew test        # full Testcontainers suite — Docker must be running
./gradlew unitTests   # fast unit-only run, no Docker required
./gradlew testClasses # compile-only check, no tests run
```

> **End-to-end (Playwright)**: run from `web/` with `pnpm exec playwright test --workers=1`
> after both the API and frontend are up. Use `--workers=1` inside the devcontainer to avoid
> resource contention.

---

## ⚠️ Read first: the RLS / Neon role trap

Your migrations call `ENABLE ROW LEVEL SECURITY` but never `FORCE ROW LEVEL SECURITY`
(0 occurrences in `api/src/main/resources/db/migration/`). In Postgres, **the role that owns
a table bypasses that table's RLS policies** unless `FORCE` is set. The test suite only proves
isolation because it connects as a dedicated `synoptic_app` role created `NOBYPASSRLS`
(see [init-non-superuser.sql](api/src/test/resources/db/test/init-non-superuser.sql)).

So on Neon you have a choice:

- **Option A — do it properly (recommended; it's a headline feature):** keep two roles. Flyway
  migrates as the Neon **owner** role; the app connects at runtime as a separate
  **`synoptic_app` `NOBYPASSRLS`** role that is *not* the table owner. Then RLS actually fires.
  Steps in §1 below.
- **Option B — demo shortcut (document as tech debt):** connect the app as the single Neon
  owner role. RLS will **not** enforce; tenant isolation falls back to the Hibernate `@Filter`
  layer only (still app-level isolation, but the "RLS is primary" guarantee is gone). Fine for a
  hobby demo *if you say so out loud*; bad to claim multi-tenant RLS while running this way.

If multi-tenant RLS is part of your pitch, use Option A.

---

## 1. Neon (Postgres)

1. Create a project → you get a database and an owner role + connection string.
2. In the Neon **SQL Editor**, create the runtime app role (Option A):
   ```sql
   CREATE ROLE synoptic_app LOGIN PASSWORD '<pick-a-strong-password>' NOBYPASSRLS;
   GRANT USAGE ON SCHEMA public TO synoptic_app;
   GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO synoptic_app;
   GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO synoptic_app;
   -- ensure future Flyway-created tables are reachable by the app role:
   ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO synoptic_app;
   ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO synoptic_app;
   ```
   Run the two `GRANT ALL ... ON ALL ...` lines **again after the first deploy** (once Flyway
   has created the tables) so the app role can see them.
3. You'll wire **two** JDBC URLs into Fly: the owner role for Flyway, `synoptic_app` for the app.
   Neon requires SSL — append `?sslmode=require`. JDBC form:
   `jdbc:postgresql://<host>/<db>?sslmode=require`

> Option B: skip the `synoptic_app` role and use the owner connection string for both the app
> and Flyway.

## 2. Fly.io (Spring Boot API)

Artifacts already in the repo: [api/Dockerfile](api/Dockerfile), [api/fly.toml](api/fly.toml),
[api/.dockerignore](api/.dockerignore).

```bash
cd api
fly launch --no-deploy --copy-config      # creates the app from fly.toml
fly volumes create synoptic_storage --size 1 --region iad   # backs /data (uploads)
```

Set secrets (these arm `SecretsGuard`, which refuses to boot the `prod` profile without them):

```bash
# generate the two keys first:
#   JWT_SECRET:                openssl rand -base64 48
#   SYNOPTIC_ENCRYPTION_KEY:   openssl rand -base64 32   (must decode to exactly 32 bytes)

fly secrets set \
  SPRING_DATASOURCE_URL='jdbc:postgresql://<host>/<db>?sslmode=require' \
  SPRING_DATASOURCE_USERNAME='synoptic_app' \
  SPRING_DATASOURCE_PASSWORD='<synoptic_app password>' \
  SPRING_FLYWAY_URL='jdbc:postgresql://<host>/<db>?sslmode=require' \
  SPRING_FLYWAY_USER='<neon owner role>' \
  SPRING_FLYWAY_PASSWORD='<neon owner password>' \
  JWT_SECRET='<from openssl>' \
  SYNOPTIC_ENCRYPTION_KEY='<from openssl>' \
  SYNOPTIC_ADMIN_EMAIL='admin@yourdomain.dev' \
  SYNOPTIC_ADMIN_PASSWORD='<strong admin password>' \
  CORS_ALLOWED_ORIGINS='https://<your-vercel-domain>' \
  MAIL_HOST='sandbox.smtp.mailtrap.io' \
  MAIL_PORT='2525' \
  MAIL_USERNAME='<mailtrap user>' \
  MAIL_PASSWORD='<mailtrap pass>' \
  MAIL_SMTP_AUTH='true' \
  MAIL_SMTP_STARTTLS='true' \
  MAIL_FROM='no-reply@yourdomain.dev'

fly deploy
```

Notes:
- `SPRING_FLYWAY_*` (Option A) runs migrations as the **owner** while the main datasource is the
  non-owner `synoptic_app` → RLS fires. For Option B, drop the three `SPRING_FLYWAY_*` lines.
- The health check in `fly.toml` hits `/api/actuator/health` (actuator sits under the
  `server.servlet.context-path=/api`, like every other endpoint).
- Local filesystem is ephemeral on Fly; the `synoptic_storage` volume mounted at `/data` keeps
  uploaded files. `SYNOPTIC_STORAGE_ROOT=/data/storage` is already set in `fly.toml`.
- Memory is set to 1 GB — Spring Boot 4 + Hibernate can OOM at 512 MB on boot.

## 3. Vercel (Nuxt frontend)

Nuxt's Nitro auto-detects Vercel; no preset config needed. Use Vercel's Git integration:

1. New Project → import the repo → set **Root Directory = `web`**.
2. Vercel auto-detects Nuxt (build `pnpm build`, output handled by Nitro).
3. Environment variable: `NUXT_PUBLIC_API_BASE = https://<your-fly-app>.fly.dev`
   (Nuxt maps `NUXT_PUBLIC_API_BASE` → `runtimeConfig.public.apiBase` automatically — no code
   change to [nuxt.config.ts](web/nuxt.config.ts)).
4. Deploy. Then set Fly's `CORS_ALLOWED_ORIGINS` to the resulting Vercel URL and `fly deploy`
   again (chicken-and-egg: you need each other's final URL).

> `postinstall` runs `openapi-ts` against the committed `../api-docs.json`, so the typed API
> generates fine on Vercel without the backend being reachable at build time.

## 4. Mailtrap

- Use a **Sandbox** inbox for the demo (captures mail, nothing is actually delivered — ideal for
  a presentation). Credentials → the `MAIL_*` secrets above (`sandbox.smtp.mailtrap.io:2525`).
- For real delivery later, switch to a Mailtrap **Sending** stream and a verified domain.

---

## Deploy order (avoid the circular-URL dance)

1. Neon up, roles created.
2. Fly deploy with a placeholder `CORS_ALLOWED_ORIGINS` (e.g. `https://example.vercel.app`).
   Confirm `/api/actuator/health` is green and migrations ran (`fly logs`).
3. Vercel deploy with `NUXT_PUBLIC_API_BASE` = the Fly URL.
4. Re-run `fly secrets set CORS_ALLOWED_ORIGINS=<real vercel url>` → `fly deploy`.
5. (Option A) re-run the `GRANT ALL ... ON ALL ...` grants in Neon now that tables exist.
6. Log in with `SYNOPTIC_ADMIN_EMAIL` / `SYNOPTIC_ADMIN_PASSWORD`, or self-register at `/register`.

## CI/CD already wired

- [.github/workflows/ci.yml](.github/workflows/ci.yml) — backend Testcontainers suite + OpenAPI
  drift gate; web lint/typecheck/build. Runs on every push and PR.
- [.github/workflows/deploy-api.yml](.github/workflows/deploy-api.yml) — `fly deploy` on push to
  `main` after CI passes. Needs repo secret `FLY_API_TOKEN` (`fly tokens create deploy`).
- Vercel deploys itself from Git — no workflow needed.

## Pre-demo smoke checklist

- [ ] `https://<fly>/api/actuator/health` → `{"status":"UP"}`
- [ ] `https://<fly>/api/swagger-ui` loads
- [ ] Frontend loads, login works, no CORS errors in the browser console
- [ ] Create a lead → it persists after refresh
- [ ] Sending an email shows up in the Mailtrap inbox
- [ ] (Option A) two tenants can't see each other's records — your RLS talking point
- [ ] `min_machines_running = 1` in `fly.toml` during the talk so there's no cold-start pause

## Env var reference

| Variable | Purpose | Required in `prod` |
|---|---|---|
| `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` | Runtime DB conn (use `synoptic_app`) | yes |
| `SPRING_FLYWAY_URL/USER/PASSWORD` | Migrations as owner (Option A) | Option A only |
| `JWT_SECRET` | JWT signing (256-bit+) | yes (SecretsGuard) |
| `SYNOPTIC_ENCRYPTION_KEY` | AES-256-GCM at-rest secrets, base64 32 bytes | yes (SecretsGuard) |
| `CORS_ALLOWED_ORIGINS` | Vercel origin(s); no `*` with credentials | yes |
| `SYNOPTIC_ADMIN_EMAIL/PASSWORD` | Seeded admin login | recommended |
| `MAIL_HOST/PORT/USERNAME/PASSWORD/SMTP_AUTH/SMTP_STARTTLS/FROM` | Mailtrap SMTP | for email |
| `SYNOPTIC_INBOUND_MAIL_SECRET` | HMAC for inbound-mail webhook | optional |
| `NUXT_PUBLIC_API_BASE` (Vercel) | Frontend → API base URL | yes |
