# Stage 1: Build the project
FROM eclipse-temurin:25-jdk-alpine AS builder

RUN apk add --no-cache dos2unix

WORKDIR /app

COPY . .

RUN dos2unix ./gradlew

RUN chmod +x ./gradlew

RUN ./gradlew spotlessApply
RUN ./gradlew build -x test --no-daemon

# Stage 2: Runtime image
FROM eclipse-temurin:25-jdk-alpine

WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]
