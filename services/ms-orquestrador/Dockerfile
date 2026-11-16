# Etapa 1: gera o jar dentro da imagem
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# baixa as dependencias a partir do pom
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN sh mvnw -B -ntp dependency:go-offline

COPY src ./src
# copia o application.properties.example se nao houver application.properties
RUN f=src/main/resources/application.properties; [ -f "$f" ] || cp "$f.example" "$f"
RUN sh mvnw -B -ntp package -DskipTests

# Etapa 2: so o JRE e o jar
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
