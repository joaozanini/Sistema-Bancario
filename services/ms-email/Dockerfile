# Etapa 1: gera o jar
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Baixa as dependências antes de copiar o código
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN sh mvnw -B -ntp dependency:go-offline

COPY src ./src
# Cria o application.properties a partir do .example se não existir
RUN f=src/main/resources/application.properties; [ -f "$f" ] || cp "$f.example" "$f"
RUN sh mvnw -B -ntp package -DskipTests

# Etapa 2: imagem só com o JRE e o jar
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8091
ENTRYPOINT ["java", "-jar", "app.jar"]
