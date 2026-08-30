# ─────────────────────────────────────────────────────────────────────────────
# Stage 1: Build — uses Maven + JDK 21 to compile and package the JAR
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and pom first (layer-cache friendly)
COPY mvnw mvnw.cmd pom.xml ./
COPY .mvn .mvn

# Download dependencies (cached unless pom.xml changes)
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source and build (skip tests — they run in CI)
COPY src ./src
RUN ./mvnw package -DskipTests -B

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2: Runtime — slim JRE image, no build tools
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app

# Copy only the fat JAR from the builder stage
COPY --from=builder /app/target/SpringEcom-0.0.1-SNAPSHOT.jar app.jar

# Give ownership to non-root user
RUN chown spring:spring app.jar

USER spring

EXPOSE 8080

# Health check — Docker / Compose will mark the container healthy
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
