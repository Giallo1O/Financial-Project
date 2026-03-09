# Fase 1: Build con Maven e Java 21
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
# Saltiamo i test per velocizzare il deploy
RUN mvn clean package -DskipTests

# Fase 2: Esecuzione
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]