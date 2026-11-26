FROM maven:3.9.12-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre
RUN groupadd --system app && useradd --system --gid app --home-dir /app app \
    && apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build --chown=app:app /build/target/*.jar app.jar
USER app
EXPOSE 8081
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 CMD curl --fail --silent --output /dev/null http://127.0.0.1:8081/stats || exit 1
ENTRYPOINT ["java","-jar","/app/app.jar"]
