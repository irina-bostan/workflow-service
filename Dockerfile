# ---------- Build stage ----------
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /workspace

# Cache dependency resolution as a separate layer
COPY pom.xml .
RUN mvn dependency:go-offline -B -q 2>/dev/null || true

COPY src src
RUN mvn package -DskipTests -B -q

# ---------- Runtime stage ----------
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /workspace/target/workflow-service-*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseG1GC", \
  "-XX:G1HeapRegionSize=16m", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
