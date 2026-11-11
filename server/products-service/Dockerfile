# ---- dev: source is bind-mounted at /app, devtools restarts on recompile ----
FROM maven:3.9-eclipse-temurin-17 AS dev
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY dev-entrypoint.sh /usr/local/bin/dev-entrypoint.sh
RUN chmod +x /usr/local/bin/dev-entrypoint.sh
EXPOSE 8080
CMD ["dev-entrypoint.sh"]

# ---- build: fat jar ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

# ---- prod: JRE only ----
FROM eclipse-temurin:17-jre AS prod
COPY --from=build /src/target/*.jar /app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
