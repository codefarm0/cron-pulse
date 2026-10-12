# syntax=docker/dockerfile:1

# ---- Build stage: compile and package the executable jar ----
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /workspace
# Copy build files first so the dependency download is cached when only source code changes.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN --mount=type=cache,target=/root/.gradle ./gradlew dependencies --no-daemon -q > /dev/null
COPY src src
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar --no-daemon -q
# Split the jar into layers (dependencies change rarely, app code often) for smaller image updates.
RUN java -Djarmode=tools -jar build/libs/cron-pulse-*.jar extract --layers --launcher --destination /extracted

# ---- Runtime stage: JRE only, non-root user ----
FROM eclipse-temurin:25-jre-noble
RUN groupadd --system cronpulse && useradd --system --gid cronpulse cronpulse
WORKDIR /app
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./
USER cronpulse
EXPOSE 8080
# Containers log JSON (one event per line) so log collectors can parse them; local bootRun stays human-readable.
ENV LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs
# Size the heap from the container's memory limit rather than the host's.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
