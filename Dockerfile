# ---- Build stage: Gradle runs inside Docker, no JDK needed on the host ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Copy only the build definition first so dependency layers cache well
COPY gradle/wrapper gradle/wrapper
COPY gradlew settings.gradle build.gradle ./

# Download dependencies as a separate layer so they're cached across builds
RUN ./gradlew --no-daemon dependencies

COPY src src
# bootJar only: tests are run separately on the host / CI, not during image builds
RUN ./gradlew --no-daemon bootJar

# ---- Runtime stage: JRE only, no compiler ----
FROM eclipse-temurin:25-jre
WORKDIR /app

RUN useradd --system --create-home appuser

COPY --from=build /workspace/build/libs/*.jar app.jar
# Runtime data files loaded via app.data-dir in application.yaml
COPY jsonData jsonData

USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
