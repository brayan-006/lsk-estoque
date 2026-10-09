# ---- build ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

# ---- runtime ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S lsk && adduser -S lsk -G lsk
COPY --from=build /app/target/*.jar app.jar
USER lsk
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
