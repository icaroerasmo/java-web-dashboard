FROM node:20-alpine AS frontend
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src/
COPY --from=frontend /app/frontend/dist/frontend/browser ./src/main/resources/static/
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/java-web-dashboard-*.jar /app/java-web-dashboard.jar
EXPOSE 8080
ENTRYPOINT [ "java", "-jar", "/app/java-web-dashboard.jar" ]