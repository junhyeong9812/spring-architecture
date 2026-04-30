# ── Stage 1: Build ──
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app
COPY build.gradle settings.gradle gradlew ./
COPY gradle/ gradle/

# 의존성 캐싱
RUN ./gradlew dependencies --no-daemon

# 소스 복사 & 빌드
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

# ── Stage 2: Runtime ──
FROM eclipse-temurin:21-jre

WORKDIR /app

# 보안: non-root 사용자
RUN groupadd -r appuser && useradd -r -g appuser appuser

COPY --from=builder /app/build/libs/*.jar app.jar

RUN chown -R appuser:appuser /app
USER appuser

# JVM 튜닝
ENV JAVA_OPTS="-XX:+UseZGC \
               -XX:MaxRAMPercentage=75.0 \
               -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
