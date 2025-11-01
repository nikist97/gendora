# Build stage
FROM gradle:8.5-jdk21 AS builder

WORKDIR /gendora

# Copy gradle wrapper and configuration
COPY gradle/ gradle/
COPY gradlew gradlew
COPY gradlew.bat gradlew.bat
COPY settings.gradle settings.gradle
COPY gradle.properties gradle.properties

# Copy the API source code
COPY gendora-api/ gendora-api/

# Build the application
RUN cd gendora-api && ../gradlew build -x test

# Runtime stage
FROM openjdk:21-slim

RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

RUN groupadd -r gendora && useradd -r -g gendora gendora

WORKDIR /gendora

COPY --from=builder /gendora/gendora-api/build/libs/*.jar gendora.jar

USER gendora

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:8080/api/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-Xmx1024m", \
    "-Xms512m", \
    "-jar", "gendora.jar"]
