FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=builder /build/target/rate-hub-*.jar app.jar

RUN mkdir -p /config/formulas
ENV TZ=Europe/Istanbul
EXPOSE 8087

ENTRYPOINT ["java","-jar","/app/app.jar","--config=/config/application-docker.yml"]
