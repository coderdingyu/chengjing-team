FROM node:22-alpine AS frontend
WORKDIR /src/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-17 AS backend
WORKDIR /src
COPY backend/pom.xml backend/pom.xml
COPY backend/src backend/src
COPY --from=frontend /src/frontend/dist frontend/dist
RUN cd backend && mvn -B test package

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN mkdir -p /app/data
COPY --from=backend /src/backend/target/chengjing-server-0.1.0-SNAPSHOT.jar /app/chengjing.jar
ENV SERVER_ADDRESS=0.0.0.0
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/chengjing.jar"]
