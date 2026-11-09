# Etapa 1: gera o jar dentro da imagem, sem exigir Java nem Maven na maquina.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Wrapper e pom antes do codigo: a camada das dependencias so e refeita quando o pom muda.
# O mvnw esta no git sem permissao de execucao, por isso o "sh mvnw".
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN sh mvnw -B -ntp dependency:go-offline

COPY src ./src
# O application.properties nao e versionado; sem ele o jar sai sem configuracao.
RUN f=src/main/resources/application.properties; [ -f "$f" ] || cp "$f.example" "$f"
RUN sh mvnw -B -ntp package -DskipTests

# Etapa 2: so o JRE e o jar. O wget do healthcheck vem do busybox da imagem alpine.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8091
ENTRYPOINT ["java", "-jar", "app.jar"]
