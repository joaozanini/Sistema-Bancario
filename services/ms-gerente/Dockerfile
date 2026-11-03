FROM eclipse-temurin:21-jdk-alpine
# curl e usado pelo healthcheck do docker-compose
RUN apk add --no-cache curl
WORKDIR /app
COPY target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
