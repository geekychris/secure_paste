# SecurePaste Dockerfile

# Build stage
FROM maven:3.9.6-amazoncorretto-21 AS build

WORKDIR /app

# Copy pom.xml first for better layer caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests -Pproduction

# Runtime stage
FROM amazoncorretto:21-alpine

WORKDIR /app

# Create non-root user for security
RUN addgroup -g 1001 appgroup && \
    adduser -u 1001 -G appgroup -D appuser

# Copy the built JAR from build stage
COPY --from=build /app/target/secure-pastebin-*.jar app.jar
RUN chown appuser:appgroup app.jar

# Create directories for logs and data
RUN mkdir -p logs data && \
    chown -R appuser:appgroup logs data

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8080

# Add health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/pastes/health || exit 1

# Set JVM options for containerized environment
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=80.0 -XX:+ExitOnOutOfMemoryError -XX:+UnlockExperimentalVMOptions"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --spring.profiles.active=docker"]