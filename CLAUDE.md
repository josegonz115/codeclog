# CodecLog

Social game backlog tracker: syncs a Steam library, surfaces games collecting dust, and wraps
reviews in a social feed. Portfolio project — the backend is the showpiece, so API design, the sync
pipeline and operational tooling get the scrutiny.

## The spec is not in this repo

The authoritative spec is a Notion page: **CodecLog — MVP Build Specification**, page id
`3cb57d5301078143a5a8e890c6d26dad` (under "CodecLog — Dev Hub"). Fetch it before doing milestone
work — it holds the data model, API surface, sync design and visual direction, none of which are
duplicated here.

Two rules from it that govern how to work:

- **One milestone at a time.** Don't build ahead into a later milestone's scope.
- **When a decision changes mid-build, update the Notion page** rather than letting code and spec
  drift. Ask before editing the page; don't edit it unprompted.

## Where progress is tracked

Not in this file. The **Milestone Checklist** Notion page — id
`3cb57d53010781c7a3eaf8557f1e6e34`, also under the Dev Hub — breaks M1–M7 into tickable items and
is the working document day to day. Read it to learn what is done and what is next, and tick items
there as they land rather than recording status here or in the README.

## Build and test

```bash
cd api && ./gradlew build     # checkstyle + unit + slice + integration tests
```

Gradle provisions JDK 25 itself, so any installed JDK will do. The integration tests need a running
Docker daemon; the test task reads your active `docker context`, so OrbStack, Colima and Rancher
work as well as Docker Desktop. `./gradlew bootTestRun` starts the API against a throwaway Postgres
when you want a clean database without Compose.

## Conventions that must not drift

These were established in M1 and later code has to hold the line:

- **One error envelope.** Every non-2xx is `{ "error": { "code", "message", "details" } }`, routed
  through `GlobalExceptionHandler`. Clients branch on `code`, never on message text. Don't
  introduce a second error shape, and don't let ProblemDetail leak out.
- **Cursor pagination everywhere, never offset.**
- **Schema lives in Flyway migrations.** `ddl-auto` is `validate`; Hibernate never writes DDL. New
  tables get a `set_updated_at()` trigger, and handles use `citext`.
- **Authorization is checked server-side.** Never trust a client-supplied user id.
- **No secrets in the repo.** Config comes from environment variables; `.env.example` lists the
  surface.
- **Tests are not optional.** Business rules get unit tests, HTTP contracts get `@WebMvcTest`
  slices, and anything touching the database gets a Testcontainers test. No test hits a live
  external API — Steam and IGDB are stubbed with WireMock.

## Sharp edges in the current stack

Java 25, Spring Boot 4.1.1, PostgreSQL 18.6, JUnit 6.1.3, Testcontainers 2. Three things that
already cost time once:

- **Boot 4 split its modules.** The web starter is `spring-boot-starter-webmvc`, there is no single
  `spring-boot-starter-test`, and `TestRestTemplate` lives in its own module needing
  `spring-boot-restclient` plus `@AutoConfigureTestRestTemplate`. Metrics in tests need
  `@AutoConfigureMetrics`. These surface as context-load failures, not compile errors.
- **Postgres 18 moved `PGDATA`** to `/var/lib/postgresql/18/docker`. The Compose volume mounts
  `/var/lib/postgresql`; the old `.../data` path silently loses the cluster on `compose down`.
- **OpenAPI is pinned to 3.1**, not 3.2 — swagger-core defines no 3.2 spec version yet. Revisit
  when it does.
