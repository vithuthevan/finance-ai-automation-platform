FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /workspace
COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle ./gradle
COPY platform-core ./platform-core
COPY module-auth ./module-auth
COPY module-finance ./module-finance
COPY module-ai ./module-ai
COPY module-reporting ./module-reporting
COPY platform-app ./platform-app
RUN chmod +x ./gradlew && ./gradlew :platform-app:bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN apk add --no-cache wget \
    && addgroup -S app && adduser -S app -G app \
    && mkdir -p /data/uploads \
    && chown -R app:app /app /data/uploads
COPY --from=build /workspace/platform-app/build/libs/*.jar app.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=50s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/api/v1/health/ready || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
