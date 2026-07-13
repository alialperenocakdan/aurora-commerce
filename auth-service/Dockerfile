# 1. Aşama: Build (İnşa) Katmanı
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Sadece POM dosyalarını kopyalayarak bağımlılıkları önbelleğe alıyoruz
COPY pom.xml .
COPY auth-service/pom.xml auth-service/
COPY product-service/pom.xml product-service/
COPY order-service/pom.xml order-service/

# Dikkat: < ve > işaretleri yok, doğrudan klasör adını yazdık!
RUN mvn -q -pl auth-service -am dependency:go-offline

# Tüm kodları içeri alıp testleri atlayarak JAR dosyasını üretiyoruz
COPY . .
RUN mvn -q -pl auth-service -am package -DskipTests

# 2. Aşama: Çalıştırma (Run) Katmanı (Sadece JRE içerir, çok daha hafiftir)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/auth-service/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]