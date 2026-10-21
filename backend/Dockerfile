# Build stage: compile the jar with the Maven wrapper.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
# Separate layer so code changes don't re-download dependencies.
RUN sh ./mvnw -q dependency:go-offline
COPY src src
RUN sh ./mvnw -q -DskipTests package

# Runtime stage: JRE only, non-root user.
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system app
# logback-spring.xml writes logs/ under prod; the non-root user must own it.
RUN mkdir logs && chown app logs
USER app
COPY --from=build /app/target/*.jar app.jar
# Secrets (DB_PASSWORD, Clerk keys) come from compose at runtime, never baked in.
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
# Cap the heap so the JVM fits on a small EC2 instance.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
