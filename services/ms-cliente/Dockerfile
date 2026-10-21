# Etapa 1: compila o jar dentro da imagem, sem depender de um target/ gerado na máquina de quem clonou
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
# Wrapper e pom primeiro: enquanto o pom não mudar, o Docker reaproveita a camada com as dependências baixadas
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN sh mvnw -B -ntp dependency:go-offline
COPY src src
# O application.properties não é versionado: a imagem usa sempre o exemplo, e o compose passa os valores por variável
RUN cp src/main/resources/application.properties.example src/main/resources/application.properties
# Os testes rodam no CI (mvnw verify); aqui só empacota
RUN sh mvnw -B -ntp -DskipTests package

# Etapa 2: imagem final só com a JRE e o jar
FROM eclipse-temurin:21-jre-alpine
# curl é usado pelo healthcheck do docker-compose
RUN apk add --no-cache curl
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
