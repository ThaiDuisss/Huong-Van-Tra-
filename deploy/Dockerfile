# syntax=docker/dockerfile:1.7
#
# Dockerfile dung chung cho 10 service Java cua box 1.
#
# KIEN TRUC: image build ra theo kien truc cua may build. Box 1 la EC2 t3 (amd64),
# nen build tren Mac Apple Silicon se ra arm64 va KHONG CHAY duoc o do. Build tren
# box 1, tren Jenkins (box 3, amd64), hoac them --platform=linux/amd64 (cham vi phai
# gia lap). Neu doi box 1 sang t4g (Graviton/arm64) thi image arm64 dung duoc luon.
# Chon SERVICE_PATH luc build, vi du:
#   docker build -f deploy/Dockerfile --build-arg SERVICE_PATH=services/Order-Service -t hvtra/order-service .
#
# Stage "build" bien dich CA reactor mot lan. Vi 11 service dung cung Dockerfile va
# cung build-arg cho stage nay, Docker cache lai va chi build Maven DUNG MOT LAN cho
# ca 11 image. Cache ~/.m2 nam trong cache mount nen lan build sau rat nhanh.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml ./
COPY common ./common
COPY infra ./infra
COPY services ./services
# maven.test.skip bo luon viec BIEN DICH test: nhanh hon -DskipTests va khong phu thuoc
# vao trang thai test. Test duoc chay rieng o stage CI (xem deploy/README.md).
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -e -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-alpine AS runtime
ARG SERVICE_PATH
ARG SERVICE_PORT=8080

# Chay bang user thuong, khong phai root.
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app

# logback-spring.xml cua Common-Service mac dinh ghi file vao ./logs/<service>/.
# /app thuoc root nen user 'app' khong tao duoc thu muc -> container chet ngay luc
# khoi dong. Tao san va chown de image van chay duoc voi cau hinh mac dinh.
# Tren box 1 thi dat LOG_FILE_ENABLED=false (xem deploy/.env.example): log ra stdout
# cho Docker thu, vi neu ghi file thi 3 appender x totalSizeCap 1GB x 9 service co
# the chiem toi 27GB tren disk 30GB.
RUN mkdir -p /app/logs && chown app:app /app/logs

# Maven sinh <artifactId>-<version>.jar (fat jar) va .jar.original; glob chi khop fat jar.
# --chown ngay trong COPY: neu COPY roi RUN chown thi fat jar 138MB bi nhan doi
# thanh 2 layer (276MB/image).
COPY --from=build --chown=app:app /src/${SERVICE_PATH}/target/*.jar /app/app.jar

USER app
EXPOSE ${SERVICE_PORT}

# Gioi han heap, timezone... truyen qua JAVA_TOOL_OPTIONS trong compose, nen entrypoint
# khong can shell.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
