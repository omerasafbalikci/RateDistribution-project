FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /build

COPY . .

RUN mvn -f auth-common/pom.xml clean install -DskipTests

RUN mvn -f tcp-data-provider/pom.xml clean package -DskipTests

# ------------------ RUNTIME STAGE ------------------
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=builder /build/tcp-data-provider/target/tcp-data-provider-*.jar app.jar

RUN mkdir /config2
ENV TZ=Europe/Istanbul
EXPOSE 8084

ENTRYPOINT ["java", "-jar", "/app/app.jar", "--config=/config"]
