# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml ./
COPY common/pom.xml common/pom.xml
COPY biz-service/pom.xml biz-service/pom.xml
COPY agent-service/pom.xml agent-service/pom.xml
COPY gateway/pom.xml gateway/pom.xml

RUN mvn -B -q -DskipTests dependency:go-offline

COPY . .
RUN mvn -B -q -DskipTests package -pl gateway -am

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/gateway/target/gateway-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/app.jar"]
