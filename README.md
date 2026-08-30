# CodecLog

Letterboxd for your game backlog, with your library synced automatically.

CodecLog syncs a player's Steam library, surfaces the games they own but have not touched in
months, and lets them review those games for friends and followers to read, like and comment on.
Existing backlog trackers make you log everything by hand; existing review sites have no social
graph. CodecLog is the combination.

> **Milestone 1 — Foundation.** The API skeleton, database, migration path, health checks, API docs
> and CI are in place. No product features yet; those land in M2 onwards. See [Roadmap](#roadmap).

---

## Stack

| Area | Choice |
| --- | --- |
| Backend | Java 25, Spring Boot 4.1.1, Gradle (Kotlin DSL) |
| Database | PostgreSQL 18.6 — the only datastore |
| Persistence | Spring Data JPA + Flyway |
| Frontend (M2) | React SPA — Vite + TypeScript, TanStack Query, Tailwind |
| Auth (M2) | Steam OpenID 2.0 + Google OAuth 2.0, JWT sessions |
| Catalog (M3) | IGDB via Twitch developer credentials |
| Testing | JUnit 6.1.3, MockMvc slices, Testcontainers 2, WireMock |
| Ops | Docker, GitHub Actions, Actuator + Micrometer + Prometheus, OpenAPI 3.2 via springdoc |

---

## Quickstart

Requirements: **a JDK**, **Docker**. Nothing else — the Gradle wrapper fetches its own Gradle, and
the build provisions JDK 25 itself if you do not have it, so the JDK you happen to have installed
does not silently become the one the project compiles against.

```bash
cp .env.example .env
docker compose up -d db          # Postgres 18.6 on :5432
cd api && ./gradlew bootRun --args='--spring.profiles.active=local'
```

Or run the whole stack the way it runs in production:

```bash
docker compose up --build
```

Then:

| | |
| --- | --- |
| Health | <http://localhost:8080/actuator/health> |
| API docs (Swagger UI) | <http://localhost:8080/docs> |
| OpenAPI document | <http://localhost:8080/v3/api-docs> |
| Prometheus metrics | <http://localhost:8080/actuator/prometheus> |

A third option needs no Compose at all — `./gradlew bootTestRun` starts the API against a throwaway
Postgres container, which is the fastest way to get a guaranteed-clean database.

### Tests

```bash
cd api && ./gradlew build
```

That runs Checkstyle, the unit and slice tests, and the Testcontainers integration tests against a
real Postgres 18.6. It needs a running Docker daemon. If you use OrbStack, Colima or Rancher rather
than Docker Desktop, the build reads your active `docker context` and points Testcontainers at it,
so no `DOCKER_HOST` juggling is required.

---

## Layout

```
codeclog/
├── api/                        Spring Boot service
│   ├── src/main/java/com/codeclog/api/
│   │   └── common/             Cross-cutting only; features sit alongside it
│   │       ├── config/         Configuration properties, CORS, OpenAPI
│   │       ├── error/          The single error envelope and its handler
│   │       └── logging/        Request correlation ids
│   ├── src/main/resources/
│   │   ├── db/migration/       Flyway migrations — the schema's only source of truth
│   │   └── application*.yml    Production-safe defaults; `local` relaxes them
│   ├── src/test/java/.../support/   Testcontainers harness
│   └── Dockerfile              Multi-stage, layered, non-root
├── web/                        React SPA — arrives in M2
├── DECISIONS.md                Running log of choices and their tradeoffs
├── docker-compose.yml
└── .github/workflows/ci.yml
```

---

## Design notes

The decisions worth defending, and the point at which each would flip. Choices made during the
build are logged as they happen in [DECISIONS.md](DECISIONS.md).

### One datastore

Postgres holds everything; there is no Redis in the MVP. At a realistic scale of tens to low
hundreds of users, a cache is a second thing to operate, a second consistency story and a second
failure mode, in exchange for latency the app is not short of. **Flips when** a profiled endpoint
is dominated by repeated identical reads — likely first at IGDB metadata lookups, which are already
cached in `games` rather than re-fetched.

### Feed: fan-out on read

The feed is a query joining `reviews` against the viewer's `follows`, ordered by `created_at DESC`
and cursor-paginated on `(created_at, id)`. No materialised feed table, no write amplification, and
following someone new needs no backfill.

The alternative, fan-out on write, appends to a `feed_entries` table as reviews are created. It
reads faster and scales past large follow graphs, at the cost of write amplification for
high-follower accounts (usually solved with a hybrid: fan out for normal accounts, read-time merge
for the long tail). **Flips when** p95 feed latency exceeds roughly 200ms under real load.

### Providers behind an interface from day one

Steam is the only library provider in v1, but sync sits behind `LibraryProvider`, so a second
provider is an implementation plus configuration rather than a refactor.

That matters because the obvious second providers are not equal. **PSN** has no public API; every
available client is reverse-engineered and authenticates with an NPSSO token that is
password-equivalent, must be copied out of a browser session by hand, expires every couple of
months, and carries a documented account-suspension risk. Asking users to paste that into a
portfolio app is not an acceptable security posture, so it is not implemented. **Xbox** is more
tractable: the OpenXBL gateway offers real OAuth and a free tier, and is the likely second provider.

### Sync is a job, not a request

Steam syncs write a `sync_jobs` row before doing any work and update it on completion, so sync state
is durable and inspectable rather than living in a log file. The pipeline is idempotent (upserts
keyed on natural unique constraints), throttled against Steam's rate limits, and partial-failure
tolerant — one user's failure never aborts the batch. A private Steam profile resolves to its own
`PROFILE_PRIVATE` status, because it is an expected state rather than an error.

Playtime snapshots are written only when playtime actually changed. Steam exposes no playtime
history beyond "forever" and "last two weeks", so trend data has to be accumulated going forward;
there is no backfill to be had.

### Cursor pagination everywhere

Offset pagination drifts when rows are inserted between pages, which is exactly what a
reverse-chronological social feed does constantly. Every list endpoint is cursor-paginated.

### Schema lives in migrations

`ddl-auto` is `validate`, never `update`. Hibernate checks that the entities match what Flyway
built; it is never allowed to change the database itself. `updated_at` is maintained by a database
trigger rather than in application code, so a manual `UPDATE` or a later batch job cannot leave a
stale timestamp behind.

### Errors have exactly one shape

Every non-2xx response is `{ "error": { "code", "message", "details" } }`, including the failures
Spring MVC raises before a controller is reached — unparseable body, unsupported method, no such
route. Clients branch on `code`, never on message text. Validation failures carry field-level
`details`; 500s deliberately carry nothing but a generic message.

---

## Operations

- **Health.** `/actuator/health`, plus `/actuator/health/liveness` and `/actuator/health/readiness`
  for the deployment platform. Component detail is off by default and shown only under the `local`
  profile — an unauthenticated caller learns whether the service is up, and nothing else.
- **Metrics.** Micrometer with a Prometheus scrape endpoint, tagged with the application name.
  Custom metrics arrive with the components they measure: sync duration, games synced per run,
  Steam API call count and failure rate, feed query latency.
- **Logs.** Structured JSON (ECS) by default so deployed logs are queryable; plain text under the
  `local` profile. Every request carries a correlation id, honoured from `X-Correlation-Id` if the
  caller supplies a well-formed one and generated otherwise, echoed on the response and put in the
  MDC so it appears on every log line — including work the sync pipeline does on a request's behalf.
- **Configuration.** Environment variables only; no secrets in the repo. `.env.example` lists the
  full surface, including the integration credentials that later milestones will need.

## Testing

Lean, but real. The target is not a coverage number.

| Layer | What it covers |
| --- | --- |
| Unit | Business rules in isolation — correlation id handling today; dust threshold, rating validation, follow rules and review uniqueness as they land |
| Slice (`@WebMvcTest`) | The HTTP contract: status codes, response shapes, auth guards |
| Integration (Testcontainers) | Real Postgres 18.6, real Flyway run, real HTTP. M3 adds the highest-value test in the suite: the full sync pipeline against WireMock-stubbed Steam, run twice, asserting the second run changes nothing |

No test hits a live external API. CI fails on any test failure.

## CI

Every pull request builds, lints and tests, integration tests included — GitHub-hosted runners have
a working Docker daemon, so Testcontainers runs for real rather than being skipped. Pushes to `main`
additionally build and publish the API image to GHCR.

---

## Roadmap

Each milestone ends deployable and demoable.

| | Milestone |
| --- | --- |
| M1 | Foundation — project, Postgres, Flyway, health, CI, OpenAPI, Testcontainers and WireMock harnesses |
| M2 | Auth and design system — Steam OpenID, Google OAuth, JWT, handle onboarding, React shell, CRT terminal primitives |
| M3 | Library and sync — `LibraryProvider`, Steam client, IGDB client, sync jobs, backlog views, 180-day dust filter |
| M4 | Reviews — CRUD, game detail, manual entry via IGDB search |
| M5 | Social — follows, public profiles, feed |
| M6 | Engagement — likes, comments, optimistic UI |
| M7 | Polish and launch — seed data, empty states, responsive pass, accessibility audit, feed load test |

Live progress is tracked against a per-item checklist rather than here, so this table stays a
description of scope and cannot drift out of date.

**All profiles are public.** Every profile, library and review is world-readable, including to
logged-out visitors. There is no private-profile option in the MVP, and signup says so plainly.
