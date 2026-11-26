# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# A Windows checkout may convert the wrapper's line endings to CRLF.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp -DskipTests dependency:go-offline
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-noble AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl fontconfig \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 gestordocs \
    && useradd --uid 10001 --gid gestordocs --no-create-home gestordocs \
    && mkdir -p /app /datos/ficheros /datos/temporales/escaneo \
    && chown -R gestordocs:gestordocs /app /datos
WORKDIR /app
COPY --from=build /workspace/target/gestor-documentos-*.jar app.jar
ENV APP_PORT=8090 \
    APP_STORAGE_PATH=/datos/ficheros \
    APP_SCAN_TEMP_PATH=/datos/temporales/escaneo
USER 10001:10001
EXPOSE 8090
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=6 \
    CMD test "$(curl --fail --silent --output /dev/null --write-out '%{http_code}' --max-time 4 http://127.0.0.1:8090/login)" = 200
ENTRYPOINT ["java", "-Djava.awt.headless=true", "-jar", "/app/app.jar"]
