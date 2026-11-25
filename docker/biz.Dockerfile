# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml ./
COPY common/pom.xml common/pom.xml
COPY biz-service/pom.xml biz-service/pom.xml
COPY agent-service/pom.xml agent-service/pom.xml
COPY gateway/pom.xml gateway/pom.xml

# 先只拷 pom 拉依赖，源码变更时可以复用这一层缓存
RUN mvn -B -q -DskipTests dependency:go-offline

COPY . .
RUN mvn -B -q -DskipTests package -pl biz-service -am

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/biz-service/target/biz-service-1.0.0.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/app.jar"]
