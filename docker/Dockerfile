# ---- build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# ---- run ----
# A JDK image is required at runtime: the app uses javax.tools.JavaCompiler.
FROM eclipse-temurin:21-jdk
RUN useradd --system --create-home --uid 10001 doctor
WORKDIR /app
COPY --from=build /build/target/ai-code-doctor-*.jar app.jar
USER doctor
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-jar", "app.jar"]
