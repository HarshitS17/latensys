FROM maven:3.9.12-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
COPY src src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:17-jre
RUN groupadd --system app && useradd --system --gid app --home-dir /app app \
    && apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build --chown=app:app /build/target/*.jar app.jar
USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 CMD curl --fail --silent --output /dev/null http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["java","-jar","/app/app.jar"]
