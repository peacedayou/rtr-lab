# Construction
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# Exécution : image JRE minimale, utilisateur non root
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 rtr
COPY --from=build /src/target/rtr-lab-*.jar app.jar
USER rtr
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
