# Build: Maven + JDK 21 (versión fija)
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# Runtime: solo JRE 21, usuario no root, sin secretos (todo entra por variables de entorno, RNF-MS01-06)
FROM eclipse-temurin:21.0.8_9-jre-noble
RUN groupadd --system identity && useradd --system --gid identity --no-create-home identity
WORKDIR /app
COPY --from=build /workspace/target/identity-service-*.jar app.jar
USER identity
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
