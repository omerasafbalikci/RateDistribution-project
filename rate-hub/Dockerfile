# 1) BUILD STAGE
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# Sadece pom ve kaynakları kopyala
COPY pom.xml .
COPY src ./src

# Root pom.xml'i kullanarak derle
RUN mvn clean package -DskipTests

# 2) RUNTIME STAGE
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Oluşan jar'ı al (shade yapıyorsanız -jar-with-dependencies, değilse */*.jar)
COPY --from=builder /build/target/rate-hub-*.jar app.jar

# Konfig dosyaları için klasör
RUN mkdir /config
ENV TZ=Europe/Istanbul
EXPOSE 8087

ENTRYPOINT ["java","-jar","/app/app.jar","--config=/config"]
