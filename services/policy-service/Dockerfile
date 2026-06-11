FROM gradle:8.8-jdk21 AS builder
WORKDIR /app
COPY gradle gradle
COPY gradlew gradlew
COPY settings.gradle.kts settings.gradle.kts
COPY build.gradle.kts build.gradle.kts
COPY shared shared
COPY services/policy-service services/policy-service
RUN ./gradlew :services:policy-service:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY --from=builder /app/services/policy-service/build/libs/app.jar app.jar
USER appuser
EXPOSE 8007
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
