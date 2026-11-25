# --- Stufe 1: JAR mit Maven bauen ---------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# Abhängigkeiten zuerst laden, damit Docker sie bei reinen Code-Änderungen cacht
COPY pom.xml .
RUN mvn -B --no-transfer-progress dependency:go-offline
COPY src ./src
# Tests laufen bereits im CI-Job "backend", hier nur paketieren
RUN mvn -B --no-transfer-progress -DskipTests package

# --- Stufe 2: schlankes Laufzeit-Image ----------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
# H2-Datei-Datenbank (jdbc:h2:file:./data/beyblade) liegt in /app/data
VOLUME /app/data
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
