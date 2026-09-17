FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /workspace
RUN apk add --no-cache bash curl unzip \
    && curl -fL --retry 10 --retry-all-errors --retry-delay 15 --connect-timeout 60 --max-time 0 \
      https://services.gradle.org/distributions/gradle-9.5.1-bin.zip -o /tmp/gradle.zip \
    && unzip -q /tmp/gradle.zip -d /opt \
    && mv /opt/gradle-9.5.1 /opt/gradle \
    && rm /tmp/gradle.zip
ENV PATH="/opt/gradle/bin:${PATH}"
COPY settings.gradle build.gradle ./
COPY gradle ./gradle
COPY platform-core ./platform-core
COPY module-auth ./module-auth
COPY module-finance ./module-finance
COPY module-ai ./module-ai
COPY module-reporting ./module-reporting
COPY platform-app ./platform-app
RUN gradle :platform-app:bootJar --no-daemon -x test

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
