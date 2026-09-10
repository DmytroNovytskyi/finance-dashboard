# Build stage: assemble the Spring Boot fat jar with the committed Gradle wrapper.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.gradle,sharing=locked ./gradlew bootJar --no-daemon \
    && mkdir /out \
    && cp "$(find build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' | head -n 1)" /out/app.jar

# Runtime stage: run the jar on a JRE image.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /out/app.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
