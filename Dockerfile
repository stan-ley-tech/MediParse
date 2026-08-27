# --- Build stage ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Cache dependencies separately from source so code edits don't re-download the world
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

# --- Runtime stage ---
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S mediparse && adduser -S mediparse -G mediparse
WORKDIR /app

COPY --from=build /build/target/mediparse.jar app.jar
RUN mkdir -p /data/storage && chown -R mediparse:mediparse /data/storage /app

USER mediparse
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
