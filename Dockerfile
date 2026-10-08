# Builds any Spring Boot service of the Maven reactor:
#   docker build --build-arg MODULE=scheduling-service -t medirdv/scheduling-service .
# The build stage does not depend on MODULE, so BuildKit builds the reactor once and every
# service image reuses it.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml ./
COPY events events
COPY platform platform
COPY scheduling-service scheduling-service
COPY patient-service patient-service
COPY notification-service notification-service
COPY gateway gateway
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B -q -T 1 package -DskipTests

FROM eclipse-temurin:21-jre-alpine
ARG MODULE
# Fixed numeric UID/GID so that Kubernetes runAsNonRoot can verify it.
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app
WORKDIR /app
COPY --from=build /src/${MODULE}/target/app.jar /app/app.jar
USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
