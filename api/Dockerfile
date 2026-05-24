# ============================================
# Stage 1: Build
# ============================================
FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /app

# Copy Maven wrapper and pom.xml first for dependency caching
COPY .mvn/ .mvn/
COPY mvnw mvnw
COPY mvnw.cmd mvnw.cmd
COPY pom.xml pom.xml

# Make Maven wrapper executable and download dependencies
RUN chmod +x mvnw && \
    ./mvnw dependency:resolve dependency:resolve-plugins -q

# Copy source code
COPY src/ src/

# Build the application (skip tests - they run in CI)
RUN ./mvnw package -DskipTests -q && \
    mv target/plugin-factory-*.jar target/app.jar

# ============================================
# Stage 2: Runtime
# ============================================
FROM eclipse-temurin:17-jre-jammy AS runtime

RUN apt-get update && apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

# Create non-root user
RUN groupadd -r appuser && useradd -r -g appuser -d /app appuser

WORKDIR /app

# Copy the built jar from the build stage
COPY --from=build /app/target/app.jar app.jar

# Set ownership
RUN chown -R appuser:appuser /app

USER appuser

EXPOSE 8080

# JVM tuning for small containers (1 OCPU / 6GB host)
# MaxRAMPercentage relative to container limit, not host. Memory limits set in compose.
# Heap capped at 60% of 1800MB (~1080MB) to leave room for Metaspace (256MB) and
# native overhead (threads, code cache). 128MB was too small for Spring Boot + Thymeleaf.
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=60.0 \
    -XX:InitialRAMPercentage=25.0 \
    -XX:+UseSerialGC \
    -XX:MaxMetaspaceSize=256m \
    -Xss512k \
    -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
