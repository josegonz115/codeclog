plugins {
    java
    checkstyle
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.codeclog"
version = "0.1.0-SNAPSHOT"
description = "CodecLog API — social game backlog tracker"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

// Spring Boot 4.1.1 manages JUnit 6.0.3; §3 pins 6.1.3. Overriding the BOM property bumps the
// whole junit-bom (jupiter and platform together) rather than just one artifact.
extra["junit-jupiter.version"] = "6.1.3"
extra["springdocVersion"] = "3.1.0"

dependencies {
    // Web + persistence. Spring Boot 4 splits the old `-web` starter by stack; this is the
    // servlet/MVC one.
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Observability
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // API docs — springdoc 3.x emits OpenAPI 3.2 and targets Spring Boot 4.
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${property("springdocVersion")}")

    // Tests. Boot 4 replaces the single `spring-boot-starter-test` with per-slice test starters.
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    // Boot 4 split RestTemplate support out of the web starter; TestRestTemplate needs it.
    testImplementation("org.springframework.boot:spring-boot-restclient")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    // Steam and IGDB are stubbed here from M3 onward; no test ever hits a live external API.
    // The standalone (shaded) build keeps WireMock's Jetty off the Spring test classpath.
    testImplementation("org.wiremock:wiremock-standalone:3.13.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

checkstyle {
    toolVersion = "14.0.0"
    configFile = file("config/checkstyle/checkstyle.xml")
    maxWarnings = 0
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }

    // Testcontainers looks for a Docker daemon at DOCKER_HOST, then /var/run/docker.sock. Developers
    // running OrbStack, Colima or Rancher have a working `docker` CLI but neither of those, so the
    // integration tests would fail on a machine where Docker is demonstrably fine. Ask the CLI where
    // its current context points and pass that through. On CI the context is `default` and this is a
    // no-op.
    doFirst {
        if (System.getenv("DOCKER_HOST") == null && !file("/var/run/docker.sock").exists()) {
            val endpoint = runCatching {
                val process = ProcessBuilder(
                    "docker", "context", "inspect", "--format", "{{.Endpoints.docker.Host}}"
                ).redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().readText().trim()
                if (process.waitFor() == 0 && output.startsWith("unix://")) output else null
            }.getOrNull()

            if (endpoint != null) {
                logger.lifecycle("Pointing Testcontainers at the active Docker context: $endpoint")
                environment("DOCKER_HOST", endpoint)
                environment("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", "/var/run/docker.sock")
            }
        }
    }
}

// Fixed name so the Dockerfile does not have to know the project version.
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("codeclog-api.jar")
}
