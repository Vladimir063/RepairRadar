# syntax=docker/dockerfile:1

# ---- Этап сборки: Maven собирает исполняемый jar ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

# ---- Этап запуска: только JRE и приложение ----
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /build/target/repairradar-0.0.1-SNAPSHOT.jar /app/app.jar

# Непривилегированный пользователь
RUN useradd --system --create-home --uid 10001 appuser
USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]