# 构建上下文为仓库根目录（需要 assets/payment 中的收款码）
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /src
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY backend/src src
RUN ./mvnw -B -q -DskipTests package && cp target/*.jar /app.jar

FROM eclipse-temurin:25-jre-noble
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --create-home app \
    && mkdir -p /data/uploads /data/private && chown -R app:app /data
WORKDIR /app
COPY --from=build /app.jar app.jar
COPY assets/payment assets/payment
USER app
ENV SPRING_PROFILES_ACTIVE=prod \
    UPLOAD_DIR=/data/uploads \
    PRIVATE_UPLOAD_DIR=/data/private \
    PAYMENT_ASSETS_DIR=/app/assets/payment
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "app.jar"]