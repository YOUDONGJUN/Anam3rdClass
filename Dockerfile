# 안암3교시 — React 화면 + Spring Boot API 를 하나의 컨테이너로 (Render 무료 플랜용)

# ---------- 1) React 빌드 ----------
FROM node:24-alpine AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# ---------- 2) Spring Boot 빌드 (React 결과물을 static 으로 포함) ----------
FROM eclipse-temurin:21-jdk AS api
WORKDIR /app
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY backend/src src
COPY --from=web /web/dist src/main/resources/static
RUN ./mvnw -B -q package -DskipTests

# ---------- 3) 실행 ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 app
COPY --from=api /app/target/anam3.jar app.jar
USER app

# 무료 플랜 메모리 512MB 에 맞춘 JVM 설정
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-Xmx224m -Xss512k -XX:MaxMetaspaceSize=150m -XX:ReservedCodeCacheSize=48m -XX:+UseSerialGC -XX:TieredStopAtLevel=1"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
