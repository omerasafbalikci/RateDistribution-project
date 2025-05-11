########################################
# 1) BUILD STAGE
########################################
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# Tüm kodu kopyala
COPY . .

# auth-common modülünü derle (tcp-data-provider bağımlılığı)
RUN mvn -f auth-common/pom.xml clean install -DskipTests

# tcp-data-provider modülünü paketle
RUN mvn -f tcp-data-provider/pom.xml clean package -DskipTests

########################################
# 2) RUNTIME STAGE
########################################
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Derlenmiş jar'ı kopyala
COPY --from=builder /build/tcp-data-provider/target/tcp-data-provider-*.jar app.jar

# /config dizinini volume olarak işaretle (host'tan mount edilecek)
VOLUME ["/config"]

ENV TZ=Europe/Istanbul
EXPOSE 8084

# Eğer yükleme argümanı beklemiyorsanız sadece jar'ı çalıştırın
ENTRYPOINT ["java", "-jar", "app.jar"]
