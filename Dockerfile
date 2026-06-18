FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY . .
RUN chmod +x mvnw && ./mvnw clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENV JAVA_TOOL_OPTIONS="-Xmx256m"
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
