# ---- Build stage: Gradle runs inside Docker, no JDK needed on the host ----
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace

# Copy only the build definition first so dependency layers cache well
COPY gradle/wrapper gradle/wrapper
COPY gradlew settings.gradle build.gradle ./
# Keep gradlew executable even if a contributor committed it without the bit
RUN chmod +x gradlew

# Download dependencies as a separate layer so they're cached across builds
RUN ./gradlew --no-daemon dependencies

COPY src src
# bootJar only: tests are run separately on the host / CI, not during image builds
RUN ./gradlew --no-daemon bootJar

# ---- Runtime stage: JRE only, no compiler ----
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# BusyBox/Alpine has no useradd; adduser -S creates a system user
RUN adduser -S appuser

COPY --from=build /workspace/build/libs/*.jar app.jar
# Runtime data files loaded via app.data-dir in application.yaml
COPY jsonData jsonData

USER appuser
EXPOSE 8080
# MaxRAMPercentage lets the heap use more of the container's memory on
# memory-limited hosts (default JVM behavior only allows ~25%)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
