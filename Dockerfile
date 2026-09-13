FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace/app

COPY gradle gradle
COPY gradlew .
COPY settings.gradle.kts .
COPY build.gradle.kts .

# Download dependencies (cache layer)
RUN ./gradlew dependencies --no-daemon || true

COPY src src
RUN ./gradlew build -x test --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/app/build/libs/*.jar app.jar
EXPOSE 8080
EXPOSE 9090
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]
