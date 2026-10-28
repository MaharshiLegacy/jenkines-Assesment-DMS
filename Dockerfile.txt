FROM eclipse-temurin:21-jre
WORKDIR /app

# Non-root user with a NUMERIC uid (lets Kubernetes enforce runAsNonRoot)
RUN groupadd --system --gid 10001 app \
 && useradd  --system --uid 10001 --gid app --no-create-home app \
 && mkdir -p /data/uploads \
 && chown -R app:app /app /data

# The jar is built by Jenkins (./mvnw clean verify) BEFORE docker build runs
COPY --chown=app:app target/*.jar app.jar

USER 10001:10001
EXPOSE 8080

# 60% heap leaves room for metaspace/threads/buffers inside a 1Gi pod limit (75% risks OOMKilled)
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError" \
    APP_STORAGE_DIR=/data/uploads

ENTRYPOINT ["java", "-jar", "app.jar"]
