# ╔══════════════════════════════════════════════════════════════════════════════╗
# ║                         HSPRING FRAMEWORK DOCKERFILE                          ║
# ║                              by Harendra                                       ║
# ╚══════════════════════════════════════════════════════════════════════════════╝
#
# This Dockerfile creates a containerized version of HSpring Framework.
# 
# BUILD:  docker build -t hspring-framework .
# RUN:    docker run -p 8080:8080 hspring-framework
# ACCESS: http://localhost:8080

# ─────────────────────────────────────────────────────────────────────────────────
# STAGE 1: Build the application
# ─────────────────────────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy pom.xml first (for better Docker layer caching)
COPY pom.xml .

# Download dependencies (cached unless pom.xml changes)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the JAR
RUN mvn clean package -DskipTests

# ─────────────────────────────────────────────────────────────────────────────────
# STAGE 2: Create the runtime image
# ─────────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="Harendra"
LABEL description="HSpring Framework - Learn Spring Boot Internals"
LABEL version="1.0.0"

WORKDIR /app

# Copy the JAR from builder stage
COPY --from=builder /app/target/hspring-framework-1.0.0.jar app.jar

# Expose the port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
