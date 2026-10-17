
# Etapa 1: compilar con Maven y Java 21
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn -B -DskipTests package

# Etapa 2: ejecutar Spring Boot
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

ENV PORT=10000

EXPOSE 10000

ENTRYPOINT ["java", "-jar", "app.jar"]
