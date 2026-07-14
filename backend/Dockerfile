# Etapa 1: build
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline -q
COPY src ./src
RUN mvn -B package -DskipTests -q

# Etapa 2: runtime (imagen mínima, usuario sin privilegios)
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S nutriscan && adduser -S nutriscan -G nutriscan
USER nutriscan
WORKDIR /app
COPY --from=build /app/target/nutriscan-api-*.jar app.jar
EXPOSE 8080
HEALTHCHECK CMD wget -qO- http://localhost:8080/actuator/health | grep -q UP || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
