FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN ./mvnw -pl api-gateway -am package -DskipTests 2>/dev/null || \
    (apt-get update -q && apt-get install -yq maven && mvn -pl api-gateway -am package -DskipTests)

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/api-gateway/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
