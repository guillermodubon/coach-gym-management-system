FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY src/main ./src/main

RUN chmod 0755 ./gradlew \
    && ./gradlew --no-daemon bootJar \
    && jar_path="$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' -print -quit)" \
    && test -n "$jar_path" \
    && cp "$jar_path" /workspace/application.jar

FROM eclipse-temurin:21-jre

WORKDIR /app
RUN chown 10001:10001 /app
COPY --from=build --chown=10001:10001 /workspace/application.jar /app/application.jar

USER 10001:10001
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java -Dserver.port=${PORT:-8080} -jar /app/application.jar"]
