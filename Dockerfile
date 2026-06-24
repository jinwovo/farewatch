# syntax=docker/dockerfile:1

# --- build stage ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# --- runtime stage (non-root) ---
FROM eclipse-temurin:21-jre AS runtime
RUN groupadd --system appuser && useradd --system --gid appuser appuser
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
USER appuser
EXPOSE 8101
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
