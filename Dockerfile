FROM maven:3.9.8-eclipse-temurin-21-alpine AS builder

WORKDIR /app

COPY . .


RUN MAVEN_CONFIG="" ./mvnw -B package -DskipTests=true

# Use an official OpenJDK runtime as a parent image
FROM amazoncorretto:21.0.4-alpine3.20

WORKDIR /app

# Add a non-root user
# RUN adduser --system --no-create-home --group spring && \
#     chown -R spring:spring /app \
#     && chmod -R 755 /app

# Set the working directory in the container

# Copy the application JAR file into the container at /app
COPY --from=builder /app/target/spring-petclinic-3.2.0-SNAPSHOT.jar spring-petclinic-3.2.0-SNAPSHOT.jar

# Make port 8080 available to the world outside this container
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "spring-petclinic-3.2.0-SNAPSHOT.jar"]
