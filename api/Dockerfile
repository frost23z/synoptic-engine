# syntax=docker/dockerfile:1

# ---- build stage ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Cache Gradle deps: copy wrapper + build scripts first
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x ./gradlew && ./gradlew --no-daemon dependencies || true

# Now copy sources and build the executable boot jar (skip tests — CI runs them)
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# ---- runtime stage ----
FROM eclipse-temurin:25-jre
WORKDIR /app

# Copy the executable jar (exclude the -plain.jar)
COPY --from=build /app/build/libs/*-SNAPSHOT.jar app.jar

# Storage root for uploaded files — back this with a Fly volume (see fly.toml).
ENV SYNOPTIC_STORAGE_ROOT=/data/storage
EXPOSE 8090

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
