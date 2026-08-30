# Decisions

A running log of choices made while building, and why. The spec (Notion) records what to build;
this records what was decided along the way that the spec does not cover — the material the README
tradeoff section and interview answers get drawn from.

Newest first. Each entry: what was decided, what it was chosen over, and what would change it.

---

## 2026-08-30 — OPEN: the served OpenAPI document differs between tests and the container

Adding `.url(...)` to the OpenAPI licence object produces
`{"name":"MIT","url":"https://opensource.org/licenses/MIT"}` when the app runs from the jar, and
`{"name":"MIT"}` when the identical jar runs in the container — reproducibly.

Ruled out: stale image (container is newer than the image, and `--force-recreate` reproduces it),
missing bytes (the class in the image contains the URL string), library skew (same springdoc 3.1.0
and swagger 2.2.52 jars, no duplicate `License.class`), and environment (running the host jar with
the container's env vars, including locale and `JAVA_TOOL_OPTIONS`, still emits the URL). The
remaining difference is the launch mode: the image runs the extracted layout via `JarLauncher`,
the host runs the nested jar via `java -jar`.

The URL is dropped for now so that what the tests assert matches what the artifact serves. The
licence name is served in both, and `LICENSE` in the repo is what actually backs the claim.

**Why this is recorded rather than shrugged off:** it means a springdoc assertion passing in an
integration test does not by itself prove the deployed artifact serves the same document. Worth
resolving before anything depends on the generated document — client generation, for instance.

## 2026-08-30 — Cross-cutting code lives in `common/`, features at the top level

`config`, `error` and `logging` moved under `com.codeclog.api.common`. The top level is now
features plus `common`, so M2's `auth/` and M3's `library/` sit alongside it rather than being
buried under a layer package.

**Over:** packaging by layer (`controller/`, `service/`, `repository/`), which spreads one feature
across three directories and makes the interesting question — what does this feature touch — hard
to answer by looking.

**Changes if:** `common` starts accumulating feature logic rather than genuinely shared concerns.
That is the signal it has become a junk drawer.

## 2026-08-30 — WireMock via `wiremock-standalone`, using the JUnit 5 extension

`wiremock-standalone:3.13.2`, driven through `WireMockExtension`.

**Over:** `wiremock-jetty12`. The shaded standalone build keeps WireMock's Jetty and servlet API off
a Spring test classpath that already has Tomcat's.

WireMock's extension is built against JUnit 5, and this project runs JUnit 6.1.3. That turns out not
to matter — JUnit 6 kept the `org.junit.jupiter.*` package names and the extension API the
extension uses, and it was verified working here rather than assumed. Using it means per-test stub
reset and lifecycle handling for free, instead of hand-rolled `@BeforeAll` / `@AfterAll`.

**Changes if:** WireMock 4 leaves beta — it ships a dedicated `wiremock-junit5` module. No reason to
take a beta dependency for this.

## 2026-08-30 — OpenAPI pinned to 3.1, not 3.2

§3 asked for OpenAPI 3.2.0. No released tooling emits it — springdoc 3.1.0 is current and
swagger-core's `SpecVersion` enum defines only `V30` and `V31`. Pinned `openapi_3_1` explicitly so
the ceiling is visible in config rather than being a silent default, and corrected §3 in Notion.

**Changes if:** swagger-core adds 3.2. Then flip the one property.

## 2026-08-30 — `TestRestTemplate` kept over `RestTestClient`

Spring Boot 4 modularised `TestRestTemplate` into `spring-boot-resttestclient` and stopped
registering it implicitly; it now needs `spring-boot-restclient` plus
`@AutoConfigureTestRestTemplate`. It is not deprecated, so the integration tests keep it.

**Over:** migrating to `RestTestClient`, Spring 7's newer fluent client. That is the more
forward-looking API and needs no extra dependency, but rewriting working assertions during a
version bump risks weakening them silently.

**Changes if:** `TestRestTemplate` is deprecated, or the fluent assertions start earning their
keep — likely once there are real endpoints with real response bodies.

## 2026-08-30 — Compose volume mounts `/var/lib/postgresql`

Postgres 18 moved `PGDATA` to `/var/lib/postgresql/18/docker` and declares `/var/lib/postgresql` as
the image volume. The pre-18 `/var/lib/postgresql/data` mount leaves the real cluster inside the
container, where it is destroyed by `compose down` — with no error at any point.

## 2026-08-29 — `updated_at` maintained by a database trigger

`set_updated_at()` ships in the baseline migration; every table with the column attaches it.

**Over:** JPA's `@PreUpdate`, which only fires for writes that go through Hibernate. A manual
`UPDATE` during an incident, or a future batch job using plain SQL, would leave a stale timestamp
and quietly corrupt any logic reading it.

## 2026-08-29 — Schema is Flyway's, never Hibernate's

`ddl-auto: validate`. Hibernate checks that entities match the migrated schema and is never allowed
to change the database.

**Over:** `update`, which is convenient in development and unreviewable in production — the schema
becomes a function of whatever the entities happened to look like on deploy day.

## 2026-08-29 — One error envelope, including for framework errors

`GlobalExceptionHandler` extends `ResponseEntityExceptionHandler`, so Spring MVC's own failures —
unparseable body, unsupported method, no such route — come back in the same
`{ error: { code, message, details } }` shape as application errors. Codes are a closed enum;
clients branch on `code`, never on message text.

**Over:** letting `ProblemDetail` handle framework errors, which produces a second error shape that
clients then have to handle twice.

## 2026-08-29 — Health detail off by default

`show-details: never`, relaxed to `always` only under the `local` profile, and only `health`,
`info`, `metrics` and `prometheus` are exposed. An unauthenticated caller learns whether the
service is up and nothing about its components.

**Changes if:** authentication lands on the actuator endpoints — then `when-authorized` is strictly
better.

## 2026-08-29 — Structured JSON logging is the default, plain text is the opt-in

ECS JSON by default; the `local` profile switches to readable console output.

**Over:** the reverse. Defaults should be the deployed behaviour, so a missing profile in
production degrades to correct-and-verbose rather than to unqueryable logs.

## 2026-08-29 — Toolchain provisioned by Gradle, not by the machine

The foojay resolver downloads JDK 25 if it is absent, so the build never silently compiles against
whatever JDK happens to be first on `PATH`.

## 2026-08-29 — One shared Postgres container for the whole suite

The container is a `@Bean` in a single `@TestConfiguration` rather than a per-class
`@Container` field, so Spring's context cache keeps one container across every integration test.

**Over:** per-class containers, which are more isolated and pay a fresh startup per test class.
Isolation is not free here and is not yet needed; tests that mutate shared state will need to clean
up after themselves.

**Changes if:** a test needs a genuinely pristine database. `bootTestRun` already provides that
shape for manual use.
