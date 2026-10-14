FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
COPY common common
COPY common-sql common-sql
COPY common-reactive common-reactive
COPY trade-service trade-service
COPY compliance-service compliance-service
COPY investigation-service investigation-service
COPY live-view-service live-view-service
RUN mvn -B -ntp package -DskipTests
FROM eclipse-temurin:21.0.9_10-jre
ARG SERVICE
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends gosu && rm -rf /var/lib/apt/lists/* && groupadd -g 10001 app && useradd -u 10001 -g app app
COPY --from=build /build/${SERVICE}/target/${SERVICE}-1.0.0.jar /app/app.jar
COPY infra/app-entrypoint.sh /app/entrypoint.sh
RUN sed -i 's/\r$//' /app/entrypoint.sh
ENTRYPOINT ["bash", "/app/entrypoint.sh"]
