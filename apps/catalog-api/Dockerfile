# syntax=docker/dockerfile:1

FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update \
	&& apt-get install -y --no-install-recommends curl \
	&& rm -rf /var/lib/apt/lists/* \
	&& useradd --system --uid 10001 --create-home appuser
COPY --from=build /workspace/target/catalog-api-*.jar /app/app.jar
USER appuser
EXPOSE 3845
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=20 \
	CMD curl -fsS http://localhost:3845/actuator/health | grep -q UP || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
