# ---- build ----------------------------------------------------------------
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace

# Wrapper and build scripts first: dependency resolution is cached unless they change.
COPY gradlew ./
COPY gradle gradle
COPY settings.gradle.kts build.gradle.kts ./
COPY config config
RUN ./gradlew --no-daemon --quiet dependencies

COPY src src
RUN ./gradlew --no-daemon clean bootJar -x test

# Explode the jar so the layers below split by change frequency: dependencies (rarely change)
# land in their own image layer, application classes (change every build) in the last one.
RUN java -Djarmode=tools -jar build/libs/codeclog-api.jar extract --layers --launcher --destination extracted

# ---- runtime --------------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine AS runtime
RUN addgroup -S codeclog && adduser -S codeclog -G codeclog
WORKDIR /app

COPY --from=build --chown=codeclog:codeclog /workspace/extracted/dependencies/ ./
COPY --from=build --chown=codeclog:codeclog /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=codeclog:codeclog /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=codeclog:codeclog /workspace/extracted/application/ ./

USER codeclog
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Djava.security.egd=file:/dev/./urandom"

# The platform's own probe should hit /actuator/health/readiness; this is the local fallback.
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1

# `extract --launcher` writes an exploded layout rather than a jar, so the launcher is invoked
# directly. Class loading reads real files instead of a nested archive, which starts faster.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
