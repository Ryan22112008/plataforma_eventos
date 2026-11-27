# Build stage
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app
ENV PORT=8080
ENV TZ=America/Cuiaba
RUN useradd --uid 10001 --create-home appuser \
    && mkdir -p /app/uploads /app/dados \
    && chown -R appuser:appuser /app
COPY --from=build --chown=appuser:appuser /workspace/target/eventos-0.0.1-SNAPSHOT.jar /app/app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java -Dserver.port=${PORT:-8080} -jar /app/app.jar"]
