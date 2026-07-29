# 1. Aşama: Build (İnşa) Katmanı
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Sadece POM dosyalarını kopyalayarak bağımlılıkları önbelleğe alıyoruz
COPY pom.xml .
COPY auth-service/pom.xml auth-service/
COPY product-service/pom.xml product-service/
COPY order-service/pom.xml order-service/


RUN mvn -q -pl auth-service -am dependency:go-offline

# Tüm kodları içeri alıp testleri atlayarak JAR dosyasını üretiyoruz
COPY . .
RUN mvn -q -pl auth-service -am package -DskipTests

# 2. Aşama: Çalıştırma (Run) Katmanı (Sadece JRE içerir, çok daha hafiftir)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/auth-service/target/*.jar app.jar

# Konteyneri root olarak çalıştırmamak temel bir sertleştirme adımıdır:
# uygulamada bir açık bulunsa bile saldırgan konteyner içinde root olamaz.
# USER, WORKDIR/COPY'den SONRA gelmeli — önce gelirse /app dizinini
# oluşturma/yazma izni olmayan bir kullanıcıyla denenir ve build/çalıştırma patlar.
RUN addgroup -S aurora && adduser -S aurora -G aurora && chown -R aurora:aurora /app
USER aurora
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]