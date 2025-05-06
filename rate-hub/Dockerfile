FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /build

COPY . .

RUN mvn -pl rate-hub -am clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /build/rate-hub/target/rate-hub-*.jar app.jar

RUN mkdir /config
ENV TZ=Europe/Istanbul
EXPOSE 8087
ENTRYPOINT ["java","-jar","/app/app.jar","--config=/config"]
